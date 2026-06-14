package dev.qtremors.arcile.core.storage.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "storage_usage_snapshots")
data class StorageUsageSnapshotEntity(
    @PrimaryKey val key: String,
    @ColumnInfo(name = "root_path") val rootPath: String,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    @ColumnInfo(name = "cached_at") val cachedAt: Long
)

