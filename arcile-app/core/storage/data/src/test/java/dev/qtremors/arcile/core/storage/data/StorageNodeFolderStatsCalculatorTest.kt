package dev.qtremors.arcile.core.storage.data

import dev.qtremors.arcile.core.storage.data.source.FileSystemDataSource
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.FolderStatsStatus
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.util.concurrent.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class StorageNodeFolderStatsCalculatorTest {
    @Test
    fun `privileged tree is totaled through node listings`() = runTest {
        val source = mockk<FileSystemDataSource>()
        val root = rootNode("/data")
        val nested = rootNode("/data/nested")
        val first = rootNode("/data/first.bin")
        val second = rootNode("/data/nested/second.bin")
        coEvery { source.inspectNode(root) } returns Result.success(model(root, directory = true))
        coEvery { source.listNodeFiles(root) } returns Result.success(
            listOf(model(first, size = 11L), model(nested, directory = true))
        )
        coEvery { source.listNodeFiles(nested) } returns Result.success(
            listOf(model(second, size = 29L))
        )

        val stats = StorageNodeFolderStatsCalculator(source).calculate(root, now = 100L)

        assertEquals(2L, stats.fileCount)
        assertEquals(40L, stats.totalBytes)
        assertEquals(100L, stats.cachedAt)
        assertEquals(FolderStatsStatus.Ready, stats.status)
    }

    @Test
    fun `descendant thumbnails are excluded without opening their backend`() = runTest {
        val source = mockk<FileSystemDataSource>()
        val root = rootNode("/pictures")
        val camera = rootNode("/pictures/camera")
        val thumbnails = rootNode("/pictures/.thumbnails")
        val photo = rootNode("/pictures/camera/photo.jpg")
        coEvery { source.inspectNode(root) } returns Result.success(model(root, directory = true))
        coEvery { source.listNodeFiles(root) } returns Result.success(
            listOf(
                model(camera, directory = true),
                model(thumbnails, directory = true)
            )
        )
        coEvery { source.listNodeFiles(camera) } returns Result.success(
            listOf(model(photo, size = 512L))
        )

        val stats = StorageNodeFolderStatsCalculator(source).calculate(root)

        assertEquals(1L, stats.fileCount)
        assertEquals(512L, stats.totalBytes)
        assertEquals(FolderStatsStatus.Ready, stats.status)
        coVerify(exactly = 0) { source.listNodeFiles(thumbnails) }
    }

    @Test
    fun `direct thumbnails root remains measurable`() = runTest {
        val source = mockk<FileSystemDataSource>()
        val root = rootNode("/pictures/.thumbnails")
        val file = rootNode("/pictures/.thumbnails/thumb.db")
        coEvery { source.inspectNode(root) } returns Result.success(model(root, directory = true))
        coEvery { source.listNodeFiles(root) } returns Result.success(listOf(model(file, size = 64L)))

        val stats = StorageNodeFolderStatsCalculator(source).calculate(root)

        assertEquals(1L, stats.fileCount)
        assertEquals(64L, stats.totalBytes)
        assertEquals(FolderStatsStatus.Ready, stats.status)
    }

    @Test
    fun `unavailable root is reported without partial totals`() = runTest {
        val source = mockk<FileSystemDataSource>()
        val root = shizukuNode("/data/restricted")
        coEvery { source.inspectNode(root) } returns Result.success(model(root, directory = true))
        coEvery { source.listNodeFiles(root) } returns Result.failure(SecurityException("denied"))

        val stats = StorageNodeFolderStatsCalculator(source).calculate(root, now = 44L)

        assertEquals(0L, stats.fileCount)
        assertEquals(0L, stats.totalBytes)
        assertEquals(44L, stats.cachedAt)
        assertEquals(FolderStatsStatus.Unavailable, stats.status)
    }

    @Test
    fun `unavailable descendant retains readable totals as partial`() = runTest {
        val source = mockk<FileSystemDataSource>()
        val root = rootNode("/data")
        val file = rootNode("/data/readable.bin")
        val restricted = rootNode("/data/restricted")
        coEvery { source.inspectNode(root) } returns Result.success(model(root, directory = true))
        coEvery { source.listNodeFiles(root) } returns Result.success(
            listOf(model(file, size = 12L), model(restricted, directory = true))
        )
        coEvery { source.listNodeFiles(restricted) } returns Result.failure(SecurityException("denied"))

        val stats = StorageNodeFolderStatsCalculator(source).calculate(root)

        assertEquals(1L, stats.fileCount)
        assertEquals(12L, stats.totalBytes)
        assertEquals(FolderStatsStatus.Partial, stats.status)
    }

    @Test
    fun `canonical cycle terminates and reports partial`() = runTest {
        val source = mockk<FileSystemDataSource>()
        val root = rootNode("/data")
        val cycle = model(
            node = root,
            directory = true,
            name = "cycle",
            displayPath = "/data/cycle"
        )
        coEvery { source.inspectNode(root) } returns Result.success(model(root, directory = true))
        coEvery { source.listNodeFiles(root) } returns Result.success(listOf(cycle))

        val stats = StorageNodeFolderStatsCalculator(source).calculate(root)

        assertEquals(0L, stats.fileCount)
        assertEquals(FolderStatsStatus.Partial, stats.status)
        coVerify(exactly = 1) { source.listNodeFiles(root) }
    }

    @Test
    fun `node limit returns bounded partial result`() = runTest {
        val source = mockk<FileSystemDataSource>()
        val root = rootNode("/data")
        coEvery { source.inspectNode(root) } returns Result.success(model(root, directory = true))
        coEvery { source.listNodeFiles(root) } returns Result.success(
            (1..5).map { index -> model(rootNode("/data/$index.bin"), size = index.toLong()) }
        )

        val stats = StorageNodeFolderStatsCalculator(source).calculate(root, nodeLimit = 3)

        assertEquals(3L, stats.fileCount)
        assertEquals(6L, stats.totalBytes)
        assertEquals(FolderStatsStatus.Partial, stats.status)
    }

    @Test
    fun `non-directory and missing roots are unavailable`() = runTest {
        val source = mockk<FileSystemDataSource>()
        val file = rootNode("/data/file.bin")
        val missing = rootNode("/data/missing")
        coEvery { source.inspectNode(file) } returns Result.success(model(file, size = 4L))
        coEvery { source.inspectNode(missing) } returns Result.failure(IllegalStateException("gone"))
        val calculator = StorageNodeFolderStatsCalculator(source)

        assertEquals(FolderStatsStatus.Unavailable, calculator.calculate(file).status)
        assertEquals(FolderStatsStatus.Unavailable, calculator.calculate(missing).status)
        coVerify(exactly = 0) { source.listNodeFiles(any()) }
    }

    @Test
    fun `byte totals saturate at long max`() = runTest {
        val source = mockk<FileSystemDataSource>()
        val root = rootNode("/data")
        coEvery { source.inspectNode(root) } returns Result.success(model(root, directory = true))
        coEvery { source.listNodeFiles(root) } returns Result.success(
            listOf(
                model(rootNode("/data/huge.bin"), size = Long.MAX_VALUE),
                model(rootNode("/data/extra.bin"), size = 20L)
            )
        )

        val stats = StorageNodeFolderStatsCalculator(source).calculate(root)

        assertEquals(Long.MAX_VALUE, stats.totalBytes)
        assertEquals(FolderStatsStatus.Ready, stats.status)
    }

    @Test
    fun `backend cancellation is never converted to unavailable`() = runTest {
        val source = mockk<FileSystemDataSource>()
        val root = rootNode("/data")
        coEvery { source.inspectNode(root) } returns Result.success(model(root, directory = true))
        coEvery { source.listNodeFiles(root) } returns Result.failure(CancellationException("stop"))

        try {
            StorageNodeFolderStatsCalculator(source).calculate(root)
            fail("Expected cancellation")
        } catch (error: CancellationException) {
            assertEquals("stop", error.message)
        }
    }

    private fun rootNode(path: String): StorageNodeRef = StorageNodeRef.root(path, path)

    private fun shizukuNode(path: String): StorageNodeRef = StorageNodeRef.shizuku(path, path)

    private fun model(
        node: StorageNodeRef,
        size: Long = 0L,
        directory: Boolean = false,
        name: String = node.displayPath.absolutePath.substringAfterLast('/'),
        displayPath: String = node.displayPath.absolutePath
    ): FileModel = FileModel(
        name = name,
        absolutePath = displayPath,
        size = size,
        isDirectory = directory,
        nodeRef = node
    )
}
