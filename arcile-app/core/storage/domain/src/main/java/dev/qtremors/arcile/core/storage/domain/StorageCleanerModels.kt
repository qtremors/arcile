package dev.qtremors.arcile.core.storage.domain

import kotlinx.serialization.Serializable

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

@Serializable
@Immutable
data class StorageCleanerRules(
    val ignoredPaths: Set<String> = emptySet(),
    val sections: Map<CleanerGroupType, CleanerSectionRule> = defaultSections()
) {
    fun section(type: CleanerGroupType): CleanerSectionRule =
        sections[type] ?: CleanerSectionRule()

    fun withSection(type: CleanerGroupType, rule: CleanerSectionRule): StorageCleanerRules =
        copy(sections = sections + (type to rule.normalized()))

    fun withIgnoredPath(path: String): StorageCleanerRules =
        copy(ignoredPaths = ignoredPaths + path)

    fun withoutIgnoredPath(path: String): StorageCleanerRules =
        copy(ignoredPaths = ignoredPaths - path)

    fun normalized(): StorageCleanerRules =
        copy(
            ignoredPaths = ignoredPaths.mapTo(linkedSetOf()) { it.trim() }.filterTo(linkedSetOf()) { it.isNotBlank() },
            sections = CleanerGroupType.entries.associateWith { section(it).normalized() }
        )

    companion object {
        fun defaultSections(): Map<CleanerGroupType, CleanerSectionRule> =
            CleanerGroupType.entries.associateWith { CleanerSectionRule() }
    }
}

@Serializable
@Immutable
data class CleanerSectionRule(
    val enabled: Boolean = true,
    val ignoredNamePatterns: Set<String> = emptySet(),
    val ignoredPathPatterns: Set<String> = emptySet(),
    val largeFileThresholdBytes: Long? = null,
    val oldDownloadAgeMs: Long? = null
) {
    fun normalized(): CleanerSectionRule =
        copy(
            ignoredNamePatterns = ignoredNamePatterns.normalizePatterns(),
            ignoredPathPatterns = ignoredPathPatterns.normalizePatterns(),
            largeFileThresholdBytes = largeFileThresholdBytes?.coerceAtLeast(1L),
            oldDownloadAgeMs = oldDownloadAgeMs?.coerceAtLeast(1L)
        )
}

private fun Set<String>.normalizePatterns(): Set<String> =
    mapTo(linkedSetOf()) { it.trim() }.filterTo(linkedSetOf()) { it.isNotBlank() }
