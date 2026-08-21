package dev.qtremors.arcile.core.storage.domain

enum class CleanerGroupType {
    LargeFiles,
    OldDownloads,
    Duplicates,
    Apks,
    Videos,
    MarkerFiles,
    EmptyFolders,
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
    val riskReasons: Set<CleanerRiskReason> = emptySet(),
    val isDirectory: Boolean = false,
    val duplicateGroupKey: String? = null
)

@Immutable
data class CleanerGroup(
    val type: CleanerGroupType,
    val candidates: List<CleanerCandidate>
) {
    val totalBytes: Long get() = candidates.sumOf(CleanerCandidate::size)
}

@Immutable
data class StorageCleanerResult(
    val groups: List<CleanerGroup>,
    val scannedFiles: Int,
    val isPartial: Boolean
)
