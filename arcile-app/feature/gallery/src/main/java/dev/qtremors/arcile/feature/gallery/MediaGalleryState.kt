package dev.qtremors.arcile.feature.gallery

import dev.qtremors.arcile.core.presentation.OperationUiState
import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.core.storage.domain.ClipboardState
import dev.qtremors.arcile.core.storage.domain.DeleteDecision
import dev.qtremors.arcile.core.storage.domain.FileConflict
import dev.qtremors.arcile.core.storage.domain.FileCategories
import dev.qtremors.arcile.core.storage.domain.FileListingPreferences
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.storageParentPath
import dev.qtremors.arcile.core.storage.domain.normalizeStoragePath
import dev.qtremors.arcile.core.storage.domain.FileSortOption
import dev.qtremors.arcile.core.storage.domain.FileViewMode
import dev.qtremors.arcile.core.storage.domain.CategoryLibraryPage
import dev.qtremors.arcile.core.storage.domain.CategoryGrouping
import dev.qtremors.arcile.core.storage.domain.SearchFilters
import dev.qtremors.arcile.core.ui.category.matchesCategorySearchFilters
import dev.qtremors.arcile.core.presentation.PropertiesUiModel
import dev.qtremors.arcile.core.presentation.filterAndSortFiles
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.collections.immutable.toPersistentSet

internal data class MediaGalleryState(
    val volumeId: String? = null,
    val categoryId: String = FileCategories.Images.id.value,
    val files: PersistentList<FileModel> = persistentListOf(),
    val displayedFiles: PersistentList<FileModel> = persistentListOf(),
    val folders: PersistentList<MediaGalleryFolder> = persistentListOf(),
    val selectedFolderPath: String? = null,
    val searchQuery: String = "",
    val searchFilters: SearchFilters = SearchFilters(),
    val presentation: FileListingPreferences = FileListingPreferences(
        sortOption = FileListingPreferences.DEFAULT_CATEGORY_SORT_OPTION,
        viewMode = FileViewMode.GRID,
        gridMinCellSize = 136f,
        showThumbnails = true
    ),
    val showFileDetails: Boolean = true,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isSnapshotStale: Boolean = false,
    val error: UiText? = null,
    val isAspectRatio: Boolean = false,
    val isSectioned: Boolean = false,
    val grouping: CategoryGrouping = CategoryGrouping.MONTH,
    val defaultPage: CategoryLibraryPage = CategoryLibraryPage.ITEMS,
    val galleryScrollbarEnabled: Boolean = true,
    val preferencesLoaded: Boolean = false,
    val folderPresentation: FileListingPreferences = FileListingPreferences(
        sortOption = FileSortOption.NAME_ASC,
        viewMode = FileViewMode.GRID,
        gridMinCellSize = 160f
    ),
    val aspectRatios: PersistentMap<String, Float> = persistentMapOf(),
    val favoriteFiles: PersistentSet<String> = persistentSetOf(),
    val pinnedFolders: PersistentSet<String> = persistentSetOf(),
    val folderCovers: PersistentMap<String, String> = persistentMapOf(),
    val viewerReturnPath: String? = null,
    val fileActions: MediaGalleryFileActionState = MediaGalleryFileActionState()
) {
    val isVideoGallery: Boolean get() = FileCategories.Videos.matches(categoryId)
    val selectedFiles: PersistentSet<String> get() = fileActions.selectedFiles
    val showTrashConfirmation: Boolean get() = fileActions.showTrashConfirmation
    val showPermanentDeleteConfirmation: Boolean get() = fileActions.showPermanentDeleteConfirmation
    val showMixedDeleteExplanation: Boolean get() = fileActions.showMixedDeleteExplanation
    val deleteDecision: DeleteDecision? get() = fileActions.deleteDecision
    val isPermanentDeleteChecked: Boolean get() = fileActions.isPermanentDeleteChecked
    val isPermanentDeleteToggleEnabled: Boolean get() = fileActions.isPermanentDeleteToggleEnabled
    val isShredChecked: Boolean get() = fileActions.isShredChecked
    val isPropertiesVisible: Boolean get() = fileActions.isPropertiesVisible
    val isPropertiesLoading: Boolean get() = fileActions.isPropertiesLoading
    val properties: PropertiesUiModel? get() = fileActions.properties
    val clipboardState: ClipboardState? get() = fileActions.clipboardState
    val activeFileOperation: OperationUiState? get() = fileActions.activeFileOperation
    val pasteConflicts: PersistentList<FileConflict> get() = fileActions.pasteConflicts
    val showConflictDialog: Boolean get() = fileActions.showConflictDialog
    val pasteDestinationFolderPath: String? get() = fileActions.pasteDestinationFolderPath
}

internal fun MediaGalleryState.withoutGalleryPaths(paths: Collection<String>): MediaGalleryState {
    if (paths.isEmpty()) return this
    val removed = paths.mapTo(mutableSetOf(), ::normalizeStoragePath)
    fun isRemoved(path: String): Boolean = normalizeStoragePath(path) in removed
    val nextFiles = files.filterNot { isRemoved(it.reference) }
    return copy(
        files = nextFiles.toPersistentList(),
        folders = buildMediaGalleryFolders(nextFiles).toPersistentList(),
        favoriteFiles = favoriteFiles.filterNot(::isRemoved).toPersistentSet(),
        folderCovers = folderCovers.filterValues { !isRemoved(it) }.toPersistentMap()
    ).withResolvedDisplayedFiles()
}

internal fun MediaGalleryState.withResolvedDisplayedFiles(): MediaGalleryState {
    val folderFiltered = when (selectedFolderPath) {
        "__favorites__" -> files.filter { it.reference in favoriteFiles }
        null -> files
        else -> files.filter { storageParentPath(it.reference) == selectedFolderPath }
    }
    val searchFiltered = folderFiltered.filter { file ->
        file.matchesCategorySearchFilters(searchFilters, scopedVolumeId = volumeId)
    }
    return copy(
        displayedFiles = filterAndSortFiles(
            searchFiltered,
            searchQuery,
            presentation.sortOption,
            foldersFirst = presentation.foldersFirst
        ).toPersistentList()
    )
}
