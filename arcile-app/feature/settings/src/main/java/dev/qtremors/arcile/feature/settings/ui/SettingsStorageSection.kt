package dev.qtremors.arcile.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.storage.domain.StorageKind
import dev.qtremors.arcile.core.storage.domain.StorageVolume
import dev.qtremors.arcile.core.ui.ArcileSectionHeader
import dev.qtremors.arcile.core.ui.EmptyState
import dev.qtremors.arcile.core.ui.EmptyStateVariant
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.settings.SettingsSection
import dev.qtremors.arcile.core.ui.storage.StorageManagementCard

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsStorageSection(
    volumes: List<StorageVolume>? = null,
    cache: SettingsExternalCacheState,
    onSetVolumeClassification: (String, StorageKind) -> Unit = { _, _ -> },
    onResetVolumeClassification: (String) -> Unit = {},
    onClearExternalCache: () -> Unit,
    showHeading: Boolean = true
) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        if (showHeading) ArcileSectionHeader(text = stringResource(R.string.section_storage))
        SettingsSection(title = stringResource(R.string.storage_management_title)) {
            Card(
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.storage_management_intro_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.storage_management_intro_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (volumes == null) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator(modifier = Modifier.size(32.dp).testTag("storage_volumes_loading"))
                }
            } else if (volumes.isEmpty()) {
                EmptyState(
                    variant = EmptyStateVariant.StorageAccess,
                    title = stringResource(R.string.storage_management_empty_title),
                    description = stringResource(R.string.storage_management_empty_description)
                )
            } else {
                volumes.forEach { volume ->
                    StorageManagementCard(
                        volume = volume,
                        onSetVolumeClassification = onSetVolumeClassification,
                        onResetVolumeClassification = onResetVolumeClassification
                    )
                }
            }
        }
        SettingsSection(title = stringResource(R.string.settings_storage_temporary_section)) {
            SegmentedListItem(
                onClick = { if (!cache.isBusy && cache.fileCount > 0) onClearExternalCache() },
                enabled = !cache.isBusy && cache.fileCount > 0,
                shapes = dev.qtremors.arcile.core.ui.theme.expressiveSegmentedShapes(index = 0, count = 1),
                content = { Text(stringResource(R.string.clear_external_access_cache)) },
                supportingContent = {
                    Text(
                        if (cache.fileCount == 0) {
                            stringResource(R.string.clear_external_access_cache_empty)
                        } else {
                            pluralStringResource(
                                R.plurals.clear_external_access_cache_description,
                                cache.fileCount,
                                cache.fileCount
                            )
                        }
                    )
                },
                leadingContent = {
                    Box(
                        modifier = Modifier.fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                trailingContent = {
                    if (cache.isBusy) {
                        Box(
                            modifier = Modifier.fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            LoadingIndicator(
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                },
                colors = ListItemDefaults.segmentedColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                modifier = Modifier
                    .testTag("external_cache_setting_row")
                    .height(IntrinsicSize.Min)
            )
        }
    }
}
