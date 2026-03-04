package dev.qtremors.arcile.presentation.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.domain.FileModel
import dev.qtremors.arcile.presentation.FileManagerState
import dev.qtremors.arcile.presentation.ui.components.ArcileTopBar
import dev.qtremors.arcile.presentation.ui.components.Breadcrumbs
import dev.qtremors.arcile.presentation.ui.components.TopBarAction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerScreen(
    state: FileManagerState,
    storageRootPath: String,
    onNavigateBack: () -> Unit,
    onNavigateTo: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
    onClearSelection: () -> Unit,
    onCreateFolder: (String) -> Unit,
    onDeleteSelected: () -> Unit,
    onRenameFile: (String, String) -> Unit,
    onClearError: () -> Unit
) {
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    BackHandler {
        onNavigateBack()
    }

    Scaffold(
        topBar = {
            ArcileTopBar(
                title = "Browse",
                selectionCount = state.selectedFiles.size,
                showBackArrow = true,
                onBackClick = onNavigateBack,
                onClearSelection = onClearSelection,
                onSearchClick = {},
                onSortClick = {},
                onActionSelected = { action ->
                    when (action) {
                        TopBarAction.NewFolder -> showCreateFolderDialog = true
                        TopBarAction.DeleteSelected -> showDeleteConfirmation = true
                        TopBarAction.Rename -> if (state.selectedFiles.size == 1) showRenameDialog = true
                        TopBarAction.GridView -> { /* TODO: implement grid view */ }
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
                    storageRootPath = storageRootPath,
                    onPathSegmentClick = { path ->
                        onNavigateTo(path)
                    }
                )

                if (state.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (state.files.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.FolderOff,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Empty Directory",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        val formatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                        items(state.files, key = { it.absolutePath }) { file ->
                            FileItemRow(
                                modifier = Modifier.animateItem(),
                                file = file,
                                formattedDate = formatter.format(Date(file.lastModified)),
                                isSelected = state.selectedFiles.contains(file.absolutePath),
                                onClick = {
                                    if (state.selectedFiles.isNotEmpty()) {
                                        onToggleSelection(file.absolutePath)
                                    } else if (file.isDirectory) {
                                        onNavigateTo(file.absolutePath)
                                    } else {
                                        onOpenFile(file.absolutePath)
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

        if (showDeleteConfirmation) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmation = false },
                title = { Text("Delete ${state.selectedFiles.size} item(s)?") },
                text = { Text("This action cannot be undone. Directories will be deleted recursively.") },
                confirmButton = {
                    Button(
                        onClick = {
                            showDeleteConfirmation = false
                            onDeleteSelected()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmation = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showRenameDialog && state.selectedFiles.size == 1) {
            val selectedPath = state.selectedFiles.first()
            val currentName = selectedPath.substringAfterLast('/')
            RenameDialog(
                currentName = currentName,
                onDismiss = { showRenameDialog = false },
                onConfirm = { newName ->
                    onRenameFile(selectedPath, newName)
                    showRenameDialog = false
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
    formattedDate: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {

    ListItem(
        modifier = modifier
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
        leadingContent = {
            Icon(
                imageVector = if (file.isDirectory) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                contentDescription = if (file.isDirectory) "Folder" else "File",
                tint = if (file.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp)
            )
        },
        headlineContent = { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(formattedDate)
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
        .coerceAtMost(units.size - 1)
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

@Composable
fun RenameDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var newName by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename") },
        text = {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("New Name") },
                singleLine = true
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(newName) },
                enabled = newName.isNotBlank() && newName != currentName
            ) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

