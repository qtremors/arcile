package dev.qtremors.arcile.core.storage.data

import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import dev.qtremors.arcile.core.storage.domain.StorageUsageNodeKind
import dev.qtremors.arcile.core.storage.domain.StorageUsageScanLimits
import dev.qtremors.arcile.core.storage.domain.StorageUsageScanState
import dev.qtremors.arcile.core.storage.domain.StorageUsageScanStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class StorageUsageScannerTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()
    private val scanner = DefaultStorageUsageScanner(
        ArcileDispatchers(
            io = dispatcher,
            default = dispatcher,
            main = dispatcher,
            storage = dispatcher
        )
    )

    @Test
    fun `scanner calculates nested folder sizes and sorts children by size`() = runTest {
        val root = temporaryFolder.newFolder("root")
        val small = File(root, "small").apply { mkdirs() }
        val large = File(root, "large").apply { mkdirs() }
        File(small, "one.bin").writeBytes(ByteArray(10))
        File(large, "two.bin").writeBytes(ByteArray(30))

        val loaded = scanner.scanStorageUsage(root.absolutePath).loadedState()

        assertEquals(40L, loaded.root.sizeBytes)
        assertEquals(listOf("large", "small"), loaded.root.children.map { it.name })
        assertEquals(StorageUsageNodeKind.Folder, loaded.root.children.first().kind)
    }

    @Test
    fun `scanner reuses cached result until invalidated`() = runTest {
        val root = temporaryFolder.newFolder("cached")
        File(root, "one.bin").writeBytes(ByteArray(10))

        val first = scanner.scanStorageUsage(root.absolutePath).loadedState()
        File(root, "two.bin").writeBytes(ByteArray(20))
        val cached = scanner.scanStorageUsage(root.absolutePath).loadedState()
        scanner.invalidateStorageUsage(listOf(root.absolutePath))
        val refreshed = scanner.scanStorageUsage(root.absolutePath).loadedState()

        assertEquals(10L, first.root.sizeBytes)
        assertEquals(10L, cached.root.sizeBytes)
        assertEquals(30L, refreshed.root.sizeBytes)
    }

    @Test
    fun `scanner groups tiny children when folder exceeds child limit`() = runTest {
        val root = temporaryFolder.newFolder("many")
        repeat(8) { index ->
            File(root, "file-$index.bin").writeBytes(ByteArray(index + 1))
        }

        val loaded = scanner.scanStorageUsage(
            root.absolutePath,
            StorageUsageScanLimits(maxChildrenPerFolder = 3, minChildShare = 0f)
        ).loadedState()

        assertEquals(4, loaded.root.children.size)
        assertEquals("Other small items", loaded.root.children.last().name)
        assertEquals(StorageUsageNodeKind.Grouped, loaded.root.children.last().kind)
    }

    @Test
    fun `scanner marks scan partial when node limit is reached`() = runTest {
        val root = temporaryFolder.newFolder("limited")
        repeat(20) { index ->
            File(root, "file-$index.bin").writeBytes(ByteArray(1))
        }

        val loaded = scanner.scanStorageUsage(
            root.absolutePath,
            StorageUsageScanLimits(maxNodes = 5, maxChildrenPerFolder = 20)
        ).loadedState()

        assertEquals(StorageUsageScanStatus.Partial, loaded.root.status)
        assertTrue(loaded.root.children.size < 20)
    }

    @Test
    fun `scanner does not run fallback traversal after reaching scan budget`() = runTest {
        val root = temporaryFolder.newFolder("bounded")
        var current = root
        repeat(40) { depth ->
            current = File(current, "level-$depth").apply { mkdirs() }
            repeat(20) { index ->
                File(current, "file-$index.bin").writeBytes(ByteArray(1))
            }
        }

        val loaded = scanner.scanStorageUsage(
            root.absolutePath,
            StorageUsageScanLimits(maxDepth = 2, maxNodes = 8, maxChildrenPerFolder = 100)
        ).loadedState()

        assertEquals(StorageUsageScanStatus.Partial, loaded.root.status)
        assertTrue(countReturnedNodes(loaded.root) <= 8)
    }

    @Test
    fun `scanner returns error for missing root`() = runTest {
        val missing = File(temporaryFolder.root, "missing")
        val states = mutableListOf<StorageUsageScanState>()

        scanner.scanStorageUsage(missing.absolutePath).collect { states += it }

        assertTrue(states.last() is StorageUsageScanState.Error)
    }

    private suspend fun kotlinx.coroutines.flow.Flow<StorageUsageScanState>.loadedState(): StorageUsageScanState.Loaded {
        var loaded: StorageUsageScanState.Loaded? = null
        collect { state ->
            if (state is StorageUsageScanState.Loaded) loaded = state
        }
        return requireNotNull(loaded)
    }

    private fun countReturnedNodes(node: dev.qtremors.arcile.core.storage.domain.StorageUsageNode): Int =
        1 + node.children.sumOf(::countReturnedNodes)
}
