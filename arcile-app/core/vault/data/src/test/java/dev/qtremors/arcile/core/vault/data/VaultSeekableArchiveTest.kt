package dev.qtremors.arcile.core.vault.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.core.archive.ArchiveReadSource
import dev.qtremors.arcile.core.archive.ArchiveReader
import dev.qtremors.arcile.core.archive.SevenZipArchiveWriter
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import dev.qtremors.arcile.core.storage.domain.ArchiveCompressionLevel
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.core.storage.domain.StorageVolume
import dev.qtremors.arcile.core.storage.domain.VolumeRepository
import dev.qtremors.arcile.core.vault.domain.DirectoryId
import dev.qtremors.arcile.core.vault.domain.VAULT_ARCHIVE_MAX_BYTES
import dev.qtremors.arcile.core.vault.domain.VaultFailure
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VaultSeekableArchiveTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val mime = "application/x-7z-compressed"

    @Test fun `7z headers can be rewritten before a verified encrypted file is published`() = runTest {
        fixture { f ->
            val payload = "Private archive contents".repeat(4000).encodeToByteArray()
            val password = "protected".toCharArray()
            val result = f.repository.createSeekableArchiveFile(f.vault, DirectoryId.Root, "Result.7z", mime) { channel ->
                SevenZipArchiveWriter.open(channel, password, ArchiveCompressionLevel.FAST, closeChannel = false).use { archive ->
                    archive.add(SevenZArchiveEntry().apply { name = "Document.txt"; size = payload.size.toLong() }) { payload.inputStream() }
                }
                assertTrue(channel.isOpen)
                assertTrue(kotlinx.coroutines.runBlocking { f.rows() }.isEmpty())
                f.assertNoPlaintext("Private archive contents")
            }.getOrThrow()
            assertEquals(mime, result.mimeType)
            assertEquals(listOf(result), f.rows())
            val bytes = f.bytes(result)
            ArchiveReader().open(ArchiveReadSource(result.name, bytes.size.toLong(), null) { SeekableInMemoryByteChannel(bytes) },
                ArchiveFormat.SEVEN_Z, password).use { archive ->
                assertEquals("Document.txt", archive.entries.single().segments!!.single())
                assertArrayEquals(payload, archive.entries.single().openStream().use { it.readBytes() })
            }
            password.fill('\u0000')
            assertTrue(f.scratch.listFiles()!!.isEmpty())
            f.assertNoPlaintext("Private archive contents")
        }
    }

    @Test fun `encoder failure publishes nothing and removes the encrypted scratch and object`() = runTest {
        fixture { f ->
            val before = f.objectPaths()
            var captured: SeekableByteChannel? = null
            val failure = f.repository.createSeekableArchiveFile(f.vault, DirectoryId.Root, "Broken.7z", mime) { channel ->
                captured = channel
                channel.write(ByteBuffer.wrap(ByteArray(150_000) { 91 }))
                error("Interrupted encoder")
            }
            assertTrue(failure.isFailure)
            assertFalse(requireNotNull(captured).isOpen)
            assertTrue(f.rows().isEmpty())
            assertEquals(before, f.objectPaths())
            assertTrue(f.scratch.listFiles()!!.isEmpty())
        }
    }

    @Test fun `name collisions and locked vaults are rejected before calling the encoder`() = runTest {
        fixture { f ->
            f.repository.createEmptyFile(f.vault, DirectoryId.Root, "Existing.7z").getOrThrow()
            var calls = 0
            assertTrue(f.repository.createSeekableArchiveFile(f.vault, DirectoryId.Root, "existing.7z", mime) { calls++ }.isFailure)
            f.repository.lock(f.vault)
            val failure = f.repository.createSeekableArchiveFile(f.vault, DirectoryId.Root, "New.7z", mime) { calls++ }
            assertTrue(failure.exceptionOrNull() is VaultFailure.Locked)
            assertEquals(0, calls)
            assertFalse(f.scratch.exists())
        }
    }

    @Test fun `an encoder cannot exceed the existing vault archive limit by seeking`() = runTest {
        fixture { f ->
            val before = f.objectPaths()
            val result = f.repository.createSeekableArchiveFile(f.vault, DirectoryId.Root, "Huge.7z", mime) { channel ->
                channel.position(VAULT_ARCHIVE_MAX_BYTES - 1)
                channel.write(ByteBuffer.wrap(byteArrayOf(1, 2)))
            }
            assertTrue(result.isFailure)
            assertTrue(f.rows().isEmpty())
            assertEquals(before, f.objectPaths())
            assertTrue(f.scratch.listFiles()!!.isEmpty())
        }
    }

    @Test fun `header rewriting and truncation do not retain discarded private output`() = runTest {
        fixture { f ->
            val result = f.repository.createSeekableArchiveFile(f.vault, DirectoryId.Root, "Rewritten.7z", mime) { channel ->
                channel.write(ByteBuffer.wrap(ByteArray(170_000) { 41 }))
                channel.truncate(70_000)
                channel.position(0).write(ByteBuffer.wrap("HEADER".encodeToByteArray()))
                channel.position(75_000).write(ByteBuffer.wrap(byteArrayOf(99)))
            }.getOrThrow()
            val bytes = f.bytes(result)
            assertEquals(75_001, bytes.size)
            assertEquals("HEADER", bytes.copyOf(6).decodeToString())
            assertTrue(bytes.sliceArray(6 until 70_000).all { it == 41.toByte() })
            assertTrue(bytes.sliceArray(70_000 until 75_000).all { it == 0.toByte() })
            assertEquals(99, bytes.last().toInt())
        }
    }

    @Test fun `cancelling during generation removes output and releases the vault for another mutation`() = runTest {
        fixture { f ->
            val before = f.objectPaths()
            var captured: SeekableByteChannel? = null
            val operation = async {
                val ownJob = currentCoroutineContext().job
                f.repository.createSeekableArchiveFile(f.vault, DirectoryId.Root, "Cancelled.7z", mime) { channel ->
                    captured = channel
                    channel.write(ByteBuffer.wrap(ByteArray(150_000) { 91 }))
                    ownJob.cancel()
                    // Every protected channel access checks this operation's cancellation.
                    channel.position(0)
                }
            }
            assertTrue(runCatching { operation.await() }.exceptionOrNull() is CancellationException)
            assertFalse(requireNotNull(captured).isOpen)
            assertTrue(f.rows().isEmpty())
            assertEquals(before, f.objectPaths())
            assertTrue(f.scratch.listFiles()!!.isEmpty())
            val next = f.repository.createEmptyFile(f.vault, DirectoryId.Root, "Next.txt").getOrThrow()
            assertEquals(listOf(next), f.rows())
        }
    }

    @Test fun `a prematurely closed borrowed output is rejected instead of publishing empty data`() = runTest {
        fixture { f ->
            assertTrue(f.repository.createSeekableArchiveFile(f.vault, DirectoryId.Root, "Closed.7z", mime) { channel ->
                channel.write(ByteBuffer.wrap(byteArrayOf(1)))
                channel.close()
            }.isFailure)
            assertTrue(f.rows().isEmpty())
            assertTrue(f.scratch.listFiles()!!.isEmpty())
        }
    }

    @Test fun `opening new scratch removes abandoned encrypted output without removing other files`() = runTest {
        fixture { f ->
            check(f.scratch.mkdirs() || f.scratch.isDirectory)
            File(f.scratch, "protected-abandoned.scratch").writeBytes(ByteArray(100))
            val unrelated = File(f.scratch, "keep.txt").apply { writeText("keep") }
            f.repository.createSeekableArchiveFile(f.vault, DirectoryId.Root, "Current.7z", mime) {
                assertFalse(File(f.scratch, "protected-abandoned.scratch").exists())
                it.write(ByteBuffer.wrap(byteArrayOf(1, 2)))
            }.getOrThrow()
            assertEquals(listOf(unrelated.name), f.scratch.list()!!.toList())
        }
    }

    private inner class Fixture {
        private val dispatcher = UnconfinedTestDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        private val volumes = object : VolumeRepository {
            val root = context.cacheDir
            val volume = StorageVolume("test", "test", "Test", root.path, root.totalSpace, root.freeSpace, true, false)
            override fun observeStorageVolumes() = MutableStateFlow(listOf(volume))
            override suspend fun getStorageVolumes() = Result.success(listOf(volume))
            override suspend fun getVolumeForPath(path: String) = Result.success(volume)
            override fun getStandardFolders(): Map<String, String?> = emptyMap()
        }
        val repository = DefaultVaultRepository(context, ArcileDispatchers(dispatcher, dispatcher, dispatcher, dispatcher),
            scope, VaultPortableLocationResolver(volumes))
        val scratch = File(context.cacheDir, "vault-protected-scratch")
        var vault = VaultId.of("pending")
        suspend fun rows() = repository.listDirectory(vault, DirectoryId.Root).getOrThrow().items
        fun objectPaths() = File(context.noBackupFilesDir, ROOT_DIRECTORY).walkTopDown()
            .filter { it.isFile && it.parentFile?.parentFile?.name == "objects" }.map { it.path }.toSet()
        fun bytes(node: VaultNodeMetadata) = repository.openReader(node.ref).getOrThrow().use { reader ->
            ByteArray(reader.sizeBytes.toInt()).also { output ->
                var offset = 0
                while (offset < output.size) {
                    val count = reader.readAt(offset.toLong(), output, offset, output.size - offset)
                    check(count > 0)
                    offset += count
                }
            }
        }
        fun assertNoPlaintext(marker: String) {
            for (root in listOf(scratch, File(context.noBackupFilesDir, ROOT_DIRECTORY))) {
                root.walkTopDown().filter(File::isFile).forEach { assertFalse(it.readBytes().decodeToString().contains(marker)) }
            }
        }
    }

    private suspend fun fixture(test: suspend (Fixture) -> Unit) {
        val fixture = Fixture()
        try {
            fixture.vault = fixture.repository.createAppPrivateVault("Archives", "password".toCharArray()).getOrThrow()
            test(fixture)
        } finally { fixture.repository.lockAll(); fixture.scope.cancel() }
    }
}
