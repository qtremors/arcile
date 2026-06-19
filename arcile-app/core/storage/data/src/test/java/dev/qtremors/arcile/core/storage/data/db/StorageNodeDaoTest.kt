package dev.qtremors.arcile.core.storage.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StorageNodeDaoTest {
    private lateinit var database: ArcileDatabase
    private lateinit var dao: StorageNodeDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ArcileDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.storageNodeDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `listImages ignores browser local listing rows`() = runTest {
        val localListingRow = imageNode(
            path = "/storage/emulated/0/Pictures/root.jpg",
            contentUri = null,
            mediaStoreId = null
        )
        val mediaStoreRow = imageNode(
            path = "/storage/emulated/0/Pictures/Album/photo.jpg",
            contentUri = "content://media/external/images/media/42",
            mediaStoreId = 42L
        )
        dao.upsert(listOf(localListingRow, mediaStoreRow))

        val images = dao.listImages(volumeId = null, extensions = listOf("jpg"))

        assertEquals(listOf(mediaStoreRow.path), images.map { it.path })
    }

    private fun imageNode(
        path: String,
        contentUri: String?,
        mediaStoreId: Long?
    ): StorageNodeEntity = StorageNodeEntity(
        path = path,
        parentPath = path.substringBeforeLast('/'),
        name = path.substringAfterLast('/'),
        extension = "jpg",
        mimeType = "image/jpeg",
        sizeBytes = 1024L,
        lastModified = mediaStoreId ?: 1L,
        isDirectory = false,
        isHidden = false,
        contentUri = contentUri,
        mediaStoreId = mediaStoreId,
        mediaStoreVolume = mediaStoreId?.let { "external" },
        volumeId = "primary",
        width = mediaStoreId?.let { 100 },
        height = mediaStoreId?.let { 100 },
        dateAdded = 100L,
        scannedAt = 100L
    )
}
