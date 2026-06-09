package dev.qtremors.arcile.core.storage.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.core.storage.data.db.ArcileDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThumbnailCacheStoreTest {
    private lateinit var database: ArcileDatabase
    private lateinit var store: DefaultThumbnailCacheStore

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ArcileDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = DefaultThumbnailCacheStore(database.thumbnailDao())
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `loaded thumbnails are restored as loaded variants`() = runBlocking {
        val record = record(path = "/storage/emulated/0/DCIM/a.jpg")

        store.recordLoaded(record)
        val snapshot = store.snapshot()

        assertEquals(listOf(record.variantKey), snapshot.loadedVariantKeys)
        assertTrue(snapshot.failedIdentityKeys.isEmpty())
    }

    @Test
    fun `failure removes variants and restores failed identity`() = runBlocking {
        val record = record(path = "/storage/emulated/0/DCIM/b.jpg")

        store.recordLoaded(record)
        store.recordFailure(record)
        val snapshot = store.snapshot()

        assertTrue(snapshot.loadedVariantKeys.isEmpty())
        assertEquals(listOf(record.identityKey), snapshot.failedIdentityKeys)
    }

    @Test
    fun `clear failure keeps identity but removes failed restore state`() = runBlocking {
        val record = record(path = "/storage/emulated/0/DCIM/c.jpg")

        store.recordFailure(record)
        store.clearFailure(record.identityKey)
        val snapshot = store.snapshot()

        assertTrue(snapshot.failedIdentityKeys.isEmpty())
    }

    @Test
    fun `invalidate sources removes descendant thumbnail records`() = runBlocking {
        val kept = record(path = "/storage/emulated/0/Pictures/keep.jpg")
        val removed = record(path = "/storage/emulated/0/DCIM/Camera/remove.jpg")

        store.recordLoaded(kept)
        store.recordLoaded(removed)
        store.invalidateSources(listOf("/storage/emulated/0/DCIM"))
        val snapshot = store.snapshot()

        assertEquals(listOf(kept.variantKey), snapshot.loadedVariantKeys)
    }

    private fun record(path: String): ThumbnailCacheRecord {
        val identityKey = "thumbnail:$path:jpg:12:34"
        return ThumbnailCacheRecord(
            identityKey = identityKey,
            variantKey = "$identityKey:512",
            source = path,
            extension = "jpg",
            sizeBytes = 12L,
            lastModified = 34L,
            contentUri = null,
            type = "Image",
            sizeBucketPx = 512
        )
    }
}
