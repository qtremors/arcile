package dev.qtremors.arcile.core.storage.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recent_file_snapshots")
data class RecentFilesSnapshotEntity(
    @PrimaryKey val key: String,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    @ColumnInfo(name = "cached_at") val cachedAt: Long
)

