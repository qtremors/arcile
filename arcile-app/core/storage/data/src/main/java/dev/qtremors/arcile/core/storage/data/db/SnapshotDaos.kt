package dev.qtremors.arcile.core.storage.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface RecentFilesSnapshotDao {
    @Query("SELECT * FROM recent_file_snapshots WHERE key = :key LIMIT 1")
    suspend fun get(key: String): RecentFilesSnapshotEntity?

    @Upsert
    suspend fun upsert(entity: RecentFilesSnapshotEntity)

    @Query("DELETE FROM recent_file_snapshots WHERE key IN (:keys)")
    suspend fun delete(keys: List<String>)

    @Query("DELETE FROM recent_file_snapshots")
    suspend fun clear()
}

@Dao
interface StorageUsageSnapshotDao {
    @Query("SELECT * FROM storage_usage_snapshots WHERE key = :key LIMIT 1")
    suspend fun get(key: String): StorageUsageSnapshotEntity?

    @Upsert
    suspend fun upsert(entity: StorageUsageSnapshotEntity)

    @Query("SELECT * FROM storage_usage_snapshots")
    suspend fun getAll(): List<StorageUsageSnapshotEntity>

    @Query("DELETE FROM storage_usage_snapshots WHERE `key` IN (:keys)")
    suspend fun deleteKeys(keys: List<String>)

    @Query("DELETE FROM storage_usage_snapshots WHERE root_path = :rootPath OR root_path LIKE :descendantPrefix")
    suspend fun deleteForRoot(rootPath: String, descendantPrefix: String)

    @Query("DELETE FROM storage_usage_snapshots")
    suspend fun clear()
}

@Dao
interface StorageCleanerSnapshotDao {
    @Query("SELECT * FROM storage_cleaner_snapshots WHERE key = :key LIMIT 1")
    suspend fun get(key: String): StorageCleanerSnapshotEntity?

    @Query("SELECT * FROM storage_cleaner_snapshots")
    suspend fun getAll(): List<StorageCleanerSnapshotEntity>

    @Query("UPDATE storage_cleaner_snapshots SET cached_at = 0")
    suspend fun markAllStale()

    @Upsert
    suspend fun upsert(entity: StorageCleanerSnapshotEntity)

    @Query("DELETE FROM storage_cleaner_snapshots")
    suspend fun clear()
}
