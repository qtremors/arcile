package dev.qtremors.arcile.core.storage.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "storage_cleaner_snapshots")
data class StorageCleanerSnapshotEntity(
    @PrimaryKey val key: String,
    @ColumnInfo(name = "root_paths_key") val rootPathsKey: String,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    @ColumnInfo(name = "cached_at") val cachedAt: Long
)

