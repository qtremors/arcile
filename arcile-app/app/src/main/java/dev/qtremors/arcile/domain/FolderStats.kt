package dev.qtremors.arcile.domain

import androidx.compose.runtime.Immutable

enum class FolderStatsStatus {
    Ready,
    Unavailable
}

@Immutable
data class FolderStats(
    val fileCount: Long,
    val totalBytes: Long,
    val cachedAt: Long,
    val status: FolderStatsStatus = FolderStatsStatus.Ready
)

@Immutable
data class FolderStatUpdate(
    val path: String,
    val stats: FolderStats
)
