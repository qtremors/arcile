package dev.qtremors.arcile.presentation.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.style.TextOverflow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArcileTopBar(
    title: String,
    selectionCount: Int = 0,
    showBackArrow: Boolean = false,
    showSettingsIcon: Boolean = false,
    onBackClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onClearSelection: () -> Unit,
    onSearchClick: () -> Unit,
    onSortClick: () -> Unit,
    onActionSelected: (String) -> Unit
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
                IconButton(onClick = onSearchClick) {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                }
                IconButton(onClick = onSortClick) {
                    Icon(Icons.Default.SortByAlpha, contentDescription = "Sort")
                }
                if (showSettingsIcon) {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More Options")
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("New Folder") },
                        onClick = {
                            showMenu = false
                            onActionSelected("New Folder")
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Grid View") },
                        onClick = {
                            showMenu = false
                            onActionSelected("Grid View")
                        }
                    )
                }
            } else {
                // actions when items are selected
                if (selectionCount == 1) {
                    IconButton(onClick = { onActionSelected("Rename") }) {
                        Icon(Icons.Default.Edit, contentDescription = "Rename")
                    }
                }
                IconButton(onClick = { onActionSelected("Delete Selected") }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete selected")
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = if (selectionCount > 0) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = if (selectionCount > 0) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}
