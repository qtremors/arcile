package dev.qtremors.arcile.core.storage.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        FolderStatsEntity::class,
        StorageNodeEntity::class,
        CategorySummaryEntity::class,
        ThumbnailEntryEntity::class,
        ThumbnailVariantEntity::class,
        RecentFilesSnapshotEntity::class,
        StorageUsageSnapshotEntity::class,
        StorageCleanerSnapshotEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class ArcileDatabase : RoomDatabase() {
    abstract fun folderStatsDao(): FolderStatsDao
    abstract fun storageNodeDao(): StorageNodeDao
    abstract fun categorySummaryDao(): CategorySummaryDao
    abstract fun thumbnailDao(): ThumbnailDao
    abstract fun recentFilesSnapshotDao(): RecentFilesSnapshotDao
    abstract fun storageUsageSnapshotDao(): StorageUsageSnapshotDao
    abstract fun storageCleanerSnapshotDao(): StorageCleanerSnapshotDao

    companion object {
        private const val DATABASE_NAME = "arcile-cache.db"
        private const val RESTORED_CACHE_INVALIDATED_MARKER = "arcile-cache-restored-state-invalidated"

        @Volatile
        private var instance: ArcileDatabase? = null

        fun getInstance(context: Context): ArcileDatabase =
            instance ?: synchronized(this) {
                instance ?: run {
                    invalidateLegacyRestoredCacheIfNeeded(context.applicationContext)
                    Room.databaseBuilder(
                        context.applicationContext,
                        ArcileDatabase::class.java,
                        DATABASE_NAME
                    )
                        .fallbackToDestructiveMigrationFrom(dropAllTables = true, 1)
                        .build()
                        .also { instance = it }
                }
            }

        private fun invalidateLegacyRestoredCacheIfNeeded(context: Context) {
            val marker = context.noBackupFilesDir.resolve(RESTORED_CACHE_INVALIDATED_MARKER)
            if (marker.exists()) return

            context.deleteDatabase(DATABASE_NAME)
            runCatching {
                marker.parentFile?.mkdirs()
                marker.writeText("1")
            }
        }
    }
}
