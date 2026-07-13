package dev.qtremors.arcile.feature.activitylog.ui

import android.text.format.DateUtils
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.HighlightOff
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import dev.qtremors.arcile.core.storage.domain.ActivityLogEntry
import dev.qtremors.arcile.core.storage.domain.ActivityLogOperationStatus
import dev.qtremors.arcile.core.ui.R

@Composable
internal fun ActivityLogRow(entry: ActivityLogEntry) {
    when (entry) {
        is ActivityLogEntry.FolderOpened -> FolderOpenedRow(entry)
        is ActivityLogEntry.FileOperation -> FileOperationRow(entry)
    }
}

@Composable
private fun FolderOpenedRow(entry: ActivityLogEntry.FolderOpened) {
    ListItem(
        leadingContent = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
        headlineContent = { Text(stringResource(R.string.activity_log_folder_opened)) },
        supportingContent = {
            Text(text = entry.path, maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        trailingContent = {
            Text(activityTime(entry.timestampMillis), style = MaterialTheme.typography.labelSmall)
        }
    )
}

@Composable
private fun FileOperationRow(entry: ActivityLogEntry.FileOperation) {
    ListItem(
        leadingContent = { Icon(operationStatusIcon(entry.status), contentDescription = null) },
        headlineContent = { Text(operationTitle(entry.operationType, entry.status)) },
        supportingContent = {
            Text(
                text = operationDescription(entry),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingContent = {
            Text(activityTime(entry.timestampMillis), style = MaterialTheme.typography.labelSmall)
        }
    )
}

@Composable
private fun operationTitle(type: String, status: ActivityLogOperationStatus): String =
    stringResource(
        R.string.activity_log_operation_title,
        stringResource(type.activityLogOperationNameRes()),
        stringResource(status.activityLogStatusRes())
    )

@Composable
private fun operationDescription(entry: ActivityLogEntry.FileOperation): String {
    val sourceText = pluralStringResource(
        R.plurals.activity_log_source_count,
        entry.sourceCount,
        entry.sourceCount
    )
    return when {
        !entry.errorMessage.isNullOrBlank() -> "$sourceText\n${entry.errorMessage}"
        !entry.destinationPath.isNullOrBlank() -> "$sourceText\n${entry.destinationPath}"
        else -> sourceText
    }
}

@Composable
private fun activityTime(timestampMillis: Long): String =
    DateUtils.getRelativeTimeSpanString(
        timestampMillis,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS
    ).toString()

private fun operationStatusIcon(status: ActivityLogOperationStatus): ImageVector = when (status) {
    ActivityLogOperationStatus.RUNNING -> Icons.Default.HourglassTop
    ActivityLogOperationStatus.COMPLETED -> Icons.Default.CheckCircle
    ActivityLogOperationStatus.FAILED -> Icons.Default.ErrorOutline
    ActivityLogOperationStatus.CANCELLED -> Icons.Default.HighlightOff
}

internal fun ActivityLogOperationStatus.activityLogStatusRes(): Int = when (this) {
    ActivityLogOperationStatus.RUNNING -> R.string.activity_log_status_running
    ActivityLogOperationStatus.COMPLETED -> R.string.activity_log_status_completed
    ActivityLogOperationStatus.FAILED -> R.string.activity_log_status_failed
    ActivityLogOperationStatus.CANCELLED -> R.string.activity_log_status_cancelled
}

internal fun String.activityLogOperationNameRes(): Int = when (this) {
    "COPY" -> R.string.activity_log_operation_copy
    "MOVE" -> R.string.activity_log_operation_move
    "TRASH" -> R.string.activity_log_operation_trash
    "DELETE" -> R.string.activity_log_operation_delete
    "SHRED" -> R.string.activity_log_operation_shred
    "CREATE_FAKE" -> R.string.activity_log_operation_create_fake
    "EXTRACT_ARCHIVE" -> R.string.activity_log_operation_extract_archive
    "CREATE_ARCHIVE" -> R.string.activity_log_operation_create_archive
    else -> R.string.activity_log_operation_unknown
}
