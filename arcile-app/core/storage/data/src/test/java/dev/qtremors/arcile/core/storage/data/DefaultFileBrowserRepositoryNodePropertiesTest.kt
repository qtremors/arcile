package dev.qtremors.arcile.core.storage.data

import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import dev.qtremors.arcile.core.storage.data.source.FileSystemDataSource
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.PropertiesAccessStatus
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultFileBrowserRepositoryNodePropertiesTest {
    @Test
    fun `legacy path request adapts to a local storage node`() = runTest {
        val source = mockk<FileSystemDataSource>()
        val local = StorageNodeRef.local("/storage/report.txt")
        coEvery { source.inspectNode(local) } returns Result.success(
            model(local, size = 81L, mimeType = "text/plain")
        )

        val properties = repository(source)
            .getSelectionProperties(listOf("/storage/report.txt"))
            .getOrThrow()

        assertEquals("report.txt", properties.displayName)
        assertEquals(81L, properties.totalBytes)
        assertEquals("text/plain", properties.mimeTypeSummary)
        assertEquals(PropertiesAccessStatus.Full, properties.accessStatus)
    }

    @Test
    fun `empty node selection fails before backend inspection`() = runTest {
        val source = mockk<FileSystemDataSource>(relaxed = true)

        val result = repository(source).getNodeSelectionProperties(emptyList())

        assertTrue(result.isFailure)
        assertEquals("No items selected", result.exceptionOrNull()?.message)
    }

    @Test
    fun `single privileged file uses backend metadata`() = runTest {
        val source = mockk<FileSystemDataSource>()
        val node = rootNode("/system/build.prop")
        coEvery { source.inspectNode(node) } returns Result.success(
            model(
                node = node,
                size = 321L,
                modified = 456L,
                mimeType = "text/plain"
            )
        )

        val properties = repository(source).getNodeSelectionProperties(listOf(node)).getOrThrow()

        assertEquals("build.prop", properties.displayName)
        assertEquals("/system/build.prop", properties.pathSummary)
        assertEquals(1, properties.itemCount)
        assertEquals(1, properties.fileCount)
        assertEquals(0, properties.folderCount)
        assertEquals(321L, properties.totalBytes)
        assertEquals(456L, properties.newestModifiedAt)
        assertEquals(456L, properties.oldestModifiedAt)
        assertEquals("text/plain", properties.mimeTypeSummary)
        assertEquals("prop", properties.extensionSummary)
        assertEquals(PropertiesAccessStatus.Full, properties.accessStatus)
        assertTrue(properties.isSingleItem)
        assertEquals(false, properties.isDirectory)
        assertNull(properties.folderStats)
    }

    @Test
    fun `selected directory listing denial reports limited properties`() = runTest {
        val source = mockk<FileSystemDataSource>()
        val node = shizukuNode("/data/restricted")
        coEvery { source.inspectNode(node) } returns Result.success(
            model(node, directory = true, modified = 99L)
        )
        coEvery { source.listNodeFiles(node) } returns Result.failure(SecurityException("denied"))

        val properties = repository(source).getNodeSelectionProperties(listOf(node)).getOrThrow()

        assertEquals(0, properties.fileCount)
        assertEquals(1, properties.folderCount)
        assertEquals(PropertiesAccessStatus.Limited, properties.accessStatus)
        assertEquals(false, properties.folderStats?.status?.name == "Ready")
        assertEquals(0L, properties.folderStats?.fileCount)
        assertEquals(0L, properties.folderStats?.totalBytes)
        assertTrue(properties.isSingleItem)
        assertEquals(true, properties.isDirectory)
    }

    @Test
    fun `descendant denial retains totals and reports partial properties`() = runTest {
        val source = mockk<FileSystemDataSource>()
        val root = rootNode("/data")
        val readable = rootNode("/data/readable.bin")
        val restricted = rootNode("/data/restricted")
        coEvery { source.inspectNode(root) } returns Result.success(model(root, directory = true))
        coEvery { source.listNodeFiles(root) } returns Result.success(
            listOf(model(readable, size = 14L), model(restricted, directory = true))
        )
        coEvery { source.listNodeFiles(restricted) } returns Result.failure(SecurityException("denied"))

        val properties = repository(source).getNodeSelectionProperties(listOf(root)).getOrThrow()

        assertEquals(1, properties.fileCount)
        assertEquals(2, properties.folderCount)
        assertEquals(14L, properties.totalBytes)
        assertEquals(PropertiesAccessStatus.Partial, properties.accessStatus)
        assertEquals("Partial", properties.folderStats?.status?.name)
    }

    @Test
    fun `same visible path on root and shizuku remains two selected items`() = runTest {
        val source = mockk<FileSystemDataSource>()
        val root = rootNode("/data/shared.conf")
        val shizuku = shizukuNode("/data/shared.conf")
        coEvery { source.inspectNode(root) } returns Result.success(model(root, size = 10L))
        coEvery { source.inspectNode(shizuku) } returns Result.success(model(shizuku, size = 20L))

        val properties = repository(source)
            .getNodeSelectionProperties(listOf(root, shizuku))
            .getOrThrow()

        assertEquals("2 items", properties.displayName)
        assertEquals("/data", properties.pathSummary)
        assertEquals(2, properties.itemCount)
        assertEquals(2, properties.fileCount)
        assertEquals(30L, properties.totalBytes)
        assertEquals(PropertiesAccessStatus.Full, properties.accessStatus)
        assertFalse(properties.isSingleItem)
        assertNull(properties.isDirectory)
    }

    @Test
    fun `unavailable item in mixed selection reports partial without discarding readable item`() = runTest {
        val source = mockk<FileSystemDataSource>()
        val readable = rootNode("/system/readable.conf")
        val missing = rootNode("/system/missing.conf")
        coEvery { source.inspectNode(readable) } returns Result.success(model(readable, size = 50L))
        coEvery { source.inspectNode(missing) } returns Result.failure(IllegalStateException("gone"))

        val properties = repository(source)
            .getNodeSelectionProperties(listOf(readable, missing))
            .getOrThrow()

        assertEquals(1, properties.itemCount)
        assertEquals(1, properties.fileCount)
        assertEquals(50L, properties.totalBytes)
        assertEquals(PropertiesAccessStatus.Partial, properties.accessStatus)
        assertTrue(properties.isSingleItem)
    }

    @Test
    fun `all unavailable selected nodes fail clearly`() = runTest {
        val source = mockk<FileSystemDataSource>()
        val first = rootNode("/missing/one")
        val second = shizukuNode("/missing/two")
        coEvery { source.inspectNode(first) } returns Result.failure(IllegalStateException("one gone"))
        coEvery { source.inspectNode(second) } returns Result.failure(IllegalStateException("two gone"))

        val result = repository(source).getNodeSelectionProperties(listOf(first, second))

        assertTrue(result.isFailure)
        assertEquals("Selected items are no longer available", result.exceptionOrNull()?.message)
    }

    @Test
    fun `duplicate canonical node is counted once`() = runTest {
        val source = mockk<FileSystemDataSource>()
        val node = rootNode("/system/build.prop")
        val duplicate = node.copy(displayPath = dev.qtremors.arcile.core.storage.domain.StorageNodePath.of("/alias/build.prop"))
        coEvery { source.inspectNode(node) } returns Result.success(model(node, size = 7L))

        val properties = repository(source)
            .getNodeSelectionProperties(listOf(node, duplicate))
            .getOrThrow()

        assertEquals(1, properties.itemCount)
        assertEquals(7L, properties.totalBytes)
        assertEquals(PropertiesAccessStatus.Full, properties.accessStatus)
    }

    @Test
    fun `multiple totals saturate instead of wrapping negative`() = runTest {
        val source = mockk<FileSystemDataSource>()
        val first = rootNode("/data/huge.bin")
        val second = rootNode("/data/extra.bin")
        coEvery { source.inspectNode(first) } returns Result.success(model(first, size = Long.MAX_VALUE))
        coEvery { source.inspectNode(second) } returns Result.success(model(second, size = 100L))

        val properties = repository(source)
            .getNodeSelectionProperties(listOf(first, second))
            .getOrThrow()

        assertEquals(Long.MAX_VALUE, properties.totalBytes)
        assertEquals(2, properties.fileCount)
    }

    private fun repository(source: FileSystemDataSource) = DefaultFileBrowserRepository(
        fileSystemDataSource = source,
        folderStatsStore = mockk(relaxed = true),
        dispatchers = ArcileDispatchers(
            main = Dispatchers.Unconfined,
            io = Dispatchers.Unconfined,
            default = Dispatchers.Unconfined,
            storage = Dispatchers.Unconfined
        )
    )

    private fun rootNode(path: String) = StorageNodeRef.root(path, path)

    private fun shizukuNode(path: String) = StorageNodeRef.shizuku(path, path)

    private fun model(
        node: StorageNodeRef,
        size: Long = 0L,
        modified: Long = 0L,
        directory: Boolean = false,
        mimeType: String? = if (directory) null else "application/octet-stream"
    ) = FileModel(
        name = node.displayPath.absolutePath.substringAfterLast('/'),
        absolutePath = node.displayPath.absolutePath,
        size = size,
        lastModified = modified,
        isDirectory = directory,
        extension = if (directory) "" else node.displayPath.absolutePath.substringAfterLast('.', ""),
        mimeType = mimeType,
        nodeRef = node
    )
}
