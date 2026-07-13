package dev.qtremors.arcile.feature.archive

import dev.qtremors.arcile.core.storage.domain.ArchiveEntryModel

internal data class ArchiveBrowserItem(
    val name: String,
    val path: String,
    val size: Long,
    val lastModified: Long?,
    val isDirectory: Boolean,
    val childCount: Int = 0
)

internal fun buildArchiveBrowserItems(
    entries: List<ArchiveEntryModel>,
    prefix: String?,
    searchQuery: String
): List<ArchiveBrowserItem> {
    val normalizedPrefix = prefix?.trimEnd('/')?.takeIf { it.isNotBlank() }
    val normalizedQuery = searchQuery.trim().lowercase().takeIf { it.isNotBlank() }
    val children = linkedMapOf<String, ArchiveBrowserItem>()
    entries.forEach { entry ->
        if (normalizedQuery != null && !entry.path.lowercase().contains(normalizedQuery)) return@forEach
        val path = entry.path.trim('/')
        val remainder = when {
            normalizedPrefix == null -> path
            path == normalizedPrefix -> ""
            path.startsWith("$normalizedPrefix/") -> path.removePrefix("$normalizedPrefix/")
            else -> return@forEach
        }
        if (remainder.isBlank()) return@forEach

        val childName = remainder.substringBefore('/')
        val childPath = if (normalizedPrefix == null) childName else "$normalizedPrefix/$childName"
        val isFolder = remainder.contains('/') ||
            entries.any { it.isDirectory && it.path.trimEnd('/') == childPath }
        val childCount = if (isFolder) {
            entries.count { candidate ->
                val candidatePath = candidate.path.trim('/')
                candidatePath.startsWith("$childPath/") &&
                    candidatePath.removePrefix("$childPath/").trim('/').isNotBlank()
            }
        } else {
            0
        }
        val existing = children[childPath]
        if (existing == null || (!isFolder && existing.isDirectory)) {
            children[childPath] = ArchiveBrowserItem(
                name = childName,
                path = childPath,
                size = if (isFolder) 0L else entry.size,
                lastModified = entry.lastModified,
                isDirectory = isFolder,
                childCount = childCount
            )
        }
    }
    return children.values.sortedWith(
        compareBy<ArchiveBrowserItem> { !it.isDirectory }.thenBy { it.name.lowercase() }
    )
}
