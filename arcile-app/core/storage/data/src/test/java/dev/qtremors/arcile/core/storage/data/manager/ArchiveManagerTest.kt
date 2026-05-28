package dev.qtremors.arcile.core.storage.data.manager

import dev.qtremors.arcile.core.storage.data.MutationFinalizer
import dev.qtremors.arcile.core.storage.data.provider.VolumeProvider
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.core.storage.domain.StorageVolume
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class ArchiveManagerTest {
    private lateinit var root: File
    private lateinit var manager: DefaultArchiveManager
    private lateinit var volumeProvider: VolumeProvider
    private lateinit var finalizer: MutationFinalizer

    @Before
    fun setup() {
        root = createTempDir(prefix = "archive-manager-test").canonicalFile
        volumeProvider = object : VolumeProvider {
            override val activeStorageRoots: List<String> = listOf(root.absolutePath)
            override fun observeStorageVolumes(): Flow<List<StorageVolume>> = flowOf(emptyList())
            override suspend fun getStorageVolumes(): Result<List<StorageVolume>> = Result.success(emptyList())
            override suspend fun currentVolumes(): List<StorageVolume> = emptyList()
            override fun invalidateCache() = Unit
        }
        finalizer = mockk<MutationFinalizer>(relaxed = true)
        coEvery { finalizer.finalize(*anyVararg()) } returns Unit
        manager = DefaultArchiveManager(volumeProvider, finalizer)
    }

    @After
    fun teardown() {
        root.deleteRecursively()
    }

    @Test
    fun `archive format detects supported extensions`() {
        assertEquals(ArchiveFormat.ZIP, ArchiveFormat.fromPath("demo.ZIP"))
        assertEquals(ArchiveFormat.SEVEN_Z, ArchiveFormat.fromPath("demo.7z"))
        assertEquals(null, ArchiveFormat.fromPath("demo.rar"))
    }

    @Test
    fun `zip archive can be created listed and extracted`() = runTest {
        val sourceDir = File(root, "source").apply { mkdirs() }
        File(sourceDir, "nested").mkdirs()
        File(sourceDir, "nested/hello.txt").writeText("hello archive")
        val archive = File(root, "bundle.zip")
        val destination = File(root, "out").apply { mkdirs() }

        assertTrue(manager.createArchive(listOf(sourceDir.absolutePath), archive.absolutePath, ArchiveFormat.ZIP).isSuccess)
        val entries = manager.listArchiveEntries(archive.absolutePath).getOrThrow()
        val summary = manager.getArchiveMetadata(archive.absolutePath).getOrThrow()
        assertTrue(entries.any { it.path == "source/nested/hello.txt" })
        assertEquals(1, summary.fileCount)

        assertTrue(manager.extractArchive(archive.absolutePath, destination.absolutePath).isSuccess)
        assertEquals("hello archive", File(destination, "source/nested/hello.txt").readText())
    }

    @Test
    fun `7z archive can be created listed and extracted`() = runTest {
        val source = File(root, "note.txt").apply { writeText("seven zip") }
        val archive = File(root, "note.7z")
        val destination = File(root, "out7z").apply { mkdirs() }

        assertTrue(manager.createArchive(listOf(source.absolutePath), archive.absolutePath, ArchiveFormat.SEVEN_Z).isSuccess)
        assertTrue(manager.listArchiveEntries(archive.absolutePath).getOrThrow().any { it.path == "note.txt" })
        assertTrue(manager.extractArchive(archive.absolutePath, destination.absolutePath).isSuccess)
        assertEquals("seven zip", File(destination, "note.txt").readText())
    }

    @Test
    fun `password zip archive can be created listed and extracted`() = runTest {
        val source = File(root, "secret.txt").apply { writeText("zip secret") }
        val archive = File(root, "secret.zip")
        val destination = File(root, "zip-secret-out").apply { mkdirs() }
        val progress = mutableListOf<Long>()

        assertTrue(
            manager.createArchive(
                listOf(source.absolutePath),
                archive.absolutePath,
                ArchiveFormat.ZIP,
                password = "pass123"
            ) { update ->
                progress += update.bytesCopied ?: 0L
            }.isSuccess
        )
        assertTrue(manager.listArchiveEntries(archive.absolutePath).isFailure)
        assertTrue(manager.listArchiveEntries(archive.absolutePath, "pass123").getOrThrow().any { it.path == "secret.txt" })

        assertTrue(manager.extractArchive(archive.absolutePath, destination.absolutePath, password = "pass123").isSuccess)
        assertEquals("zip secret", File(destination, "secret.txt").readText())
        assertEquals(source.length(), progress.last())
    }

    @Test
    fun `password 7z archive can be created listed and extracted`() = runTest {
        val source = File(root, "vault.txt").apply { writeText("seven secret") }
        val archive = File(root, "vault.7z")
        val destination = File(root, "seven-secret-out").apply { mkdirs() }

        assertTrue(
            manager.createArchive(
                listOf(source.absolutePath),
                archive.absolutePath,
                ArchiveFormat.SEVEN_Z,
                password = "pass123"
            ).isSuccess
        )
        assertTrue(manager.listArchiveEntries(archive.absolutePath, "pass123").getOrThrow().any { it.path == "vault.txt" })

        assertTrue(manager.extractArchive(archive.absolutePath, File(root, "seven-wrong-out").absolutePath, password = "wrong").isFailure)
        assertTrue(manager.extractArchive(archive.absolutePath, destination.absolutePath, password = "pass123").isSuccess)
        assertEquals("seven secret", File(destination, "vault.txt").readText())
    }

    @Test
    fun `wrong archive password returns friendly failure`() = runTest {
        val source = File(root, "wrong-password.txt").apply { writeText("secret") }
        val archive = File(root, "wrong-password.zip")

        assertTrue(
            manager.createArchive(
                listOf(source.absolutePath),
                archive.absolutePath,
                ArchiveFormat.ZIP,
                password = "correct"
            ).isSuccess
        )

        val result = manager.extractArchive(archive.absolutePath, File(root, "wrong-out").absolutePath, password = "wrong")

        assertTrue(result.isFailure)
        assertEquals("A password is required or the password is incorrect", result.exceptionOrNull()?.message)
    }

    @Test
    fun `zip extraction rejects traversal entries`() = runTest {
        val archive = File(root, "unsafe.zip")
        ZipArchiveOutputStream(archive).use { zip ->
            zip.putArchiveEntry(ZipArchiveEntry("../evil.txt"))
            zip.write("bad".toByteArray())
            zip.closeArchiveEntry()
        }
        val destination = File(root, "safe").apply { mkdirs() }

        val result = manager.extractArchive(archive.absolutePath, destination.absolutePath)

        assertTrue(result.isFailure)
        assertFalse(File(root, "evil.txt").exists())
    }

    @Test
    fun `extraction keeps both when destination file exists`() = runTest {
        val source = File(root, "same.txt").apply { writeText("incoming") }
        val archive = File(root, "same.zip")
        val destination = File(root, "dest").apply { mkdirs() }
        File(destination, "same.txt").writeText("existing")

        assertTrue(manager.createArchive(listOf(source.absolutePath), archive.absolutePath, ArchiveFormat.ZIP).isSuccess)
        assertTrue(manager.extractArchive(archive.absolutePath, destination.absolutePath).isSuccess)

        assertEquals("existing", File(destination, "same.txt").readText())
        assertEquals("incoming", File(destination, "same (1).txt").readText())
    }

    @Test
    fun `archive extraction rejects excessive entry count`() = runTest {
        val archive = File(root, "too-many.zip")
        ZipArchiveOutputStream(archive).use { zip ->
            repeat(2) { index ->
                zip.putArchiveEntry(ZipArchiveEntry("file-$index.txt"))
                zip.write("x".toByteArray())
                zip.closeArchiveEntry()
            }
        }
        val strictManager = managerWith(ArchiveSafetyPolicy(maxEntries = 1))

        val result = strictManager.extractArchive(archive.absolutePath, File(root, "too-many-out").apply { mkdirs() }.absolutePath)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("too many entries") == true)
    }

    @Test
    fun `archive extraction rejects unsafe path length nesting and size`() = runTest {
        val pathArchive = zipWithEntry("long-path.zip", "${"a".repeat(12)}.txt", "x")
        val nestedArchive = zipWithEntry("nested.zip", "a/b/c/d.txt", "x")
        val largeArchive = zipWithEntry("large.zip", "large.txt", "oversized")

        val pathResult = managerWith(ArchiveSafetyPolicy(maxEntryPathLength = 8))
            .extractArchive(pathArchive.absolutePath, File(root, "path-out").apply { mkdirs() }.absolutePath)
        val nestedResult = managerWith(ArchiveSafetyPolicy(maxNestedDepth = 2))
            .extractArchive(nestedArchive.absolutePath, File(root, "nested-out").apply { mkdirs() }.absolutePath)
        val sizeResult = managerWith(ArchiveSafetyPolicy(maxUncompressedBytes = 2))
            .extractArchive(largeArchive.absolutePath, File(root, "large-out").apply { mkdirs() }.absolutePath)

        assertTrue(pathResult.isFailure)
        assertTrue(nestedResult.isFailure)
        assertTrue(sizeResult.isFailure)
    }

    @Test
    fun `archive extraction rejects excessive compression ratio`() = runTest {
        val archive = zipWithEntry("ratio.zip", "ratio.txt", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
        val strictManager = managerWith(ArchiveSafetyPolicy(maxCompressionRatio = 0.1))

        val result = strictManager.extractArchive(archive.absolutePath, File(root, "ratio-out").apply { mkdirs() }.absolutePath)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("compression ratio") == true)
    }

    @Test
    fun `failed extraction removes created partial outputs`() = runTest {
        val archive = File(root, "partial.zip")
        ZipArchiveOutputStream(archive).use { zip ->
            zip.putArchiveEntry(ZipArchiveEntry("ok.txt"))
            zip.write("ok".toByteArray())
            zip.closeArchiveEntry()
            zip.putArchiveEntry(ZipArchiveEntry("../bad.txt"))
            zip.write("bad".toByteArray())
            zip.closeArchiveEntry()
        }
        val destination = File(root, "partial-out").apply { mkdirs() }

        val result = manager.extractArchive(archive.absolutePath, destination.absolutePath)

        assertTrue(result.isFailure)
        assertFalse(File(destination, "ok.txt").exists())
        assertFalse(File(root, "bad.txt").exists())
    }

    private fun managerWith(policy: ArchiveSafetyPolicy): DefaultArchiveManager {
        return DefaultArchiveManager(volumeProvider, finalizer, policy)
    }

    private fun zipWithEntry(name: String, entryName: String, body: String): File {
        val archive = File(root, name)
        ZipArchiveOutputStream(archive).use { zip ->
            zip.putArchiveEntry(ZipArchiveEntry(entryName))
            zip.write(body.toByteArray())
            zip.closeArchiveEntry()
        }
        return archive
    }
}
