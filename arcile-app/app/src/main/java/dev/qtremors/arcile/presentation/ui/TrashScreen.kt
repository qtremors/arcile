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
import androidx.compose.material3.Button
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
import dev.qtremors.arcile.ui.theme.ExpressiveSquircleShape
import dev.qtremors.arcile.ui.theme.ExpressiveCutShape
import dev.qtremors.arcile.domain.TrashMetadata
import dev.qtremors.arcile.presentation.FileManagerState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TrashScreen(
    state: FileManagerState,
    onNavigateBack: () -> Unit,
    onToggleSelection: (String) -> Unit,
    onClearSelection: () -> Unit,
    onRestoreSelected: () -> Unit,
    onEmptyTrash: () -> Unit,
    onClearError: () -> Unit
) {
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
                        text = if (state.selectedFiles.isNotEmpty()) "${state.selectedFiles.size} selected" else "Trash Bin",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    if (state.selectedFiles.isNotEmpty()) {
                        IconButton(onClick = onClearSelection) {
                            Icon(Icons.Default.Close, contentDescription = "Clear selection")
                        }
                    } else {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (state.selectedFiles.isNotEmpty()) {
                        IconButton(onClick = onRestoreSelected) {
                            Icon(Icons.Default.Restore, contentDescription = "Restore selected")
                        }
                    } else if (state.trashFiles.isNotEmpty()) {
                        IconButton(onClick = { showEmptyTrashConfirmation = true }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Empty Trash")
                        }
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
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
            if (state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator()
                }
            } else if (state.trashFiles.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(8.dp))
                        Text(
                            text = "Trash is empty",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                TrashList(
                    files = state.trashFiles,
                    selectedFiles = state.selectedFiles,
                    onToggleSelection = onToggleSelection
                )
            }
        }

        if (showEmptyTrashConfirmation) {
            AlertDialog(
                onDismissRequest = { showEmptyTrashConfirmation = false },
                icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                title = { Text("Empty Trash?") },
                shape = ExpressiveSquircleShape,
                text = { Text("All items in the trash will be permanently deleted. This action cannot be undone.") },
                confirmButton = {
                    FilledTonalButton(
                        onClick = {
                            showEmptyTrashConfirmation = false
                            onEmptyTrash()
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Text("Empty Trash")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEmptyTrashConfirmation = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        state.error?.let { errorMsg ->
            AlertDialog(
                onDismissRequest = onClearError,
                title = { Text("Error") },
                text = { Text(errorMsg) },
                confirmButton = {
                    TextButton(onClick = onClearError) {
                        Text("OK")
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrashList(
    files: List<TrashMetadata>,
    selectedFiles: Set<String>,
    onToggleSelection: (String) -> Unit
) {
    val formatter = remember { SimpleDateFormat("MMM dd, yyyy \u2022 HH:mm", Locale.getDefault()) }

    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        items(files, key = { it.id }) { trashItem ->
            val isSelected = selectedFiles.contains(trashItem.id)
            
            val animatedSurfaceColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                label = "trashListItemColor"
            )
            
            

            Surface(
                shape = if (isSelected) dev.qtremors.arcile.ui.theme.ExpressiveShapes.large else ExpressiveSquircleShape,
                color = animatedSurfaceColor,
                modifier = Modifier
                    .padding(horizontal = if (isSelected) 8.dp else 0.dp, vertical = if (isSelected) 4.dp else 0.dp)
                    .combinedClickable(
                        onClick = { onToggleSelection(trashItem.id) },
                        onLongClick = { onToggleSelection(trashItem.id) }
                    )
            ) {
                ListItem(
                    headlineContent = { Text(trashItem.fileModel.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        Column {
                            Text(
                                text = "Deleted: ${formatter.format(Date(trashItem.deletionTime))}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "From: ${trashItem.originalPath.substringBeforeLast("/")}/",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
    }
}
