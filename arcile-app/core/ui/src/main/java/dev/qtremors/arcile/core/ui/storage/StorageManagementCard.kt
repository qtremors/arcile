package dev.qtremors.arcile.core.ui.storage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.storage.domain.StorageKind
import dev.qtremors.arcile.core.storage.domain.StorageVolume
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.theme.ExpressiveShapes

@Composable
fun StorageManagementCard(
    volume: StorageVolume,
    onSetVolumeClassification: (String, StorageKind) -> Unit,
    onResetVolumeClassification: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = volume.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(text = volume.path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(
                    shape = ExpressiveShapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when (volume.kind) {
                                StorageKind.INTERNAL -> Icons.Default.Storage
                                StorageKind.SD_CARD -> Icons.Default.SdCard
                                StorageKind.OTG -> Icons.Default.Usb
                                StorageKind.EXTERNAL_UNCLASSIFIED -> Icons.Default.Info
                            },
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(storageKindLabel(volume.kind), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            Text(
                text = when (volume.kind) {
                    StorageKind.INTERNAL -> stringResource(R.string.storage_kind_internal_description)
                    StorageKind.SD_CARD -> stringResource(R.string.storage_kind_sd_description)
                    StorageKind.OTG -> stringResource(R.string.storage_kind_otg_description)
                    StorageKind.EXTERNAL_UNCLASSIFIED -> stringResource(R.string.storage_kind_unclassified_description)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val onClassifySdClick = { onSetVolumeClassification(volume.storageKey, StorageKind.SD_CARD) }
                val classifySdEnabled = !volume.isPrimary && volume.kind != StorageKind.SD_CARD
                TextButton(
                    onClick = onClassifySdClick,
                    enabled = classifySdEnabled,
                    shape = ExpressiveShapes.medium
                ) {
                    Icon(
                        imageVector = Icons.Default.SdCard,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.classify_as_sd))
                }

                val onClassifyOtgClick = { onSetVolumeClassification(volume.storageKey, StorageKind.OTG) }
                val classifyOtgEnabled = !volume.isPrimary && volume.kind != StorageKind.OTG
                TextButton(
                    onClick = onClassifyOtgClick,
                    enabled = classifyOtgEnabled,
                    shape = ExpressiveShapes.medium
                ) {
                    Icon(
                        imageVector = Icons.Default.Usb,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.classify_as_otg))
                }

                if (!volume.isPrimary) {
                    val onResetClick = { onResetVolumeClassification(volume.storageKey) }
                    val resetEnabled = volume.isUserClassified
                    TextButton(
                        onClick = onResetClick,
                        enabled = resetEnabled,
                        shape = ExpressiveShapes.medium
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.reset))
                    }
                }
            }

            if (volume.isUserClassified) {
                Text(
                    text = stringResource(R.string.storage_classification_saved),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun storageKindLabel(kind: StorageKind): String = when (kind) {
    StorageKind.INTERNAL -> stringResource(R.string.storage_kind_internal)
    StorageKind.SD_CARD -> stringResource(R.string.storage_kind_sd)
    StorageKind.OTG -> stringResource(R.string.storage_kind_otg)
    StorageKind.EXTERNAL_UNCLASSIFIED -> stringResource(R.string.storage_kind_unclassified)
}
