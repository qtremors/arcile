package dev.qtremors.arcile.feature.settings.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.settings.SettingsSection
import dev.qtremors.arcile.core.ui.theme.bounceClickable

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsStorageSection(
    cache: SettingsExternalCacheState,
    onOpenStorageManagement: () -> Unit,
    onClearExternalCache: () -> Unit
) {
    SettingsSection(title = stringResource(R.string.section_storage)) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.manage_classification)) },
            supportingContent = { Text(stringResource(R.string.manage_classification_description)) },
            leadingContent = {
                Icon(
                    Icons.Default.Storage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier
                .clip(MaterialTheme.shapes.medium)
                .bounceClickable(onClick = onOpenStorageManagement)
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.clear_external_access_cache)) },
            supportingContent = {
                Text(
                    stringResource(
                        R.string.clear_external_access_cache_description,
                        cache.fileCount
                    )
                )
            },
            leadingContent = {
                Icon(
                    Icons.Default.DeleteSweep,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            trailingContent = {
                if (cache.isBusy) {
                    LoadingIndicator(
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier
                .clip(MaterialTheme.shapes.medium)
                .testTag("external_cache_setting_row")
                .bounceClickable(enabled = !cache.isBusy, onClick = onClearExternalCache)
        )
    }
}
