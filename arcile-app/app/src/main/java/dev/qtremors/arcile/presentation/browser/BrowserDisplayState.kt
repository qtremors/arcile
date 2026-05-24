package dev.qtremors.arcile.presentation.browser

import androidx.compose.runtime.Immutable
import dev.qtremors.arcile.domain.FileModel
import dev.qtremors.arcile.domain.StorageVolume
import dev.qtremors.arcile.presentation.FileSortOption
import dev.qtremors.arcile.presentation.FolderTab
import dev.qtremors.arcile.presentation.buildFolderTabs
import dev.qtremors.arcile.presentation.filterAndSortFiles
import dev.qtremors.arcile.presentation.filterFilesByFolderTab
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentSet

@Immutable
data class BrowserDisplayState(
    val visibleFiles: PersistentList<FileModel> = persistentListOf(),
    val sortedCategoryFiles: PersistentList<FileModel> = persistentListOf(),
    val categoryFolderTabs: PersistentList<FolderTab> = persistentListOf(),
    val selectedCategoryFolderTabIndex: Int = 0,
    val currentVolume: StorageVolume? = null,
    val visiblePaths: PersistentList<String> = persistentListOf(),
    val existingNames: PersistentSet<String> = persistentSetOf()
)

fun buildBrowserDisplayState(
    files: List<FileModel>,
    sortOption: FileSortOption,
    selectedFolderTabPath: String?,
    isCategoryScreen: Boolean,
    currentVolumeId: String?,
    storageVolumes: List<StorageVolume>,
    allFilesLabel: String
): BrowserDisplayState {
    val tabFilteredFiles = filterFilesByFolderTab(files, selectedFolderTabPath)
    val visibleFiles = filterAndSortFiles(tabFilteredFiles, "", sortOption)
    val sortedCategoryFiles = filterAndSortFiles(files, "", sortOption)
    val categoryFolderTabs = if (isCategoryScreen) {
        buildFolderTabs(sortedCategoryFiles, allFilesLabel)
    } else {
        emptyList()
    }
    val selectedCategoryFolderTabIndex = categoryFolderTabs
        .indexOfFirst { it.path == selectedFolderTabPath }
        .takeIf { it >= 0 }
        ?: 0

    return BrowserDisplayState(
        visibleFiles = visibleFiles.toPersistentList(),
        sortedCategoryFiles = sortedCategoryFiles.toPersistentList(),
        categoryFolderTabs = categoryFolderTabs.toPersistentList(),
        selectedCategoryFolderTabIndex = selectedCategoryFolderTabIndex,
        currentVolume = storageVolumes.firstOrNull { it.id == currentVolumeId },
        visiblePaths = visibleFiles.map { it.absolutePath }.toPersistentList(),
        existingNames = files.map { it.name }.toPersistentSet()
    )
}
