package dev.qtremors.arcile.feature.gallery

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.operation.BulkFileOperationType
import dev.qtremors.arcile.core.operation.OperationCompletionStatus
import dev.qtremors.arcile.core.presentation.OperationUiState
import dev.qtremors.arcile.core.storage.domain.ClipboardOperation
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.SplitButtonGroup
import dev.qtremors.arcile.core.ui.ToolbarAction
import dev.qtremors.arcile.core.presentation.formatFileSize
private const val PROGRESS_PILL_WIDTH_DP = 192

@Composable
internal fun GalleryClipboardOperationToolbar(
    state: MediaGalleryState,
    pasteDestinationPath: String?,
    onPasteToFolder: (String) -> Unit,
    onCancelClipboard: () -> Unit,
    onShowClipboardContents: () -> Unit
) {
    val clipboard = state.clipboardState
    val activeOperation = state.activeFileOperation

    val toolbarActions = when {
        activeOperation != null && activeOperation.terminalStatus == null -> listOf(
            ToolbarAction(
                icon = Icons.Default.Close,
                contentDescription = stringResource(R.string.action_cancel_transfer),
                containerColor = MaterialTheme.colorScheme.error,
                tint = MaterialTheme.colorScheme.onError,
                onClick = onCancelClipboard
            )
        )
        activeOperation == null && clipboard != null -> buildList {
            if (pasteDestinationPath != null) {
                add(
                    ToolbarAction(
                        icon = Icons.Default.ContentPaste,
                        contentDescription = stringResource(R.string.action_paste_here),
                        onClick = { onPasteToFolder(pasteDestinationPath) }
                    )
                )
            }
            add(
                ToolbarAction(
                    icon = Icons.Default.Close,
                    contentDescription = stringResource(R.string.action_cancel_transfer),
                    containerColor = MaterialTheme.colorScheme.error,
                    tint = MaterialTheme.colorScheme.onError,
                    onClick = onCancelClipboard
                )
            )
        }
        else -> emptyList()
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        GalleryClipboardProgressPill(
            clipboardOperation = clipboard?.operation,
            clipboardItemCount = clipboard?.files?.size ?: 0,
            clipboardTotalSize = clipboard?.totalSize ?: 0L,
            activeOperation = activeOperation,
            onClick = {
                if (activeOperation == null && clipboard != null) {
                    onShowClipboardContents()
                }
            }
        )
        SplitButtonGroup(
            actions = toolbarActions,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            height = 48.dp,
            minWidth = 48.dp,
            iconSize = 24.dp
        )
    }
}

@Composable
private fun GalleryClipboardProgressPill(
    clipboardOperation: ClipboardOperation?,
    clipboardItemCount: Int,
    clipboardTotalSize: Long,
    activeOperation: OperationUiState?,
    onClick: () -> Unit
) {
    val rawProgress = activeOperation?.let { operation ->
        val byteProgress = operation.totalBytes
            ?.takeIf { it > 0L }
            ?.let { total ->
                ((operation.bytesProcessed ?: 0L).toFloat() / total.toFloat()).coerceIn(0f, 1f)
            }
        val itemProgress = operation.totalItems
            .takeIf { it > 0 }
            ?.let { total -> operation.completedItems.toFloat() / total.toFloat() }
            ?.coerceIn(0f, 1f)
        byteProgress ?: itemProgress
    } ?: 0f
    val displayedProgress = if (activeOperation?.terminalStatus != null) 1f else rawProgress
    val progressFillColor = when (activeOperation?.terminalStatus) {
        OperationCompletionStatus.SUCCESS -> Color(0xFF4CAF50).copy(alpha = 0.25f)
        OperationCompletionStatus.FAILED,
        OperationCompletionStatus.CANCELLED -> MaterialTheme.colorScheme.error.copy(alpha = 0.25f)
        null -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
    }

    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 4.dp,
        shadowElevation = 2.dp,
        modifier = Modifier
            .height(56.dp)
            .padding(end = 8.dp)
            .width(PROGRESS_PILL_WIDTH_DP.dp)
            .animateContentSize()
    ) {
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (activeOperation != null) {
                        Modifier.drawBehind {
                            drawRect(
                                color = progressFillColor,
                                size = Size(size.width * displayedProgress, size.height)
                            )
                        }
                    } else {
                        Modifier
                    }
                )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val icon = when {
                    activeOperation?.type == BulkFileOperationType.MOVE ||
                        clipboardOperation == ClipboardOperation.CUT -> Icons.Default.ContentCut
                    activeOperation?.type == BulkFileOperationType.CREATE_ARCHIVE -> Icons.Default.FolderZip
                    else -> Icons.Default.ContentCopy
                }
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Column(
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    val itemCount = activeOperation?.totalItems ?: clipboardItemCount
                    Text(
                        text = pluralStringResource(
                            if (activeOperation == null) {
                                R.plurals.clipboard_items_ready_to_paste
                            } else {
                                R.plurals.clipboard_item_count
                            },
                            itemCount,
                            itemCount
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val subtitle = if (activeOperation != null) {
                        activeOperation.totalBytes
                            ?.takeIf { it > 0L }
                            ?.let { total ->
                                formatFileSize(
                                    context,
                                    (total - (activeOperation.bytesProcessed ?: 0L)).coerceAtLeast(0L)
                                )
                            }
                            ?: androidx.compose.ui.res.pluralStringResource(
                                R.plurals.transfer_progress_items,
                                activeOperation.totalItems,
                                activeOperation.completedItems,
                                activeOperation.totalItems
                            )
                    } else {
                        formatFileSize(context, clipboardTotalSize)
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
