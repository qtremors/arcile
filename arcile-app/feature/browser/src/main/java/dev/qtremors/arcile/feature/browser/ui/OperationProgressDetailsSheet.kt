package dev.qtremors.arcile.feature.browser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.feature.browser.BrowserFileOperationUiState
import dev.qtremors.arcile.shared.ui.ArcileActionSheet
import dev.qtremors.arcile.ui.theme.bounceClickable
import dev.qtremors.arcile.utils.formatFileSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OperationProgressDetailsSheet(
    activeOp: BrowserFileOperationUiState,
    actions: BrowserUiActions,
    onDismissRequest: () -> Unit
) {
    val timeElapsedSec = (System.currentTimeMillis() - activeOp.startTimeMillis) / 1000f
    val speedBytesPerSec = if (timeElapsedSec > 0.5f && activeOp.bytesCopied != null) {
        activeOp.bytesCopied.toFloat() / timeElapsedSec
    } else {
        0f
    }
    val etaSec = if (speedBytesPerSec > 1024f && activeOp.totalBytes != null && activeOp.bytesCopied != null) {
        val remainingBytes = activeOp.totalBytes - activeOp.bytesCopied
        (remainingBytes.toFloat() / speedBytesPerSec).toLong()
    } else {
        -1L
    }
    val speedText = if (speedBytesPerSec > 0f) {
        stringResource(R.string.transfer_speed_value, formatFileSize(speedBytesPerSec.toLong()))
    } else {
        stringResource(R.string.transfer_calculating)
    }
    val etaText = when {
        etaSec < 0 -> stringResource(R.string.transfer_calculating)
        etaSec >= 60 -> stringResource(R.string.transfer_eta_minutes, etaSec / 60, etaSec % 60)
        else -> stringResource(R.string.transfer_eta_seconds, etaSec)
    }

    ArcileActionSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            TransferDetailsHeader(onDismissRequest)
            TransferMetrics(speedText = speedText, etaText = etaText)
            TransferProgress(activeOp)
            TransferCancelButton(actions = actions, onDismissRequest = onDismissRequest)
            TransferQueue(activeOp)
        }
    }
}

@Composable
private fun TransferDetailsHeader(onDismissRequest: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.transfer_details_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        IconButton(
            onClick = onDismissRequest,
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .bounceClickable(onClick = onDismissRequest)
        ) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close))
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun TransferMetrics(speedText: String, etaText: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = stringResource(R.string.transfer_speed),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(speedText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = stringResource(R.string.transfer_estimated_time),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(etaText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun TransferProgress(activeOp: BrowserFileOperationUiState) {
    val progressFraction = when {
        activeOp.totalBytes != null && activeOp.totalBytes > 0L ->
            ((activeOp.bytesCopied ?: 0L).toFloat() / activeOp.totalBytes.toFloat()).coerceIn(0f, 1f)
        activeOp.totalItems > 0 ->
            (activeOp.completedItems.toFloat() / activeOp.totalItems.toFloat()).coerceIn(0f, 1f)
        else -> 0f
    }
    LinearProgressIndicator(
        progress = { progressFraction },
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(CircleShape)
    )
    Spacer(modifier = Modifier.height(8.dp))
    val progressText = if (activeOp.totalBytes != null && activeOp.totalBytes > 0L) {
        stringResource(
            R.string.transfer_progress_bytes,
            formatFileSize(activeOp.bytesCopied ?: 0L),
            formatFileSize(activeOp.totalBytes)
        )
    } else {
        stringResource(R.string.transfer_progress_items, activeOp.completedItems, activeOp.totalItems)
    }
    Text(
        text = progressText,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun TransferCancelButton(
    actions: BrowserUiActions,
    onDismissRequest: () -> Unit
) {
    val onCancelClick = {
        actions.onCancelClipboard()
        onDismissRequest()
    }
    Button(
        onClick = onCancelClick,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        shape = dev.qtremors.arcile.ui.theme.ExpressiveShapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .bounceClickable(onClick = onCancelClick)
    ) {
        Text(stringResource(R.string.transfer_cancel_operation), color = Color.White)
    }
}

@Composable
private fun TransferQueue(activeOp: BrowserFileOperationUiState) {
    if (activeOp.sourcePaths.isEmpty()) return
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = stringResource(R.string.transfer_file_queue),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(activeOp.sourcePaths) { path ->
            TransferQueueRow(
                name = path.substringAfterLast('/'),
                isCurrent = path == activeOp.currentPath
            )
        }
    }
}

@Composable
private fun TransferQueueRow(name: String, isCurrent: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
            contentDescription = null,
            tint = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
