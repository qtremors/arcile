package dev.qtremors.arcile.feature.apk

import dev.qtremors.arcile.core.storage.domain.FileListingPreferences
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.FileSortOption
import dev.qtremors.arcile.core.storage.domain.FileViewMode
import dev.qtremors.arcile.core.storage.domain.SearchFilters
import dev.qtremors.arcile.core.storage.domain.CategoryGrouping
import dev.qtremors.arcile.core.ui.category.CategoryFolderSummary
import dev.qtremors.arcile.core.storage.domain.CategoryLibraryPage
import dev.qtremors.arcile.core.ui.category.CategoryFileActionState
import dev.qtremors.arcile.core.ui.category.buildCategoryFolders
import dev.qtremors.arcile.core.ui.category.sortCategoryFiles
import dev.qtremors.arcile.core.ui.category.sortCategoryFolders
import dev.qtremors.arcile.core.ui.category.matchesCategorySearchFilters

internal data class ApkLibraryState(
    val allFiles: List<FileModel> = emptyList(),
    val files: List<FileModel> = emptyList(),
    val folders: List<CategoryFolderSummary> = emptyList(),
    val selectedPaths: Set<String> = emptySet(),
    val query: String = "",
    val searchFilters: SearchFilters = SearchFilters(),
    val tab: CategoryLibraryPage = CategoryLibraryPage.ITEMS,
    val folderFilter: CategoryFolderSummary? = null,
    val presentation: FileListingPreferences = FileListingPreferences(
        sortOption = FileSortOption.DATE_NEWEST
    ),
    val folderPresentation: FileListingPreferences = FileListingPreferences(
        sortOption = FileSortOption.DATE_NEWEST,
        viewMode = FileViewMode.GRID
    ),
    val grouping: CategoryGrouping = CategoryGrouping.MONTH,
    val defaultPage: CategoryLibraryPage = CategoryLibraryPage.ITEMS,
    val showFileDetails: Boolean = true,
    val preferencesLoaded: Boolean = false,
    val scrollbarEnabled: Boolean = true,
    val fileActions: CategoryFileActionState = CategoryFileActionState(),
    val isLoading: Boolean = true,
    val error: String? = null
)

internal fun presentApks(state: ApkLibraryState): ApkLibraryState {
    val query = state.query.trim()
    val categoryFiles = state.allFiles.filter {
        it.matchesCategorySearchFilters(state.searchFilters)
    }
    val filtered = categoryFiles.asSequence()
        .filter {
            state.folderFilter == null ||
                dev.qtremors.arcile.core.storage.domain.storageParentPath(it.absolutePath) ==
                    state.folderFilter.path
        }
        .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
        .toList()
    val folders = buildCategoryFolders(categoryFiles)
        .filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
    return state.copy(
        files = sortCategoryFiles(filtered, state.presentation.sortOption),
        folders = sortCategoryFolders(folders, state.folderPresentation.sortOption),
        selectedPaths = state.selectedPaths.intersect(state.allFiles.mapTo(mutableSetOf(), FileModel::absolutePath))
    )
}
