package dev.qtremors.arcile.feature.browser

import androidx.compose.runtime.Immutable
import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.core.storage.domain.FileListingPreferences
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.FileSortOption
import dev.qtremors.arcile.core.storage.domain.FileViewMode
import dev.qtremors.arcile.core.storage.domain.FolderStats
import dev.qtremors.arcile.core.storage.domain.StorageVolume
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentList

@Immutable
internal data class BrowserLocationState(
    val currentPath: String = "",
    val currentVolumeId: String? = null,
    val isVolumeRootScreen: Boolean = false,
    val isCategoryScreen: Boolean = false,
    val activeCategoryName: String = "",
    val selectedFolderTabPath: String? = null,
    val storageVolumes: PersistentList<StorageVolume> = persistentListOf(),
    val archiveContext: BrowserArchiveContext? = null
)

@Immutable
internal data class BrowserListingState(
    val files: PersistentList<FileModel> = persistentListOf(),
    val folderStatsByPath: PersistentMap<String, FolderStats> = persistentMapOf(),
    val folderStatsLoadingPaths: PersistentSet<String> = persistentSetOf(),
    val isLoading: Boolean = true,
    val isPullToRefreshing: Boolean = false,
    val error: UiText? = null,
    val browserSortOption: FileSortOption = FileSortOption.NAME_ASC,
    val browserViewMode: FileViewMode = FileViewMode.LIST,
    val browserListZoom: Float = FileListingPreferences.DEFAULT_LIST_ZOOM,
    val browserGridMinCellSize: Float = FileListingPreferences.DEFAULT_GRID_MIN_CELL_SIZE,
    val browserShowThumbnails: Boolean = FileListingPreferences.DEFAULT_SHOW_THUMBNAILS,
    val browserScrollbarEnabled: Boolean = true,
    val showHiddenFiles: Boolean = true,
    val displayState: BrowserDisplayState = BrowserDisplayState()
)

@Immutable
internal data class BrowserNavigationState(
    val location: BrowserLocationState = BrowserLocationState(),
    val listing: BrowserListingState = BrowserListingState()
) {
    val currentPath get() = location.currentPath
    val currentVolumeId get() = location.currentVolumeId
    val isVolumeRootScreen get() = location.isVolumeRootScreen
    val isCategoryScreen get() = location.isCategoryScreen
    val activeCategoryName get() = location.activeCategoryName
    val selectedFolderTabPath get() = location.selectedFolderTabPath
    val storageVolumes get() = location.storageVolumes
    val archiveContext get() = location.archiveContext
    val files get() = listing.files
    val folderStatsByPath get() = listing.folderStatsByPath
    val folderStatsLoadingPaths get() = listing.folderStatsLoadingPaths
    val isLoading get() = listing.isLoading
    val isPullToRefreshing get() = listing.isPullToRefreshing
    val error get() = listing.error
    val browserSortOption get() = listing.browserSortOption
    val browserViewMode get() = listing.browserViewMode
    val browserListZoom get() = listing.browserListZoom
    val browserGridMinCellSize get() = listing.browserGridMinCellSize
    val browserShowThumbnails get() = listing.browserShowThumbnails
    val browserScrollbarEnabled get() = listing.browserScrollbarEnabled
    val showHiddenFiles get() = listing.showHiddenFiles
    val displayState get() = listing.displayState

    @Suppress("LongParameterList")
    fun withValues(
        currentPath: String = this.currentPath,
        currentVolumeId: String? = this.currentVolumeId,
        isVolumeRootScreen: Boolean = this.isVolumeRootScreen,
        isCategoryScreen: Boolean = this.isCategoryScreen,
        activeCategoryName: String = this.activeCategoryName,
        selectedFolderTabPath: String? = this.selectedFolderTabPath,
        storageVolumes: PersistentList<StorageVolume> = this.storageVolumes,
        archiveContext: BrowserArchiveContext? = this.archiveContext,
        files: PersistentList<FileModel> = this.files,
        folderStatsByPath: PersistentMap<String, FolderStats> = this.folderStatsByPath,
        folderStatsLoadingPaths: PersistentSet<String> = this.folderStatsLoadingPaths,
        isLoading: Boolean = this.isLoading,
        isPullToRefreshing: Boolean = this.isPullToRefreshing,
        error: UiText? = this.error,
        browserSortOption: FileSortOption = this.browserSortOption,
        browserViewMode: FileViewMode = this.browserViewMode,
        browserListZoom: Float = this.browserListZoom,
        browserGridMinCellSize: Float = this.browserGridMinCellSize,
        browserShowThumbnails: Boolean = this.browserShowThumbnails,
        browserScrollbarEnabled: Boolean = this.browserScrollbarEnabled,
        showHiddenFiles: Boolean = this.showHiddenFiles
    ): BrowserNavigationState = BrowserNavigationState(
        location = location.copy(
            currentPath = currentPath,
            currentVolumeId = currentVolumeId,
            isVolumeRootScreen = isVolumeRootScreen,
            isCategoryScreen = isCategoryScreen,
            activeCategoryName = activeCategoryName,
            selectedFolderTabPath = selectedFolderTabPath,
            storageVolumes = storageVolumes,
            archiveContext = archiveContext
        ),
        listing = listing.copy(
            files = files,
            folderStatsByPath = folderStatsByPath,
            folderStatsLoadingPaths = folderStatsLoadingPaths,
            isLoading = isLoading,
            isPullToRefreshing = isPullToRefreshing,
            error = error,
            browserSortOption = browserSortOption,
            browserViewMode = browserViewMode,
            browserListZoom = browserListZoom,
            browserGridMinCellSize = browserGridMinCellSize,
            browserShowThumbnails = browserShowThumbnails,
            browserScrollbarEnabled = browserScrollbarEnabled,
            showHiddenFiles = showHiddenFiles
        )
    ).withUpdatedDisplayState()
}

internal sealed interface BrowserNavigationEvent {
    data class StorageVolumesChanged(val volumes: List<StorageVolume>) : BrowserNavigationEvent
    data class OpenVolumeRoots(val volumes: List<FileModel>) : BrowserNavigationEvent
    data class OpenDirectory(val path: String, val volumeId: String?) : BrowserNavigationEvent
    data class OpenCategory(val categoryName: String, val volumeId: String?) : BrowserNavigationEvent
    data class SelectFolderTab(val path: String?) : BrowserNavigationEvent
}

internal fun BrowserNavigationState.reduce(event: BrowserNavigationEvent): BrowserNavigationState = when (event) {
    is BrowserNavigationEvent.StorageVolumesChanged -> withValues(
        storageVolumes = event.volumes.toPersistentList()
    )
    is BrowserNavigationEvent.OpenVolumeRoots -> withValues(
        archiveContext = null,
        currentPath = "",
        currentVolumeId = null,
        isVolumeRootScreen = true,
        isCategoryScreen = false,
        activeCategoryName = "",
        selectedFolderTabPath = null,
        files = event.volumes.toPersistentList(),
        folderStatsByPath = persistentMapOf(),
        folderStatsLoadingPaths = persistentSetOf(),
        isLoading = false,
        isPullToRefreshing = false
    )
    is BrowserNavigationEvent.OpenDirectory -> {
        val preserveListing = archiveContext == null &&
            !isVolumeRootScreen &&
            !isCategoryScreen &&
            currentPath == event.path &&
            currentVolumeId == event.volumeId
        withValues(
            archiveContext = null,
            currentPath = event.path,
            currentVolumeId = event.volumeId,
            isVolumeRootScreen = false,
            isCategoryScreen = false,
            activeCategoryName = "",
            selectedFolderTabPath = null,
            files = if (preserveListing) files else persistentListOf(),
            folderStatsByPath = if (preserveListing) folderStatsByPath else persistentMapOf(),
            folderStatsLoadingPaths = if (preserveListing) folderStatsLoadingPaths else persistentSetOf()
        )
    }
    is BrowserNavigationEvent.OpenCategory -> {
        val preserveListing = archiveContext == null &&
            isCategoryScreen &&
            activeCategoryName == event.categoryName &&
            currentVolumeId == event.volumeId
        withValues(
            archiveContext = null,
            currentPath = "",
            currentVolumeId = event.volumeId,
            isVolumeRootScreen = false,
            isCategoryScreen = true,
            activeCategoryName = event.categoryName,
            selectedFolderTabPath = null,
            files = if (preserveListing) files else persistentListOf(),
            folderStatsByPath = if (preserveListing) folderStatsByPath else persistentMapOf(),
            folderStatsLoadingPaths = if (preserveListing) folderStatsLoadingPaths else persistentSetOf()
        )
    }
    is BrowserNavigationEvent.SelectFolderTab -> withValues(selectedFolderTabPath = event.path)
}

internal fun BrowserNavigationState.withUpdatedDisplayState(): BrowserNavigationState = copy(
    listing = listing.copy(
        displayState = buildBrowserDisplayState(
            files = files,
            sortOption = browserSortOption,
            selectedFolderTabPath = selectedFolderTabPath,
            isCategoryScreen = isCategoryScreen,
            currentVolumeId = currentVolumeId,
            storageVolumes = storageVolumes,
            showHiddenFiles = showHiddenFiles,
            allFilesLabel = "All files",
            folderStatsByPath = folderStatsByPath,
            browserListZoom = browserListZoom,
            browserGridMinCellSize = browserGridMinCellSize,
            previousDisplayState = displayState
        )
    )
)
