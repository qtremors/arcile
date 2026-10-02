package dev.qtremors.arcile.core.ui.category

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.qtremors.arcile.core.storage.domain.FileModel

@Composable
fun FileCategoryLibrary(
    state: CategoryLibraryState,
    actions: CategoryLibraryActions,
    fileItem: @Composable (
        file: FileModel,
        selected: Boolean,
        selectionMode: Boolean,
        onClick: () -> Unit,
        onLongClick: () -> Unit,
        modifier: Modifier
    ) -> Unit,
    folderItem: @Composable (
        folder: CategoryFolderSummary,
        onClick: () -> Unit,
        modifier: Modifier
    ) -> Unit
) {
    FileCategoryLibraryContent(
        files = state.files,
        folders = state.folders,
        selectedPaths = state.selectedPaths,
        query = state.query,
        searchFilters = state.searchFilters,
        tab = state.tab,
        itemPresentation = state.itemPresentation,
        folderPresentation = state.folderPresentation,
        defaultPage = state.defaultPage,
        grouping = state.grouping,
        showFileDetails = state.showFileDetails,
        scrollbarEnabled = state.scrollbarEnabled,
        isLoading = state.isLoading,
        folderFilterLabel = state.folderFilterLabel,
        folderFilterPath = state.folderFilterPath,
        labels = state.labels,
        onNavigateBack = actions.onNavigateBack,
        onQueryChange = actions.onQueryChange,
        onSearchFiltersChange = actions.onSearchFiltersChange,
        onTabChange = actions.onTabChange,
        onPresentationChange = actions.onPresentationChange,
        onDefaultPageChange = actions.onDefaultPageChange,
        onGroupingChange = actions.onGroupingChange,
        onShowFileDetailsChange = actions.onShowFileDetailsChange,
        onRefresh = actions.onRefresh,
        onClearFolderFilter = actions.onClearFolderFilter,
        onToggleSelection = actions.onToggleSelection,
        onSelectPaths = actions.onSelectPaths,
        onClearSelection = actions.onClearSelection,
        onSelectAll = actions.onSelectAll,
        onInvertSelection = actions.onInvertSelection,
        onShareSelection = actions.onShareSelection,
        onOpenSelectionWith = actions.onOpenSelectionWith,
        fileActions = actions.fileActions,
        fileItem = fileItem,
        folderItem = folderItem,
        onOpenFile = actions.onOpenFile,
        onOpenFolder = actions.onOpenFolder
    )
}
