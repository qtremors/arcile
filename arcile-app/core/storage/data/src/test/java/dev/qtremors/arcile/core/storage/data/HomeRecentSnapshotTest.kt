package dev.qtremors.arcile.core.storage.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.core.storage.data.db.ArcileDatabase
import dev.qtremors.arcile.core.storage.data.db.RecentFilesSnapshotEntity
import dev.qtremors.arcile.core.storage.domain.StorageScope
import dev.qtremors.arcile.testutil.testFile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeRecentSnapshotTest {
    @Test
    fun `successful reads keep one window per scope and remove expired scopes`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),
            ArcileDatabase::class.java).build()
        try {
            val day = 24L * 60 * 60 * 1000
            val now = System.currentTimeMillis()
            val file = testFile("recent.jpg", "/storage/emulated/0/recent.jpg", lastModified = now)
            val dao = db.recentFilesSnapshotDao()
            val store = RecentFilesSnapshotStore(dao)

            store.put(StorageScope.AllStorage, 50, day, listOf(file))
            store.put(StorageScope.AllStorage, 50, 2 * day, listOf(file))
            assertNull(dao.get("recent:all:50:1"))
            assertEquals(listOf(file), store.get(StorageScope.AllStorage, 50, 3 * day,
                allowPreviousWindow = true))

            val expiredKey = "recent:volume:detached:50:1"
            dao.upsert(RecentFilesSnapshotEntity(expiredKey, "[]", now - 9 * day))
            store.put(StorageScope.AllStorage, 50, 3 * day, listOf(file))
            assertNull(dao.get(expiredKey))
            assertNull(dao.get("recent:all:50:2"))
            assertNotNull(dao.get("recent:all:50:3"))
        } finally {
            db.close()
        }
    }

    @Test
    fun `home restores the previous day snapshot without including out of window files`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),
            ArcileDatabase::class.java).build()
        try {
            val day = 24L * 60 * 60 * 1000
            val keep = testFile("recent.jpg", "/storage/emulated/0/recent.jpg", lastModified = 10 * day)
            val old = testFile("old.jpg", "/storage/emulated/0/old.jpg", lastModified = day)
            RecentFilesSnapshotStore(db.recentFilesSnapshotDao()).put(StorageScope.AllStorage, 50, day, listOf(keep, old))
            val reopened = RecentFilesSnapshotStore(db.recentFilesSnapshotDao())
            assertNull(reopened.get(StorageScope.AllStorage, 50, 2 * day))
            assertEquals(listOf(keep), reopened.get(StorageScope.AllStorage, 50, 2 * day, allowPreviousWindow = true))
            assertNull(reopened.get(StorageScope.Volume("other"), 50, 2 * day, allowPreviousWindow = true))
        } finally {
            db.close()
        }
    }
}
