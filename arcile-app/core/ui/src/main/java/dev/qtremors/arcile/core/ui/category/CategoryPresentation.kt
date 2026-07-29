package dev.qtremors.arcile.core.ui.category

import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.FileSortOption
import dev.qtremors.arcile.core.storage.domain.CategoryGrouping
import dev.qtremors.arcile.core.storage.domain.SearchFilters
import dev.qtremors.arcile.core.storage.domain.matchesSearchFilters
import dev.qtremors.arcile.core.storage.domain.storageParentPath
import dev.qtremors.arcile.core.storage.domain.storagePathName
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class CategoryDateGroup(
    val label: String,
    val timestamp: Long
) : Comparable<CategoryDateGroup> {
    override fun compareTo(other: CategoryDateGroup): Int =
        other.timestamp.compareTo(timestamp)
}

fun groupCategoryFiles(
    files: List<FileModel>,
    grouping: CategoryGrouping
): Map<CategoryDateGroup, List<FileModel>> {
    if (grouping == CategoryGrouping.NONE) return emptyMap()
    val dayFormatter = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
    val monthFormatter = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    return files.groupBy { file ->
        val calendar = Calendar.getInstance().apply { timeInMillis = file.lastModified }
        val label = when (grouping) {
            CategoryGrouping.DAY -> dayFormatter.format(Date(file.lastModified))
            CategoryGrouping.WEEK -> categoryWeekLabel(file.lastModified)
            CategoryGrouping.MONTH -> monthFormatter.format(Date(file.lastModified))
            CategoryGrouping.NONE -> ""
        }
        when (grouping) {
            CategoryGrouping.DAY -> Unit
            CategoryGrouping.WEEK -> {
                while (calendar.get(Calendar.DAY_OF_WEEK) != calendar.firstDayOfWeek) {
                    calendar.add(Calendar.DAY_OF_MONTH, -1)
                }
            }
            CategoryGrouping.MONTH -> calendar.set(Calendar.DAY_OF_MONTH, 1)
            CategoryGrouping.NONE -> Unit
        }
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        CategoryDateGroup(label, calendar.timeInMillis)
    }.toSortedMap()
}

fun categoryFileForLazyIndex(
    index: Int,
    files: List<FileModel>,
    grouping: CategoryGrouping,
    groups: Map<CategoryDateGroup, List<FileModel>>
): FileModel? {
    if (grouping == CategoryGrouping.NONE) return files.getOrNull(index)
    var lazyIndex = 0
    groups.values.forEach { groupFiles ->
        if (groupFiles.isEmpty()) return@forEach
        if (index == lazyIndex) return groupFiles.firstOrNull()
        lazyIndex += 1
        val fileIndex = index - lazyIndex
        if (fileIndex in groupFiles.indices) return groupFiles[fileIndex]
        lazyIndex += groupFiles.size
    }
    return null
}

private fun categoryWeekLabel(timestamp: Long): String {
    val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
    while (calendar.get(Calendar.DAY_OF_WEEK) != calendar.firstDayOfWeek) {
        calendar.add(Calendar.DAY_OF_MONTH, -1)
    }
    val formatter = SimpleDateFormat("MMM d", Locale.getDefault())
    val start = formatter.format(calendar.time)
    calendar.add(Calendar.DAY_OF_MONTH, 6)
    return "$start – ${formatter.format(calendar.time)}, ${calendar.get(Calendar.YEAR)}"
}

fun FileModel.matchesCategorySearchFilters(
    filters: SearchFilters,
    scopedVolumeId: String? = null
): Boolean {
    val requestedVolume = filters.storageVolumeId?.takeIf(String::isNotBlank)
    val volumeMatches = requestedVolume == null ||
        requestedVolume == scopedVolumeId ||
        requestedVolume == nodeRef.volumeId?.value
    return volumeMatches && matchesSearchFilters(filters.copy(storageVolumeId = null))
}

fun buildCategoryFolders(files: List<FileModel>): List<CategoryFolderSummary> =
    files.groupBy { storageParentPath(it.absolutePath).orEmpty() }
        .filterKeys(String::isNotBlank)
        .map { (path, children) ->
            CategoryFolderSummary(
                path = path,
                label = storagePathName(path).ifBlank { path },
                itemCount = children.size,
                totalSize = children.sumOf(FileModel::size),
                lastModified = children.maxOfOrNull(FileModel::lastModified) ?: 0L,
                preview = children.maxByOrNull(FileModel::lastModified)
            )
        }

fun sortCategoryFiles(
    files: List<FileModel>,
    option: FileSortOption
): List<FileModel> = when (option) {
    FileSortOption.NAME_ASC -> files.sortedBy { it.name.lowercase() }
    FileSortOption.NAME_DESC -> files.sortedByDescending { it.name.lowercase() }
    FileSortOption.DATE_NEWEST -> files.sortedByDescending(FileModel::lastModified)
    FileSortOption.DATE_OLDEST -> files.sortedBy(FileModel::lastModified)
    FileSortOption.SIZE_LARGEST -> files.sortedByDescending(FileModel::size)
    FileSortOption.SIZE_SMALLEST -> files.sortedBy(FileModel::size)
}

fun sortCategoryFolders(
    folders: List<CategoryFolderSummary>,
    option: FileSortOption
): List<CategoryFolderSummary> = when (option) {
    FileSortOption.NAME_ASC -> folders.sortedBy { it.label.lowercase() }
    FileSortOption.NAME_DESC -> folders.sortedByDescending { it.label.lowercase() }
    FileSortOption.DATE_NEWEST -> folders.sortedByDescending(CategoryFolderSummary::lastModified)
    FileSortOption.DATE_OLDEST -> folders.sortedBy(CategoryFolderSummary::lastModified)
    FileSortOption.SIZE_LARGEST -> folders.sortedByDescending(CategoryFolderSummary::totalSize)
    FileSortOption.SIZE_SMALLEST -> folders.sortedBy(CategoryFolderSummary::totalSize)
}
