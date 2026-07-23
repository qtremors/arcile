package dev.qtremors.arcile.feature.trash

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import dev.qtremors.arcile.core.ui.EmptyState
import dev.qtremors.arcile.core.ui.EmptyStateVariant
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.feature.trash.ui.TrashList

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun TrashBody(
    state: TrashState,
    showLoading: Boolean,
    showSearchBar: Boolean,
    bottomContentPadding: Dp,
    onToggleSelection: (String) -> Unit,
    onOpenFile: (dev.qtremors.arcile.core.storage.domain.FileModel) -> Unit,
    onRequestRestore: (String) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            showLoading && state.trashFiles.isEmpty() -> TrashLoading()
            state.trashFiles.isEmpty() && !state.isLoading && !showSearchBar -> EmptyState(
                variant = EmptyStateVariant.Trash,
                title = stringResource(R.string.trash_is_empty),
                description = stringResource(R.string.trash_empty_description),
                modifier = Modifier.fillMaxSize()
            )
            showSearchBar && state.isSearching -> TrashLoading()
            showSearchBar && state.searchQuery.isNotEmpty() && state.searchResults.isEmpty() -> {
                EmptyState(
                    variant = EmptyStateVariant.Search,
                    title = stringResource(R.string.no_results_found),
                    description = stringResource(R.string.no_results_description, state.searchQuery),
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> TrashList(
                files = if (showSearchBar) state.searchResults else state.visibleTrashFiles,
                selectedFiles = state.selectedFiles,
                onToggleSelection = onToggleSelection,
                onOpenFile = onOpenFile,
                onRequestRestore = onRequestRestore,
                contentPadding = PaddingValues(bottom = bottomContentPadding)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TrashLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        LoadingIndicator()
    }
}
