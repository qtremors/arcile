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
        val grouped = loaded.root.children.single { it.kind == StorageUsageNodeKind.Grouped }
        assertEquals("Other small items", grouped.name)
    }

    @Test
    fun `scanner counts every entry independently of the child display limit`() = runTest {
        val root = temporaryFolder.newFolder("unbounded")
        repeat(75) { index ->
            File(root, "file-$index.bin").createNewFile()
        }

        val loaded = scanner.scanStorageUsage(
            root.absolutePath,
            StorageUsageScanLimits(maxChildrenPerFolder = 20)
        ).loadedState()

        assertEquals(75, loaded.root.childCount)
        assertEquals(StorageUsageScanStatus.Ready, loaded.root.status)
    }

    @Test
    fun `scanner retains every immediate folder beyond the child display limit`() = runTest {
        val root = temporaryFolder.newFolder("folders")
        repeat(60) { index ->
            val folder = File(root, "folder-$index").apply { mkdirs() }
            File(folder, "file.bin").writeBytes(ByteArray(index + 1))
        }

        val loaded = scanner.scanStorageUsage(
            root.absolutePath,
            StorageUsageScanLimits(maxChildrenPerFolder = 10)
        ).loadedState()

        assertEquals(60, loaded.root.childCount)
        assertEquals(60, loaded.root.children.count { it.kind == StorageUsageNodeKind.Folder })
    }

    @Test
    fun `scanner calculates exact sizes below retained tree depth`() = runTest {
        val root = temporaryFolder.newFolder("bounded-depth")
        val first = File(root, "first").apply { mkdirs() }
        val second = File(first, "second").apply { mkdirs() }
        File(second, "payload.bin").writeBytes(ByteArray(37))

        val loaded = scanner.scanStorageUsage(
            root.absolutePath,
            StorageUsageScanLimits(maxDepth = 1, maxChildrenPerFolder = 100)
        ).loadedState()

        assertEquals(37L, loaded.root.sizeBytes)
        assertEquals(37L, loaded.root.children.single().sizeBytes)
        assertEquals(StorageUsageScanStatus.Partial, loaded.root.status)
        assertTrue(loaded.root.children.single().children.isEmpty())
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
}
