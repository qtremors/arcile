package dev.qtremors.arcile.feature.home.ui.components

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.storage.domain.CategoryStorage
import dev.qtremors.arcile.core.storage.domain.StorageKind
import dev.qtremors.arcile.core.storage.domain.StorageVolume
import dev.qtremors.arcile.core.storage.domain.RootStorageUsage
import dev.qtremors.arcile.core.storage.domain.showTemporaryStorageBadge
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.theme.bodyMediumMedium
import dev.qtremors.arcile.core.ui.theme.bodySmallMedium
import dev.qtremors.arcile.core.ui.theme.bounceCombinedClickable
import dev.qtremors.arcile.core.ui.theme.bounceClickable
import dev.qtremors.arcile.core.ui.theme.spacing
import dev.qtremors.arcile.core.ui.theme.titleLargeBold
import dev.qtremors.arcile.core.ui.theme.titleMediumBold
import dev.qtremors.arcile.core.presentation.formatFileSize

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun StorageVolumeCard(
    volume: StorageVolume,
    categoryStorages: List<CategoryStorage>,
    trashBytes: Long,
    isLoading: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val used = volume.totalBytes - volume.freeBytes
    val showPlaceholder = isLoading || volume.totalBytes <= 0L
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .bounceCombinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(
            containerColor = if (volume.isPrimary) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
            contentColor = if (volume.isPrimary) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            }
        ),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(modifier = Modifier.padding(MaterialTheme.spacing.space20)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = volume.name,
                            style = MaterialTheme.typography.titleMediumBold
                        )
                        if (volume.kind.showTemporaryStorageBadge) {
                            Spacer(modifier = Modifier.width(MaterialTheme.spacing.small))
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = CircleShape,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (volume.kind == StorageKind.OTG) {
                                            Icons.Default.Usb
                                        } else {
                                            Icons.Default.Info
                                        },
                                        contentDescription = stringResource(R.string.desc_temporary),
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    if (volume.kind.showTemporaryStorageBadge) {
                        Text(
                            text = if (volume.kind == StorageKind.OTG) {
                                stringResource(R.string.otg_usb)
                            } else {
                                stringResource(R.string.external_unclassified)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
                Icon(
                    imageVector = when (volume.kind) {
                        StorageKind.INTERNAL -> Icons.Default.Storage
                        StorageKind.SD_CARD -> Icons.Default.SdCard
                        StorageKind.OTG -> Icons.Default.Usb
                        StorageKind.EXTERNAL_UNCLASSIFIED -> Icons.Default.SdCard
                    },
                    contentDescription = volume.name,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(MaterialTheme.spacing.space12))
            MultiColorStorageBar(
                totalBytes = volume.totalBytes.takeIf { it > 0L } ?: 1L,
                freeBytes = volume.freeBytes.takeIf { volume.totalBytes > 0L } ?: 1L,
                categoryStorages = categoryStorages,
                trashBytes = trashBytes,
                isCalculating = showPlaceholder
            )
            Spacer(modifier = Modifier.height(MaterialTheme.spacing.small))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${formatFileSize(used)} / ${formatFileSize(volume.totalBytes)}",
                    style = MaterialTheme.typography.bodySmallMedium
                )
                val percent = if (volume.totalBytes > 0) {
                    used * 100 / volume.totalBytes
                } else {
                    0
                }
                Text(
                    text = "$percent%",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
internal fun RootStorageUsageCard(
    usage: RootStorageUsage?,
    isLoading: Boolean,
    hasLoadCompleted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val showPlaceholder = usage == null && (isLoading || !hasLoadCompleted)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .bounceClickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        ),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(modifier = Modifier.padding(MaterialTheme.spacing.large)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.root_storage),
                    style = MaterialTheme.typography.titleLargeBold
                )
                Icon(
                    imageVector = Icons.Default.AccountTree,
                    contentDescription = stringResource(R.string.root_storage)
                )
            }
            Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))
            MultiColorStorageBar(
                totalBytes = usage?.totalBytes ?: 0L,
                freeBytes = usage?.freeBytes ?: 0L,
                categoryStorages = emptyList(),
                isCalculating = showPlaceholder
            )
            Spacer(modifier = Modifier.height(MaterialTheme.spacing.space12))
            when {
                usage != null -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${formatFileSize(usage.usedBytes)} used",
                        style = MaterialTheme.typography.bodyMediumMedium
                    )
                    Text(
                        text = "${formatFileSize(usage.freeBytes)} free",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                showPlaceholder -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    repeat(2) {
                        Surface(
                            modifier = Modifier
                                .width(96.dp)
                                .height(16.dp),
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.10f),
                            shape = CircleShape
                        ) {}
                    }
                }

                else -> Text(
                    text = stringResource(R.string.error_storage_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f)
                )
            }
            Spacer(modifier = Modifier.height(MaterialTheme.spacing.space12))
            when {
                usage != null -> CategoryLegend(
                    categoryStorages = emptyList(),
                    systemBytes = usage.usedBytes
                )

                showPlaceholder -> CategoryLegendPlaceholder(
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                )

                else -> Spacer(modifier = Modifier.height(14.dp))
            }
        }
    }
}
