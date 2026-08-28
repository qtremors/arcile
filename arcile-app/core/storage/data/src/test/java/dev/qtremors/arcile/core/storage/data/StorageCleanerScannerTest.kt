package dev.qtremors.arcile.core.storage.data

import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import dev.qtremors.arcile.core.storage.domain.CleanerGroupType
import dev.qtremors.arcile.core.storage.domain.CleanerRiskLevel
import dev.qtremors.arcile.core.storage.domain.CleanerRiskReason
import dev.qtremors.arcile.core.storage.domain.CleanerSectionRule
import dev.qtremors.arcile.core.storage.domain.StorageCleanerScanLimits
import dev.qtremors.arcile.core.storage.domain.StorageCleanerRules
import dev.qtremors.arcile.core.storage.domain.StorageCleanerScanPhase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.toList
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

    @Test
    fun `category scan streams progress and returns only the requested cleaner`() = runTest {
        val root = temporaryFolder.newFolder("progress-storage")
        repeat(300) { index -> File(root, "trace-$index.tmp").writeBytes(byteArrayOf(1)) }
        File(root, "installer.apk").writeBytes(byteArrayOf(2))

        val updates = scanner.scanGroupUpdates(
            rootPaths = listOf(root.absolutePath),
            groupTypes = setOf(CleanerGroupType.Junk),
            limits = StorageCleanerScanLimits(maxFiles = 1_000)
        ).toList()

        assertTrue(updates.any {
            it.progress.phase == StorageCleanerScanPhase.Discovering &&
                (it.progress.progressFraction ?: 1f) < 1f
        })
        val completed = updates.last()
        assertEquals(StorageCleanerScanPhase.Complete, completed.progress.phase)
        assertEquals(setOf(CleanerGroupType.Junk), completed.result?.groups?.map { it.type }?.toSet())
        assertEquals(200, completed.result?.groups?.single()?.candidates?.size)
    }

    @Test
    fun `scanner recognizes semantic version families and marks newest version`() = runTest {
        val root = temporaryFolder.newFolder("versions-storage")
        val now = 10_000L
        File(root, "app-1.0.0.apk").apply { writeBytes(ByteArray(10)); setLastModified(now - 2000) }
        File(root, "app-1.1.0.apk").apply { writeBytes(ByteArray(15)); setLastModified(now - 1000) }
        File(root, "app-2.0.0.apk").apply { writeBytes(ByteArray(20)); setLastModified(now) }

        val result = scanner.scan(
            rootPaths = listOf(root.absolutePath),
            now = now
        )

        val candidates = result.groups.first { it.type == CleanerGroupType.FilenameVersions }.candidates
        assertEquals(3, candidates.size)
        val newest = candidates.first { it.name == "app-2.0.0.apk" }
        assertTrue(newest.isLikelyNewestVersion)
        assertEquals(dev.qtremors.arcile.core.storage.domain.FilenameVersionEvidence.SemanticVersion, newest.filenameVersionMetadata?.evidenceType)
        assertFalse(candidates.first { it.name == "app-1.0.0.apk" }.isLikelyNewestVersion)
        assertFalse(candidates.first { it.name == "app-1.1.0.apk" }.isLikelyNewestVersion)
    }

    @Test
    fun `scanner recognizes duplicate suffix families and marks newest duplicate ordinal`() = runTest {
        val root = temporaryFolder.newFolder("dup-versions-storage")
        val now = 10_000L
        File(root, "document.pdf").apply { writeBytes(ByteArray(10)); setLastModified(now - 2000) }
        File(root, "document (1).pdf").apply { writeBytes(ByteArray(10)); setLastModified(now - 1000) }
        File(root, "document (2).pdf").apply { writeBytes(ByteArray(10)); setLastModified(now) }

        val result = scanner.scan(
            rootPaths = listOf(root.absolutePath),
            now = now
        )

        val candidates = result.groups.first { it.type == CleanerGroupType.FilenameVersions }.candidates
        assertEquals(3, candidates.size)
        val newest = candidates.first { it.name == "document (2).pdf" }
        assertTrue(newest.isLikelyNewestVersion)
        assertEquals(dev.qtremors.arcile.core.storage.domain.FilenameVersionEvidence.DuplicateSuffix, newest.filenameVersionMetadata?.evidenceType)
        assertFalse(candidates.first { it.name == "document.pdf" }.isLikelyNewestVersion)
        assertFalse(candidates.first { it.name == "document (1).pdf" }.isLikelyNewestVersion)
    }

    @Test
    fun `scanner recognizes single numeric apk versions when package id matches`() = runTest {
        val root = temporaryFolder.newFolder("apk-versions-storage")
        val now = 10_000L
        File(root, "arcile-1.apk").apply { writeBytes(ByteArray(10)); setLastModified(now - 2000) }
        File(root, "arcile-2.apk").apply { writeBytes(ByteArray(12)); setLastModified(now) }

        val customScanner = DefaultStorageCleanerScanner(
            dispatchers = ArcileDispatchers(
                io = dispatcher,
                default = dispatcher,
                main = dispatcher,
                storage = dispatcher
            ),
            apkPackageResolver = { path ->
                if (path.contains("arcile")) "dev.qtremors.arcile" to if (path.contains("-2")) 200L else 100L
                else null
            }
        )

        val result = customScanner.scan(
            rootPaths = listOf(root.absolutePath),
            now = now
        )

        val candidates = result.groups.first { it.type == CleanerGroupType.FilenameVersions }.candidates
        assertEquals(2, candidates.size)
        val newest = candidates.first { it.name == "arcile-2.apk" }
        assertTrue(newest.isLikelyNewestVersion)
        assertEquals(dev.qtremors.arcile.core.storage.domain.FilenameVersionEvidence.PackageVersion, newest.filenameVersionMetadata?.evidenceType)
    }

    @Test
    fun `scanner rejects dates camera photos numbered documents mixed extensions and weak single number matches`() = runTest {
        val root = temporaryFolder.newFolder("rejected-versions-storage")
        // Dates
        File(root, "2026-08-28.log").writeBytes(ByteArray(5))
        File(root, "2026-08-29.log").writeBytes(ByteArray(5))
        // Camera
        File(root, "IMG_0001.jpg").writeBytes(ByteArray(5))
        File(root, "IMG_0002.jpg").writeBytes(ByteArray(5))
        // Chapter / Page
        File(root, "chapter 1.pdf").writeBytes(ByteArray(5))
        File(root, "chapter 2.pdf").writeBytes(ByteArray(5))
        // Weak single number on non-APK
        File(root, "notes1.txt").writeBytes(ByteArray(5))
        File(root, "notes2.txt").writeBytes(ByteArray(5))
        // Mixed extensions
        File(root, "tool-1.0.apk").writeBytes(ByteArray(5))
        File(root, "tool-1.0.zip").writeBytes(ByteArray(5))

        val result = scanner.scan(
            rootPaths = listOf(root.absolutePath)
        )

        val candidates = result.groups.first { it.type == CleanerGroupType.FilenameVersions }.candidates
        assertTrue(candidates.isEmpty())
    }

    @Test
    fun `scanner marks filename version candidates as review risk`() = runTest {
        val root = temporaryFolder.newFolder("risk-versions-storage")
        File(root, "tool-1.0.zip").writeBytes(ByteArray(5))
        File(root, "tool-2.0.zip").writeBytes(ByteArray(5))

        val result = scanner.scan(
            rootPaths = listOf(root.absolutePath)
        )

        val candidates = result.groups.first { it.type == CleanerGroupType.FilenameVersions }.candidates
        assertEquals(2, candidates.size)
        assertTrue(candidates.all { it.riskLevel == CleanerRiskLevel.Review })
    }

    @Test
    fun `scanner prunes onlyfiles vault root and its contents entirely`() = runTest {
        val root = temporaryFolder.newFolder("storage-with-vault")
        val vaultDir = File(root, "SecretVault").apply { mkdirs() }
        File(vaultDir, "vault.onlyfiles").writeBytes(ByteArray(100))
        File(vaultDir, "vault.onlyfiles.bak").writeBytes(ByteArray(100))
        val subDir = File(vaultDir, "payload").apply { mkdirs() }
        File(subDir, "large_secret.bin").writeBytes(ByteArray(500))
        File(subDir, "old_secret.txt").apply {
            writeBytes(ByteArray(50))
            setLastModified(1000L)
        }

        File(root, "regular.bin").apply { writeBytes(ByteArray(300)) }

        val result = scanner.scan(
            rootPaths = listOf(root.absolutePath),
            limits = StorageCleanerScanLimits(largeFileThresholdBytes = 100)
        )

        assertTrue(result.group(CleanerGroupType.LargeFiles).contains("regular.bin"))
        assertFalse(result.group(CleanerGroupType.LargeFiles).contains("large_secret.bin"))
        assertFalse(result.group(CleanerGroupType.LargeFiles).contains("vault.onlyfiles"))
        assertTrue(result.groups.all { group ->
            group.candidates.none { it.absolutePath.contains("SecretVault") }
        })
        assertEquals(
            setOf(File(subDir, "large_secret.bin").absolutePath),
            scanner.protectedCleanerPaths(
                listOf(
                    File(subDir, "large_secret.bin").absolutePath,
                    File(root, "regular.bin").absolutePath
                )
            )
        )
    }

    @Test
    fun `scanner does not treat an ordinary folder named onlyfiles as a vault`() = runTest {
        val root = temporaryFolder.newFolder("storage-with-ordinary-onlyfiles-folder")
        val ordinaryDir = File(root, "OnlyFiles").apply { mkdirs() }
        File(ordinaryDir, "large.bin").writeBytes(ByteArray(500))

        val result = scanner.scan(
            rootPaths = listOf(root.absolutePath),
            limits = StorageCleanerScanLimits(largeFileThresholdBytes = 100)
        )

        assertTrue(result.group(CleanerGroupType.LargeFiles).contains("large.bin"))
    }

    @Test
    fun `scanner excludes entire subtree of ignored paths`() = runTest {
        val root = temporaryFolder.newFolder("storage-with-ignored")
        val ignoredDir = File(root, "IgnoredSubtree").apply { mkdirs() }
        val nestedDir = File(ignoredDir, "Nested").apply { mkdirs() }
        File(nestedDir, "large.bin").writeBytes(ByteArray(500))
        File(root, "other.bin").writeBytes(ByteArray(500))

        val result = scanner.scan(
            rootPaths = listOf(root.absolutePath),
            limits = StorageCleanerScanLimits(largeFileThresholdBytes = 100),
            rules = StorageCleanerRules(ignoredPaths = setOf(ignoredDir.absolutePath))
        )

        assertTrue(result.group(CleanerGroupType.LargeFiles).contains("other.bin"))
        assertFalse(result.group(CleanerGroupType.LargeFiles).contains("large.bin"))
    }

    private fun dev.qtremors.arcile.core.storage.domain.StorageCleanerResult.group(type: CleanerGroupType): List<String> =
        groups.first { it.type == type }.candidates.map { it.name }

    private fun dev.qtremors.arcile.core.storage.domain.StorageCleanerResult.candidate(
        type: CleanerGroupType,
        name: String
    ) = groups.first { it.type == type }.candidates.first { it.name == name }
}
