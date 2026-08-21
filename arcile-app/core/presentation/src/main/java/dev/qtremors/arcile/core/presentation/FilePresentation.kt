package dev.qtremors.arcile.core.presentation

import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.FileSortOption

fun filterAndSortFiles(
    files: List<FileModel>,
    query: String,
    sortOption: FileSortOption,
    fileCountFor: (FileModel) -> Long? = { file -> if (file.isDirectory) null else 1L }
): List<FileModel> {
    val normalizedQuery = query.trim().lowercase()
    val filteredFiles = if (normalizedQuery.isBlank()) {
        files
    } else {
        files.filter { file ->
            file.name.lowercase().contains(normalizedQuery)
        }
    }

    val sortComparator = when (sortOption) {
        FileSortOption.NAME_ASC -> compareBy<FileModel> { it.name.lowercase() }
        FileSortOption.NAME_DESC -> compareByDescending<FileModel> { it.name.lowercase() }
        FileSortOption.DATE_NEWEST -> compareByDescending<FileModel> { it.lastModified }
        FileSortOption.DATE_OLDEST -> compareBy<FileModel> { it.lastModified }
        FileSortOption.SIZE_LARGEST -> compareByDescending<FileModel> { it.size }
        FileSortOption.SIZE_SMALLEST -> compareBy<FileModel> { it.size }
        FileSortOption.FILE_COUNT_HIGHEST -> compareBy<FileModel> { fileCountFor(it) == null }
            .thenByDescending { fileCountFor(it) ?: Long.MIN_VALUE }
        FileSortOption.FILE_COUNT_LOWEST -> compareBy<FileModel> { fileCountFor(it) == null }
            .thenBy { fileCountFor(it) ?: Long.MAX_VALUE }
    }

    val comparator = when (sortOption) {
        FileSortOption.DATE_NEWEST,
        FileSortOption.DATE_OLDEST -> sortComparator
        else -> compareBy<FileModel> { !it.isDirectory }.then(sortComparator)
    }
    return filteredFiles.sortedWith(
        comparator.thenBy(String.CASE_INSENSITIVE_ORDER, FileModel::name)
    )
}
