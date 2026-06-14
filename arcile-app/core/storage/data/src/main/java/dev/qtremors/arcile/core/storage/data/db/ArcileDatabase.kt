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
    exportSchema = false
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

        @Volatile
        private var instance: ArcileDatabase? = null

        fun getInstance(context: Context): ArcileDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ArcileDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
    }
}
