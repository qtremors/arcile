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
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
            
            val animatedSurfaceColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                label = "trashListItemColor"
            )
            
            Surface(
                shape = if (isSelected) MaterialTheme.shapes.large else MaterialTheme.shapes.extraLarge,
                color = animatedSurfaceColor,
                modifier = Modifier
                    .padding(horizontal = if (isSelected) 8.dp else 0.dp, vertical = if (isSelected) 4.dp else 0.dp)
                    .combinedClickable(
                        onClick = { onToggleSelection(trashItem.id) },
                        onLongClick = { onToggleSelection(trashItem.id) }
                    )
            ) {
                ListItem(
                    headlineContent = { Text(trashItem.fileModel.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        Column {
                            Text(
                                text = stringResource(R.string.deleted_at, formatter.format(Date(trashItem.deletionTime))),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            val sourceVolumeStr = when (trashItem.sourceStorageKind) {
                                dev.qtremors.arcile.domain.StorageKind.INTERNAL -> stringResource(R.string.internal_storage)
                                dev.qtremors.arcile.domain.StorageKind.SD_CARD -> stringResource(R.string.sd_card)
                                else -> stringResource(R.string.otg_usb)
                            }
                            Text(
                                text = stringResource(R.string.from_volume, sourceVolumeStr, trashItem.originalPath.substringBeforeLast("/")),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
    }
}
