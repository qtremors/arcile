package dev.qtremors.arcile.core.storage.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import dev.qtremors.arcile.core.storage.domain.FolderStats
import dev.qtremors.arcile.core.storage.domain.FolderStatsStatus

@Entity(tableName = "folder_stats")
data class FolderStatsEntity(
    @PrimaryKey val path: String,
    @ColumnInfo(name = "file_count") val fileCount: Long,
    @ColumnInfo(name = "total_bytes") val totalBytes: Long,
    @ColumnInfo(name = "cached_at") val cachedAt: Long,
    val status: String
) {
    fun toDomain(): FolderStats =
        FolderStats(
            fileCount = fileCount,
            totalBytes = totalBytes,
            cachedAt = cachedAt,
            status = FolderStatsStatus.entries.find { it.name == status } ?: FolderStatsStatus.Unavailable
        )

    companion object {
        fun from(path: String, stats: FolderStats): FolderStatsEntity =
            FolderStatsEntity(
                path = path,
                fileCount = stats.fileCount,
                totalBytes = stats.totalBytes,
                cachedAt = stats.cachedAt,
                status = stats.status.name
            )
    }
}
