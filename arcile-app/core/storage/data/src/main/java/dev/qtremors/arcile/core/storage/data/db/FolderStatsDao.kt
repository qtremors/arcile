package dev.qtremors.arcile.core.storage.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface FolderStatsDao {
    @Query("SELECT * FROM folder_stats WHERE path IN (:paths)")
    suspend fun get(paths: List<String>): List<FolderStatsEntity>

    @Upsert
    suspend fun upsert(entity: FolderStatsEntity)

    @Query("DELETE FROM folder_stats WHERE path IN (:paths)")
    suspend fun delete(paths: List<String>)

    @Query("SELECT COUNT(*) FROM folder_stats")
    suspend fun count(): Int

    @Query("SELECT path FROM folder_stats ORDER BY cached_at ASC LIMIT :limit")
    suspend fun oldestPaths(limit: Int): List<String>
}
