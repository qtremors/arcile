package dev.qtremors.arcile.feature.trash.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.feature.trash.R
import dev.qtremors.arcile.core.storage.domain.TrashMetadata
import dev.qtremors.arcile.core.storage.domain.TrashRestoreStatus
import dev.qtremors.arcile.shared.ui.rememberDateFormatter
import dev.qtremors.arcile.utils.formatFileSize
import java.util.Calendar
import java.util.Date

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrashList(
    files: List<TrashMetadata>,
    selectedFiles: Set<String>,
    onToggleSelection: (String) -> Unit,
    contentPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(0.dp)
) {
    val formatter = rememberDateFormatter("MMM dd, yyyy \u2022 HH:mm")

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = contentPadding
    ) {
        groupedTrash(files).forEach { (label, groupItems) ->
            item(key = "trash_header_$label") {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
            items(
                items = groupItems,
                key = { it.id },
                contentType = { if (it.fileModel.isDirectory) "trash_directory" else "trash_file" }
            ) { trashItem ->
                TrashRow(trashItem, selectedFiles.contains(trashItem.id), formatter, onToggleSelection)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrashRow(
    trashItem: TrashMetadata,
    isSelected: Boolean,
    formatter: java.text.DateFormat,
    onToggleSelection: (String) -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .combinedClickable(
                onClick = { onToggleSelection(trashItem.id) },
                onLongClick = { onToggleSelection(trashItem.id) }
            ),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        ListItem(
            leadingContent = {
                val file = trashItem.fileModel
                if (!file.isDirectory &&
                    (dev.qtremors.arcile.core.storage.domain.FileCategories.Images.extensions.contains(file.name.substringAfterLast('.').lowercase()) ||
                            dev.qtremors.arcile.core.storage.domain.FileCategories.Videos.extensions.contains(file.name.substringAfterLast('.').lowercase()) ||
                            dev.qtremors.arcile.core.storage.domain.FileCategories.APKs.extensions.contains(file.name.substringAfterLast('.').lowercase()) ||
                            dev.qtremors.arcile.core.storage.domain.FileCategories.Audio.extensions.contains(file.name.substringAfterLast('.').lowercase()))
                ) {
                    SubcomposeAsyncImage(
                        model = java.io.File(file.absolutePath),
                        contentDescription = null,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(MaterialTheme.shapes.small),
                        contentScale = ContentScale.Crop,
                        error = {
                            androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                                androidx.compose.material3.Icon(
                                    imageVector = dev.qtremors.arcile.shared.ui.getFileIconVector(file),
                                    contentDescription = null,
                                    tint = if (file.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    )
                } else {
                    androidx.compose.material3.Icon(
                        imageVector = dev.qtremors.arcile.shared.ui.getFileIconVector(file),
                        contentDescription = null,
                        tint = if (file.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(40.dp)
                    )
                }
            },
            headlineContent = {
                Text(
                    text = trashItem.fileModel.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            supportingContent = {
                Column {
                    val sourceVolumeStr = when (trashItem.sourceStorageKind) {
                        dev.qtremors.arcile.core.storage.domain.StorageKind.INTERNAL -> stringResource(R.string.internal_storage)
                        dev.qtremors.arcile.core.storage.domain.StorageKind.SD_CARD -> stringResource(R.string.sd_card)
                        dev.qtremors.arcile.core.storage.domain.StorageKind.EXTERNAL_UNCLASSIFIED -> stringResource(R.string.external_unclassified)
                        else -> stringResource(R.string.otg_usb)
                    }
                    val parentPath = if (trashItem.originalPath.contains("/")) {
                        trashItem.originalPath.substringBeforeLast("/")
                    } else {
                        trashItem.originalPath.ifBlank { stringResource(R.string.trash_original_unavailable) }
                    }
                    Text(
                        text = stringResource(R.string.trash_original_location, sourceVolumeStr, parentPath),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${stringResource(R.string.deleted_at, formatter.format(Date(trashItem.deletionTime)))} • ${formatFileSize(trashItem.fileModel.size)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.secondary
                    )
                }
            },
            trailingContent = {
                Text(
                    text = restoreStatusLabel(trashItem.restoreStatus),
                    style = MaterialTheme.typography.labelSmall,
                    color = when (trashItem.restoreStatus) {
                        TrashRestoreStatus.ORIGINAL_AVAILABLE -> MaterialTheme.colorScheme.primary
                        TrashRestoreStatus.ORIGINAL_CONFLICT_RENAME -> MaterialTheme.colorScheme.tertiary
                        TrashRestoreStatus.DESTINATION_REQUIRED,
                        TrashRestoreStatus.RECOVERED_ITEM -> MaterialTheme.colorScheme.error
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}

@Composable
private fun restoreStatusLabel(status: TrashRestoreStatus): String {
    return when (status) {
        TrashRestoreStatus.ORIGINAL_AVAILABLE -> stringResource(R.string.trash_status_can_restore)
        TrashRestoreStatus.ORIGINAL_CONFLICT_RENAME -> stringResource(R.string.trash_status_conflict)
        TrashRestoreStatus.DESTINATION_REQUIRED -> stringResource(R.string.trash_status_needs_destination)
        TrashRestoreStatus.RECOVERED_ITEM -> stringResource(R.string.trash_status_recovered)
    }
}

private fun groupedTrash(files: List<TrashMetadata>): List<Pair<String, List<TrashMetadata>>> {
    val todayStart = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val yesterdayStart = Calendar.getInstance().apply {
        timeInMillis = todayStart
        add(Calendar.DAY_OF_YEAR, -1)
    }.timeInMillis
    return listOf(
        "Today" to files.filter { it.deletionTime >= todayStart },
        "Yesterday" to files.filter { it.deletionTime in yesterdayStart until todayStart },
        "Older" to files.filter { it.deletionTime < yesterdayStart }
    ).filter { it.second.isNotEmpty() }
}
