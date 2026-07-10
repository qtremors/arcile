package dev.qtremors.arcile.feature.browser.delegate

import dev.qtremors.arcile.core.storage.domain.ArchiveEntryModel
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.ui.image.ArchiveEntryThumbnailData

internal object BrowserArchiveListingMapper {
    fun map(
        archivePath: String,
        entries: List<ArchiveEntryModel>,
        prefix: String?
    ): List<FileModel> {
        val normalizedPrefix = prefix?.trimEnd('/')?.takeIf { it.isNotBlank() }
        val directoryPaths = entries
            .asSequence()
            .flatMap { entry ->
                val path = entry.path.trim('/').takeIf { it.isNotBlank() }
                    ?: return@flatMap emptySequence()
                val explicit = if (entry.isDirectory) {
                    sequenceOf(path.trimEnd('/'))
                } else {
                    emptySequence()
                }
                val implicit = path.split('/')
                    .dropLast(1)
                    .runningFold("") { parent, segment ->
                        if (parent.isBlank()) segment else "$parent/$segment"
                    }
                    .drop(1)
                    .asSequence()
                explicit + implicit
            }
            .toSet()
        val children = linkedMapOf<String, FileModel>()
        entries.forEach { entry ->
            val path = entry.path.trim('/')
            val remainder = when {
                normalizedPrefix == null -> path
                path == normalizedPrefix -> ""
                path.startsWith("$normalizedPrefix/") ->
                    path.removePrefix("$normalizedPrefix/")
                else -> return@forEach
            }
            if (remainder.isBlank()) return@forEach
            val childName = remainder.substringBefore('/')
            val childPath = if (normalizedPrefix == null) {
                childName
            } else {
                "$normalizedPrefix/$childName"
            }
            val isDirectory = remainder.contains('/') || childPath in directoryPaths
            val existing = children[childPath]
            if (existing == null || (!isDirectory && existing.isDirectory)) {
                children[childPath] = FileModel(
                    name = childName,
                    absolutePath = ArchiveEntryThumbnailData.virtualPath(archivePath, childPath),
                    size = if (isDirectory) 0L else entry.size,
                    lastModified = entry.lastModified ?: 0L,
                    isDirectory = isDirectory,
                    extension = if (isDirectory) {
                        ""
                    } else {
                        childName.substringAfterLast('.', missingDelimiterValue = "").lowercase()
                    },
                    isHidden = childName.startsWith(".")
                )
            }
        }
        return children.values.toList()
    }
}
