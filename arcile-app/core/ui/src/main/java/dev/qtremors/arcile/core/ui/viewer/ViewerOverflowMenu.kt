package dev.qtremors.arcile.core.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.ui.ArcileDropdownMenuItem
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.rememberArcileHaptics
import dev.qtremors.arcile.core.ui.theme.bounceClickable
import dev.qtremors.arcile.core.ui.theme.menuGroupFirst
import dev.qtremors.arcile.core.ui.theme.menuGroupLast
import dev.qtremors.arcile.core.ui.theme.menuGroupMiddle
import dev.qtremors.arcile.core.ui.theme.menuGroupSingle

data class ViewerOverflowExtraAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

@Composable
fun ViewerOverflowMenu(
    currentFile: FileModel?,
    allowedActions: Set<ViewerFileAction>,
    onAction: (ViewerFileAction) -> Unit,
    onShowMetadata: (() -> Unit)? = null,
    extraActions: List<ViewerOverflowExtraAction> = emptyList(),
    modifier: Modifier = Modifier,
    extraItems: List<@Composable () -> Unit> = emptyList(),
    buttonShape: Shape = CircleShape,
    buttonColor: Color = Color.Black.copy(alpha = 0.5f),
    buttonContentColor: Color = Color.White
) {
    var expanded by remember { mutableStateOf(false) }
    val haptics = rememberArcileHaptics()

    Box(modifier = modifier) {
        Surface(
            onClick = {
                haptics.selectionStart()
                expanded = true
            },
            shape = buttonShape,
            color = buttonColor,
            modifier = Modifier
                .size(48.dp)
                .bounceClickable(
                    onClick = {
                        haptics.selectionStart()
                        expanded = true
                    }
                )
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.action_more_options),
                    tint = buttonContentColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        val menuItems = buildList<@Composable () -> Unit> {
            extraActions.forEach { action ->
                add {
                    ArcileDropdownMenuItem(
                        text = action.label,
                        leadingIcon = { Icon(action.icon, contentDescription = null) },
                        onClick = {
                            expanded = false
                            action.onClick()
                        }
                    )
                }
            }
            extraItems.forEach { add(it) }

            if (onShowMetadata != null) {
                add {
                    ArcileDropdownMenuItem(
                        text = stringResource(R.string.action_info),
                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                        onClick = {
                            expanded = false
                            onShowMetadata()
                        }
                    )
                }
            }

            if (ViewerFileAction.Rename in allowedActions) {
                add {
                    ArcileDropdownMenuItem(
                        text = stringResource(R.string.action_rename),
                        leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null) },
                        onClick = {
                            expanded = false
                            onAction(ViewerFileAction.Rename)
                        }
                    )
                }
            }
            if (ViewerFileAction.Copy in allowedActions) {
                add {
                    ArcileDropdownMenuItem(
                        text = stringResource(R.string.action_copy),
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                        onClick = {
                            expanded = false
                            onAction(ViewerFileAction.Copy)
                        }
                    )
                }
            }
            if (ViewerFileAction.Cut in allowedActions) {
                add {
                    ArcileDropdownMenuItem(
                        text = stringResource(R.string.action_cut),
                        leadingIcon = { Icon(Icons.Default.ContentCut, contentDescription = null) },
                        onClick = {
                            expanded = false
                            onAction(ViewerFileAction.Cut)
                        }
                    )
                }
            }
            if (ViewerFileAction.Share in allowedActions) {
                add {
                    ArcileDropdownMenuItem(
                        text = stringResource(R.string.action_share),
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                        onClick = {
                            expanded = false
                            onAction(ViewerFileAction.Share)
                        }
                    )
                }
            }
            if (ViewerFileAction.OpenWith in allowedActions) {
                add {
                    ArcileDropdownMenuItem(
                        text = stringResource(R.string.action_open_with),
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
                        onClick = {
                            expanded = false
                            onAction(ViewerFileAction.OpenWith)
                        }
                    )
                }
            }
            if (ViewerFileAction.CreateArchive in allowedActions) {
                add {
                    ArcileDropdownMenuItem(
                        text = stringResource(R.string.action_create_archive),
                        leadingIcon = { Icon(Icons.Default.FolderZip, contentDescription = null) },
                        onClick = {
                            expanded = false
                            onAction(ViewerFileAction.CreateArchive)
                        }
                    )
                }
            }
            if (ViewerFileAction.Properties in allowedActions && onShowMetadata == null) {
                add {
                    ArcileDropdownMenuItem(
                        text = stringResource(R.string.action_properties),
                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                        onClick = {
                            expanded = false
                            onAction(ViewerFileAction.Properties)
                        }
                    )
                }
            }
            if (ViewerFileAction.Delete in allowedActions) {
                add {
                    ArcileDropdownMenuItem(
                        text = stringResource(R.string.action_delete),
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        onClick = {
                            expanded = false
                            onAction(ViewerFileAction.Delete)
                        }
                    )
                }
            }
        }

        if (menuItems.isNotEmpty()) {
            dev.qtremors.arcile.core.ui.ArcileDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                items = menuItems
            )
        }
    }
}
