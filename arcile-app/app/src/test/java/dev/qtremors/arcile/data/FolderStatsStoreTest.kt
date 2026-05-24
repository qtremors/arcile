package dev.qtremors.arcile.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.domain.FolderStatsStatus
import dev.qtremors.arcile.domain.FolderStats
import dev.qtremors.arcile.testutil.createTempStorageRoot
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.first
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
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FolderStatsStoreTest {

    private lateinit var context: Context
    private lateinit var root: File
    private val stores = mutableListOf<DefaultFolderStatsStore>()

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        root = createTempStorageRoot("folder-stats-store")
    }

    @After
    fun teardown() {
        stores.forEach(DefaultFolderStatsStore::close)
        stores.clear()
        root.deleteRecursively()
    }

    @Test
    fun `invalidate during in-flight scan prevents stale publish and reruns latest stats`() = runBlocking {
        val folder = File(root, "Docs").apply { mkdirs() }
        File(folder, "a.txt").writeText("a")

        val started = CountDownLatch(1)
        val allowPublish = CountDownLatch(1)
        val store = trackedStore(
            context = context,
            onCalculationStarted = { path ->
                if (path == folder.absolutePath) {
                    started.countDown()
                }
            },
            beforePublish = { path ->
                if (path == folder.absolutePath) {
                    allowPublish.await(2, TimeUnit.SECONDS)
                }
            }
        )

        store.queue(listOf(folder.absolutePath))
        assertTrue(started.await(2, TimeUnit.SECONDS))

        File(folder, "b.txt").writeText("bb")
        store.invalidate(listOf(folder.absolutePath))
        store.queue(listOf(folder.absolutePath))

        val updateDeferred = async(start = CoroutineStart.UNDISPATCHED) {
            store.observeUpdates().first { it.path == folder.absolutePath }
        }
        allowPublish.countDown()
        val update = withTimeout(5_000) { updateDeferred.await() }

        assertEquals(2L, update.stats.fileCount)
        assertEquals(3L, update.stats.totalBytes)
        assertEquals(FolderStatsStatus.Ready, update.stats.status)

        val cached = store.getCached(listOf(folder.absolutePath))[folder.absolutePath]
        requireNotNull(cached)
        assertEquals(2L, cached.fileCount)
        assertEquals(3L, cached.totalBytes)
    }

    @Test
    fun `parent folder stats exclude descendant thumbnails folder but direct thumbnails stats remain available`() = runBlocking {
        val pictures = File(root, "Pictures").apply { mkdirs() }
        val camera = File(pictures, "Camera").apply { mkdirs() }
        val thumbnails = File(pictures, ".thumbnails").apply { mkdirs() }

        File(camera, "photo.jpg").writeText("12345")
        File(thumbnails, "thumb.db").writeText("1234567890")

        val store = trackedStore(context = context)
        val updatesDeferred = async(start = CoroutineStart.UNDISPATCHED) {
            store.observeUpdates().take(2).toList()
        }

        store.queue(listOf(pictures.absolutePath, thumbnails.absolutePath))
        val updates = withTimeout(5_000) { updatesDeferred.await() }

        val updatesByPath = updates.associate { it.path to it.stats }
        val picturesStats = requireNotNull(updatesByPath[pictures.absolutePath])
        val thumbnailsStats = requireNotNull(updatesByPath[thumbnails.absolutePath])

        assertEquals(1L, picturesStats.fileCount)
        assertEquals(5L, picturesStats.totalBytes)
        assertEquals(FolderStatsStatus.Ready, picturesStats.status)

        assertEquals(1L, thumbnailsStats.fileCount)
        assertEquals(10L, thumbnailsStats.totalBytes)
        assertEquals(FolderStatsStatus.Ready, thumbnailsStats.status)
    }

    @Test
    fun `partial calculator result is persisted and published`() = runBlocking {
        val folder = File(root, "Android").apply { mkdirs() }
        val store = trackedStore(
            context = context,
            calculator = {
                FolderStats(
                    fileCount = 2L,
                    totalBytes = 512L,
                    cachedAt = System.currentTimeMillis(),
                    status = FolderStatsStatus.Partial
                )
            }
        )

        val updateDeferred = async(start = CoroutineStart.UNDISPATCHED) {
            store.observeUpdates().first { it.path == folder.absolutePath }
        }
        store.queue(listOf(folder.absolutePath))
        val update = withTimeout(5_000) { updateDeferred.await() }

        assertEquals(2L, update.stats.fileCount)
        assertEquals(512L, update.stats.totalBytes)
        assertEquals(FolderStatsStatus.Partial, update.stats.status)
        assertEquals(FolderStatsStatus.Partial, store.getCached(listOf(folder.absolutePath))[folder.absolutePath]?.status)
    }

    @Test
    fun `requeue cancels stale in-flight scan and publishes newest stats`() = runBlocking {
        val folder = File(root, "Changing").apply { mkdirs() }
        File(folder, "old.txt").writeText("old")
        val firstStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val store = trackedStore(
            context = context,
            onCalculationStarted = { path ->
                if (path == folder.absolutePath) firstStarted.countDown()
            },
            beforePublish = { path ->
                if (path == folder.absolutePath) releaseFirst.await(2, TimeUnit.SECONDS)
            }
        )

        store.queue(listOf(folder.absolutePath))
        assertTrue(firstStarted.await(2, TimeUnit.SECONDS))

        File(folder, "new.txt").writeText("newer")
        val updateDeferred = async(start = CoroutineStart.UNDISPATCHED) {
            store.observeUpdates().first { it.path == folder.absolutePath }
        }
        store.queue(listOf(folder.absolutePath))
        releaseFirst.countDown()

        val update = withTimeout(5_000) { updateDeferred.await() }

        assertEquals(2L, update.stats.fileCount)
        assertEquals(8L, update.stats.totalBytes)
    }

    @Test
    fun `folder stats wait while foreground mutation is active`() = runBlocking {
        val folder = File(root, "Deferred").apply { mkdirs() }
        File(folder, "file.txt").writeText("content")
        val storageWorkCoordinator = DefaultStorageWorkCoordinator()
        val started = CountDownLatch(1)
        val store = trackedStore(
            context = context,
            storageWorkCoordinator = storageWorkCoordinator,
            onCalculationStarted = { path ->
                if (path == folder.absolutePath) started.countDown()
            }
        )

        storageWorkCoordinator.beginMutation()
        store.queue(listOf(folder.absolutePath))

        assertFalse(started.await(300, TimeUnit.MILLISECONDS))
        storageWorkCoordinator.endMutation()
        assertTrue(started.await(2, TimeUnit.SECONDS))
    }

    @Test
    fun `folder stats calculator returns partial when node limit is exceeded`() = runBlocking {
        val folder = File(root, "Huge").apply { mkdirs() }
        repeat(5) { index ->
            File(folder, "file-$index.txt").writeText("x")
        }

        val stats = FolderStatsCalculator.calculate(folder, nodeLimit = 3)

        assertEquals(FolderStatsStatus.Partial, stats.status)
        assertTrue(stats.fileCount <= 3L)
    }

    @Test
    fun `unavailable calculator result is persisted and published`() = runBlocking {
        val folder = File(root, "Restricted").apply { mkdirs() }
        val store = trackedStore(
            context = context,
            calculator = {
                FolderStats(
                    fileCount = 0L,
                    totalBytes = 0L,
                    cachedAt = System.currentTimeMillis(),
                    status = FolderStatsStatus.Unavailable
                )
            }
        )

        val updateDeferred = async(start = CoroutineStart.UNDISPATCHED) {
            store.observeUpdates().first { it.path == folder.absolutePath }
        }
        store.queue(listOf(folder.absolutePath))
        val update = withTimeout(5_000) { updateDeferred.await() }

        assertEquals(FolderStatsStatus.Unavailable, update.stats.status)
        assertEquals(FolderStatsStatus.Unavailable, store.getCached(listOf(folder.absolutePath))[folder.absolutePath]?.status)
    }

    private fun trackedStore(
        context: Context,
        calculator: suspend (File) -> FolderStats = FolderStatsCalculator::calculate,
        onCalculationStarted: ((String) -> Unit)? = null,
        beforePublish: ((String) -> Unit)? = null,
        storageWorkCoordinator: StorageWorkCoordinator = NoOpStorageWorkCoordinator
    ): DefaultFolderStatsStore {
        return DefaultFolderStatsStore(
            context = context,
            calculator = calculator,
            onCalculationStarted = onCalculationStarted,
            beforePublish = beforePublish,
            storageWorkCoordinator = storageWorkCoordinator
        ).also(stores::add)
    }
}
