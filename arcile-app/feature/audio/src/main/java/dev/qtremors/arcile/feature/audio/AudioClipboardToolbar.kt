package dev.qtremors.arcile.feature.audio

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.operation.BulkFileOperationType
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
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 4.dp,
            shadowElevation = 2.dp,
            modifier = Modifier
                .width(192.dp)
                .animateContentSize()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (
                        operation?.type == BulkFileOperationType.MOVE ||
                        clipboard?.operation == ClipboardOperation.CUT
                    ) {
                        Icons.Default.ContentCut
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
                        when {
                            operation != null ->
                                "${operation.completedItems}/${operation.totalItems}"
                            clipboard != null -> formatFileSize(clipboard.totalSize)
                            else -> ""
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        SplitButtonGroup(
            actions = buildList {
                if (operation == null && clipboard != null && canPaste) {
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
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            height = 48.dp,
            minWidth = 48.dp,
            iconSize = 24.dp
        )
    }
}
