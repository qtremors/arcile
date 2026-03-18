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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    onConfirmTrash: () -> Unit,
    onConfirmPermanentDelete: () -> Unit,
    onDismissDeleteConfirmation: () -> Unit,
    onShareSelected: () -> Unit,
    onRefresh: () -> Unit,
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
                dev.qtremors.arcile.presentation.recentfiles.RecentNativeAction.TRASH -> onConfirmTrash()
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

    // Intercept system back to clear selection before navigating away
    BackHandler(enabled = isSelectionMode) {
        onClearSelection()
    }

    LifecycleResumeEffect(Unit) {
        onRefresh()
        onPauseOrDispose { }
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
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.recent_files_title)) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                    scrollBehavior = scrollBehavior
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (state.isLoading && !state.isPullToRefreshing) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator()
                }
            } else if (state.recentFiles.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.History,
                    title = stringResource(R.string.no_recent_files),
                    description = stringResource(R.string.no_recent_files_description),
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                val groupedFiles = remember(state.recentFiles) {
                val groupFormat = SimpleDateFormat("EEEE, MMM dd", Locale.getDefault())
                val cal = Calendar.getInstance()
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val today = cal.timeInMillis
                
                cal.add(Calendar.DAY_OF_YEAR, -1)
                val yesterday = cal.timeInMillis

                state.recentFiles.groupBy { file ->
                    when {
                        file.lastModified >= today -> "Today"
                        file.lastModified >= yesterday -> "Yesterday"
                        else -> groupFormat.format(Date(file.lastModified))
                    }
                }
            }

                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    groupedFiles.forEach { (dateHeader, files) ->
                        @OptIn(ExperimentalFoundationApi::class)
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

    if (state.showTrashConfirmation) {
        AlertDialog(
            onDismissRequest = onDismissDeleteConfirmation,
            title = { Text(stringResource(R.string.delete_items_title, state.selectedFiles.size)) },
            text = { Text(stringResource(R.string.delete_items_description)) },
            confirmButton = {
                FilledTonalButton(
                    onClick = onConfirmTrash,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDeleteConfirmation) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (state.showPermanentDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = onDismissDeleteConfirmation,
            title = { Text(stringResource(R.string.delete_permanent_title, state.selectedFiles.size)) },
            text = { Text(stringResource(R.string.delete_permanent_description)) },
            confirmButton = {
                FilledTonalButton(
                    onClick = onConfirmPermanentDelete,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDeleteConfirmation) {
                    Text(stringResource(R.string.cancel))
                }
            }
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
