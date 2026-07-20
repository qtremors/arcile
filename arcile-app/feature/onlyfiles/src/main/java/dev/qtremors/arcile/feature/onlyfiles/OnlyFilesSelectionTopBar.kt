package dev.qtremors.arcile.feature.onlyfiles

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SelectionTopBar(
    state: OnlyFilesUiState,
    viewModel: OnlyFilesViewModel,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onOpenWith: () -> Unit,
    onExport: () -> Unit,
    onMoveOut: () -> Unit
) {
    var more by remember { mutableStateOf(false) }
    LargeTopAppBar(
        title = {
            Text(pluralStringResource(
                R.plurals.onlyfiles_selected_count,
                state.selectedNodeIds.size,
                state.selectedNodeIds.size
            ))
        },
        navigationIcon = {
            IconButton(onClick = viewModel::clearSelection) {
                Icon(Icons.Default.Close, stringResource(R.string.onlyfiles_close))
            }
        },
        actions = {
            IconButton(onClick = viewModel::selectAll) {
                Icon(Icons.Default.SelectAll, stringResource(R.string.onlyfiles_select_all))
            }
            IconButton(onClick = { viewModel.copy(state.selectedNodes) }) {
                Icon(Icons.Default.ContentCopy, stringResource(R.string.onlyfiles_copy))
            }
            IconButton(onClick = { viewModel.move(state.selectedNodes) }) {
                Icon(Icons.Default.ContentCut, stringResource(R.string.onlyfiles_move))
            }
            IconButton(onClick = onShare) {
                Icon(Icons.Default.Share, stringResource(R.string.onlyfiles_share))
            }
            IconButton(onClick = { more = true }) {
                Icon(Icons.Default.MoreVert, stringResource(R.string.onlyfiles_more))
            }
            DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.onlyfiles_rename)) },
                    enabled = state.selectedNodes.size == 1,
                    onClick = { more = false; onRename() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.onlyfiles_open_with)) },
                    enabled = state.selectedNodes.singleOrNull()?.isDirectory == false,
                    onClick = { more = false; onOpenWith() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.onlyfiles_export)) },
                    onClick = { more = false; onExport() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.onlyfiles_move_out)) },
                    onClick = { more = false; onMoveOut() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.onlyfiles_invert_selection)) },
                    onClick = { more = false; viewModel.invertSelection() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.onlyfiles_properties)) },
                    leadingIcon = { Icon(Icons.Default.Info, null) },
                    onClick = { more = false; viewModel.showProperties(state.selectedNodes) }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.onlyfiles_delete)) },
                    leadingIcon = { Icon(Icons.Default.Delete, null) },
                    onClick = { more = false; onDelete() }
                )
            }
        },
        colors = TopAppBarDefaults.largeTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            scrolledContainerColor = MaterialTheme.colorScheme.primaryContainer
        )
    )
}
