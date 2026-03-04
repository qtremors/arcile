package dev.qtremors.arcile.presentation.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.domain.FileModel
import dev.qtremors.arcile.presentation.FileManagerState
import dev.qtremors.arcile.presentation.ui.components.ArcileTopBar
import dev.qtremors.arcile.presentation.ui.components.Breadcrumbs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerScreen(
    state: FileManagerState,
    onMenuClick: () -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateTo: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
    onClearSelection: () -> Unit,
    onCreateFolder: (String) -> Unit,
    onDeleteSelected: () -> Unit,
    onClearError: () -> Unit
) {
    var showCreateFolderDialog by remember { mutableStateOf(false) }

    BackHandler {
        onNavigateBack()
    }

    Scaffold(
        topBar = {
            ArcileTopBar(
                title = "Storage",
                selectionCount = state.selectedFiles.size,
                onMenuClick = onMenuClick,
                onClearSelection = onClearSelection,
                onSearchClick = {},
                onSortClick = {},
                onActionSelected = { action ->
                    when (action) {
                        "New Folder" -> showCreateFolderDialog = true
                        "Delete Selected" -> onDeleteSelected()
                    }
                }
            )
        },
        floatingActionButton = {
            if (state.selectedFiles.isEmpty()) {
                FloatingActionButton(onClick = { showCreateFolderDialog = true }) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = "Create Folder")
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Breadcrumbs(
                    path = state.currentPath,
                    onPathSegmentClick = {
                        // TODO: Implement direct jump to breadcrumb path
                    }
                )

                if (state.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (state.files.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Empty Directory",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.files, key = { it.absolutePath }) { file ->
                            FileItemRow(
                                file = file,
                                isSelected = state.selectedFiles.contains(file.absolutePath),
                                onClick = {
                                    if (state.selectedFiles.isNotEmpty()) {
                                        onToggleSelection(file.absolutePath)
                                    } else if (file.isDirectory) {
                                        onNavigateTo(file.absolutePath)
                                    }
                                },
                                onLongClick = { onToggleSelection(file.absolutePath) }
                            )
                        }
                    }
                }
            }
        }

        if (showCreateFolderDialog) {
            CreateFolderDialog(
                onDismiss = { showCreateFolderDialog = false },
                onConfirm = { name ->
                    onCreateFolder(name)
                    showCreateFolderDialog = false
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
fun FileItemRow(
    file: FileModel,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val formatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    
    ListItem(
        modifier = Modifier
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface),
        leadingContent = {
            Icon(
                imageVector = if (file.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                contentDescription = null,
                tint = if (file.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp)
            )
        },
        headlineContent = { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(formatter.format(Date(file.lastModified)))
                if (!file.isDirectory) {
                    Text(formatFileSize(file.size))
                }
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent
        )
    )
}

fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(Locale.US, "%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

@Composable
fun CreateFolderDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var folderName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Folder") },
        text = {
            OutlinedTextField(
                value = folderName,
                onValueChange = { folderName = it },
                label = { Text("Folder Name") },
                singleLine = true
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(folderName) },
                enabled = folderName.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
