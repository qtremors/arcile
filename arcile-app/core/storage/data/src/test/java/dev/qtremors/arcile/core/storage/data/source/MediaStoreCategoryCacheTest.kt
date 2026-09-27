package dev.qtremors.arcile.core.storage.data.source

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.core.storage.data.db.ArcileDatabase
import dev.qtremors.arcile.core.storage.domain.CategoryStorage
import dev.qtremors.arcile.core.storage.domain.FileCategories
import dev.qtremors.arcile.core.storage.domain.StorageScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaStoreCategoryCacheTest {
    @Test
    fun `expired and invalidated category values remain available for initial display`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, ArcileDatabase::class.java).build()
        try {
            var now = 1_000L
            val cache = MediaStoreCategoryCache(db.categorySummaryDao()) { now }
            val values = FileCategories.all.map { CategoryStorage(it.displayName, 42L, it.extensions) }
            cache.save(StorageScope.AllStorage, values)
            assertEquals(values, cache.get(StorageScope.AllStorage))
            now += 6 * 60 * 1000L
            assertNull(cache.get(StorageScope.AllStorage))
            assertEquals(values, cache.get(StorageScope.AllStorage, allowStale = true))
            cache.save(StorageScope.AllStorage, values)
            cache.invalidateVolumes(setOf("primary"))
            assertNull(cache.get(StorageScope.AllStorage))
            assertEquals(values, MediaStoreCategoryCache(db.categorySummaryDao()).get(StorageScope.AllStorage, true))
            cache.clear()
            assertEquals(values, cache.get(StorageScope.AllStorage, true))
        } finally {
            db.close()
        }
    }
}
