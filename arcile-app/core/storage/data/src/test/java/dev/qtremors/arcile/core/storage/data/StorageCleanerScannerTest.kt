package dev.qtremors.arcile.core.storage.data

import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import dev.qtremors.arcile.core.storage.domain.CleanerGroupType
import dev.qtremors.arcile.core.storage.domain.CleanerRiskLevel
import dev.qtremors.arcile.core.storage.domain.CleanerRiskReason
import dev.qtremors.arcile.core.storage.domain.CleanerSectionRule
import dev.qtremors.arcile.core.storage.domain.StorageCleanerScanLimits
import dev.qtremors.arcile.core.storage.domain.StorageCleanerRules
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class StorageCleanerScannerTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()
    private val scanner = DefaultStorageCleanerScanner(
        ArcileDispatchers(
            io = dispatcher,
            default = dispatcher,
            main = dispatcher,
            storage = dispatcher
        )
    )

    @Test
    fun `scanner groups large old apk video junk and duplicate files`() = runTest {
        val root = temporaryFolder.newFolder("storage")
        val downloads = File(root, "Download").apply { mkdirs() }
        val now = 10_000L
        val old = now - 5_000L

        File(root, "large.bin").writeBytes(ByteArray(50))
        File(downloads, "old.txt").apply {
            writeBytes(ByteArray(2))
            setLastModified(old)
        }
        File(root, "app.apk").writeBytes(ByteArray(3))
        File(root, "movie.mp4").writeBytes(ByteArray(4))
        File(root, "trace.log").writeBytes(ByteArray(1))
        File(root, "same.dat").writeBytes(ByteArray(7))
        File(downloads, "same.dat").writeBytes(ByteArray(7))

        val result = scanner.scan(
            rootPaths = listOf(root.absolutePath),
            now = now,
            limits = StorageCleanerScanLimits(
                largeFileThresholdBytes = 25,
                oldDownloadAgeMs = 1_000
            )
        )

        assertTrue(result.group(CleanerGroupType.LargeFiles).contains("large.bin"))
        assertTrue(result.group(CleanerGroupType.OldDownloads).contains("old.txt"))
        assertTrue(result.group(CleanerGroupType.Apks).contains("app.apk"))
        assertTrue(result.group(CleanerGroupType.Videos).contains("movie.mp4"))
        assertTrue(result.group(CleanerGroupType.Junk).contains("trace.log"))
        assertEquals(2, result.groups.first { it.type == CleanerGroupType.Duplicates }.candidates.size)
    }

    @Test
    fun `scanner excludes arcile trash folders`() = runTest {
        val root = temporaryFolder.newFolder("storage")
        val trash = File(root, ".arcile/.trash").apply { mkdirs() }
        File(trash, "large.bin").writeBytes(ByteArray(50))

        val result = scanner.scan(
            rootPaths = listOf(root.absolutePath),
            limits = StorageCleanerScanLimits(largeFileThresholdBytes = 25)
        )

        assertFalse(result.group(CleanerGroupType.LargeFiles).contains("large.bin"))
    }

    @Test
    fun `duplicate scan ignores same name and size files with different content`() = runTest {
        val root = temporaryFolder.newFolder("storage")
        val downloads = File(root, "Download").apply { mkdirs() }
        File(root, "same.dat").writeText("aaaa")
        File(downloads, "same.dat").writeText("bbbb")

        val result = scanner.scan(rootPaths = listOf(root.absolutePath))

        assertTrue(result.group(CleanerGroupType.Duplicates).isEmpty())
    }

    @Test
    fun `duplicate scan groups different names with identical content`() = runTest {
        val root = temporaryFolder.newFolder("storage")
        File(root, "one.bin").writeText("matching payload")
        File(root, "two.bin").writeText("matching payload")

        val duplicates = scanner.scan(rootPaths = listOf(root.absolutePath))
            .groups.first { it.type == CleanerGroupType.Duplicates }
            .candidates

        assertEquals(setOf("one.bin", "two.bin"), duplicates.map { it.name }.toSet())
        assertEquals(1, duplicates.mapNotNull { it.duplicateGroupKey }.toSet().size)
    }

    @Test
    fun `scanner applies ignored paths disabled sections and custom thresholds`() = runTest {
        val root = temporaryFolder.newFolder("storage")
        val downloads = File(root, "Download").apply { mkdirs() }
        val ignored = File(root, "ignored.bin").apply { writeBytes(ByteArray(200)) }
        File(root, "medium.bin").writeBytes(ByteArray(60))
        File(root, "installer.apk").writeBytes(ByteArray(4))
        File(downloads, "recent.txt").apply {
            writeBytes(ByteArray(3))
            setLastModified(9_000L)
        }
        val now = 10_000L
        val rules = StorageCleanerRules(
            ignoredPaths = setOf(ignored.absolutePath),
            sections = StorageCleanerRules.defaultSections() + mapOf(
                CleanerGroupType.Apks to CleanerSectionRule(enabled = false),
                CleanerGroupType.LargeFiles to CleanerSectionRule(largeFileThresholdBytes = 50L),
                CleanerGroupType.OldDownloads to CleanerSectionRule(oldDownloadAgeMs = 500L)
            )
        )

        val result = scanner.scan(
            rootPaths = listOf(root.absolutePath),
            now = now,
            limits = StorageCleanerScanLimits(largeFileThresholdBytes = 500L, oldDownloadAgeMs = 5_000L),
            rules = rules
        )

        assertFalse(result.group(CleanerGroupType.LargeFiles).contains("ignored.bin"))
        assertTrue(result.group(CleanerGroupType.LargeFiles).contains("medium.bin"))
        assertTrue(result.group(CleanerGroupType.Apks).isEmpty())
        assertTrue(result.group(CleanerGroupType.OldDownloads).contains("recent.txt"))
    }

    @Test
    fun `scanner groups common marker files separately from junk`() = runTest {
        val root = temporaryFolder.newFolder("storage")
        val downloads = File(root, "Download").apply { mkdirs() }
        val hidden = File(root, "Hidden").apply { mkdirs() }
        listOf(".nomedia", "desktop.ini", "Thumbs.db", ".DS_Store").forEach { name ->
            File(root, name).writeBytes(ByteArray(1))
        }
        File(hidden, ".nomedia").writeBytes(ByteArray(1))
        File(downloads, ".nomedia").apply {
            writeBytes(ByteArray(40))
            setLastModified(0L)
        }

        val result = scanner.scan(
            rootPaths = listOf(root.absolutePath),
            now = 10_000L,
            limits = StorageCleanerScanLimits(
                largeFileThresholdBytes = 25,
                oldDownloadAgeMs = 1_000
            )
        )
        val markerFiles = result.group(CleanerGroupType.MarkerFiles).toSet()
        val junkFiles = result.group(CleanerGroupType.Junk).toSet()

        assertEquals(setOf(".nomedia", "desktop.ini", "Thumbs.db", ".DS_Store"), markerFiles)
        assertTrue(junkFiles.intersect(markerFiles).isEmpty())
        assertFalse(result.group(CleanerGroupType.LargeFiles).contains(".nomedia"))
        assertFalse(result.group(CleanerGroupType.OldDownloads).contains(".nomedia"))
        assertTrue(result.group(CleanerGroupType.Duplicates).contains(".nomedia"))
    }

    @Test
    fun `scanner groups empty folders separately`() = runTest {
        val root = temporaryFolder.newFolder("storage")
        File(root, "Empty").mkdirs()
        File(root, "Nested/EmptyChild").mkdirs()
        File(root, "NotEmpty").apply { mkdirs() }.resolve("file.txt").writeText("content")

        val result = scanner.scan(rootPaths = listOf(root.absolutePath))
        val emptyFolders = result.groups.first { it.type == CleanerGroupType.EmptyFolders }.candidates

        assertEquals(listOf("Empty", "EmptyChild"), emptyFolders.map { it.name }.sorted())
        assertTrue(emptyFolders.all { it.isDirectory })
        assertFalse(result.group(CleanerGroupType.EmptyFolders).contains("NotEmpty"))
    }

    @Test
    fun `scanner marks partial when file limit is reached`() = runTest {
        val root = temporaryFolder.newFolder("storage")
        repeat(10) { index -> File(root, "file-$index.tmp").writeBytes(ByteArray(1)) }

        val result = scanner.scan(
            rootPaths = listOf(root.absolutePath),
            limits = StorageCleanerScanLimits(maxFiles = 3)
        )

        assertTrue(result.isPartial)
        assertEquals(3, result.scannedFiles)
    }

    @Test
    fun `scanner classifies temp files in cache folders as low risk`() = runTest {
        val root = temporaryFolder.newFolder("storage")
        val cache = File(root, "cache").apply { mkdirs() }
        File(cache, "payload.tmp").writeBytes(ByteArray(1))

        val result = scanner.scan(rootPaths = listOf(root.absolutePath))
        val candidate = result.candidate(CleanerGroupType.Junk, "payload.tmp")

        assertEquals(CleanerRiskLevel.Low, candidate.riskLevel)
        assertTrue(candidate.riskReasons.contains(CleanerRiskReason.TemporaryOrCache))
    }

    @Test
    fun `scanner marks log backup old and dump files in user folders as review risk`() = runTest {
        val root = temporaryFolder.newFolder("storage")
        val folders = listOf("Download", "DCIM", "Documents", "Pictures", "Movies")
        val names = listOf("trace.log", "copy.bak", "legacy.old", "crash.dmp", "camera.log")
        folders.zip(names).forEach { (folderName, fileName) ->
            File(root, folderName).apply { mkdirs() }.resolve(fileName).writeBytes(ByteArray(1))
        }

        val result = scanner.scan(rootPaths = listOf(root.absolutePath))

        names.forEach { name ->
            val candidate = result.candidate(CleanerGroupType.Junk, name)
            assertEquals(name, CleanerRiskLevel.Review, candidate.riskLevel)
            assertTrue(name, candidate.riskReasons.any {
                it == CleanerRiskReason.UserFolder || it == CleanerRiskReason.MediaFolder
            })
        }
    }

    @Test
    fun `scanner marks android and app-like junk paths as high risk and excludes arcile internals`() = runTest {
        val root = temporaryFolder.newFolder("storage")
        File(root, "Android/data/dev.qtremors.arcile").apply { mkdirs() }.resolve("debug.log").writeBytes(ByteArray(1))
        File(root, "com.example.app/cache").apply { mkdirs() }.resolve("trace.log").writeBytes(ByteArray(1))
        File(root, ".arcile").apply { mkdirs() }.resolve("internal.log").writeBytes(ByteArray(1))

        val result = scanner.scan(rootPaths = listOf(root.absolutePath))
        val androidCandidate = result.candidate(CleanerGroupType.Junk, "debug.log")
        val packageCandidate = result.candidate(CleanerGroupType.Junk, "trace.log")

        assertEquals(CleanerRiskLevel.High, androidCandidate.riskLevel)
        assertTrue(androidCandidate.riskReasons.contains(CleanerRiskReason.SystemOwnedPath))
        assertEquals(CleanerRiskLevel.High, packageCandidate.riskLevel)
        assertTrue(packageCandidate.riskReasons.contains(CleanerRiskReason.AppLikeFolder))
        assertFalse(result.group(CleanerGroupType.Junk).contains("internal.log"))
    }

    private fun dev.qtremors.arcile.core.storage.domain.StorageCleanerResult.group(type: CleanerGroupType): List<String> =
        groups.first { it.type == type }.candidates.map { it.name }

    private fun dev.qtremors.arcile.core.storage.domain.StorageCleanerResult.candidate(
        type: CleanerGroupType,
        name: String
    ) = groups.first { it.type == type }.candidates.first { it.name == name }
}
