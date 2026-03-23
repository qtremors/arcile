package dev.qtremors.arcile.presentation

import dev.qtremors.arcile.domain.FileModel

enum class FileSortOption {
    NAME_ASC,
    NAME_DESC,
    DATE_NEWEST,
    DATE_OLDEST,
    SIZE_LARGEST,
    SIZE_SMALLEST
}

fun filterAndSortFiles(
    files: List<FileModel>,
    query: String,
    sortOption: FileSortOption
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
    }

    return filteredFiles.sortedWith(
        compareBy<FileModel> { !it.isDirectory }
            .then(sortComparator)
    )
}
