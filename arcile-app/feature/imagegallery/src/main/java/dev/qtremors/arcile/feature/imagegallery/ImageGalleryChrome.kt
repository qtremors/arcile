package dev.qtremors.arcile.feature.imagegallery

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.qtremors.arcile.core.storage.domain.CategoryLibraryPage
import dev.qtremors.arcile.core.storage.domain.SearchFilters
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.category.CategoryFloatingTopBar
import dev.qtremors.arcile.core.ui.category.CategoryMenuAction

@Composable
internal fun FloatingGalleryTopBar(
    state: ImageGalleryState,
    showSearchBar: Boolean,
    currentTab: CategoryLibraryPage,
    onSearchClick: () -> Unit,
    onSortClick: () -> Unit,
    onNavigateBack: () -> Unit,
    onClearSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchFiltersChange: (SearchFilters) -> Unit,
    onShowFileDetailsChange: (Boolean) -> Unit,
    onDefaultPageChange: (CategoryLibraryPage) -> Unit,
    onSelectAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val menuActions = buildList {
        CategoryLibraryPage.entries.forEach { tab ->
            add(
                CategoryMenuAction(
                    label = stringResource(
                        when (tab) {
                            CategoryLibraryPage.ITEMS -> when {
                                state.isVideoGallery ->
                                    R.string.video_gallery_open_to_videos
                                else ->
                                    R.string.image_gallery_open_to_photos
                            }
                            CategoryLibraryPage.FOLDERS ->
                                R.string.image_gallery_open_to_albums
                        }
                    ),
                    icon = when (tab) {
                        CategoryLibraryPage.ITEMS -> when {
                            state.isVideoGallery -> Icons.Default.VideoLibrary
                            else -> Icons.Default.Image
                        }
                        CategoryLibraryPage.FOLDERS -> Icons.Default.Folder
                    },
                    selected = state.defaultPage == tab,
                    onClick = { onDefaultPageChange(tab) }
                )
            )
        }
        if (state.displayedFiles.isNotEmpty()) {
            add(
                CategoryMenuAction(
                    label = stringResource(R.string.select_all),
                    icon = Icons.Default.SelectAll,
                    onClick = onSelectAll
                )
            )
        }
    }
    CategoryFloatingTopBar(
        query = state.searchQuery,
        searchPlaceholder = stringResource(
            if (state.isVideoGallery) {
                R.string.video_gallery_search_placeholder
            } else {
                R.string.image_gallery_search_placeholder
            }
        ),
        showSearchBar = showSearchBar,
        menuActions = menuActions,
        onSearchClick = onSearchClick,
        onCloseSearch = onClearSearch,
        onQueryChange = onSearchQueryChange,
        onViewSort = onSortClick,
        onNavigateBack = onNavigateBack,
        searchFilters = state.searchFilters,
        onSearchFiltersChange = onSearchFiltersChange,
        showCategoryFilter = false,
        modifier = modifier
    )
}
