package dev.qtremors.arcile.feature.home

import androidx.compose.runtime.Immutable
import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.core.storage.domain.CategoryStorage
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.FileSortOption
import dev.qtremors.arcile.core.storage.domain.QuickAccessItem
import dev.qtremors.arcile.core.storage.domain.SearchFilters
import dev.qtremors.arcile.core.storage.domain.StorageInfo
import dev.qtremors.arcile.core.storage.domain.StorageVolume
import dev.qtremors.arcile.core.storage.domain.TrashStorageUsage
import dev.qtremors.arcile.core.storage.domain.isIndexed
import dev.qtremors.arcile.core.presentation.filterAndSortFiles
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentList

@Immutable
internal data class HomeDisplayState(
    val todayRecentFiles: PersistentList<FileModel> = persistentListOf(),
    val indexedDashboardVolumes: PersistentList<StorageVolume> = persistentListOf(),
    val sortedCategoryStorages: PersistentList<CategoryStorage> = persistentListOf()
)

@Immutable
internal data class HomeState(
    val allStorageVolumes: PersistentList<StorageVolume> = persistentListOf(),
    val quickAccessItems: PersistentList<QuickAccessItem> = persistentListOf(),
    val storageInfo: StorageInfo? = null,
    val categoryStorages: PersistentList<CategoryStorage> = persistentListOf(),
    val categoryStoragesByVolume: PersistentMap<String, PersistentList<CategoryStorage>> = persistentMapOf(),
    val trashStorageUsage: TrashStorageUsage = TrashStorageUsage(0L, emptyMap()),
    val recentFiles: PersistentList<FileModel> = persistentListOf(),
    val searchResults: PersistentList<FileModel> = persistentListOf(),
    val homeSearchQuery: String = "",
    val homeSortOption: FileSortOption = FileSortOption.DATE_NEWEST,
    val activeSearchFilters: SearchFilters = SearchFilters(),
    val isSearching: Boolean = false,
    val isSearchFilterMenuVisible: Boolean = false,
    val isLoading: Boolean = true,
    val isPullToRefreshing: Boolean = false,
    val isCalculatingStorage: Boolean = false,
    val error: UiText? = null,
    val unclassifiedVolumes: PersistentList<StorageVolume> = persistentListOf(),
    val showClassificationPrompt: Boolean = false,
    val todayStart: Long = 0L,
    val displayState: HomeDisplayState = HomeDisplayState(),
    val homeUtilityIds: PersistentSet<String> = persistentSetOf("trash", "cleaner")
)

internal fun HomeState.withUpdatedDisplayState(): HomeState {
    val todayFiles = recentFiles.filter { it.lastModified >= todayStart }
    return copy(
        displayState = HomeDisplayState(
            todayRecentFiles = filterAndSortFiles(
                todayFiles,
                homeSearchQuery,
                homeSortOption
            ).toPersistentList(),
            indexedDashboardVolumes = storageInfo
                ?.volumes
                ?.filter { it.kind.isIndexed }
                .orEmpty()
                .toPersistentList(),
            sortedCategoryStorages = categoryStorages
                .sortedByDescending { it.sizeBytes }
                .toPersistentList()
        )
    )
}

internal enum class HomeRefreshMode {
    INITIAL,
    MANUAL,
    SILENT
}
