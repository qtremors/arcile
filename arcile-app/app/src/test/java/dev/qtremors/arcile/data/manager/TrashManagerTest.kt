package dev.qtremors.arcile.data.manager

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.data.FolderStatsStore
import dev.qtremors.arcile.data.provider.VolumeProvider
import dev.qtremors.arcile.data.source.MediaStoreClient
import dev.qtremors.arcile.domain.FolderStatUpdate
import dev.qtremors.arcile.domain.FolderStats
import dev.qtremors.arcile.domain.StorageKind
import dev.qtremors.arcile.testutil.createTempStorageRoot
import dev.qtremors.arcile.testutil.testVolume
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrashManagerTest {

    private lateinit var context: Context
    private lateinit var volumeProvider: VolumeProvider
    private lateinit var mediaStoreClient: MediaStoreClient
    private lateinit var folderStatsStore: FolderStatsStore
    private lateinit var trashManager: DefaultTrashManager
    private lateinit var root: File

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        root = createTempStorageRoot("trash-test")

        volumeProvider = mockk(relaxed = true)
        val vol = testVolume("primary", root.absolutePath, kind = StorageKind.INTERNAL)
        every { volumeProvider.activeStorageRoots } returns listOf(root.absolutePath)
        coEvery { volumeProvider.currentVolumes() } returns listOf(vol)
        every { volumeProvider.observeStorageVolumes() } returns flowOf(listOf(vol))

        mediaStoreClient = mockk(relaxed = true)
        folderStatsStore = object : FolderStatsStore {
            override suspend fun getCached(paths: Collection<String>): Map<String, FolderStats> = emptyMap()
            override fun observeUpdates() = emptyFlow<FolderStatUpdate>()
            override fun queue(paths: List<String>) = Unit
            override fun invalidate(paths: Collection<String>) = Unit
        }

        trashManager = DefaultTrashManager(context, volumeProvider, mediaStoreClient, folderStatsStore)
    }

    @After
    fun teardown() {
        root.deleteRecursively()
    }

    @Test
    fun `moveToTrash moves file to trash directory and creates metadata`() = runTest {
        val file = File(root, "test.txt").apply { 
            createNewFile()
            writeText("trash me")
        }

        val result = trashManager.moveToTrash(listOf(file.absolutePath))
        
        assertTrue(result.isSuccess)
        assertFalse(file.exists()) // original is gone

        val arcileDir = File(root, ".arcile")
        val trashDir = File(arcileDir, ".trash")
        val metadataDir = File(arcileDir, ".metadata")

        assertTrue(trashDir.exists())
        assertTrue(metadataDir.exists())

        // One file in trash, one metadata in metadata
        assertEquals(2, trashDir.listFiles()?.size ?: 0) // contains the trashed file + .nomedia
        assertEquals(1, metadataDir.listFiles()?.size ?: 0) // contains one .json file
    }

    @Test
    fun `getTrashFiles returns correct metadata`() = runTest {
        val file = File(root, "file1.txt").apply { createNewFile() }
        trashManager.moveToTrash(listOf(file.absolutePath))

        val result = trashManager.getTrashFiles()
        assertTrue(result.isSuccess)
        
        val trashItems = result.getOrThrow()
        assertEquals(1, trashItems.size)
        assertEquals(file.absolutePath, trashItems.first().originalPath)
        assertEquals("primary", trashItems.first().sourceVolumeId)
    }

    @Test
    fun `restoreFromTrash restores file to original location`() = runTest {
        val file = File(root, "important.txt").apply { 
            createNewFile()
            writeText("data")
        }
        val originalPath = file.absolutePath
        trashManager.moveToTrash(listOf(originalPath))
        assertFalse(file.exists())

        val trashItems = trashManager.getTrashFiles().getOrThrow()
        val trashId = trashItems.first().id

        val restoreResult = trashManager.restoreFromTrash(listOf(trashId), null)
        assertTrue(restoreResult.isSuccess)

        val restoredFile = File(originalPath)
        assertTrue(restoredFile.exists())
        assertEquals("data", restoredFile.readText())

        // Ensure trash is empty now
        val newTrashItems = trashManager.getTrashFiles().getOrThrow()
        assertTrue(newTrashItems.isEmpty())
    }

    @Test
    fun `emptyTrash deletes all files in trash`() = runTest {
        val file1 = File(root, "a.txt").apply { createNewFile() }
        val file2 = File(root, "b.txt").apply { createNewFile() }
        trashManager.moveToTrash(listOf(file1.absolutePath, file2.absolutePath))

        assertEquals(2, trashManager.getTrashFiles().getOrThrow().size)

        val result = trashManager.emptyTrash()
        assertTrue(result.isSuccess)

        assertEquals(0, trashManager.getTrashFiles().getOrThrow().size)
    }
}
