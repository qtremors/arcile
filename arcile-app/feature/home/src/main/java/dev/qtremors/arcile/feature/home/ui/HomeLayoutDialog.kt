package dev.qtremors.arcile.feature.home.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.qtremors.arcile.core.storage.domain.HomeLayoutPreferences
import dev.qtremors.arcile.core.storage.domain.HomeSectionIds
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.rememberArcileHaptics
import dev.qtremors.arcile.core.ui.reorder.ReorderControls
import dev.qtremors.arcile.core.ui.theme.ExpressiveShapes
import dev.qtremors.arcile.core.ui.theme.bounceClickable

internal data class HomeSectionDefinition(
    val id: String,
    @param:StringRes val nameRes: Int,
    val icon: ImageVector
)

internal val HomeSectionCatalog = listOf(
    HomeSectionDefinition(HomeSectionIds.STORAGE, R.string.home_storage, Icons.Default.Storage),
    HomeSectionDefinition(HomeSectionIds.CATEGORIES, R.string.categories, Icons.Default.GridView),
    HomeSectionDefinition(HomeSectionIds.QUICK_ACCESS, R.string.quick_access, Icons.Default.Folder),
    HomeSectionDefinition(HomeSectionIds.UTILITIES, R.string.utilities, Icons.Default.Build),
    HomeSectionDefinition(HomeSectionIds.RECENT_FILES, R.string.recent_files, Icons.Default.History)
)

private data class HomeSectionDraft(
    val definition: HomeSectionDefinition,
    val enabled: Boolean
)

@Composable
internal fun HomeLayoutDialog(
    preferences: HomeLayoutPreferences,
    onDismiss: () -> Unit,
    onApply: (HomeLayoutPreferences) -> Unit
) {
    val catalogById = remember { HomeSectionCatalog.associateBy(HomeSectionDefinition::id) }
    var draftOrderedIds by rememberSaveable(preferences.orderedSectionIds) {
        mutableStateOf<List<String>>(preferences.orderedSectionIds.toList())
    }
    var draftEnabledIds by rememberSaveable(preferences.enabledSectionIds) {
        mutableStateOf<List<String>>(preferences.enabledSectionIds.toList())
    }
    val draftSections = draftOrderedIds.mapNotNull { id ->
        catalogById[id]?.let { definition ->
            HomeSectionDraft(
                definition = definition,
                enabled = id in draftEnabledIds
            )
        }
    }
    val haptics = rememberArcileHaptics()

    fun moveSection(index: Int, direction: Int) {
        val targetIndex = index + direction
        if (targetIndex !in draftOrderedIds.indices) return
        haptics.selectionChanged()
        draftOrderedIds = ArrayList(draftOrderedIds).apply {
            add(targetIndex, removeAt(index))
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.86f)
                .widthIn(max = 560.dp),
            shape = ExpressiveShapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(top = 24.dp, bottom = 16.dp)
            ) {
                Text(
                    text = stringResource(R.string.home_edit_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                Text(
                    text = stringResource(R.string.home_edit_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = ExpressiveShapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    LazyColumn {
                        itemsIndexed(
                            items = draftSections,
                            key = { _, item -> item.definition.id }
                        ) { index, draft ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateItem()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    modifier = Modifier.size(40.dp),
                                    shape = ExpressiveShapes.medium,
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = draft.definition.icon,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                                Text(
                                    text = stringResource(draft.definition.nameRes),
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                val sectionLabel = stringResource(draft.definition.nameRes)
                                val visibilityDescription = stringResource(
                                    R.string.home_show_section,
                                    sectionLabel
                                )
                                Switch(
                                    checked = draft.enabled,
                                    onCheckedChange = { enabled ->
                                        haptics.toggleMenu()
                                        draftEnabledIds = ArrayList(draftEnabledIds).apply {
                                            if (enabled) {
                                                if (draft.definition.id !in this) add(draft.definition.id)
                                            } else {
                                                remove(draft.definition.id)
                                            }
                                        }
                                    },
                                    thumbContent = {
                                        Icon(
                                            imageVector = if (draft.enabled) {
                                                Icons.Default.Check
                                            } else {
                                                Icons.Default.Close
                                            },
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    modifier = Modifier.semantics {
                                        contentDescription = visibilityDescription
                                    }
                                )
                                ReorderControls(
                                    itemLabel = sectionLabel,
                                    position = index,
                                    itemCount = draftSections.size,
                                    onMoveUp = { moveSection(index, -1) },
                                    onMoveDown = { moveSection(index, 1) }
                                )
                            }
                            if (index < draftSections.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val resetClick = {
                        haptics.toggleMenu()
                        draftOrderedIds = HomeSectionIds.ALL
                        draftEnabledIds = HomeSectionIds.ALL
                    }
                    TextButton(
                        onClick = resetClick,
                        modifier = Modifier.bounceClickable(onClick = resetClick),
                        shape = ExpressiveShapes.medium
                    ) {
                        Text(stringResource(R.string.reset))
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.bounceClickable(onClick = onDismiss),
                        shape = ExpressiveShapes.medium
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                    val applyClick = {
                        onApply(
                            HomeLayoutPreferences(
                                orderedSectionIds = draftOrderedIds,
                                enabledSectionIds = draftEnabledIds.toSet()
                            )
                        )
                    }
                    Button(
                        onClick = applyClick,
                        modifier = Modifier.bounceClickable(onClick = applyClick),
                        shape = ExpressiveShapes.medium
                    ) {
                        Text(stringResource(R.string.apply))
                    }
                }
            }
        }
    }
}
