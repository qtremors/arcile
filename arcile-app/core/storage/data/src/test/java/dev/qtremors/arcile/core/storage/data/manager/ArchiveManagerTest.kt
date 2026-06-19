package dev.qtremors.arcile.core.storage.data.manager

import dev.qtremors.arcile.core.storage.data.MutationFinalizer
import dev.qtremors.arcile.core.storage.data.MutationJournal
import dev.qtremors.arcile.core.storage.data.provider.VolumeProvider
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.core.storage.domain.ArchiveNameEncoding
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.StorageVolume
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
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
    private lateinit var mutationJournal: RecordingMutationJournal

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
        mutationJournal = RecordingMutationJournal()
        manager = DefaultArchiveManager(volumeProvider, finalizer, mutationJournal = mutationJournal)
    }

    @After
    fun teardown() {
        root.deleteRecursively()
    }

    @Test
    fun `archive format detects supported extensions`() {
        assertEquals(ArchiveFormat.ZIP, ArchiveFormat.fromPath("demo.ZIP"))
        assertEquals(ArchiveFormat.SEVEN_Z, ArchiveFormat.fromPath("demo.7z"))
        assertEquals(ArchiveFormat.TAR_GZIP, ArchiveFormat.fromPath("demo.tar.gz"))
        assertEquals(ArchiveFormat.TGZ, ArchiveFormat.fromPath("demo.tgz"))
        assertEquals(ArchiveFormat.TAR_BZIP2, ArchiveFormat.fromPath("demo.tar.bz2"))
        assertEquals(ArchiveFormat.TAR_XZ, ArchiveFormat.fromPath("demo.tar.xz"))
        assertEquals(ArchiveFormat.RAR, ArchiveFormat.fromPath("demo.rar"))
        assertFalse(ArchiveFormat.isSupported("demo.rar"))
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
    fun `tar archive families can be created listed and extracted`() = runTest {
        val formats = listOf(
            ArchiveFormat.TAR,
            ArchiveFormat.TAR_GZIP,
            ArchiveFormat.TGZ,
            ArchiveFormat.TAR_BZIP2,
            ArchiveFormat.TBZ2,
            ArchiveFormat.TAR_XZ,
            ArchiveFormat.TXZ
        )
        for (format in formats) {
            val sourceDir = File(root, "source-${format.extension}").apply { mkdirs() }
            File(sourceDir, "nested").mkdirs()
            File(sourceDir, "nested/file.txt").writeText("body ${format.extension}")
            val archive = File(root, "bundle.${format.extension}")
            val destination = File(root, "out-${format.extension.replace('.', '-')}").apply { mkdirs() }

            assertTrue(manager.createArchive(listOf(sourceDir.absolutePath), archive.absolutePath, format).isSuccess)
            assertTrue(manager.listArchiveEntries(archive.absolutePath).getOrThrow().any { it.path == "${sourceDir.name}/nested/file.txt" })
            assertTrue(manager.extractArchive(archive.absolutePath, destination.absolutePath).isSuccess)
            assertEquals("body ${format.extension}", File(destination, "${sourceDir.name}/nested/file.txt").readText())
        }
    }

    @Test
    fun `single stream compressed files can be listed and extracted`() = runTest {
        val gzip = compressedFile("plain.txt.gz", ArchiveFormat.GZIP, "gzip body")
        val bzip = compressedFile("plain.log.bz2", ArchiveFormat.BZIP2, "bzip body")
        val xz = compressedFile("plain.data.xz", ArchiveFormat.XZ, "xz body")

        assertEquals("plain.txt", manager.listArchiveEntries(gzip.absolutePath).getOrThrow().single().path)
        assertEquals("plain.log", manager.listArchiveEntries(bzip.absolutePath).getOrThrow().single().path)
        assertEquals("plain.data", manager.listArchiveEntries(xz.absolutePath).getOrThrow().single().path)

        val destination = File(root, "single-stream-out").apply { mkdirs() }
        assertTrue(manager.extractArchive(gzip.absolutePath, destination.absolutePath).isSuccess)
        assertTrue(manager.extractArchive(bzip.absolutePath, destination.absolutePath).isSuccess)
        assertTrue(manager.extractArchive(xz.absolutePath, destination.absolutePath).isSuccess)

        assertEquals("gzip body", File(destination, "plain.txt").readText())
        assertEquals("bzip body", File(destination, "plain.log").readText())
        assertEquals("xz body", File(destination, "plain.data").readText())
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
    fun `archive creation records unique staging path and forgets after promotion`() = runTest {
        val source = File(root, "unique.txt").apply { writeText("unique") }
        val archive = File(root, "unique.zip")

        assertTrue(manager.createArchive(listOf(source.absolutePath), archive.absolutePath, ArchiveFormat.ZIP).isSuccess)

        val stagingPath = mutationJournal.recordedTemporaryPaths.single()
        assertTrue(stagingPath.contains(".unique.zip.arcile-archive-"))
        assertFalse(stagingPath.endsWith(".unique.zip.arcile-archive.tmp"))
        assertEquals(mutationJournal.recordedTemporaryPaths, mutationJournal.forgottenTemporaryPaths)
        assertTrue(root.listFiles().orEmpty().none { it.name.contains(".arcile-archive-") })
    }

    @Test
    fun `archive creation failure before promotion journals and deletes staging file`() = runTest {
        val source = File(root, "failure.txt").apply { writeText("failure") }
        val archive = File(root, "failure.zip")
        val failingManager = DefaultArchiveManager(
            volumeProvider = volumeProvider,
            mutationFinalizer = finalizer,
            mutationJournal = mutationJournal,
            rename = { _, _ -> false }
        )

        val result = failingManager.createArchive(listOf(source.absolutePath), archive.absolutePath, ArchiveFormat.ZIP)

        assertTrue(result.isFailure)
        val stagingPath = mutationJournal.recordedTemporaryPaths.single()
        assertTrue(stagingPath.contains(".failure.zip.arcile-archive-"))
        assertFalse(File(stagingPath).exists())
        assertEquals(mutationJournal.recordedTemporaryPaths, mutationJournal.forgottenTemporaryPaths)
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
    fun `archive extraction replaces or skips conflicting entries from resolutions`() = runTest {
        val replaceSource = File(root, "replace.txt").apply { writeText("incoming replace") }
        val skipSource = File(root, "skip.txt").apply { writeText("incoming skip") }
        val archive = File(root, "conflicts.zip")
        val destination = File(root, "conflict-dest").apply { mkdirs() }
        File(destination, "replace.txt").writeText("existing replace")
        File(destination, "skip.txt").writeText("existing skip")

        assertTrue(manager.createArchive(listOf(replaceSource.absolutePath, skipSource.absolutePath), archive.absolutePath, ArchiveFormat.ZIP).isSuccess)

        assertTrue(
            manager.extractArchive(
                archive.absolutePath,
                destination.absolutePath,
                resolutions = mapOf(
                    "replace.txt" to ConflictResolution.REPLACE,
                    "skip.txt" to ConflictResolution.SKIP
                )
            ).isSuccess
        )

        assertEquals("incoming replace", File(destination, "replace.txt").readText())
        assertEquals("existing skip", File(destination, "skip.txt").readText())
        assertFalse(File(destination, "skip (1).txt").exists())
    }

    @Test
    fun `detect archive conflicts returns normalized archive entry keys`() = runTest {
        val source = File(root, "detect.txt").apply { writeText("incoming") }
        val archive = File(root, "detect.zip")
        val destination = File(root, "detect-dest").apply { mkdirs() }
        File(destination, "detect.txt").writeText("existing")

        assertTrue(manager.createArchive(listOf(source.absolutePath), archive.absolutePath, ArchiveFormat.ZIP).isSuccess)

        val conflicts = manager.detectArchiveConflicts(archive.absolutePath, destination.absolutePath).getOrThrow()

        assertEquals(listOf("detect.txt"), conflicts.map { it.sourcePath })
    }

    @Test
    fun `zip archive supports selected legacy filename encoding`() = runTest {
        val source = File(root, "café.txt").apply { writeText("legacy name") }
        val archive = File(root, "legacy.zip")
        val destination = File(root, "legacy-out").apply { mkdirs() }

        assertTrue(
            manager.createArchive(
                listOf(source.absolutePath),
                archive.absolutePath,
                ArchiveFormat.ZIP,
                nameEncoding = ArchiveNameEncoding.WINDOWS_1252
            ).isSuccess
        )
        assertTrue(
            manager.listArchiveEntries(
                archive.absolutePath,
                password = null,
                nameEncoding = ArchiveNameEncoding.WINDOWS_1252
            ).getOrThrow().any { it.path == "café.txt" }
        )
        assertTrue(
            manager.extractArchive(
                archive.absolutePath,
                destination.absolutePath,
                nameEncoding = ArchiveNameEncoding.WINDOWS_1252
            ).isSuccess
        )
        assertEquals("legacy name", File(destination, "café.txt").readText())
    }

    @Test
    fun `large archive creation switches to indeterminate progress without failing`() = runTest {
        val sourceDir = File(root, "large-tree").apply { mkdirs() }
        repeat(2_060) { index ->
            File(sourceDir, "file-$index.txt").writeText("x")
        }
        val archive = File(root, "large-tree.zip")
        val totals = mutableListOf<Int>()

        assertTrue(
            manager.createArchive(listOf(sourceDir.absolutePath), archive.absolutePath, ArchiveFormat.ZIP) { progress ->
                totals += progress.totalItems
            }.isSuccess
        )

        assertTrue(archive.isFile)
        assertTrue(totals.any { it == 0 })
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

    @Test
    fun `failed extraction restores replaced outputs`() = runTest {
        val archive = File(root, "replace-partial.zip")
        ZipArchiveOutputStream(archive).use { zip ->
            zip.putArchiveEntry(ZipArchiveEntry("same.txt"))
            zip.write("incoming".toByteArray())
            zip.closeArchiveEntry()
            zip.putArchiveEntry(ZipArchiveEntry("../bad.txt"))
            zip.write("bad".toByteArray())
            zip.closeArchiveEntry()
        }
        val destination = File(root, "replace-partial-out").apply { mkdirs() }
        File(destination, "same.txt").writeText("existing")

        val result = manager.extractArchive(
            archive.absolutePath,
            destination.absolutePath,
            resolutions = mapOf("same.txt" to ConflictResolution.REPLACE)
        )

        assertTrue(result.isFailure)
        assertEquals("existing", File(destination, "same.txt").readText())
        assertTrue(destination.listFiles().orEmpty().none { it.name.contains(".arcile-replace-") })
    }

    @Test
    fun `archive extraction replaces directory with file and restores on success`() = runTest {
        val archive = zipWithEntry("file-over-dir.zip", "target", "incoming file")
        val destination = File(root, "file-over-dir-out").apply { mkdirs() }
        File(destination, "target").apply { mkdirs() }
        File(destination, "target/old.txt").writeText("old")

        val result = manager.extractArchive(
            archive.absolutePath,
            destination.absolutePath,
            resolutions = mapOf("target" to ConflictResolution.REPLACE)
        )

        assertTrue(result.isSuccess)
        assertEquals("incoming file", File(destination, "target").readText())
    }

    @Test
    fun `archive extraction replaces file with directory`() = runTest {
        val archive = zipWithEntries(
            "dir-over-file.zip",
            "target/" to null,
            "target/new.txt" to "incoming"
        )
        val destination = File(root, "dir-over-file-out").apply { mkdirs() }
        File(destination, "target").writeText("old file")

        val result = manager.extractArchive(
            archive.absolutePath,
            destination.absolutePath,
            resolutions = mapOf("target" to ConflictResolution.REPLACE)
        )

        assertTrue(result.isSuccess)
        assertTrue(File(destination, "target").isDirectory)
        assertEquals("incoming", File(destination, "target/new.txt").readText())
    }

    @Test
    fun `archive extraction keeps both directory conflicts with nested children`() = runTest {
        val archive = zipWithEntries(
            "dir-keep-both.zip",
            "folder/" to null,
            "folder/new.txt" to "incoming"
        )
        val destination = File(root, "dir-keep-both-out").apply { mkdirs() }
        File(destination, "folder").mkdirs()
        File(destination, "folder/old.txt").writeText("old")

        val result = manager.extractArchive(archive.absolutePath, destination.absolutePath)

        assertTrue(result.isSuccess)
        assertEquals("old", File(destination, "folder/old.txt").readText())
        assertEquals("incoming", File(destination, "folder (1)/new.txt").readText())
    }

    @Test
    fun `same archive manager does not leak directory conflict state between extractions`() = runTest {
        val firstArchive = zipWithEntries(
            "first-dir-conflict.zip",
            "folder/" to null,
            "folder/new.txt" to "incoming"
        )
        val secondArchive = zipWithEntries(
            "second-dir-clean.zip",
            "other/" to null,
            "other/file.txt" to "second"
        )
        val destination = File(root, "same-handler-state-out").apply { mkdirs() }
        File(destination, "folder").mkdirs()
        File(destination, "folder/old.txt").writeText("old")

        assertTrue(manager.extractArchive(firstArchive.absolutePath, destination.absolutePath).isSuccess)
        assertTrue(manager.extractArchive(secondArchive.absolutePath, destination.absolutePath).isSuccess)

        assertEquals("incoming", File(destination, "folder (1)/new.txt").readText())
        assertEquals("second", File(destination, "other/file.txt").readText())
        assertFalse(File(destination, "other (1)").exists())
    }

    @Test
    fun `failed nested directory replace restores old tree`() = runTest {
        val archive = File(root, "dir-replace-partial.zip")
        ZipArchiveOutputStream(archive).use { zip ->
            zip.putArchiveEntry(ZipArchiveEntry("folder/"))
            zip.closeArchiveEntry()
            zip.putArchiveEntry(ZipArchiveEntry("folder/new.txt"))
            zip.write("incoming".toByteArray())
            zip.closeArchiveEntry()
            zip.putArchiveEntry(ZipArchiveEntry("../bad.txt"))
            zip.write("bad".toByteArray())
            zip.closeArchiveEntry()
        }
        val destination = File(root, "dir-replace-partial-out").apply { mkdirs() }
        File(destination, "folder").mkdirs()
        File(destination, "folder/old.txt").writeText("old")

        val result = manager.extractArchive(
            archive.absolutePath,
            destination.absolutePath,
            resolutions = mapOf("folder" to ConflictResolution.REPLACE)
        )

        assertTrue(result.isFailure)
        assertEquals("old", File(destination, "folder/old.txt").readText())
        assertFalse(File(destination, "folder/new.txt").exists())
        assertTrue(destination.listFiles().orEmpty().none { it.name.contains(".arcile-replace-") })
    }

    @Test
    fun `wrong password extraction leaves existing replacement target intact`() = runTest {
        val source = File(root, "protected.txt").apply { writeText("incoming secret") }
        val archive = File(root, "protected.zip")
        val destination = File(root, "protected-out").apply { mkdirs() }
        File(destination, "protected.txt").writeText("existing")
        assertTrue(
            manager.createArchive(
                listOf(source.absolutePath),
                archive.absolutePath,
                ArchiveFormat.ZIP,
                password = "correct"
            ).isSuccess
        )

        val result = manager.extractArchive(
            archive.absolutePath,
            destination.absolutePath,
            password = "wrong",
            resolutions = mapOf("protected.txt" to ConflictResolution.REPLACE)
        )

        assertTrue(result.isFailure)
        assertEquals("existing", File(destination, "protected.txt").readText())
    }

    private fun managerWith(policy: ArchiveSafetyPolicy): DefaultArchiveManager {
        return DefaultArchiveManager(volumeProvider, finalizer, policy, mutationJournal = mutationJournal)
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

    private fun zipWithEntries(name: String, vararg entries: Pair<String, String?>): File {
        val archive = File(root, name)
        ZipArchiveOutputStream(archive).use { zip ->
            entries.forEach { (entryName, body) ->
                zip.putArchiveEntry(ZipArchiveEntry(entryName))
                body?.let { zip.write(it.toByteArray()) }
                zip.closeArchiveEntry()
            }
        }
        return archive
    }

    private fun compressedFile(name: String, format: ArchiveFormat, body: String): File {
        val archive = File(root, name)
        val output = when (format) {
            ArchiveFormat.GZIP -> GzipCompressorOutputStream(archive.outputStream())
            ArchiveFormat.BZIP2 -> BZip2CompressorOutputStream(archive.outputStream())
            ArchiveFormat.XZ -> XZCompressorOutputStream(archive.outputStream())
            else -> error("Unsupported single-stream test format")
        }
        output.use { it.write(body.toByteArray()) }
        return archive
    }

    private class RecordingMutationJournal : MutationJournal {
        val recordedTemporaryPaths = mutableListOf<String>()
        val forgottenTemporaryPaths = mutableListOf<String>()

        override fun recordTemporaryPath(path: String) {
            recordedTemporaryPaths += path
        }

        override fun forgetTemporaryPath(path: String) {
            forgottenTemporaryPaths += path
        }

        override fun recordTrashFallback(sourcePath: String, payloadPath: String, metadataPath: String) = Unit
        override fun forgetTrashFallback(payloadPath: String, metadataPath: String) = Unit
        override suspend fun cleanupAbandonedMutations() = Unit
    }
}
