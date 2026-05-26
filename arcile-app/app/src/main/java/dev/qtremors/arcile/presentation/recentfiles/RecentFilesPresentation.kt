package dev.qtremors.arcile.presentation.recentfiles

import dev.qtremors.arcile.core.storage.domain.BrowserPresentationPreferences
import dev.qtremors.arcile.core.storage.domain.FileCategories
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.SearchFilters
import dev.qtremors.arcile.presentation.filterAndSortFiles

fun buildRecentFilesDisplay(
    files: List<FileModel>,
    query: String,
    filters: SearchFilters,
    presentation: BrowserPresentationPreferences
): List<FileModel> {
    val normalizedQuery = query.trim().lowercase()
    val filtered = files.filter { file ->
        (normalizedQuery.isBlank() || file.name.lowercase().contains(normalizedQuery)) &&
            file.matches(filters)
    }

    return filterAndSortFiles(
        files = filtered,
        query = "",
        sortOption = presentation.normalized().sortOption
    )
}

private fun FileModel.matches(filters: SearchFilters): Boolean {
    val itemType = filters.itemType?.lowercase()
    if (itemType == "files" && isDirectory) return false
    if (itemType == "folders" && !isDirectory) return false

    filters.fileType?.let { type ->
        val selectedCategory = FileCategories.all.firstOrNull { category -> category.name == type }
        if (selectedCategory != null) {
            val fileCategory = FileCategories.getCategoryForFile(extension, mimeType)
            if (fileCategory != selectedCategory) return false
        }
    }

    filters.minSize?.let { if (size < it) return false }
    filters.maxSize?.let { if (size > it) return false }
    filters.minDateMillis?.let { if (lastModified < it) return false }
    filters.maxDateMillis?.let { if (lastModified > it) return false }

    return true
}
