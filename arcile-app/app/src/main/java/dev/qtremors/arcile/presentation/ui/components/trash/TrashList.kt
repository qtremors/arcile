package dev.qtremors.arcile.presentation.ui.components.trash

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.R
import dev.qtremors.arcile.domain.TrashMetadata
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrashList(
    files: List<TrashMetadata>,
    selectedFiles: Set<String>,
    onToggleSelection: (String) -> Unit
) {
    val formatter = remember { SimpleDateFormat("MMM dd, yyyy \u2022 HH:mm", Locale.getDefault()) }

    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        items(files, key = { it.id }) { trashItem ->
            val isSelected = selectedFiles.contains(trashItem.id)
            
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
                            (dev.qtremors.arcile.domain.FileCategories.Images.extensions.contains(file.name.substringAfterLast('.').lowercase()) ||
                                    dev.qtremors.arcile.domain.FileCategories.Videos.extensions.contains(file.name.substringAfterLast('.').lowercase()) ||
                                    dev.qtremors.arcile.domain.FileCategories.APKs.extensions.contains(file.name.substringAfterLast('.').lowercase()) ||
                                    dev.qtremors.arcile.domain.FileCategories.Audio.extensions.contains(file.name.substringAfterLast('.').lowercase()))
                        ) {
                            AsyncImage(
                                model = java.io.File(file.absolutePath),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(MaterialTheme.shapes.small),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            androidx.compose.material3.Icon(
                                imageVector = if (file.isDirectory) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
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
                                dev.qtremors.arcile.domain.StorageKind.INTERNAL -> stringResource(R.string.internal_storage)
                                dev.qtremors.arcile.domain.StorageKind.SD_CARD -> stringResource(R.string.sd_card)
                                else -> stringResource(R.string.otg_usb)
                            }
                            Text(
                                text = "Original: $sourceVolumeStr${trashItem.originalPath.substringBeforeLast("/")}",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = stringResource(R.string.deleted_at, formatter.format(Date(trashItem.deletionTime))),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.secondary
                            )
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
    }
}
