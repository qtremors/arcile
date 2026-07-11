package dev.qtremors.arcile.core.storage.domain

@Immutable
data class StorageCleanerScanLimits(
    val maxFiles: Int = 25_000,
    val maxDepth: Int = 10,
    val maxCandidatesPerGroup: Int = 200,
    val largeFileThresholdBytes: Long = 100L * 1024L * 1024L,
    val oldDownloadAgeMs: Long = 30L * 24L * 60L * 60L * 1000L
)
