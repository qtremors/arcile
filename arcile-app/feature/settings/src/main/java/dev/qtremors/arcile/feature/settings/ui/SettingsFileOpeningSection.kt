package dev.qtremors.arcile.feature.settings.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.qtremors.arcile.core.storage.domain.FileCategories
import dev.qtremors.arcile.core.storage.domain.FileOpenBehavior
import dev.qtremors.arcile.core.ui.ExpressiveSwitch
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.settings.SettingsSection
import dev.qtremors.arcile.core.ui.theme.expressiveSegmentedShapes

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsFileOpeningSection(
    behaviors: Map<String, FileOpenBehavior>,
    onBehaviorChange: (String, FileOpenBehavior) -> Unit
) {
    val categories = FileCategories.all
    SettingsSection(title = stringResource(R.string.settings_file_opening_title)) {
        categories.forEachIndexed { index, category ->
            val behavior = behaviors[category.name] ?: FileOpenBehavior.ARCILE
            val useArcile = behavior == FileOpenBehavior.ARCILE
            SegmentedListItem(
                onClick = {
                    onBehaviorChange(
                        category.name,
                        if (useArcile) FileOpenBehavior.EXTERNAL else FileOpenBehavior.ARCILE
                    )
                },
                shapes = expressiveSegmentedShapes(index, categories.size),
                content = { Text(category.name) },
                supportingContent = {
                    Text(
                        stringResource(
                            if (useArcile) {
                                R.string.settings_file_opening_arcile
                            } else {
                                R.string.settings_file_opening_external
                            }
                        )
                    )
                },
                leadingContent = {
                    Box(
                        modifier = Modifier.fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                trailingContent = {
                    ExpressiveSwitch(
                        checked = useArcile,
                        onCheckedChange = { checked ->
                            onBehaviorChange(
                                category.name,
                                if (checked) FileOpenBehavior.ARCILE else FileOpenBehavior.EXTERNAL
                            )
                        }
                    )
                },
                colors = ListItemDefaults.segmentedColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                modifier = Modifier.height(IntrinsicSize.Min)
            )
        }
    }
}
