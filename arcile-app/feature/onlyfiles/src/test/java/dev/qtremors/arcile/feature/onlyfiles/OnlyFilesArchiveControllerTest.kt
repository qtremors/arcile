package dev.qtremors.arcile.feature.onlyfiles

import dev.qtremors.arcile.core.vault.domain.DirectoryId
import dev.qtremors.arcile.core.vault.domain.NodeId
import dev.qtremors.arcile.core.vault.domain.VaultArchiveSource
import dev.qtremors.arcile.core.vault.domain.VaultFileSystem
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultNodeCapabilities
import dev.qtremors.arcile.core.vault.domain.VaultNodeKind
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import dev.qtremors.arcile.core.vault.domain.VaultNodeRef
import dev.qtremors.arcile.core.vault.domain.VaultPage
import dev.qtremors.arcile.core.vault.domain.VaultPath
import dev.qtremors.arcile.core.vault.domain.VaultSeekableReader
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnlyFilesArchiveControllerTest {
    private val vault = VaultId.of("vault")
    private val root = DirectoryId.Root

    @Test
    fun `extracting a selection reads only entries under that selection and strips its folder prefix`() = runTest {
        val payload = zipBytes(
            "docs/one.txt" to "one",
            "docs/chapter/two.txt" to "two",
            "outside.txt" to "outside"
        )
        val source = file("source.zip", payload.size.toLong())
        val fs = mockk<VaultFileSystem>()
        val reader = MemoryReader(payload)
        every { fs.openReader(source.ref) } returns Result.success(reader)
        val extracted = mutableMapOf<List<String>, String>()
        coEvery { fs.extractArchive(vault, root, "Result", any(), false, any()) } coAnswers {
            val entries = arg<List<VaultArchiveSource>>(3)
            entries.filterIsInstance<VaultArchiveSource.File>().forEach {
                extracted[it.path] = it.open().use { input -> input.readBytes().decodeToString() }
            }
            Result.success(folder("Result"))
        }
        val state = mounted()
        var reloads = 0
        val controller = OnlyFilesArchiveController(fs, state, this, { reloads++ }, { throw it })

        controller.extract(
            source, root, VaultZipSelection(listOf("docs"), setOf(listOf("docs", "chapter"))),
            "Result", "Extracted"
        )
        state.first { !it.busy }

        assertEquals(mapOf(listOf("chapter", "two.txt") to "two"), extracted)
        assertTrue(reader.closed)
        assertEquals(1, reloads)
        assertEquals("Extracted", state.value.message)
        assertEquals("Result", state.value.completedArchive?.name)
    }

    @Test
    fun `whole archive extraction rejects a traversal entry before writing output`() = runTest {
        val payload = zipBytes("valid.txt" to "valid", "../outside.txt" to "invalid")
        val source = file("source.zip", payload.size.toLong())
        val fs = mockk<VaultFileSystem>()
        every { fs.openReader(source.ref) } returns Result.success(MemoryReader(payload))
        val state = mounted()
        var failed = false
        val controller = OnlyFilesArchiveController(fs, state, this, { error("Unexpected reload") }, { failed = true })

        controller.extract(source, root, VaultZipSelection(emptyList(), emptySet()), "Result", "Extracted")
        state.first { !it.busy }

        assertTrue(failed)
        coVerify(exactly = 0) { fs.extractArchive(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `creation streams a selected directory and reads children across pages`() = runTest {
        val docs = folder("docs")
        val parent = requireNotNull(docs.ref.directoryId)
        val first = file("one.txt", 3, parent)
        val second = file("two.txt", 3, parent)
        val fs = mockk<VaultFileSystem>()
        coEvery { fs.listDirectory(vault, parent, match { it.pageToken == null }) } returns
            Result.success(VaultPage(listOf(first), "next", 1))
        coEvery { fs.listDirectory(vault, parent, match { it.pageToken == "next" }) } returns
            Result.success(VaultPage(listOf(second), null, 1))
        every { fs.openReader(first.ref) } returns Result.success(MemoryReader("one".encodeToByteArray()))
        every { fs.openReader(second.ref) } returns Result.success(MemoryReader("two".encodeToByteArray()))
        val output = ByteArrayOutputStream()
        coEvery { fs.createZip(vault, root, "docs.zip", any()) } coAnswers {
            arg<(OutputStream) -> Unit>(3)(output)
            Result.success(file("docs.zip", output.size().toLong()))
        }
        val state = mounted()
        val controller = OnlyFilesArchiveController(fs, state, this, {}, { throw it })
        controller.create(listOf(docs), "docs.zip", 1, "Created")
        advanceUntilIdle()

        ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zip ->
            assertEquals("docs/", zip.nextEntry.name)
            assertEquals("docs/one.txt", zip.nextEntry.name)
            assertEquals("one", zip.readBytes().decodeToString())
            assertEquals("docs/two.txt", zip.nextEntry.name)
            assertEquals("two", zip.readBytes().decodeToString())
            assertNull(zip.nextEntry)
        }
        assertEquals("Created", state.value.message)
    }

    @Test
    fun `a result from a prior mount cannot expose its name after lock`() = runTest {
        val fs = mockk<VaultFileSystem>()
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        coEvery { fs.createZip(vault, root, "private.zip", any()) } coAnswers {
            started.complete(Unit)
            finish.await()
            Result.success(file("private.zip", 10))
        }
        val state = mounted()
        var reloads = 0
        val controller = OnlyFilesArchiveController(fs, state, this, { reloads++ }, { throw it })
        controller.create(listOf(file("source.txt", 0)), "private.zip", 1, "Created")
        runCurrent()
        assertTrue(started.isCompleted)

        state.value = OnlyFilesUiState(mountGeneration = 2)
        finish.complete(Unit)
        advanceUntilIdle()

        assertEquals(0, reloads)
        assertNull(state.value.completedArchive)
        assertNull(state.value.message)
        assertNull(state.value.archiveProgress)
    }

    @Test
    fun `cancelling before the writer starts releases the busy state`() = runTest {
        val fs = mockk<VaultFileSystem>()
        val state = mounted()
        val controller = OnlyFilesArchiveController(fs, state, this, { error("Unexpected reload") }, { throw it })

        controller.create(listOf(file("source.txt", 0)), "archive.zip", 1, "Created")
        assertTrue(state.value.busy)
        controller.cancel()
        advanceUntilIdle()

        coVerify(exactly = 0) { fs.createZip(any(), any(), any(), any()) }
        assertFalse(state.value.busy)
        assertNull(state.value.archiveProgress)
        assertNull(state.value.completedArchive)
    }

    @Test
    fun `cancel clears progress without announcing a completed archive`() = runTest {
        val source = file("source.txt", 0)
        val fs = mockk<VaultFileSystem>()
        val writerStarted = CompletableDeferred<Unit>()
        coEvery { fs.createZip(vault, root, "archive.zip", any()) } coAnswers {
            writerStarted.complete(Unit)
            kotlinx.coroutines.awaitCancellation()
        }
        val state = mounted()
        val controller = OnlyFilesArchiveController(fs, state, this, { error("Unexpected reload") }, { throw it })
        controller.create(listOf(source), "archive.zip", 1, "Created")
        runCurrent()
        assertTrue(writerStarted.isCompleted)

        controller.cancel()
        advanceUntilIdle()

        assertFalse(state.value.busy)
        assertNull(state.value.archiveProgress)
        assertNull(state.value.message)
    }

    private fun mounted() = MutableStateFlow(OnlyFilesUiState(
        selectedVaultId = vault, mountGeneration = 1L,
        directoryStack = listOf(VaultDirectoryCrumb(root, "", VaultPath.Root))
    ))

    private fun file(name: String, bytes: Long, parent: DirectoryId = root) = VaultNodeMetadata(
        VaultNodeRef(vault, NodeId.of(name), parent, VaultNodeCapabilities()),
        name, VaultNodeKind.FILE, bytes, 0L, 1L
    )

    private fun folder(name: String) = VaultNodeMetadata(
        VaultNodeRef(vault, NodeId.of(name), root, VaultNodeCapabilities(), directoryId = DirectoryId.of(name)),
        name, VaultNodeKind.DIRECTORY, 0L, 0L, 1L
    )

    private fun zipBytes(vararg files: Pair<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            for ((path, content) in files) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(content.encodeToByteArray())
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private class MemoryReader(private val data: ByteArray) : VaultSeekableReader {
        var closed = false
            private set
        override val sizeBytes: Long get() = data.size.toLong()
        override fun readAt(position: Long, target: ByteArray, offset: Int, length: Int): Int {
            if (position >= data.size) return -1
            val count = minOf(length, data.size - position.toInt())
            data.copyInto(target, offset, position.toInt(), position.toInt() + count)
            return count
        }
        override fun close() {
            closed = true
        }
    }
}
