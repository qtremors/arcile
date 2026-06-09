package dev.qtremors.arcile.core.storage.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface StorageNodeDao {
    @Query("SELECT * FROM storage_nodes WHERE path = :path LIMIT 1")
    suspend fun get(path: String): StorageNodeEntity?

    @Query("SELECT * FROM storage_nodes WHERE parent_path = :parentPath ORDER BY is_directory DESC, name COLLATE NOCASE ASC")
    suspend fun listChildren(parentPath: String): List<StorageNodeEntity>

    @Query(
        """
        SELECT * FROM storage_nodes
        WHERE is_directory = 0
            AND (:volumeId IS NULL OR volume_id = :volumeId)
            AND (mime_type LIKE 'image/%' OR extension IN (:extensions))
        ORDER BY last_modified DESC
        """
    )
    suspend fun listImages(volumeId: String?, extensions: List<String>): List<StorageNodeEntity>

    @Upsert
    suspend fun upsert(nodes: List<StorageNodeEntity>)

    @Query("DELETE FROM storage_nodes WHERE path IN (:paths)")
    suspend fun delete(paths: List<String>)

    @Query("DELETE FROM storage_nodes WHERE parent_path = :parentPath")
    suspend fun deleteChildren(parentPath: String)

    @Query(
        """
        DELETE FROM storage_nodes
        WHERE is_directory = 0
            AND (:volumeId IS NULL OR volume_id = :volumeId)
            AND (mime_type LIKE 'image/%' OR extension IN (:extensions))
        """
    )
    suspend fun deleteImages(volumeId: String?, extensions: List<String>)

    @Query("DELETE FROM storage_nodes WHERE path = :path OR parent_path = :path OR path LIKE :descendantPrefix")
    suspend fun deleteTree(path: String, descendantPrefix: String)

    @Query("DELETE FROM storage_nodes")
    suspend fun clear()
}
