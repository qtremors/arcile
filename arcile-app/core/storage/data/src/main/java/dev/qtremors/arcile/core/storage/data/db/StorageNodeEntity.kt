package dev.qtremors.arcile.core.storage.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "storage_nodes",
    indices = [
        Index("parent_path"),
        Index("volume_id"),
        Index("content_uri"),
        Index("media_store_id"),
        Index("last_modified")
    ]
)
data class StorageNodeEntity(
    @PrimaryKey val path: String,
    @ColumnInfo(name = "parent_path") val parentPath: String?,
    val name: String,
    val extension: String,
    @ColumnInfo(name = "mime_type") val mimeType: String?,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    @ColumnInfo(name = "last_modified") val lastModified: Long,
    @ColumnInfo(name = "is_directory") val isDirectory: Boolean,
    @ColumnInfo(name = "is_hidden") val isHidden: Boolean,
    @ColumnInfo(name = "content_uri") val contentUri: String?,
    @ColumnInfo(name = "media_store_id") val mediaStoreId: Long?,
    @ColumnInfo(name = "media_store_volume") val mediaStoreVolume: String?,
    @ColumnInfo(name = "volume_id") val volumeId: String?,
    val width: Int?,
    val height: Int?,
    @ColumnInfo(name = "date_added") val dateAdded: Long?,
    @ColumnInfo(name = "scanned_at") val scannedAt: Long,
    val stale: Boolean = false
)
