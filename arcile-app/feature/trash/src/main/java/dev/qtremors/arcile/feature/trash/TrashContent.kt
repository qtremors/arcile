package dev.qtremors.arcile.feature.trash

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
    onToggleSelection: (String) -> Unit
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

@Composable
internal fun TrashInfoCard() {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.trash_info_title),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.trash_info_description),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
