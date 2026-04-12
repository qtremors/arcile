package dev.qtremors.arcile.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.domain.FolderStatsStatus
import dev.qtremors.arcile.testutil.createTempStorageRoot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        root = createTempStorageRoot("folder-stats-store")
    }

    @After
    fun teardown() {
        root.deleteRecursively()
    }

    @Test
    fun `invalidate during in-flight scan prevents stale publish and reruns latest stats`() = runBlocking {
        val folder = File(root, "Docs").apply { mkdirs() }
        File(folder, "a.txt").writeText("a")

        val started = CountDownLatch(1)
        val allowPublish = CountDownLatch(1)
        val store = DefaultFolderStatsStore(
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

        val updateDeferred = async { store.observeUpdates().first { it.path == folder.absolutePath } }
        allowPublish.countDown()
        val update = updateDeferred.await()

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

        val store = DefaultFolderStatsStore(context)
        val updatesDeferred = async { store.observeUpdates().take(2).toList() }

        store.queue(listOf(pictures.absolutePath, thumbnails.absolutePath))
        val updates = updatesDeferred.await()

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
}
