package dev.qtremors.arcile.data

import dev.qtremors.arcile.di.ArcileDispatchers
import dev.qtremors.arcile.domain.CleanerGroupType
import dev.qtremors.arcile.domain.StorageCleanerScanLimits
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
    private val scanner = StorageCleanerScanner(
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

    private fun dev.qtremors.arcile.domain.StorageCleanerResult.group(type: CleanerGroupType): List<String> =
        groups.first { it.type == type }.candidates.map { it.name }
}
