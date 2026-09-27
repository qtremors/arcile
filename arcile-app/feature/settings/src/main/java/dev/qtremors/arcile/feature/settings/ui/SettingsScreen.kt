package dev.qtremors.arcile.feature.settings.ui

import dev.qtremors.arcile.core.ui.arcileTopAppBarNestedScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import dev.qtremors.arcile.core.ui.ArcileScreenScaffold
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.theme.bounceClickable
import dev.qtremors.arcile.core.ui.theme.spacing
import androidx.compose.foundation.shape.CircleShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    state: SettingsScreenState,
    navigationActions: SettingsNavigationActions,
    preferenceActions: SettingsPreferenceActions,
    backupActions: SettingsBackupActions,
    storageActions: SettingsStorageActions
) {
    val scrollBehavior = androidx.compose.material3.TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    ArcileScreenScaffold(
        modifier = Modifier.arcileTopAppBarNestedScroll(scrollBehavior),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.settings_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                scrollBehavior = dev.qtremors.arcile.core.ui.arcileTopAppBarScrollBehavior(scrollBehavior),
                navigationIcon = {
                    IconButton(
                        onClick = navigationActions.navigateBack,
                        modifier = Modifier
                            .clip(CircleShape)
                            .bounceClickable(onClick = navigationActions.navigateBack)
                    ) {
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
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                SettingsAppearanceSection(
                    theme = state.theme,
                    preferences = state.preferences,
                    actions = preferenceActions
                )
            }
            item {
                SettingsBrowsingSection(
                    theme = state.theme,
                    preferences = state.preferences,
                    actions = preferenceActions
                )
            }
            item {
                SettingsActivityPrivacySection(
                    recordingEnabled = state.preferences.activityRecordingEnabled,
                    onRecordingChange = preferenceActions.activityRecordingChange
                )
            }
            item {
                SettingsPluginSection(onOpen = navigationActions.navigateToPlugins)
            }
            item {
                SettingsStorageSection(
                    cache = state.externalCache,
                    onOpenStorageManagement = navigationActions.openStorageManagement,
                    onClearExternalCache = storageActions.clearExternalCache
                )
            }
            item {
                SettingsFileOpeningSection(
                    behaviors = state.preferences.fileOpenBehaviors,
                    onBehaviorChange = preferenceActions.fileOpenBehaviorChange
                )
            }
            item {
                SettingsBackupSection(
                    state = state.backup,
                    onExport = backupActions.requestExport,
                    onRestore = backupActions.requestRestore
                )
            }
            item {
                SettingsAboutSection(onOpen = navigationActions.navigateToAbout)
            }
        }
    }
}
