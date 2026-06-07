package dev.qtremors.arcile.core.storage.domain


enum class CleanerGroupType {
    LargeFiles,
    OldDownloads,
    Duplicates,
    Apks,
    Videos,
    Junk
}

enum class CleanerRiskLevel {
    Low,
    Review,
    High
}

enum class CleanerRiskReason {
    TemporaryOrCache,
    LogFile,
    BackupFile,
    DumpFile,
    UserFolder,
    MediaFolder,
    AppLikeFolder,
    ArcileInternal,
    SystemOwnedPath
}

@Immutable
data class CleanerCandidate(
    val name: String,
    val absolutePath: String,
    val size: Long,
    val lastModified: Long,
    val groupTypes: Set<CleanerGroupType>,
    val riskLevel: CleanerRiskLevel = CleanerRiskLevel.Low,
    val riskReasons: Set<CleanerRiskReason> = emptySet()
)

@Immutable
data class CleanerGroup(
    val type: CleanerGroupType,
    val candidates: List<CleanerCandidate>
) {
    val totalBytes: Long get() = candidates.sumOf { it.size }
}

@Immutable
data class StorageCleanerResult(
    val groups: List<CleanerGroup>,
    val scannedFiles: Int,
    val isPartial: Boolean
)

@Immutable
data class StorageCleanerScanLimits(
    val maxFiles: Int = 25_000,
    val maxDepth: Int = 10,
    val maxCandidatesPerGroup: Int = 200,
    val largeFileThresholdBytes: Long = 100L * 1024L * 1024L,
    val oldDownloadAgeMs: Long = 30L * 24L * 60L * 60L * 1000L
)
