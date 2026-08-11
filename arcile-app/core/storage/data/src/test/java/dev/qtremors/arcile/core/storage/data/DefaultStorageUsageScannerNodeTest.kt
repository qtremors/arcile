package dev.qtremors.arcile.core.storage.data

import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import dev.qtremors.arcile.core.storage.data.source.FileSystemDataSource
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import dev.qtremors.arcile.core.storage.domain.StorageUsageNode
import dev.qtremors.arcile.core.storage.domain.StorageUsageNodeKind
import dev.qtremors.arcile.core.storage.domain.StorageUsageScanLimits
import dev.qtremors.arcile.core.storage.domain.StorageUsageScanState
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.inject.Provider

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultStorageUsageScannerNodeTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val dispatchers = ArcileDispatchers(dispatcher, dispatcher, dispatcher, dispatcher)
    private val source = mockk<FileSystemDataSource>()

    @Test
    fun `privileged scan emits loading then loaded and caches the result`() = runTest {
        val root = rootNode("/data", "data")
        val child = file("payload.bin", "/data/payload.bin", 42L, rootNode("/data/payload.bin", "payload"))
        givenSingleLevel(root, listOf(child))
        val scanner = scanner()

        val first = scanner.scanStorageUsage(root).toList()
        val second = scanner.scanStorageUsage(root).toList()

        assertTrue(first.first() is StorageUsageScanState.Loading)
        assertEquals(42L, (first.last() as StorageUsageScanState.Loaded).root.sizeBytes)
        assertEquals(1, second.size)
        assertTrue(second.single() is StorageUsageScanState.Loaded)
        coVerify(exactly = 1) { source.inspectNode(root) }
        coVerify(exactly = 1) { source.listNodeFiles(root) }
    }

    @Test
    fun `same visible path is cached independently for Root and Shizuku`() = runTest {
        val root = rootNode("/data", "root-data")
        val shizuku = shizukuNode("/data", "shizuku-data")
        givenSingleLevel(root, listOf(file("root.bin", "/data/root.bin", 10L, rootNode("/data/root.bin", "root-file"))))
        givenSingleLevel(shizuku, listOf(file("shell.bin", "/data/shell.bin", 20L, shizukuNode("/data/shell.bin", "shell-file"))))
        val scanner = scanner()

        val rootUsage = scanner.scanStorageUsage(root).loaded()
        val shizukuUsage = scanner.scanStorageUsage(shizuku).loaded()

        assertEquals(10L, rootUsage.sizeBytes)
        assertEquals(20L, shizukuUsage.sizeBytes)
        assertEquals(StorageNodeRef.ROOT_BACKEND_ID, rootUsage.nodeRef?.backendId)
        assertEquals(StorageNodeRef.SHIZUKU_BACKEND_ID, shizukuUsage.nodeRef?.backendId)
    }

    @Test
    fun `invalidating one backend does not evict the other backend`() = runTest {
        val root = rootNode("/data", "root-data")
        val shizuku = shizukuNode("/data", "shizuku-data")
        givenSingleLevel(root, emptyList())
        givenSingleLevel(shizuku, emptyList())
        val scanner = scanner()
        scanner.scanStorageUsage(root).loaded()
        scanner.scanStorageUsage(shizuku).loaded()

        scanner.invalidateStorageUsageNodes(listOf(root))
        scanner.scanStorageUsage(root).loaded()
        scanner.scanStorageUsage(shizuku).loaded()

        coVerify(exactly = 2) { source.inspectNode(root) }
        coVerify(exactly = 1) { source.inspectNode(shizuku) }
    }

    @Test
    fun `invalidating a descendant evicts its cached ancestor on the same backend`() = runTest {
        val root = rootNode("/data", "root-data")
        givenSingleLevel(root, emptyList())
        val scanner = scanner()
        scanner.scanStorageUsage(root).loaded()

        scanner.invalidateStorageUsageNodes(listOf(rootNode("/data/user/0/app", "app")))
        scanner.scanStorageUsage(root).loaded()

        coVerify(exactly = 2) { source.inspectNode(root) }
    }

    @Test
    fun `empty node invalidation clears all backend caches`() = runTest {
        val root = rootNode("/data", "root-data")
        val shizuku = shizukuNode("/storage", "shizuku-storage")
        givenSingleLevel(root, emptyList())
        givenSingleLevel(shizuku, emptyList())
        val scanner = scanner()
        scanner.scanStorageUsage(root).loaded()
        scanner.scanStorageUsage(shizuku).loaded()

        scanner.invalidateStorageUsageNodes()
        scanner.scanStorageUsage(root).loaded()
        scanner.scanStorageUsage(shizuku).loaded()

        coVerify(exactly = 2) { source.inspectNode(root) }
        coVerify(exactly = 2) { source.inspectNode(shizuku) }
    }

    @Test
    fun `fresh scan is persisted after a node snapshot`() = runTest {
        val root = rootNode("/data", "data")
        val snapshot = usage(root, 5L)
        val store = mockk<StorageUsageSnapshotStore>()
        coEvery { store.get(root, any()) } returns snapshot
        coEvery { store.put(root, any(), any()) } just Runs
        givenSingleLevel(root, listOf(file("fresh.bin", "/data/fresh.bin", 9L, rootNode("/data/fresh.bin", "fresh"))))
        val scanner = scanner(store)

        val states = scanner.scanStorageUsage(root).toList()

        val loaded = states.filterIsInstance<StorageUsageScanState.Loaded>()
        assertEquals(listOf(5L, 9L), loaded.map { it.root.sizeBytes })
        assertTrue(states.none { it is StorageUsageScanState.Loading })
        coVerify(exactly = 1) { store.put(root, any(), match { it.sizeBytes == 9L }) }
    }

    @Test
    fun `scan error is emitted when no snapshot can be refreshed`() = runTest {
        val root = rootNode("/data", "data")
        coEvery { source.inspectNode(root) } returns Result.failure(IllegalStateException("Service stopped"))

        val states = scanner().scanStorageUsage(root).toList()

        assertTrue(states.first() is StorageUsageScanState.Loading)
        assertEquals("Service stopped", (states.last() as StorageUsageScanState.Error).message)
    }

    @Test
    fun `refresh failure keeps an already emitted snapshot without replacing it with error`() = runTest {
        val root = rootNode("/data", "data")
        val snapshot = usage(root, 5L)
        val store = mockk<StorageUsageSnapshotStore>()
        coEvery { store.get(root, any()) } returns snapshot
        coEvery { source.inspectNode(root) } returns Result.failure(IllegalStateException("Service stopped"))

        val states = scanner(store).scanStorageUsage(root).toList()

        assertEquals(1, states.size)
        assertEquals(snapshot, (states.single() as StorageUsageScanState.Loaded).root)
    }

    @Test(expected = CancellationException::class)
    fun `backend cancellation escapes the flow`() = runTest {
        val root = rootNode("/data", "data")
        coEvery { source.inspectNode(root) } throws CancellationException("cancelled")

        scanner().scanStorageUsage(root).toList()
    }

    @Test
    fun `node invalidation is forwarded to persistent snapshots`() = runTest {
        val root = rootNode("/data", "data")
        val store = mockk<StorageUsageSnapshotStore>()
        coEvery { store.invalidateNodes(any()) } just Runs
        val scope = TestScope(dispatcher)
        val scanner = DefaultStorageUsageScanner(
            dispatchers,
            Provider { source },
            store,
            scope
        )

        scanner.invalidateStorageUsageNodes(listOf(root))

        coVerify(exactly = 1) { store.invalidateNodes(listOf(root)) }
    }

    private fun scanner(store: StorageUsageSnapshotStore? = null) = DefaultStorageUsageScanner(
        dispatchers,
        Provider { source },
        store
    )

    private suspend fun kotlinx.coroutines.flow.Flow<StorageUsageScanState>.loaded(): StorageUsageNode =
        toList().filterIsInstance<StorageUsageScanState.Loaded>().last().root

    private fun givenSingleLevel(root: StorageNodeRef, children: List<FileModel>) {
        coEvery { source.inspectNode(root) } returns Result.success(
            FileModel(
                name = root.displayPath.absolutePath.substringAfterLast('/'),
                absolutePath = root.displayPath.absolutePath,
                isDirectory = true,
                nodeRef = root
            )
        )
        coEvery { source.listNodeFiles(root) } returns Result.success(children)
    }

    private fun file(name: String, path: String, size: Long, node: StorageNodeRef) = FileModel(
        name = name,
        absolutePath = path,
        size = size,
        nodeRef = node
    )

    private fun usage(root: StorageNodeRef, size: Long) = StorageUsageNode(
        name = root.displayPath.absolutePath.substringAfterLast('/'),
        path = root.displayPath.absolutePath,
        sizeBytes = size,
        kind = StorageUsageNodeKind.Folder,
        childCount = 0,
        nodeRef = root
    )

    private fun rootNode(path: String, identity: String) = StorageNodeRef.root(path, identity)

    private fun shizukuNode(path: String, identity: String) = StorageNodeRef.shizuku(path, identity)
}
