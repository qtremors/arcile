package dev.qtremors.arcile.feature.recentfiles.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.shared.presentation.containingFolderPath
import dev.qtremors.arcile.shared.ui.ArcileDropdownMenuItem
import dev.qtremors.arcile.shared.ui.FloatingSelectionToolbar
import dev.qtremors.arcile.shared.ui.ToolbarAction
import dev.qtremors.arcile.ui.theme.menuGroupFirst
import dev.qtremors.arcile.ui.theme.menuGroupLast
import dev.qtremors.arcile.ui.theme.menuGroupMiddle
import dev.qtremors.arcile.ui.theme.menuGroupSingle

@Composable
internal fun RecentSelectionTopBar(
    selectedCount: Int,
    selectedSize: String? = null,
    onClearSelection: () -> Unit
) {
    TopAppBar(
        title = {
            Column {
                Text(stringResource(R.string.selected_count, selectedCount))
                if (selectedSize != null) {
                    Text(
                        text = selectedSize,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(
                onClick = onClearSelection,
                modifier = Modifier.clip(CircleShape)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.clear_selection))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}

@Composable
internal fun RecentSelectionToolbar(
    isVisible: Boolean,
    selectedFiles: Set<String>,
    contentPadding: PaddingValues,
    onSelectAll: () -> Unit,
    onShareSelected: () -> Unit,
    onRequestDeleteSelected: () -> Unit,
    onOpenProperties: () -> Unit,
    onOpenContainingFolder: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentAlignment = Alignment.BottomCenter
    ) {
        val mainActions = listOf(
            ToolbarAction(
                icon = Icons.Default.SelectAll,
                contentDescription = stringResource(R.string.select_all),
                onClick = onSelectAll
            ),
            ToolbarAction(
                icon = Icons.Default.Share,
                contentDescription = stringResource(R.string.share),
                onClick = onShareSelected
            ),
            ToolbarAction(
                icon = Icons.Default.Delete,
                contentDescription = stringResource(R.string.delete),
                tint = MaterialTheme.colorScheme.error,
                onClick = onRequestDeleteSelected
            )
        )

        FloatingSelectionToolbar(
            isVisible = isVisible,
            actions = mainActions,
            moreContent = {
                var showMoreMenu by remember { mutableStateOf(false) }
                Box {
                    Surface(
                        onClick = { showMoreMenu = true },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.action_more_options),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    DropdownMenu(
                        shape = MaterialTheme.shapes.extraLarge,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false }
                    ) {
                        val menuActions = remember(onOpenProperties, selectedFiles) {
                            mutableListOf<@Composable () -> Unit>().apply {
                                if (selectedFiles.size == 1) {
                                    add {
                                        ArcileDropdownMenuItem(
                                            text = { Text(stringResource(R.string.open_containing_folder)) },
                                            leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                                            onClick = {
                                                showMoreMenu = false
                                                containingFolderPath(selectedFiles.first())?.let(onOpenContainingFolder)
                                            }
                                        )
                                    }
                                }
                                add {
                                    ArcileDropdownMenuItem(
                                        text = { Text(stringResource(R.string.properties_title)) },
                                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                        onClick = {
                                            showMoreMenu = false
                                            onOpenProperties()
                                        }
                                    )
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
                                modifier = Modifier
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
        )
    }
}
