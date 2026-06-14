package dev.qtremors.arcile.core.storage.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "thumbnail_entries",
    indices = [
        Index("source"),
        Index("content_uri"),
        Index("last_modified"),
        Index("last_success_at"),
        Index("last_failure_at")
    ]
)
data class ThumbnailEntryEntity(
    @PrimaryKey
    @ColumnInfo(name = "identity_key")
    val identityKey: String,
    val source: String,
    val extension: String,
    @ColumnInfo(name = "size_bytes")
    val sizeBytes: Long,
    @ColumnInfo(name = "last_modified")
    val lastModified: Long,
    @ColumnInfo(name = "content_uri")
    val contentUri: String?,
    val type: String,
    @ColumnInfo(name = "last_success_at")
    val lastSuccessAt: Long?,
    @ColumnInfo(name = "last_failure_at")
    val lastFailureAt: Long?,
    @ColumnInfo(name = "failure_count")
    val failureCount: Int,
    @ColumnInfo(name = "failure_message")
    val failureMessage: String?
)
