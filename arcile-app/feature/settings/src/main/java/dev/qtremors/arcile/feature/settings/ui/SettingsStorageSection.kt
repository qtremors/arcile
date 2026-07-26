package dev.qtremors.arcile.feature.settings.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.settings.SettingsSection

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsStorageSection(
    cache: SettingsExternalCacheState,
    onOpenStorageManagement: () -> Unit,
    onClearExternalCache: () -> Unit
) {
    SettingsSection(title = stringResource(R.string.section_storage)) {
        SegmentedListItem(
            onClick = onOpenStorageManagement,
            shapes = dev.qtremors.arcile.core.ui.theme.expressiveSegmentedShapes(index = 0, count = 2),
            content = { Text(stringResource(R.string.manage_classification)) },
            supportingContent = { Text(stringResource(R.string.manage_classification_description)) },
            leadingContent = {
                Box(
                    modifier = Modifier.fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Storage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            },
            colors = ListItemDefaults.segmentedColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            modifier = Modifier.height(IntrinsicSize.Min)
        )
        SegmentedListItem(
            onClick = { if (!cache.isBusy) onClearExternalCache() },
            enabled = !cache.isBusy,
            shapes = dev.qtremors.arcile.core.ui.theme.expressiveSegmentedShapes(index = 1, count = 2),
            content = { Text(stringResource(R.string.clear_external_access_cache)) },
            supportingContent = {
                Text(
                    stringResource(
                        R.string.clear_external_access_cache_description,
                        cache.fileCount
                    )
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
