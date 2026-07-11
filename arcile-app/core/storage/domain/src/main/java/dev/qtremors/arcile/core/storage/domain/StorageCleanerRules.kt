package dev.qtremors.arcile.core.storage.domain

import kotlinx.serialization.Serializable

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

    fun normalized(): StorageCleanerRules = copy(
        ignoredPaths = ignoredPaths.mapTo(linkedSetOf(), String::trim)
            .filterTo(linkedSetOf(), String::isNotBlank),
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
    fun normalized(): CleanerSectionRule = copy(
        ignoredNamePatterns = ignoredNamePatterns.normalizePatterns(),
        ignoredPathPatterns = ignoredPathPatterns.normalizePatterns(),
        largeFileThresholdBytes = largeFileThresholdBytes?.coerceAtLeast(1L),
        oldDownloadAgeMs = oldDownloadAgeMs?.coerceAtLeast(1L)
    )
}

private fun Set<String>.normalizePatterns(): Set<String> =
    mapTo(linkedSetOf(), String::trim).filterTo(linkedSetOf(), String::isNotBlank)
