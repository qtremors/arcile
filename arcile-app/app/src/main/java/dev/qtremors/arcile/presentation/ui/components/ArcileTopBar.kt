package dev.qtremors.arcile.presentation.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ArcileTopBar(
    title: String,
    selectionCount: Int = 0,
    showBackArrow: Boolean = false,
    showSettingsIcon: Boolean = false,
    showSearchAction: Boolean = true,
    showSortAction: Boolean = true,
    showGridViewAction: Boolean = false,
    isGridView: Boolean = false,
    hasClipboardItems: Boolean = false,
    onBackClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onClearSelection: () -> Unit,
    onSearchClick: () -> Unit,
    onSortClick: () -> Unit,
    onPasteClick: () -> Unit = {},
    onCancelPaste: () -> Unit = {},
    onActionSelected: (TopBarAction) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Text(
                text = if (selectionCount > 0) "$selectionCount selected" else title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            if (selectionCount > 0) {
                IconButton(onClick = onClearSelection) {
                    Icon(Icons.Default.Close, contentDescription = "Clear selection")
                }
            } else if (showBackArrow) {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        },
        actions = {
            if (selectionCount == 0) {
                if (hasClipboardItems) {
                    // Floating Clipboard Command tray overlaying standard actions
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        tonalElevation = 4.dp,
                        modifier = androidx.compose.ui.Modifier.padding(end = 8.dp)
                    ) {
                        Row {
                            IconButton(onClick = onCancelPaste) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel transfer", tint = MaterialTheme.colorScheme.onTertiaryContainer)
                            }
                            IconButton(onClick = onPasteClick) {
                                Icon(Icons.Default.ContentPaste, contentDescription = "Paste here", tint = MaterialTheme.colorScheme.onTertiaryContainer)
                            }
                        }
                    }
                } else {
                    if (showSearchAction) {
                        IconButton(onClick = onSearchClick) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    }
                    if (showSortAction) {
                        IconButton(onClick = onSortClick) {
                            Icon(Icons.Default.SortByAlpha, contentDescription = "Sort")
                        }
                    }
                    if (showSettingsIcon) {
                        IconButton(onClick = onSettingsClick) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("New Folder") },
                        onClick = {
                            showMenu = false
                            onActionSelected(TopBarAction.NewFolder)
                        }
                    )
                    if (showGridViewAction) {
                        DropdownMenuItem(
                            text = { Text(if (isGridView) "List View" else "Grid View") },
                            onClick = {
                                showMenu = false
                                onActionSelected(TopBarAction.GridView)
                            }
                        )
                    }
                }
            } else {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    tonalElevation = 2.dp,
                    modifier = androidx.compose.ui.Modifier.padding(end = 8.dp)
                ) {
                    Row {
                        IconButton(onClick = { onActionSelected(TopBarAction.Share) }) {
                            Icon(Icons.Default.Share, contentDescription = "Share")
                        }
                        IconButton(onClick = { onActionSelected(TopBarAction.Copy) }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                        }
                        IconButton(onClick = { onActionSelected(TopBarAction.Cut) }) {
                            Icon(Icons.Default.ContentCut, contentDescription = "Cut")
                        }
                        if (selectionCount == 1) {
                            IconButton(onClick = { onActionSelected(TopBarAction.Rename) }) {
                                Icon(Icons.Default.Edit, contentDescription = "Rename")
                            }
                        }
                        IconButton(onClick = { onActionSelected(TopBarAction.DeleteSelected) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete selected")
                        }
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = if (selectionCount > 0) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = if (selectionCount > 0) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}
