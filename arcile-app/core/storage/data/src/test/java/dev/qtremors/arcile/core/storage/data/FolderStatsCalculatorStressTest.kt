package dev.qtremors.arcile.core.storage.data

import dev.qtremors.arcile.core.storage.domain.FolderStatsStatus
import dev.qtremors.arcile.testutil.createTempStorageRoot
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class FolderStatsCalculatorStressTest {
    private lateinit var root: File

    @Before
    fun setup() {
        root = createTempStorageRoot("folder-stats-stress")
    }

    @After
    fun teardown() {
        root.deleteRecursively()
    }

    @Test
    fun `large tree calculation streams without loading a recursive list`() = runTest {
        repeat(50) { dirIndex ->
            val dir = File(root, "dir-$dirIndex").apply { mkdirs() }
            repeat(20) { fileIndex ->
                File(dir, "file-$fileIndex.txt").writeText("data")
            }
        }

        val stats = FolderStatsCalculator.calculate(root, now = 123L)

        assertEquals(FolderStatsStatus.Ready, stats.status)
        assertEquals(1_000L, stats.fileCount)
        assertTrue(stats.totalBytes >= 4_000L)
    }

    @Test
    fun `large tree calculation reports partial when node limit is exceeded`() = runTest {
        repeat(20) { index ->
            File(root, "file-$index.txt").writeText("data")
        }

        val stats = FolderStatsCalculator.calculate(root, now = 123L, nodeLimit = 5)

        assertEquals(FolderStatsStatus.Partial, stats.status)
    }
}

