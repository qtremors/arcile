package dev.qtremors.arcile.shared.ui
import dev.qtremors.arcile.R
import androidx.compose.ui.res.stringResource

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.draw.clip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import dev.qtremors.arcile.shared.ui.TopBarAction
import dev.qtremors.arcile.ui.theme.menuGroupFirst
import dev.qtremors.arcile.ui.theme.menuGroupLast
import dev.qtremors.arcile.ui.theme.menuGroupMiddle
import dev.qtremors.arcile.ui.theme.menuGroupSingle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription

import androidx.compose.ui.platform.testTag

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
    showNewFolderAction: Boolean = true,
    showPinAction: Boolean = false,
    showSettingsMenuAction: Boolean = false,
    showAboutAction: Boolean = false,
    isGridView: Boolean = false,
    selectedSize: String? = null,
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior? = null,
    onBackClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onClearSelection: () -> Unit,
    onSearchClick: () -> Unit,
    onSortClick: () -> Unit,
    onActionSelected: (TopBarAction) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    val containerColor = if (selectionCount > 0) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    androidx.compose.material3.LargeTopAppBar(
        scrollBehavior = scrollBehavior,
        title = {
            Column {
                Text(
                    text = if (selectionCount > 0) stringResource(R.string.selected_count, selectionCount) else title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (selectionCount > 0 && selectedSize != null) {
                    Text(
                        text = selectedSize,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        navigationIcon = {
            if (selectionCount > 0) {
                IconButton(onClick = onClearSelection) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear_selection))
                }
            } else if (showBackArrow) {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            }
        },
        actions = {
            if (selectionCount > 0) {
                IconButton(onClick = { onActionSelected(TopBarAction.SelectAll) }) {
                    Icon(
                        imageVector = Icons.Default.GridView,
                        contentDescription = stringResource(R.string.select_all)
                    )
                }
                IconButton(onClick = { onActionSelected(TopBarAction.InvertSelection) }) {
                    Icon(
                        imageVector = Icons.Default.SelectAll,
                        contentDescription = stringResource(R.string.invert_selection)
                    )
                }
            } else {
                Row(
                    modifier = androidx.compose.ui.Modifier.padding(end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val searchDesc = stringResource(R.string.action_search)
                    val sortDesc = stringResource(R.string.action_sort)
                    val topActions = remember(showSearchAction, showSortAction, searchDesc, sortDesc) {
                        mutableListOf<ToolbarAction>().apply {
                            if (showSearchAction) {
                                add(ToolbarAction(
                                    icon = Icons.Default.Search,
                                    contentDescription = searchDesc,
                                    onClick = onSearchClick
                                ))
                            }
                            if (showSortAction) {
                                add(ToolbarAction(
                                    icon = Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = sortDesc,
                                    onClick = onSortClick
                                ))
                            }
                        }
                    }

                    if (showSettingsIcon) {
                        Surface(
                            onClick = onSettingsClick,
                            shape = CircleShape,
                            color = Color.Transparent,
                            modifier = androidx.compose.ui.Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings_title))
                            }
                        }
                        Spacer(modifier = androidx.compose.ui.Modifier.width(4.dp))
                    }

                    if (topActions.isNotEmpty()) {
                        SplitButtonGroup(actions = topActions)
                        Spacer(modifier = androidx.compose.ui.Modifier.width(4.dp))
                    }

                    Box {
                        Surface(
                            onClick = { showMenu = true },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = androidx.compose.ui.Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.action_more_options)
                                )
                            }
                        }
                        DropdownMenu(
                            shape = MaterialTheme.shapes.extraLarge,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            offset = androidx.compose.ui.unit.DpOffset(0.dp, 4.dp),
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            val menuActions = remember(showNewFolderAction, showPinAction, showSettingsMenuAction, showAboutAction, showGridViewAction, isGridView) {
                                mutableListOf<@Composable () -> Unit>().apply {
                                    if (showNewFolderAction) {
                                        add {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.new_folder)) },
                                                leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
                                                onClick = {
                                                    showMenu = false
                                                    onActionSelected(TopBarAction.NewFolder)
                                                }
                                            )
                                        }
                                    }
                                    if (showPinAction) {
                                        add {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.pin_to_quick_access)) },
                                                leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null) },
                                                onClick = {
                                                    showMenu = false
                                                    onActionSelected(TopBarAction.PinToQuickAccess)
                                                }
                                            )
                                        }
                                    }
                                    if (showSettingsMenuAction && !showSettingsIcon) {
                                        add {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.settings_title)) },
                                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                                onClick = {
                                                    showMenu = false
                                                    onActionSelected(TopBarAction.Settings)
                                                }
                                            )
                                        }
                                    }
                                    if (showAboutAction) {
                                        add {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.about_title)) },
                                                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                                onClick = {
                                                    showMenu = false
                                                    onActionSelected(TopBarAction.About)
                                                }
                                            )
                                        }
                                    }
                                    if (showGridViewAction) {
                                        add {
                                            DropdownMenuItem(
                                                text = { Text(if (isGridView) stringResource(R.string.list_view) else stringResource(R.string.grid_view)) },
                                                leadingIcon = {
                                                    Icon(
                                                        if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                                                        contentDescription = null
                                                    )
                                                },
                                                onClick = {
                                                    showMenu = false
                                                    onActionSelected(TopBarAction.GridView)
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            menuActions.forEachIndexed { index, action ->
                                val shape = when {
                                    menuActions.size == 1 -> MaterialTheme.shapes.menuGroupSingle
                                    index == 0 -> MaterialTheme.shapes.menuGroupFirst
                                    index == menuActions.size - 1 -> MaterialTheme.shapes.menuGroupLast
                                    else -> MaterialTheme.shapes.menuGroupMiddle
                                }
                                Box(
                                    modifier = androidx.compose.ui.Modifier
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                        .clip(shape)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                ) {
                                    action()
                                }
                            }
                        }
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = containerColor,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}
