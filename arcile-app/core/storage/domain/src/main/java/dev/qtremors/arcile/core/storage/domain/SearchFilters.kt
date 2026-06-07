package dev.qtremors.arcile.core.storage.domain

data class SearchFilters(
    val fileType: String? = null,
    val itemType: String? = null,
    val minSize: Long? = null,
    val maxSize: Long? = null,
    val minDateMillis: Long? = null,
    val maxDateMillis: Long? = null,
    val extensions: Set<String> = emptySet(),
    val includeHidden: Boolean = false,
    val storageVolumeId: String? = null,
    val folderScopePath: String? = null,
    val mimeType: String? = null,
    val savedPresetName: String? = null
) {
    val hasActiveFilters: Boolean
        get() = fileType != null ||
            itemType != null ||
            minSize != null ||
            maxSize != null ||
            minDateMillis != null ||
            maxDateMillis != null ||
            extensions.isNotEmpty() ||
            includeHidden ||
            storageVolumeId != null ||
            folderScopePath != null ||
            mimeType != null
}

fun FileModel.matchesSearchFilters(
    filters: SearchFilters,
    volumes: List<StorageVolume> = emptyList()
): Boolean {
    if (!filters.includeHidden && isHidden) return false

    when (filters.itemType) {
        "Files" -> if (isDirectory) return false
        "Folders" -> if (!isDirectory) return false
    }

    if (filters.fileType != null && filters.fileType != "All") {
        val category = FileCategories.all.find { it.name == filters.fileType }
        if (category != null) {
            val categoryExtensions = category.extensions.map { it.lowercase() }.toSet()
            if (isDirectory || extension.lowercase() !in categoryExtensions) return false
        }
    }

    if (filters.extensions.isNotEmpty() && extension.lowercase() !in filters.extensions.map { it.lowercase().trimStart('.') }) {
        return false
    }

    filters.mimeType?.takeIf { it.isNotBlank() }?.let { requestedMime ->
        val normalized = requestedMime.trim().lowercase()
        val actual = mimeType.orEmpty().lowercase()
        val matchesMime = if (normalized.endsWith("/*")) {
            actual.startsWith(normalized.removeSuffix("*"))
        } else {
            actual == normalized
        }
        if (!matchesMime) return false
    }

    filters.minSize?.let { if (!isDirectory && size < it) return false }
    filters.maxSize?.let { if (!isDirectory && size > it) return false }
    filters.minDateMillis?.let { if (lastModified < it) return false }
    filters.maxDateMillis?.let { if (lastModified > it) return false }

    filters.folderScopePath?.takeIf { it.isNotBlank() }?.let { scope ->
        val normalizedScope = scope.trimEnd('/', '\\')
        val normalizedPath = absolutePath.trimEnd('/', '\\')
        if (normalizedPath != normalizedScope && !normalizedPath.startsWith("$normalizedScope/")) {
            return false
        }
    }

    filters.storageVolumeId?.takeIf { it.isNotBlank() }?.let { volumeId ->
        val volume = volumes.firstOrNull { it.id == volumeId } ?: return false
        val root = volume.path.trimEnd('/', '\\')
        val normalizedPath = absolutePath.trimEnd('/', '\\')
        if (normalizedPath != root && !normalizedPath.startsWith("$root/")) return false
    }

    return true
}
