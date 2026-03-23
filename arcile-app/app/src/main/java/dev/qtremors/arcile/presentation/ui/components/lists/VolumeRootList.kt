package dev.qtremors.arcile.presentation.ui.components.lists
import dev.qtremors.arcile.R
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.domain.StorageKind
import dev.qtremors.arcile.domain.StorageVolume
import dev.qtremors.arcile.utils.formatFileSize

@Composable
fun VolumeRootList(
    volumes: List<StorageVolume>,
    onNavigateTo: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        items(volumes, key = { it.id }) { volume ->
            VolumeItemRow(
                volume = volume,
                onClick = { onNavigateTo(volume.path) },
                modifier = Modifier.animateItem()
            )
        }
    }
}

@Composable
fun VolumeItemRow(
    volume: StorageVolume,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val usedBytes = volume.totalBytes - volume.freeBytes
    val subtitle = "${formatFileSize(usedBytes)} used of ${formatFileSize(volume.totalBytes)}"

    val icon = when (volume.kind) {
        StorageKind.INTERNAL -> Icons.Default.Storage
        StorageKind.SD_CARD -> Icons.Default.SdStorage
        StorageKind.OTG, StorageKind.EXTERNAL_UNCLASSIFIED -> Icons.Default.Usb
    }

    val badgeText = when (volume.kind) {
        StorageKind.SD_CARD -> "SD Card"
        StorageKind.OTG -> "Temporary USB"
        StorageKind.EXTERNAL_UNCLASSIFIED -> "Unclassified external"
        else -> null
    }

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = Color.Transparent,
        modifier = modifier
            .clickable(onClick = onClick)
    ) {
        ListItem(
            leadingContent = {
                Icon(
                    imageVector = icon,
                    contentDescription = stringResource(R.string.desc_volume_icon),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            },
            headlineContent = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(volume.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (badgeText != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            },
            supportingContent = {
                Text(subtitle)
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}