package dev.qtremors.arcile.feature.imagegallery

import dev.qtremors.arcile.core.presentation.OperationUiState
import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.core.storage.domain.ClipboardState
import dev.qtremors.arcile.core.storage.domain.DeleteDecision
import dev.qtremors.arcile.core.storage.domain.FileConflict
import dev.qtremors.arcile.core.storage.domain.FileListingPreferences
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.storageParentPath
import dev.qtremors.arcile.core.storage.domain.normalizeStoragePath
import dev.qtremors.arcile.core.storage.domain.FileSortOption
import dev.qtremors.arcile.core.storage.domain.FileViewMode
import dev.qtremors.arcile.core.storage.domain.ImageGalleryDefaultTab
import dev.qtremors.arcile.core.storage.domain.ImageGalleryGrouping
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

internal data class ImageGalleryState(
    val volumeId: String? = null,
    val files: PersistentList<FileModel> = persistentListOf(),
    val displayedFiles: PersistentList<FileModel> = persistentListOf(),
    val albums: PersistentList<ImageGalleryAlbum> = persistentListOf(),
    val selectedAlbumPath: String? = null,
    val searchQuery: String = "",
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
    val imageGalleryGrouping: ImageGalleryGrouping = ImageGalleryGrouping.MONTH,
    val imageGalleryDefaultTab: ImageGalleryDefaultTab = ImageGalleryDefaultTab.PHOTOS,
    val galleryScrollbarEnabled: Boolean = true,
    val preferencesLoaded: Boolean = false,
    val albumPresentation: FileListingPreferences = FileListingPreferences(
        sortOption = FileSortOption.NAME_ASC,
        viewMode = FileViewMode.GRID,
        gridMinCellSize = 160f
    ),
    val aspectRatios: PersistentMap<String, Float> = persistentMapOf(),
    val favoriteFiles: PersistentSet<String> = persistentSetOf(),
    val pinnedAlbums: PersistentSet<String> = persistentSetOf(),
    val albumCovers: PersistentMap<String, String> = persistentMapOf(),
    val viewerReturnPath: String? = null,
    val fileActions: ImageGalleryFileActionState = ImageGalleryFileActionState()
) {
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
    val pasteDestinationAlbumPath: String? get() = fileActions.pasteDestinationAlbumPath
}

internal fun ImageGalleryState.withoutGalleryPaths(paths: Collection<String>): ImageGalleryState {
    if (paths.isEmpty()) return this
    val removed = paths.mapTo(mutableSetOf(), ::normalizeStoragePath)
    fun isRemoved(path: String): Boolean = normalizeStoragePath(path) in removed
    val nextFiles = files.filterNot { isRemoved(it.absolutePath) }
    return copy(
        files = nextFiles.toPersistentList(),
        albums = buildImageGalleryAlbums(nextFiles).toPersistentList(),
        favoriteFiles = favoriteFiles.filterNot(::isRemoved).toPersistentSet(),
        albumCovers = albumCovers.filterValues { !isRemoved(it) }.toPersistentMap()
    ).withResolvedDisplayedFiles()
}

internal fun ImageGalleryState.withResolvedDisplayedFiles(): ImageGalleryState {
    val albumFiltered = when (selectedAlbumPath) {
        "__favorites__" -> files.filter { it.absolutePath in favoriteFiles }
        null -> files
        else -> files.filter { storageParentPath(it.absolutePath) == selectedAlbumPath }
    }
    return copy(
        displayedFiles = filterAndSortFiles(
            albumFiltered,
            searchQuery,
            presentation.sortOption
        ).toPersistentList()
    )
}
