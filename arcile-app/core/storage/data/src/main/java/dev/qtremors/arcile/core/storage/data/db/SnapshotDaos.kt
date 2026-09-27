package dev.qtremors.arcile.core.storage.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

@Dao
interface RecentFilesSnapshotDao {
    @Query("SELECT * FROM recent_file_snapshots WHERE key = :key LIMIT 1")
    suspend fun get(key: String): RecentFilesSnapshotEntity?

    @Query("SELECT * FROM recent_file_snapshots WHERE substr(key, 1, length(:prefix)) = :prefix ORDER BY cached_at DESC LIMIT 1")
    suspend fun latest(prefix: String): RecentFilesSnapshotEntity?

    @Upsert
    suspend fun upsert(entity: RecentFilesSnapshotEntity)

    @Query("DELETE FROM recent_file_snapshots WHERE substr(key, 1, length(:prefix)) = :prefix AND key != :keepKey")
    suspend fun deleteOtherWindows(prefix: String, keepKey: String)

    @Query("DELETE FROM recent_file_snapshots WHERE cached_at < :cutoff")
    suspend fun deleteExpired(cutoff: Long)

    @Transaction
    suspend fun upsertAndPrune(entity: RecentFilesSnapshotEntity, prefix: String, cutoff: Long) {
        upsert(entity)
        deleteOtherWindows(prefix, entity.key)
        deleteExpired(cutoff)
    }

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
