package dev.qtremors.arcile.data.manager

import dev.qtremors.arcile.data.MutationFinalizer
import dev.qtremors.arcile.data.provider.VolumeProvider
import dev.qtremors.arcile.domain.ArchiveFormat
import dev.qtremors.arcile.domain.StorageVolume
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

    @Before
    fun setup() {
        root = createTempDir(prefix = "archive-manager-test").canonicalFile
        val volumeProvider = object : VolumeProvider {
            override val activeStorageRoots: List<String> = listOf(root.absolutePath)
            override fun observeStorageVolumes(): Flow<List<StorageVolume>> = flowOf(emptyList())
            override suspend fun getStorageVolumes(): Result<List<StorageVolume>> = Result.success(emptyList())
            override suspend fun currentVolumes(): List<StorageVolume> = emptyList()
            override fun invalidateCache() = Unit
        }
        val finalizer = mockk<MutationFinalizer>(relaxed = true)
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
    fun `zip extraction skips traversal entries without aborting`() = runTest {
        val archive = File(root, "unsafe.zip")
        ZipArchiveOutputStream(archive).use { zip ->
            zip.putArchiveEntry(ZipArchiveEntry("../evil.txt"))
            zip.write("bad".toByteArray())
            zip.closeArchiveEntry()
        }
        val destination = File(root, "safe").apply { mkdirs() }

        val result = manager.extractArchive(archive.absolutePath, destination.absolutePath)

        assertTrue(result.isSuccess)
        assertFalse(File(root, "evil.txt").exists())
    }

    @Test
    fun `zip extraction continues past unsafe entry in the middle`() = runTest {
        val archive = File(root, "mixed.zip")
        ZipArchiveOutputStream(archive).use { zip ->
            zip.putArchiveEntry(ZipArchiveEntry("before_good.txt"))
            zip.write("before".toByteArray())
            zip.closeArchiveEntry()

            zip.putArchiveEntry(ZipArchiveEntry("../escape.txt"))
            zip.write("escape".toByteArray())
            zip.closeArchiveEntry()

            zip.putArchiveEntry(ZipArchiveEntry("after_good.txt"))
            zip.write("after".toByteArray())
            zip.closeArchiveEntry()
        }
        val destination = File(root, "mixed-dest").apply { mkdirs() }

        val result = manager.extractArchive(archive.absolutePath, destination.absolutePath)

        assertTrue(result.isSuccess)
        assertTrue(File(destination, "before_good.txt").exists())
        assertTrue(File(destination, "after_good.txt").exists())
        assertEquals("before", File(destination, "before_good.txt").readText())
        assertEquals("after", File(destination, "after_good.txt").readText())
        assertFalse(File(destination.parentFile, "escape.txt").exists())
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
}
