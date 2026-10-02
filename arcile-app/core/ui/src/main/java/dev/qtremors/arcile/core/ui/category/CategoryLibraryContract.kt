package dev.qtremors.arcile.core.ui.category

import androidx.compose.runtime.Stable
import dev.qtremors.arcile.core.storage.domain.CategoryGrouping
import dev.qtremors.arcile.core.storage.domain.CategoryLibraryPage
import dev.qtremors.arcile.core.storage.domain.FileListingPreferences
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.SearchFilters

@Stable
data class CategoryLibraryState(
    val files: List<FileModel>,
    val folders: List<CategoryFolderSummary>,
    val selectedPaths: Set<String>,
    val query: String,
    val searchFilters: SearchFilters,
    val tab: CategoryLibraryPage,
    val itemPresentation: FileListingPreferences,
    val folderPresentation: FileListingPreferences,
    val defaultPage: CategoryLibraryPage,
    val grouping: CategoryGrouping,
    val showFileDetails: Boolean,
    val scrollbarEnabled: Boolean,
    val isLoading: Boolean,
    val folderFilterLabel: String?,
    val folderFilterPath: String?,
    val labels: CategoryLibraryLabels
)

@Stable
data class CategoryLibraryActions(
    val onNavigateBack: () -> Unit,
    val onQueryChange: (String) -> Unit,
    val onSearchFiltersChange: (SearchFilters) -> Unit,
    val onTabChange: (CategoryLibraryPage) -> Unit,
    val onPresentationChange: (CategoryLibraryPage, FileListingPreferences) -> Unit,
    val onDefaultPageChange: (CategoryLibraryPage) -> Unit,
    val onGroupingChange: (CategoryGrouping) -> Unit,
    val onShowFileDetailsChange: (Boolean) -> Unit,
    val onRefresh: () -> Unit,
    val onClearFolderFilter: () -> Unit,
    val onToggleSelection: (String) -> Unit,
    val onSelectPaths: (Collection<String>) -> Unit,
    val onClearSelection: () -> Unit,
    val onSelectAll: () -> Unit,
    val onInvertSelection: () -> Unit,
    val onShareSelection: () -> Unit,
    val onOpenSelectionWith: () -> Unit,
    val fileActions: CategoryLibraryFileActionCallbacks,
    val onOpenFile: (FileModel) -> Unit,
    val onOpenFolder: (CategoryFolderSummary) -> Unit
)
