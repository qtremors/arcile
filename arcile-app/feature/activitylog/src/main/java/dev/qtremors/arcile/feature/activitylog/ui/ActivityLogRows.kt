package dev.qtremors.arcile.feature.activitylog.ui

import android.text.format.DateUtils
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.HighlightOff
import androidx.compose.material.icons.filled.History
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
import dev.qtremors.arcile.core.storage.domain.ActivityLogPage
import dev.qtremors.arcile.core.storage.domain.ActivityLogOperationStatus
import dev.qtremors.arcile.core.ui.R

@Composable
internal fun ActivityLogRow(entry: ActivityLogEntry) {
    when (entry) {
        is ActivityLogEntry.FolderOpened -> FolderOpenedRow(entry)
        is ActivityLogEntry.FileOpened -> FileOpenedRow(entry)
        is ActivityLogEntry.PageVisited -> PageVisitedRow(entry)
        is ActivityLogEntry.FileOperation -> FileOperationRow(entry)
    }
}

@Composable
private fun FileOpenedRow(entry: ActivityLogEntry.FileOpened) {
    ListItem(
        leadingContent = { Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null) },
        content = { Text(entry.path.displayName()) },
        supportingContent = {
            Text(
                text = "${stringResource(R.string.activity_log_file_opened)} • ${entry.path}",
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingContent = {
            Text(activityTime(entry.timestampMillis), style = MaterialTheme.typography.labelSmall)
        }
    )
}

@Composable
private fun FolderOpenedRow(entry: ActivityLogEntry.FolderOpened) {
    ListItem(
        leadingContent = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
        content = { Text(entry.path.displayName()) },
        supportingContent = {
            Text(
                text = "${stringResource(R.string.activity_log_folder_opened)} • ${entry.path}",
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingContent = {
            Text(activityTime(entry.timestampMillis), style = MaterialTheme.typography.labelSmall)
        }
    )
}

@Composable
private fun PageVisitedRow(entry: ActivityLogEntry.PageVisited) {
    ListItem(
        leadingContent = { Icon(Icons.Default.History, contentDescription = null) },
        content = { Text(stringResource(entry.page.activityLogPageNameRes())) },
        supportingContent = {
            Text(
                text = listOfNotNull(
                    stringResource(R.string.activity_log_page_visited),
                    entry.detail?.takeIf(String::isNotBlank)
                ).joinToString(" • "),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
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
        content = { Text(operationTitle(entry.operationType, entry.status)) },
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
    "CREATE_FAKE" -> R.string.activity_log_operation_create_synthetic
    "EXTRACT_ARCHIVE" -> R.string.activity_log_operation_extract_archive
    "CREATE_ARCHIVE" -> R.string.activity_log_operation_create_archive
    "CREATE_FOLDER" -> R.string.activity_log_operation_create_folder
    "CREATE_FILE" -> R.string.activity_log_operation_create_file
    "RENAME" -> R.string.activity_log_operation_rename
    "BATCH_RENAME" -> R.string.activity_log_operation_batch_rename
    "RESTORE" -> R.string.activity_log_operation_restore
    "EMPTY_TRASH" -> R.string.activity_log_operation_empty_trash
    else -> R.string.activity_log_operation_unknown
}

internal fun ActivityLogPage.activityLogPageNameRes(): Int = when (this) {
    ActivityLogPage.HOME -> R.string.activity_log_page_home
    ActivityLogPage.BROWSER -> R.string.activity_log_page_browser
    ActivityLogPage.CATEGORY -> R.string.activity_log_page_category
    ActivityLogPage.TOOLS -> R.string.activity_log_page_tools
    ActivityLogPage.ACTIVITY -> R.string.activity_log_page_activity
    ActivityLogPage.ONLY_FILES -> R.string.activity_log_page_only_files
    ActivityLogPage.SETTINGS -> R.string.activity_log_page_settings
    ActivityLogPage.PLUGINS -> R.string.activity_log_page_plugins
    ActivityLogPage.TRASH -> R.string.activity_log_page_trash
    ActivityLogPage.RECENT_FILES -> R.string.activity_log_page_recent_files
    ActivityLogPage.IMAGE_GALLERY -> R.string.activity_log_page_image_gallery
    ActivityLogPage.IMAGE_VIEWER -> R.string.activity_log_page_image_viewer
    ActivityLogPage.VIDEO_VIEWER -> R.string.activity_log_page_video_viewer
    ActivityLogPage.AUDIO_LIBRARY -> R.string.activity_log_page_audio_library
    ActivityLogPage.DOCUMENT_LIBRARY -> R.string.activity_log_page_document_library
    ActivityLogPage.APK_LIBRARY -> R.string.activity_log_page_apk_library
    ActivityLogPage.STORAGE_DASHBOARD -> R.string.activity_log_page_storage_dashboard
    ActivityLogPage.STORAGE_CLEANER -> R.string.activity_log_page_storage_cleaner
    ActivityLogPage.STORAGE_MANAGEMENT -> R.string.activity_log_page_storage_management
    ActivityLogPage.QUICK_ACCESS -> R.string.activity_log_page_quick_access
    ActivityLogPage.ARCHIVE_VIEWER -> R.string.activity_log_page_archive_viewer
    ActivityLogPage.ABOUT -> R.string.activity_log_page_about
    ActivityLogPage.LICENSES -> R.string.activity_log_page_licenses
}

private fun String.displayName(): String =
    trimEnd('/').substringAfterLast('/').ifBlank { this }
