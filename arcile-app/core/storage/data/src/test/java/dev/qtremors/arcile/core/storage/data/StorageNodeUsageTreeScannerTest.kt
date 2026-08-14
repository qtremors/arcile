package dev.qtremors.arcile.core.storage.data

import dev.qtremors.arcile.core.storage.data.source.FileSystemDataSource
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import dev.qtremors.arcile.core.storage.domain.StorageUsageNodeKind
import dev.qtremors.arcile.core.storage.domain.StorageUsageScanLimits
import dev.qtremors.arcile.core.storage.domain.StorageUsageScanProgress
import dev.qtremors.arcile.core.storage.domain.StorageUsageScanStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageNodeUsageTreeScannerTest {
    private val source = mockk<FileSystemDataSource>()

    @Test
    fun `scanner calculates nested sizes and retains privileged references`() = runTest {
        val root = rootNode("/data", "data")
        val smallDir = directory("small", "/data/small", rootNode("/data/small", "small"))
        val largeDir = directory("large", "/data/large", rootNode("/data/large", "large"))
        val smallFile = file("one.bin", "/data/small/one.bin", 10L, rootNode("/data/small/one.bin", "one"))
        val largeFile = file("two.bin", "/data/large/two.bin", 30L, rootNode("/data/large/two.bin", "two"))
        givenTree(
            root,
            listOf(smallDir, largeDir),
            smallDir.nodeRef to listOf(smallFile),
            largeDir.nodeRef to listOf(largeFile)
        )

        val usage = scanner().scan(root, limits()) {}

        assertEquals(40L, usage.sizeBytes)
        assertEquals(listOf("large", "small"), usage.children.map { it.name })
        assertEquals("data", usage.nodeRef?.backendIdentity)
        assertEquals("two", usage.children.first().children.single().nodeRef?.backendIdentity)
    }

    @Test
    fun `scanner keeps Shizuku identity for every returned node`() = runTest {
        val root = shizukuNode("/data", "data")
        val child = file("payload.bin", "/data/payload.bin", 7L, shizukuNode("/data/payload.bin", "payload"))
        givenTree(root, listOf(child))

        val usage = scanner().scan(root, limits()) {}

        assertEquals(StorageNodeRef.SHIZUKU_BACKEND_ID, usage.nodeRef?.backendId)
        assertEquals(StorageNodeRef.SHIZUKU_BACKEND_ID, usage.children.single().nodeRef?.backendId)
        assertEquals("payload", usage.children.single().nodeRef?.backendIdentity)
    }

    @Test
    fun `scanner excludes descendant thumbnail cache folders`() = runTest {
        val root = rootNode("/data", "data")
        val thumbnails = directory(".thumbnails", "/data/.thumbnails", rootNode("/data/.thumbnails", "thumbs"))
        val visible = file("photo.jpg", "/data/photo.jpg", 12L, rootNode("/data/photo.jpg", "photo"))
        givenTree(root, listOf(thumbnails, visible))

        val usage = scanner().scan(root, limits()) {}

        assertEquals(12L, usage.sizeBytes)
        assertEquals(listOf("photo.jpg"), usage.children.map { it.name })
        assertEquals(1, usage.childCount)
        coVerify(exactly = 0) { source.listNodeFiles(thumbnails.nodeRef) }
    }

    @Test
    fun `canonical directory cycle becomes unavailable and scan terminates`() = runTest {
        val root = rootNode("/data", "same")
        val loop = directory("loop", "/data/loop", rootNode("/data/loop", "same"))
        val payload = file("payload.bin", "/data/payload.bin", 8L, rootNode("/data/payload.bin", "payload"))
        givenTree(root, listOf(payload, loop))

        val usage = scanner().scan(root, limits()) {}

        assertEquals(8L, usage.sizeBytes)
        assertEquals(StorageUsageScanStatus.Partial, usage.status)
        assertEquals(StorageUsageScanStatus.Unavailable, usage.children.single { it.name == "loop" }.status)
        coVerify(exactly = 0) { source.listNodeFiles(loop.nodeRef) }
    }

    @Test
    fun `inaccessible directory marks only its branch unavailable`() = runTest {
        val root = rootNode("/data", "data")
        val denied = directory("denied", "/data/denied", rootNode("/data/denied", "denied"))
        val payload = file("payload.bin", "/data/payload.bin", 8L, rootNode("/data/payload.bin", "payload"))
        givenTree(root, listOf(denied, payload))
        coEvery { source.listNodeFiles(denied.nodeRef) } returns Result.failure(SecurityException("denied"))

        val usage = scanner().scan(root, limits()) {}

        assertEquals(8L, usage.sizeBytes)
        assertEquals(StorageUsageScanStatus.Partial, usage.status)
        assertEquals(StorageUsageScanStatus.Unavailable, usage.children.single { it.name == "denied" }.status)
    }

    @Test
    fun `node budget produces a partial root with the full direct child count`() = runTest {
        val root = rootNode("/data", "data")
        val files = (1..100).map { index ->
            file("file-$index.bin", "/data/file-$index.bin", 4L, rootNode("/data/file-$index.bin", "file-$index"))
        }
        givenTree(root, files)

        val usage = scanner().scan(root, limits(maxVisitedNodes = 12)) {}

        assertEquals(StorageUsageScanStatus.Partial, usage.status)
        assertEquals(100, usage.childCount)
        assertTrue(usage.sizeBytes < 400L)
    }

    @Test
    fun `duration budget produces a partial result`() = runTest {
        val root = rootNode("/data", "data")
        val child = file("payload.bin", "/data/payload.bin", 10L, rootNode("/data/payload.bin", "payload"))
        givenTree(root, listOf(child))
        val times = ArrayDeque(listOf(0L, 0L, 2_000_000L))
        val scanner = StorageNodeUsageTreeScanner(source) { times.removeFirst() }

        val usage = scanner.scan(root, limits(maxScanDurationMillis = 1L)) {}

        assertEquals(StorageUsageScanStatus.Partial, usage.status)
        assertEquals(0L, usage.sizeBytes)
    }

    @Test
    fun `retained depth limits children but still calculates exact sizes`() = runTest {
        val root = rootNode("/data", "data")
        val first = directory("first", "/data/first", rootNode("/data/first", "first"))
        val second = directory("second", "/data/first/second", rootNode("/data/first/second", "second"))
        val payload = file("payload.bin", "/data/first/second/payload.bin", 37L, rootNode("/data/first/second/payload.bin", "payload"))
        givenTree(
            root,
            listOf(first),
            first.nodeRef to listOf(second),
            second.nodeRef to listOf(payload)
        )

        val usage = scanner().scan(root, limits(maxDepth = 1)) {}

        assertEquals(37L, usage.sizeBytes)
        assertEquals(37L, usage.children.single().sizeBytes)
        assertTrue(usage.children.single().children.isEmpty())
        assertEquals(StorageUsageScanStatus.Partial, usage.status)
    }

    @Test
    fun `file grouping honors child limit and retains every folder`() = runTest {
        val root = rootNode("/data", "data")
        val folders = (1..4).map { index ->
            directory("folder-$index", "/data/folder-$index", rootNode("/data/folder-$index", "folder-$index"))
        }
        val files = (1..8).map { index ->
            file("file-$index.bin", "/data/file-$index.bin", index.toLong(), rootNode("/data/file-$index.bin", "file-$index"))
        }
        givenTree(root, folders + files, *folders.map { it.nodeRef to emptyList<FileModel>() }.toTypedArray())

        val usage = scanner().scan(root, limits(maxChildrenPerFolder = 3)) {}

        assertEquals(4, usage.children.count { it.kind == StorageUsageNodeKind.Folder })
        val grouped = usage.children.single { it.kind == StorageUsageNodeKind.Grouped }
        assertEquals(8, grouped.childCount)
        assertEquals(36L, grouped.sizeBytes)
        assertEquals(null, grouped.nodeRef)
    }

    @Test
    fun `minimum share can group visible file slots below their nominal limit`() = runTest {
        val root = rootNode("/data", "data")
        val files = listOf(
            file("large.bin", "/data/large.bin", 90L, rootNode("/data/large.bin", "large")),
            file("small-a.bin", "/data/small-a.bin", 5L, rootNode("/data/small-a.bin", "small-a")),
            file("small-b.bin", "/data/small-b.bin", 5L, rootNode("/data/small-b.bin", "small-b"))
        )
        givenTree(root, files)

        val usage = scanner().scan(
            root,
            limits(maxChildrenPerFolder = 2, minChildShare = 0.1f)
        ) {}

        assertEquals(listOf("large.bin", "Other small items"), usage.children.map { it.name })
        assertEquals(10L, usage.children.last().sizeBytes)
    }

    @Test
    fun `size aggregation saturates instead of overflowing`() = runTest {
        val root = rootNode("/data", "data")
        val huge = file("huge.bin", "/data/huge.bin", Long.MAX_VALUE, rootNode("/data/huge.bin", "huge"))
        val extra = file("extra.bin", "/data/extra.bin", 10L, rootNode("/data/extra.bin", "extra"))
        givenTree(root, listOf(huge, extra))

        val usage = scanner().scan(root, limits()) {}

        assertEquals(Long.MAX_VALUE, usage.sizeBytes)
    }

    @Test
    fun `progress reports bounded current path and accumulated bytes`() = runTest {
        val root = rootNode("/data", "data")
        val files = (1..192).map { index ->
            file("file-$index.bin", "/data/file-$index.bin", 2L, rootNode("/data/file-$index.bin", "file-$index"))
        }
        givenTree(root, files)
        val progress = mutableListOf<StorageUsageScanProgress>()

        scanner().scan(root, limits(maxVisitedNodes = 1_000)) { progress += it }

        assertEquals(2, progress.size)
        assertEquals(96, progress.first().scannedNodes)
        assertEquals(188L, progress.first().scannedBytes)
        assertTrue(progress.first().currentPath.orEmpty().endsWith("file-95.bin"))
        assertEquals(192, progress.last().scannedNodes)
    }

    @Test
    fun `root inspection failure is propagated`() = runTest {
        val root = rootNode("/data", "data")
        val failure = IllegalStateException("disconnected")
        coEvery { source.inspectNode(root) } returns Result.failure(failure)

        val thrown = runCatching { scanner().scan(root, limits()) {} }.exceptionOrNull()

        assertSame(failure, thrown)
        coVerify(exactly = 0) { source.listNodeFiles(any()) }
    }

    @Test
    fun `non-directory root is rejected`() = runTest {
        val root = rootNode("/data/file.bin", "file")
        coEvery { source.inspectNode(root) } returns Result.success(file("file.bin", "/data/file.bin", 1L, root))

        val thrown = runCatching { scanner().scan(root, limits()) {} }.exceptionOrNull()

        assertTrue(thrown is IllegalArgumentException)
        assertTrue(thrown?.message.orEmpty().contains("folder"))
    }

    @Test(expected = CancellationException::class)
    fun `cancellation from a backend listing is propagated`() = runTest {
        val root = rootNode("/data", "data")
        coEvery { source.inspectNode(root) } returns Result.success(directory("data", "/data", root))
        coEvery { source.listNodeFiles(root) } throws CancellationException("cancelled")

        scanner().scan(root, limits()) {}
    }

    @Test(expected = CancellationException::class)
    fun `cancellation wrapped in a failed listing result is propagated`() = runTest {
        val root = rootNode("/data", "data")
        coEvery { source.inspectNode(root) } returns Result.success(directory("data", "/data", root))
        coEvery { source.listNodeFiles(root) } returns
            Result.failure(CancellationException("cancelled"))

        scanner().scan(root, limits()) {}
    }

    private fun scanner() = StorageNodeUsageTreeScanner(source)

    private fun limits(
        maxDepth: Int = 6,
        maxChildrenPerFolder: Int = 48,
        minChildShare: Float = 0f,
        maxVisitedNodes: Int = 10_000,
        maxScanDurationMillis: Long = 30_000L
    ) = StorageUsageScanLimits(
        maxDepth,
        maxChildrenPerFolder,
        minChildShare,
        maxVisitedNodes,
        maxScanDurationMillis
    )

    private fun givenTree(
        root: StorageNodeRef,
        rootChildren: List<FileModel>,
        vararg descendants: Pair<StorageNodeRef, List<FileModel>>
    ) {
        coEvery { source.inspectNode(root) } returns Result.success(
            directory(root.displayPath.absolutePath.substringAfterLast('/'), root.displayPath.absolutePath, root)
        )
        coEvery { source.listNodeFiles(root) } returns Result.success(rootChildren)
        descendants.forEach { (node, children) ->
            coEvery { source.listNodeFiles(node) } returns Result.success(children)
        }
    }

    private fun directory(name: String, path: String, node: StorageNodeRef) = FileModel(
        name = name,
        absolutePath = path,
        isDirectory = true,
        nodeRef = node
    )

    private fun file(name: String, path: String, size: Long, node: StorageNodeRef) = FileModel(
        name = name,
        absolutePath = path,
        size = size,
        nodeRef = node
    )

    private fun rootNode(path: String, identity: String) = StorageNodeRef.root(path, identity)

    private fun shizukuNode(path: String, identity: String) = StorageNodeRef.shizuku(path, identity)
}
