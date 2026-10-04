package dev.qtremors.arcile.feature.settings.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.ArcileScreenScaffold
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.arcileTopAppBarNestedScroll
import dev.qtremors.arcile.core.ui.arcileTopAppBarScrollBehavior
import dev.qtremors.arcile.core.ui.theme.expressiveSegmentedShapes
import dev.qtremors.arcile.core.ui.theme.spacing

private enum class SettingsPage(val title: Int) {
    APPEARANCE(R.string.section_appearance),
    BROWSING(R.string.section_browsing),
    PRIVACY(R.string.section_activity_privacy),
    STORAGE(R.string.section_storage),
    BACKUP(R.string.section_setup)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsScreen(
    pluginExtensions: Set<String>,
    state: SettingsScreenState,
    navigationActions: SettingsNavigationActions,
    preferenceActions: SettingsPreferenceActions,
    backupActions: SettingsBackupActions,
    storageActions: SettingsStorageActions
) {
    var pageName by rememberSaveable { mutableStateOf<String?>(null) }
    val page = SettingsPage.entries.firstOrNull { it.name == pageName }
    val navigateBack = {
        if (page == null) navigationActions.navigateBack() else pageName = null
    }
    BackHandler(enabled = page != null) { pageName = null }

    key(page) {
        val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        ArcileScreenScaffold(
            modifier = Modifier.arcileTopAppBarNestedScroll(scrollBehavior),
            topBar = {
                LargeTopAppBar(
                    title = {
                        Text(
                            stringResource(page?.title ?: R.string.settings_title),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    scrollBehavior = arcileTopAppBarScrollBehavior(scrollBehavior),
                    navigationIcon = {
                        IconButton(onClick = navigateBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    }
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding())
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(
                    top = 16.dp,
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                        MaterialTheme.spacing.screenGutter
                ),
                verticalArrangement = Arrangement.spacedBy(if (page == null) 8.dp else 24.dp)
            ) {
                if (page == null) {
                    item {
                        SettingsDestinationRow(
                            title = stringResource(R.string.section_appearance),
                            description = stringResource(R.string.settings_appearance_summary),
                            icon = Icons.Default.Palette,
                            onClick = { pageName = SettingsPage.APPEARANCE.name }
                        )
                    }
                    item {
                        SettingsDestinationRow(
                            title = stringResource(R.string.section_browsing),
                            description = stringResource(R.string.settings_browsing_summary),
                            icon = Icons.Default.FolderOpen,
                            onClick = { pageName = SettingsPage.BROWSING.name }
                        )
                    }
                    item {
                        SettingsFileOpeningSection(
                            behaviors = state.preferences.fileOpenBehaviors,
                            pluginExtensions = pluginExtensions,
                            onBehaviorChange = preferenceActions.fileOpenBehaviorChange,
                            onBehaviorRemove = preferenceActions.fileOpenBehaviorRemove,
                            showHeading = false
                        )
                    }
                    item {
                        SettingsDestinationRow(
                            title = stringResource(R.string.section_activity_privacy),
                            description = stringResource(R.string.settings_privacy_summary),
                            icon = Icons.Default.History,
                            onClick = { pageName = SettingsPage.PRIVACY.name }
                        )
                    }
                    item {
                        SettingsDestinationRow(
                            title = stringResource(R.string.section_storage),
                            description = stringResource(R.string.settings_storage_summary),
                            icon = Icons.Default.Storage,
                            onClick = { pageName = SettingsPage.STORAGE.name }
                        )
                    }
                    item {
                        SettingsDestinationRow(
                            title = stringResource(R.string.section_setup),
                            description = stringResource(R.string.settings_backup_summary),
                            icon = Icons.Default.Restore,
                            onClick = { pageName = SettingsPage.BACKUP.name }
                        )
                    }
                    item {
                        SettingsDestinationRow(
                            title = stringResource(R.string.plugins_title),
                            description = stringResource(R.string.plugins_settings_description),
                            icon = Icons.Default.Extension,
                            onClick = navigationActions.navigateToPlugins
                        )
                    }
                    item {
                        SettingsDestinationRow(
                            title = stringResource(R.string.about_headline),
                            description = stringResource(R.string.about_description),
                            icon = Icons.Default.Info,
                            onClick = navigationActions.navigateToAbout
                        )
                    }
                } else {
                    item {
                        when (page) {
                            SettingsPage.APPEARANCE -> SettingsAppearanceSection(
                                theme = state.theme,
                                preferences = state.preferences,
                                actions = preferenceActions,
                                showHeading = false
                            )
                            SettingsPage.BROWSING -> SettingsBrowsingSection(
                                theme = state.theme,
                                preferences = state.preferences,
                                actions = preferenceActions,
                                showHeading = false
                            )
                            SettingsPage.PRIVACY -> SettingsActivityPrivacySection(
                                recordingEnabled = state.preferences.activityRecordingEnabled,
                                onRecordingChange = preferenceActions.activityRecordingChange,
                                showHeading = false
                            )
                            SettingsPage.STORAGE -> SettingsStorageSection(
                                volumes = state.storageVolumes,
                                cache = state.externalCache,
                                onSetVolumeClassification = storageActions.setVolumeClassification,
                                onResetVolumeClassification = storageActions.resetVolumeClassification,
                                onClearExternalCache = storageActions.clearExternalCache,
                                showHeading = false
                            )
                            SettingsPage.BACKUP -> SettingsBackupSection(
                                state = state.backup,
                                onExport = backupActions.requestExport,
                                onRestore = backupActions.requestRestore,
                                showHeading = false
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsDestinationRow(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = expressiveSegmentedShapes(index = 0, count = 1),
        content = { Text(title) },
        supportingContent = { Text(description) },
        leadingContent = {
            Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        },
        trailingContent = {
            Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        colors = ListItemDefaults.segmentedColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        modifier = Modifier.height(IntrinsicSize.Min)
    )
}
