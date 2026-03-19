package dev.qtremors.arcile.presentation.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import kotlinx.coroutines.delay
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Surface
import androidx.compose.material3.FilledTonalButton
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import dev.qtremors.arcile.domain.FileModel
import dev.qtremors.arcile.domain.TrashMetadata
import dev.qtremors.arcile.domain.isIndexed
import androidx.compose.foundation.clickable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import dev.qtremors.arcile.presentation.trash.TrashState
import dev.qtremors.arcile.presentation.ui.components.EmptyState
import dev.qtremors.arcile.presentation.ui.components.trash.EmptyTrashDialog
import dev.qtremors.arcile.presentation.ui.components.trash.TrashList

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.qtremors.arcile.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TrashScreen(
    state: TrashState,
    onNavigateBack: () -> Unit,
    onToggleSelection: (String) -> Unit,
    onClearSelection: () -> Unit,
    onRestoreSelected: () -> Unit,
    onEmptyTrash: () -> Unit,
    onClearError: () -> Unit,
    onDismissDestinationPicker: () -> Unit,
    onRestoreToDestination: (List<String>, String) -> Unit,
    onClearNativeRequest: () -> Unit = {}
) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            when (state.pendingNativeAction) {
                dev.qtremors.arcile.presentation.trash.NativeAction.RESTORE -> onRestoreSelected()
                dev.qtremors.arcile.presentation.trash.NativeAction.RESTORE_TO_DESTINATION -> {
                    if (state.pendingDestinationPath != null && state.pendingRestoreIds.isNotEmpty()) {
                        onRestoreToDestination(state.pendingRestoreIds, state.pendingDestinationPath)
                    }
                }
                dev.qtremors.arcile.presentation.trash.NativeAction.EMPTY -> onEmptyTrash()
                null -> {}
            }
        }
        onClearNativeRequest()
    }

    var showLoading by remember { mutableStateOf(false) }
    LaunchedEffect(state.isLoading) {
        if (state.isLoading) {
            delay(5)
            showLoading = true
        } else {
            showLoading = false
        }
    }

    LaunchedEffect(state.nativeRequest) {
        state.nativeRequest?.let { sender ->
            launcher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }

    var showEmptyTrashConfirmation by remember { mutableStateOf(false) }

    BackHandler {
        if (state.selectedFiles.isNotEmpty()) {
            onClearSelection()
        } else {
            onNavigateBack()
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = if (state.selectedFiles.isNotEmpty()) stringResource(R.string.selected_count, state.selectedFiles.size) else stringResource(R.string.trash_bin),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    if (state.selectedFiles.isNotEmpty()) {
                        IconButton(onClick = onClearSelection) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear_selection))
                        }
                    } else {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    }
                },
                actions = {
                    if (state.selectedFiles.isNotEmpty()) {
                        IconButton(onClick = onRestoreSelected) {
                            Icon(Icons.Default.Restore, contentDescription = stringResource(R.string.restore_selected))
                        }
                    } else if (state.trashFiles.isNotEmpty()) {
                        IconButton(onClick = { showEmptyTrashConfirmation = true }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = stringResource(R.string.empty_trash))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (state.selectedFiles.isNotEmpty()) MaterialTheme.colorScheme.surfaceContainerHigh else androidx.compose.ui.graphics.Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (showLoading && state.trashFiles.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator()
                }
            } else if (state.trashFiles.isEmpty() && !state.isLoading) {
                 EmptyState(
                    icon = Icons.Default.DeleteSweep,
                    title = stringResource(R.string.trash_is_empty),
                    description = stringResource(R.string.trash_empty_description),
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                TrashList(
                    files = state.trashFiles,
                    selectedFiles = state.selectedFiles,
                    onToggleSelection = onToggleSelection
                )
            }
        }

        if (showEmptyTrashConfirmation) {
            EmptyTrashDialog(
                onDismissRequest = { showEmptyTrashConfirmation = false },
                onConfirm = {
                    showEmptyTrashConfirmation = false
                    onEmptyTrash()
                }
            )
        }

        if (state.showDestinationPicker) {
            val indexedVolumes = state.availableVolumes.filter { it.kind.isIndexed }
            if (indexedVolumes.isEmpty()) {
                AlertDialog(
                    onDismissRequest = onDismissDestinationPicker,
                    title = { Text(stringResource(R.string.no_restore_destination_title)) },
                    text = { Text(stringResource(R.string.no_restore_destination_description)) },
                    confirmButton = {
                        TextButton(onClick = onDismissDestinationPicker) {
                            Text(stringResource(R.string.dismiss))
                        }
                    }
                )
            } else {
                AlertDialog(
                    onDismissRequest = onDismissDestinationPicker,
                    title = { Text(stringResource(R.string.select_restore_destination_title)) },
                    text = {
                        Column {
                            Text(stringResource(R.string.select_restore_destination_description))
                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(8.dp))
                            LazyColumn {
                                items(indexedVolumes) { volume ->
                                    ListItem(
                                        headlineContent = { Text(volume.name) },
                                        supportingContent = { Text(volume.path, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        modifier = Modifier.clickable {
                                            onRestoreToDestination(state.selectedTrashIdsForDestination, volume.path)
                                            onDismissDestinationPicker()
                                        },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = onDismissDestinationPicker) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                )
            }
        }
    }
}

