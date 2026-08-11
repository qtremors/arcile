package dev.qtremors.arcile.core.storage.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import dev.qtremors.arcile.core.storage.data.db.ArcileDatabase
import dev.qtremors.arcile.core.storage.domain.FolderStats
import dev.qtremors.arcile.core.storage.domain.FolderStatsStatus
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FolderStatsStoreNodeTest {
    private lateinit var context: Context
    private lateinit var database: ArcileDatabase
    private val stores = mutableListOf<DefaultFolderStatsStore>()

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, ArcileDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun teardown() {
        stores.forEach(DefaultFolderStatsStore::close)
        stores.clear()
        database.close()
    }

    @Test
    fun `root and shizuku cache entries do not collide at the same display path`() = runBlocking {
        val root = rootNode("/data/shared")
        val shizuku = shizukuNode("/data/shared")
        val rootStats = stats(files = 3L, bytes = 30L)
        val shizukuStats = stats(files = 7L, bytes = 70L)
        val store = store { node ->
            when (node.backendId) {
                StorageNodeRef.ROOT_BACKEND_ID -> rootStats
                StorageNodeRef.SHIZUKU_BACKEND_ID -> shizukuStats
                else -> error("Unexpected backend")
            }
        }
        val updates = async(start = CoroutineStart.UNDISPATCHED) {
            store.observeUpdates().take(2).toList()
        }

        store.queueNodes(listOf(root, shizuku))
        val published = withTimeout(5_000) { updates.await() }

        assertEquals(setOf(root, shizuku), published.mapNotNull { it.nodeRef }.toSet())
        assertEquals(rootStats, store.getCachedNodes(listOf(root))[root.displayPath.absolutePath])
        assertEquals(shizukuStats, store.getCachedNodes(listOf(shizuku))[shizuku.displayPath.absolutePath])
    }

    @Test
    fun `privileged cache survives store recreation with backend identity`() = runBlocking {
        val root = rootNode("/system/etc")
        val shizuku = shizukuNode("/system/etc")
        val rootStats = stats(files = 2L, bytes = 200L)
        val shizukuStats = stats(files = 4L, bytes = 400L)
        val firstStore = store { node ->
            if (node.backendId == StorageNodeRef.ROOT_BACKEND_ID) rootStats else shizukuStats
        }
        val updates = async(start = CoroutineStart.UNDISPATCHED) {
            firstStore.observeUpdates().take(2).toList()
        }
        firstStore.queueNodes(listOf(root, shizuku))
        withTimeout(5_000) { updates.await() }
        firstStore.close()
        stores.remove(firstStore)

        val recreated = store { error("Persisted values should be read without recalculation") }

        assertEquals(rootStats, recreated.getCachedNodes(listOf(root))[root.displayPath.absolutePath])
        assertEquals(shizukuStats, recreated.getCachedNodes(listOf(shizuku))[shizuku.displayPath.absolutePath])
    }

    @Test
    fun `invalidating one privileged backend leaves the other backend cached`() = runBlocking {
        val root = rootNode("/data/shared")
        val shizuku = shizukuNode("/data/shared")
        val rootStats = stats(files = 1L, bytes = 10L)
        val shizukuStats = stats(files = 2L, bytes = 20L)
        val store = store { node ->
            if (node.backendId == StorageNodeRef.ROOT_BACKEND_ID) rootStats else shizukuStats
        }
        val updates = async(start = CoroutineStart.UNDISPATCHED) {
            store.observeUpdates().take(2).toList()
        }
        store.queueNodes(listOf(root, shizuku))
        withTimeout(5_000) { updates.await() }

        store.invalidateNodes(listOf(root))

        assertTrue(store.getCachedNodes(listOf(root)).isEmpty())
        assertEquals(shizukuStats, store.getCachedNodes(listOf(shizuku))[shizuku.displayPath.absolutePath])
    }

    @Test
    fun `local node and legacy path share one cache entry`() = runBlocking {
        val local = StorageNodeRef.local("/storage/emulated/0/Documents")
        val expected = stats(files = 5L, bytes = 500L)
        var calculations = 0
        val store = store {
            calculations += 1
            expected
        }
        val update = async(start = CoroutineStart.UNDISPATCHED) {
            store.observeUpdates().first { it.path == local.displayPath.absolutePath }
        }

        store.queueNodes(listOf(local))
        withTimeout(5_000) { update.await() }

        assertEquals(expected, store.getCached(listOf(local.displayPath.absolutePath))[local.displayPath.absolutePath])
        assertEquals(expected, store.getCachedNodes(listOf(local))[local.displayPath.absolutePath])
        assertEquals(1, calculations)
    }

    @Test
    fun `node update includes the exact requested backend reference`() = runBlocking {
        val node = rootNode("/vendor/etc")
        val expected = stats(files = 8L, bytes = 800L, status = FolderStatsStatus.Partial)
        val store = store { expected }
        val update = async(start = CoroutineStart.UNDISPATCHED) {
            store.observeUpdates().first()
        }

        store.queueNodes(listOf(node))
        val published = withTimeout(5_000) { update.await() }

        assertEquals(node, published.nodeRef)
        assertEquals(node.displayPath.absolutePath, published.path)
        assertEquals(expected, published.stats)
    }

    @Test
    fun `node calculator failure is published and cached as unavailable`() = runBlocking {
        val node = shizukuNode("/data/failing")
        val store = store { throw IllegalStateException("service stopped") }
        val update = async(start = CoroutineStart.UNDISPATCHED) {
            store.observeUpdates().first()
        }

        store.queueNodes(listOf(node))
        val published = withTimeout(5_000) { update.await() }

        assertEquals(FolderStatsStatus.Unavailable, published.stats.status)
        assertEquals(
            FolderStatsStatus.Unavailable,
            store.getCachedNodes(listOf(node))[node.displayPath.absolutePath]?.status
        )
    }

    @Test
    fun `requeue replaces stale node generation before publish`() = runBlocking {
        val node = rootNode("/data/changing")
        var calculation = 0
        val firstCalculated = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val store = store(
            nodeCalculator = {
                calculation += 1
                if (calculation == 1) firstCalculated.countDown()
                stats(files = calculation.toLong(), bytes = calculation * 10L)
            },
            beforePublish = {
                if (calculation == 1) releaseFirst.await(2, TimeUnit.SECONDS)
            }
        )
        val update = async(start = CoroutineStart.UNDISPATCHED) {
            store.observeUpdates().first()
        }

        store.queueNodes(listOf(node))
        assertTrue(firstCalculated.await(2, TimeUnit.SECONDS))
        store.queueNodes(listOf(node))
        releaseFirst.countDown()
        val published = withTimeout(5_000) { update.await() }

        assertEquals(2L, published.stats.fileCount)
        assertEquals(2L, store.getCachedNodes(listOf(node))[node.displayPath.absolutePath]?.fileCount)
    }

    private fun store(
        beforePublish: ((String) -> Unit)? = null,
        nodeCalculator: suspend (StorageNodeRef) -> FolderStats
    ): DefaultFolderStatsStore = DefaultFolderStatsStore(
        context = context,
        folderStatsDao = database.folderStatsDao(),
        nodeCalculator = nodeCalculator,
        beforePublish = beforePublish,
        dispatchers = dispatchers(),
        workerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    ).also(stores::add)

    private fun dispatchers() = ArcileDispatchers(
        main = Dispatchers.Unconfined,
        io = Dispatchers.Unconfined,
        default = Dispatchers.Unconfined,
        storage = Dispatchers.Default
    )

    private fun stats(
        files: Long,
        bytes: Long,
        status: FolderStatsStatus = FolderStatsStatus.Ready
    ) = FolderStats(files, bytes, cachedAt = 123L, status = status)

    private fun rootNode(path: String) = StorageNodeRef.root(path, path)

    private fun shizukuNode(path: String) = StorageNodeRef.shizuku(path, path)
}
