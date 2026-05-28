package dev.qtremors.arcile.core.storage.domain

import androidx.compose.runtime.Immutable

enum class FolderStatsStatus {
    Ready,
    Partial,
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

object FolderStatsCachePolicy {
    const val FRESH_TTL_MS = 30L * 60L * 1000L
    const val FAILURE_TTL_MS = 5L * 60L * 1000L
}
