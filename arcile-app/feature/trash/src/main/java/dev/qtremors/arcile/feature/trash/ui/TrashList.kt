package dev.qtremors.arcile.feature.trash.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import dev.qtremors.arcile.core.presentation.formatFileSize
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.StorageKind
import dev.qtremors.arcile.core.storage.domain.TrashMetadata
import dev.qtremors.arcile.core.storage.domain.storageParentPath
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.getFileIconVector
import dev.qtremors.arcile.core.ui.image.ThumbnailKey
import dev.qtremors.arcile.core.ui.image.ThumbnailTargetSize
import dev.qtremors.arcile.core.ui.image.ThumbnailType
import dev.qtremors.arcile.core.ui.image.buildThumbnailImageRequest
import dev.qtremors.arcile.core.ui.rememberDateFormatter
import java.util.Calendar
import java.util.Date

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TrashList(
    files: List<TrashMetadata>,
    selectedFiles: Set<String>,
    onToggleSelection: (String) -> Unit,
    onOpenFile: (FileModel) -> Unit,
    onRequestRestore: (String) -> Unit,
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
                TrashRow(
                    trashItem = trashItem,
                    isSelected = selectedFiles.contains(trashItem.id),
                    isSelectionMode = selectedFiles.isNotEmpty(),
                    formatter = formatter,
                    onToggleSelection = onToggleSelection,
                    onOpenFile = onOpenFile,
                    onRequestRestore = onRequestRestore
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrashRow(
    trashItem: TrashMetadata,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    formatter: java.text.DateFormat,
    onToggleSelection: (String) -> Unit,
    onOpenFile: (FileModel) -> Unit,
    onRequestRestore: (String) -> Unit
) {
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        label = "trash row container"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        label = "trash row content"
    )

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.padding(start = 16.dp)) {
                TrashPreview(
                    file = trashItem.fileModel,
                    onClick = {
                        if (isSelectionMode || trashItem.fileModel.isDirectory) {
                            onToggleSelection(trashItem.id)
                        } else {
                            onOpenFile(trashItem.fileModel)
                        }
                    }
                )
            }
            ListItem(
                modifier = Modifier
                    .weight(1f)
                    .combinedClickable(
                        onClick = { onToggleSelection(trashItem.id) },
                        onLongClick = { onToggleSelection(trashItem.id) }
                    ),
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
                            StorageKind.INTERNAL -> stringResource(R.string.internal_storage)
                            StorageKind.SD_CARD -> stringResource(R.string.sd_card)
                            StorageKind.EXTERNAL_UNCLASSIFIED -> stringResource(R.string.external_unclassified)
                            else -> stringResource(R.string.otg_usb)
                        }
                        val parentPath = storageParentPath(trashItem.originalPath)
                            ?: trashItem.originalPath.ifBlank {
                                stringResource(R.string.trash_original_unavailable)
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
                    IconButton(onClick = { onRequestRestore(trashItem.id) }) {
                        Icon(
                            imageVector = Icons.Default.Restore,
                            contentDescription = stringResource(
                                R.string.trash_restore_item_action,
                                trashItem.fileModel.name
                            ),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
        }
    }
}

@Composable
private fun TrashPreview(
    file: FileModel,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val thumbnailKey = remember(file) { ThumbnailKey.from(file) }
    val targetSizePx = remember(density) {
        ThumbnailTargetSize.fromBounds(with(density) { TrashPreviewSize.roundToPx() })
    }
    val thumbnailRequestData: Any = when (thumbnailKey.type) {
        ThumbnailType.Image -> thumbnailKey.contentUri ?: thumbnailKey.file
        ThumbnailType.Video,
        ThumbnailType.Audio,
        ThumbnailType.Pdf,
        ThumbnailType.Apk -> thumbnailKey
        ThumbnailType.Unsupported -> thumbnailKey.file
    }
    val thumbnailRequest = remember(context, thumbnailRequestData, thumbnailKey, targetSizePx) {
        buildThumbnailImageRequest(
            context = context,
            data = thumbnailRequestData,
            cacheKey = thumbnailKey.variantKey(targetSizePx).cacheKey,
            sizePx = targetSizePx
        )
    }
    val previewDescription = stringResource(R.string.trash_open_preview, file.name)
    val previewModifier = Modifier
        .size(TrashPreviewSize)
        .clip(MaterialTheme.shapes.medium)
        .clickable(
            onClickLabel = previewDescription,
            onClick = onClick
        )
        .semantics { contentDescription = previewDescription }

    if (!file.isDirectory && thumbnailKey.type != ThumbnailType.Unsupported) {
        SubcomposeAsyncImage(
            model = thumbnailRequest,
            contentDescription = null,
            modifier = previewModifier,
            contentScale = ContentScale.Crop,
            loading = { TrashPreviewFallback(file) },
            error = { TrashPreviewFallback(file) }
        )
    } else {
        Box(
            modifier = previewModifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            TrashPreviewFallback(file)
        }
    }
}

@Composable
private fun TrashPreviewFallback(file: FileModel) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = getFileIconVector(file),
            contentDescription = null,
            tint = if (file.isDirectory) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(28.dp)
        )
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

private val TrashPreviewSize = 56.dp
