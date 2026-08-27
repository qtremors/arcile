package dev.qtremors.arcile.core.storage.domain

import kotlinx.serialization.Serializable

enum class CleanerGroupType {
    LargeFiles,
    OldDownloads,
    Duplicates,
    FilenameVersions,
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

@Serializable
enum class FilenameVersionEvidence {
    SemanticVersion,
    DuplicateSuffix,
    PackageVersion
}

@Serializable
@Immutable
data class FilenameVersionMetadata(
    val familyKey: String,
    val displayStem: String,
    val evidenceType: FilenameVersionEvidence,
    val isLikelyNewest: Boolean = false
)

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
    val duplicateGroupKey: String? = null,
    val filenameVersionMetadata: FilenameVersionMetadata? = null
) {
    val versionFamilyKey: String? get() = filenameVersionMetadata?.familyKey
    val isLikelyNewestVersion: Boolean get() = filenameVersionMetadata?.isLikelyNewest ?: false
    val groups: Set<CleanerGroupType> get() = groupTypes
}

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
