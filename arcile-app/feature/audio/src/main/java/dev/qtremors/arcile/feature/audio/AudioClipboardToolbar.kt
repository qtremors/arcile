package dev.qtremors.arcile.feature.audio

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.operation.BulkFileOperationType
import dev.qtremors.arcile.core.operation.OperationCompletionStatus
import dev.qtremors.arcile.core.presentation.formatFileSize
import dev.qtremors.arcile.core.storage.domain.ClipboardOperation
import dev.qtremors.arcile.core.ui.SplitButtonGroup
import dev.qtremors.arcile.core.ui.ToolbarAction
import kotlinx.coroutines.delay

@Composable
internal fun AudioClipboardToolbar(
    state: AudioLibraryState,
    canPaste: Boolean,
    onPaste: () -> Unit,
    onCancel: () -> Unit,
    onShowContents: () -> Unit,
    onClearCompleted: () -> Unit
) {
    val clipboard = state.clipboardState
    val operation = state.activeFileOperation
    LaunchedEffect(operation?.terminalStatus) {
        if (operation?.terminalStatus != null) {
            delay(800L)
            onClearCompleted()
        }
    }
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val rawProgress = operation?.let { active ->
            active.totalBytes
                ?.takeIf { it > 0L }
                ?.let { total ->
                    ((active.bytesCopied ?: 0L).toFloat() / total.toFloat()).coerceIn(0f, 1f)
                }
                ?: active.totalItems
                    .takeIf { it > 0 }
                    ?.let { total ->
                        (active.completedItems.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                    }
        } ?: 0f
        val displayedProgress = if (operation?.terminalStatus != null) 1f else rawProgress
        val progressColor = when (operation?.terminalStatus) {
            OperationCompletionStatus.SUCCESS -> Color(0xFF4CAF50).copy(alpha = 0.25f)
            OperationCompletionStatus.FAILED,
            OperationCompletionStatus.CANCELLED ->
                MaterialTheme.colorScheme.error.copy(alpha = 0.25f)
            null -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        }
        Surface(
            onClick = {
                if (operation == null && clipboard != null) onShowContents()
            },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 4.dp,
            shadowElevation = 2.dp,
            modifier = Modifier
                .height(56.dp)
                .padding(end = 8.dp)
                .width(192.dp)
                .animateContentSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (operation != null) {
                            Modifier.drawBehind {
                                drawRect(
                                    color = progressColor,
                                    size = Size(size.width * displayedProgress, size.height)
                                )
                            }
                        } else {
                            Modifier
                        }
                    )
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (
                        operation?.type == BulkFileOperationType.MOVE ||
                        clipboard?.operation == ClipboardOperation.CUT
                    ) {
                        Icons.Default.ContentCut
                    } else if (operation?.type == BulkFileOperationType.CREATE_ARCHIVE) {
                        Icons.Default.FolderZip
                    } else {
                        Icons.Default.ContentCopy
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    val itemCount = operation?.totalItems ?: clipboard?.files?.size ?: 0
                    Text(
                        stringResource(R.string.audio_clipboard_items, itemCount),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        operation?.totalBytes
                            ?.takeIf { it > 0L }
                            ?.let { total ->
                                formatFileSize(
                                    (
                                        total -
                                            (operation.bytesCopied ?: 0L)
                                        ).coerceAtLeast(0L)
                                )
                            }
                            ?: if (operation != null) {
                                "${operation.completedItems}/${operation.totalItems}"
                            } else if (clipboard != null) {
                                formatFileSize(clipboard.totalSize)
                            } else {
                                ""
                            },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        SplitButtonGroup(
            actions = when {
                operation != null && operation.terminalStatus == null -> listOf(
                    ToolbarAction(
                        icon = Icons.Default.Close,
                        contentDescription = stringResource(R.string.audio_cancel_transfer),
                        containerColor = MaterialTheme.colorScheme.error,
                        tint = MaterialTheme.colorScheme.onError,
                        onClick = onCancel
                    )
                )
                operation == null && clipboard != null -> buildList {
                    if (canPaste) {
                        add(
                            ToolbarAction(
                                icon = Icons.Default.ContentPaste,
                                contentDescription = stringResource(R.string.audio_paste_here),
                                onClick = onPaste
                            )
                        )
                    }
                    add(
                        ToolbarAction(
                            icon = Icons.Default.Close,
                            contentDescription = stringResource(R.string.audio_cancel_transfer),
                            containerColor = MaterialTheme.colorScheme.error,
                            tint = MaterialTheme.colorScheme.onError,
                            onClick = onCancel
                        )
                    )
                }
                else -> emptyList()
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            height = 48.dp,
            minWidth = 48.dp,
            iconSize = 24.dp
        )
    }
}
