package dev.qtremors.arcile.core.storage.data

import dev.qtremors.arcile.core.storage.data.source.FileSystemDataSource
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.util.concurrent.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class StorageNodePropertiesScannerTest {
    @Test
    fun `privileged file metadata is returned without local path inspection`() = runTest {
        val source = mockk<FileSystemDataSource>()
        val node = rootNode("/data/secret.txt")
        val file = model(
            node = node,
            size = 47L,
            modified = 900L,
            hidden = true,
            mimeType = "text/plain"
        )
        coEvery { source.inspectNode(node) } returns Result.success(file)

        val scan = StorageNodePropertiesScanner(source).scan(node).getOrThrow()

        assertEquals(file, scan.root)
        assertEquals(1L, scan.aggregate.fileCount)
        assertEquals(0L, scan.aggregate.folderCount)
        assertEquals(47L, scan.aggregate.totalBytes)
        assertEquals(1L, scan.aggregate.hiddenCount)
        assertEquals(900L, scan.aggregate.newestModifiedAt)
        assertEquals(900L, scan.aggregate.oldestModifiedAt)
        assertFalse(scan.aggregate.selectedDirectoryUnavailable)
        assertFalse(scan.aggregate.descendantReadFailed)
        coVerify(exactly = 0) { source.listNodeFiles(any()) }
    }

    @Test
    fun `directory scan aggregates nested backend listings`() = runTest {
        val source = mockk<FileSystemDataSource>()
        val root = rootNode("/data")
        val nested = rootNode("/data/nested")
        val visible = rootNode("/data/visible.bin")
        val hidden = rootNode("/data/nested/.hidden.bin")
        coEvery { source.inspectNode(root) } returns Result.success(
            model(root, directory = true, modified = 500L)
        )
        coEvery { source.listNodeFiles(root) } returns Result.success(
            listOf(
                model(visible, size = 8L, modified = 700L),
                model(nested, directory = true, modified = 400L)
            )
        )
        coEvery { source.listNodeFiles(nested) } returns Result.success(
            listOf(model(hidden, size = 13L, modified = 300L, hidden = true))
        )

        val aggregate = StorageNodePropertiesScanner(source).scan(root).getOrThrow().aggregate

        assertEquals(2L, aggregate.fileCount)
        assertEquals(2L, aggregate.folderCount)
        assertEquals(21L, aggregate.totalBytes)
        assertEquals(1L, aggregate.hiddenCount)
        assertEquals(700L, aggregate.newestModifiedAt)
        assertEquals(300L, aggregate.oldestModifiedAt)
        assertFalse(aggregate.selectedDirectoryUnavailable)
        assertFalse(aggregate.descendantReadFailed)
    }

    @Test
    fun `unavailable selected directory reports limited scan`() = runTest {
        val source = mockk<FileSystemDataSource>()
        val root = shizukuNode("/data/restricted")
        coEvery { source.inspectNode(root) } returns Result.success(
            model(root, directory = true, modified = 123L, hidden = true)
        )
        coEvery { source.listNodeFiles(root) } returns
            Result.failure(SecurityException("denied"))

        val aggregate = StorageNodePropertiesScanner(source).scan(root).getOrThrow().aggregate

        assertEquals(0L, aggregate.fileCount)
        assertEquals(1L, aggregate.folderCount)
        assertEquals(1L, aggregate.hiddenCount)
        assertEquals(123L, aggregate.newestModifiedAt)
        assertTrue(aggregate.selectedDirectoryUnavailable)
        assertFalse(aggregate.descendantReadFailed)
    }

    @Test
    fun `unavailable descendant preserves readable totals and marks partial scan`() = runTest {
        val source = mockk<FileSystemDataSource>()
        val root = rootNode("/data")
        val readable = rootNode("/data/readable.txt")
        val restricted = rootNode("/data/restricted")
        coEvery { source.inspectNode(root) } returns Result.success(model(root, directory = true))
        coEvery { source.listNodeFiles(root) } returns Result.success(
            listOf(
                model(readable, size = 19L),
                model(restricted, directory = true)
            )
        )
        coEvery { source.listNodeFiles(restricted) } returns
            Result.failure(SecurityException("descendant denied"))

        val aggregate = StorageNodePropertiesScanner(source).scan(root).getOrThrow().aggregate

        assertEquals(1L, aggregate.fileCount)
        assertEquals(2L, aggregate.folderCount)
        assertEquals(19L, aggregate.totalBytes)
        assertFalse(aggregate.selectedDirectoryUnavailable)
        assertTrue(aggregate.descendantReadFailed)
    }

    @Test
    fun `canonical directory cycle is not traversed repeatedly`() = runTest {
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

        val aggregate = StorageNodePropertiesScanner(source).scan(root).getOrThrow().aggregate

        assertEquals(0L, aggregate.fileCount)
        assertEquals(2L, aggregate.folderCount)
        assertTrue(aggregate.descendantReadFailed)
        coVerify(exactly = 1) { source.listNodeFiles(root) }
    }

    @Test
    fun `byte totals saturate instead of overflowing`() = runTest {
        val source = mockk<FileSystemDataSource>()
        val root = rootNode("/data")
        val huge = rootNode("/data/huge.bin")
        val extra = rootNode("/data/extra.bin")
        coEvery { source.inspectNode(root) } returns Result.success(model(root, directory = true))
        coEvery { source.listNodeFiles(root) } returns Result.success(
            listOf(
                model(huge, size = Long.MAX_VALUE),
                model(extra, size = 10L)
            )
        )

        val aggregate = StorageNodePropertiesScanner(source).scan(root).getOrThrow().aggregate

        assertEquals(Long.MAX_VALUE, aggregate.totalBytes)
        assertEquals(2L, aggregate.fileCount)
    }

    @Test
    fun `inspection failure is returned without attempting a listing`() = runTest {
        val source = mockk<FileSystemDataSource>()
        val node = rootNode("/missing")
        val failure = IllegalStateException("gone")
        coEvery { source.inspectNode(node) } returns Result.failure(failure)

        val result = StorageNodePropertiesScanner(source).scan(node)

        assertTrue(result.isFailure)
        assertEquals(failure, result.exceptionOrNull())
        coVerify(exactly = 0) { source.listNodeFiles(any()) }
    }

    @Test
    fun `cancellation from backend listing is rethrown`() = runTest {
        val source = mockk<FileSystemDataSource>()
        val node = rootNode("/data")
        coEvery { source.inspectNode(node) } returns Result.success(model(node, directory = true))
        coEvery { source.listNodeFiles(node) } returns
            Result.failure(CancellationException("cancelled"))

        try {
            StorageNodePropertiesScanner(source).scan(node)
            fail("Expected cancellation")
        } catch (error: CancellationException) {
            assertEquals("cancelled", error.message)
        }
    }

    private fun rootNode(path: String): StorageNodeRef = StorageNodeRef.root(
        displayPath = path,
        remoteCanonicalIdentity = path
    )

    private fun shizukuNode(path: String): StorageNodeRef = StorageNodeRef.shizuku(
        displayPath = path,
        remoteCanonicalIdentity = path
    )

    private fun model(
        node: StorageNodeRef,
        size: Long = 0L,
        modified: Long = 0L,
        directory: Boolean = false,
        hidden: Boolean = false,
        mimeType: String? = null,
        name: String = node.displayPath.absolutePath.substringAfterLast('/'),
        displayPath: String = node.displayPath.absolutePath
    ): FileModel = FileModel(
        name = name,
        absolutePath = displayPath,
        size = size,
        lastModified = modified,
        isDirectory = directory,
        extension = if (directory) "" else name.substringAfterLast('.', "").lowercase(),
        isHidden = hidden,
        mimeType = mimeType,
        nodeRef = node
    )
}
