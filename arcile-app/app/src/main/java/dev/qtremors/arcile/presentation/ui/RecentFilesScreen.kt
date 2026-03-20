package dev.qtremors.arcile.presentation.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import dev.qtremors.arcile.presentation.ui.components.dialogs.DeleteConfirmationDialog
import dev.qtremors.arcile.presentation.ui.components.SearchTopBar
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff

import androidx.compose.material3.LoadingIndicator



import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.qtremors.arcile.presentation.ui.components.ArcileTopBar
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import dev.qtremors.arcile.presentation.recentfiles.RecentFilesState
import dev.qtremors.arcile.presentation.ui.components.lists.FileItemRow
import dev.qtremors.arcile.presentation.ui.components.EmptyState
import androidx.compose.ui.res.stringResource
import dev.qtremors.arcile.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RecentFilesScreen(
    state: RecentFilesState,
    onNavigateBack: () -> Unit,
    onOpenFile: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
    onClearSelection: () -> Unit,
    onRequestDeleteSelected: () -> Unit,
    onConfirmDelete: () -> Unit,
    onTogglePermanentDelete: () -> Unit,
    onDismissDeleteConfirmation: () -> Unit,
    onShareSelected: () -> Unit,
    onRefresh: () -> Unit,
    onSearchQueryChange: (String) -> Unit = {},
    onClearSearch: () -> Unit = {},
    onClearNativeRequest: () -> Unit = {}
) {

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val isSelectionMode = state.selectedFiles.isNotEmpty()
    val formatter = remember { SimpleDateFormat("MMM dd, yyyy  h:mm a", Locale.getDefault()) }

    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            when (state.pendingNativeAction) {
                dev.qtremors.arcile.presentation.recentfiles.RecentNativeAction.TRASH -> onConfirmDelete()
                null -> {}
            }
        }
        onClearNativeRequest()
    }

    androidx.compose.runtime.LaunchedEffect(state.nativeRequest) {
        state.nativeRequest?.let { sender ->
            launcher.launch(androidx.activity.result.IntentSenderRequest.Builder(sender).build())
        }
    }

    var showSearchBar by rememberSaveable { mutableStateOf(state.searchQuery.isNotEmpty()) }

    // Intercept system back to clear selection before navigating away
    BackHandler(enabled = isSelectionMode || showSearchBar) {
        if (isSelectionMode) {
            onClearSelection()
        } else {
            showSearchBar = false
            onClearSearch()
        }
    }




    Scaffold(
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = { Text(stringResource(R.string.selected_count, state.selectedFiles.size)) },
                    navigationIcon = {
                        IconButton(onClick = onClearSelection) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.clear_selection))
                        }
                    },
                    actions = {
                        IconButton(onClick = onShareSelected) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.share))
                        }
                        IconButton(onClick = onRequestDeleteSelected) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            } else if (showSearchBar) {
                SearchTopBar(
                    query = state.searchQuery,
                    onQueryChange = onSearchQueryChange,
                    onClose = {
                        showSearchBar = false
                        onClearSearch()
                    },
                    placeholder = "Search recent files..."
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.recent_files_title)) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSearchBar = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }

                    },
                    scrollBehavior = scrollBehavior
                )
            }

        }
    ) { padding ->
        var showLoading by remember { mutableStateOf(false) }
        LaunchedEffect(state.isLoading) {
            if (state.isLoading) {
                delay(5)
                showLoading = true
            } else {
                showLoading = false
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (showLoading && state.recentFiles.isEmpty() && !state.isPullToRefreshing) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator()
                }
            } else if (state.recentFiles.isEmpty() && !state.isLoading && !showSearchBar) {
                EmptyState(
                    icon = Icons.Default.History,
                    title = stringResource(R.string.no_recent_files),
                    description = stringResource(R.string.no_recent_files_description),
                    modifier = Modifier.fillMaxSize()
                )
            } else if (showSearchBar && state.isSearching) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator()
                }
            } else if (showSearchBar && state.searchQuery.isNotEmpty() && state.searchResults.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.SearchOff,
                    title = stringResource(R.string.no_results_found),
                    description = stringResource(R.string.no_results_description, state.searchQuery),
                    modifier = Modifier.fillMaxSize()
                )

            } else {
                val filesToDisplay = if (showSearchBar) state.searchResults else state.recentFiles
                val groupedFiles = remember(filesToDisplay, showSearchBar) {
                if (showSearchBar) {
                    // Don't group search results by date, just show flat
                    mapOf("" to filesToDisplay)
                } else {
                    val groupFormat = SimpleDateFormat("EEEE, MMM dd", Locale.getDefault())
                    val cal = Calendar.getInstance()
                    cal.set(Calendar.HOUR_OF_DAY, 0)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    val today = cal.timeInMillis
                    
                    cal.add(Calendar.DAY_OF_YEAR, -1)
                    val yesterday = cal.timeInMillis

                    filesToDisplay.groupBy { file ->
                        when {
                            file.lastModified >= today -> "Today"
                            file.lastModified >= yesterday -> "Yesterday"
                            else -> groupFormat.format(Date(file.lastModified))
                        }
                    }

                }}



                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    groupedFiles.forEach { (dateHeader, files) ->
                        @OptIn(ExperimentalFoundationApi::class)
                        if (dateHeader.isNotEmpty()) {
                            stickyHeader {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.95f))
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = dateHeader,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        items(files, key = { it.absolutePath }) { file ->
                            FileItemRow(
                                file = file,
                                formattedDate = formatter.format(Date(file.lastModified)),
                                isSelected = state.selectedFiles.contains(file.absolutePath),
                                onClick = {
                                    if (isSelectionMode) onToggleSelection(file.absolutePath)
                                    else onOpenFile(file.absolutePath)
                                },
                                onLongClick = {
                                    onToggleSelection(file.absolutePath)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (state.showTrashConfirmation || state.showPermanentDeleteConfirmation) {
        DeleteConfirmationDialog(
            selectedCount = state.selectedFiles.size,
            isPermanentDeleteChecked = state.isPermanentDeleteChecked,
            isPermanentDeleteToggleEnabled = state.isPermanentDeleteToggleEnabled,
            onConfirm = onConfirmDelete,
            onDismiss = onDismissDeleteConfirmation,
            onTogglePermanentDelete = onTogglePermanentDelete
        )
    }

    if (state.showMixedDeleteExplanation) {
        AlertDialog(
            onDismissRequest = onDismissDeleteConfirmation,
            title = { Text(stringResource(R.string.mixed_selection_title)) },
            text = { Text(stringResource(R.string.mixed_selection_description)) },
            confirmButton = {
                TextButton(onClick = onDismissDeleteConfirmation) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }
}
