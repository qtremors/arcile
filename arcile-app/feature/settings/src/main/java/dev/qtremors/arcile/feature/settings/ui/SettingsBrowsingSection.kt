package dev.qtremors.arcile.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Expand
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ListItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.ArcileListSurface
import dev.qtremors.arcile.core.ui.ArcileSectionHeader
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.rememberArcileHaptics
import dev.qtremors.arcile.core.ui.settings.AppStartPageSelector
import dev.qtremors.arcile.core.ui.theme.ThemeState
import dev.qtremors.arcile.feature.settings.SettingsPreferences

@Composable
internal fun SettingsBrowsingSection(
    theme: ThemeState,
    preferences: SettingsPreferences,
    actions: SettingsPreferenceActions
) {
    val haptics = rememberArcileHaptics()
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        ArcileSectionHeader(text = stringResource(R.string.section_browsing))
        ArcileListSurface {
            AppStartPageSelector(
                currentPage = preferences.appStartPage,
                onPageSelected = actions.appStartPageChange
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            SettingsSwitchRow(
                index = 0,
                count = 9,
                title = stringResource(R.string.settings_show_thumbnails),
                description = stringResource(R.string.settings_show_thumbnails_description),
                checked = preferences.globalPresentation.showThumbnails,
                switchTag = "thumbnail_switch",
                rowTag = "thumbnail_setting_row",
                leadingIcon = Icons.Default.Image,
                onCheckedChange = actions.showThumbnailsChange
            )
            HomeRecentCarouselLimit(
                index = 1,
                count = 9,
                value = preferences.homeRecentCarouselLimit,
                onValueChange = actions.homeRecentCarouselLimitChange
            )
            SettingsSwitchRow(
                index = 2,
                count = 9,
                title = stringResource(R.string.settings_show_hidden_files),
                description = stringResource(R.string.settings_show_hidden_files_description),
                checked = preferences.showHiddenFiles,
                switchTag = "hidden_files_switch",
                rowTag = "hidden_files_setting_row",
                leadingIcon = Icons.Default.VisibilityOff,
                onCheckedChange = actions.showHiddenFilesChange
            )
            SettingsSwitchRow(
                index = 3,
                count = 9,
                title = stringResource(R.string.settings_browser_tabs),
                description = stringResource(R.string.settings_browser_tabs_description),
                checked = preferences.browserTabsEnabled,
                switchTag = "browser_tabs_switch",
                rowTag = "browser_tabs_setting_row",
                leadingIcon = Icons.Default.Tab,
                onCheckedChange = { checked ->
                    haptics.toggleMenu()
                    actions.browserTabsEnabledChange(checked)
                }
            )
            SettingsSwitchRow(
                index = 4,
                count = 9,
                title = stringResource(R.string.settings_remember_last_folder),
                description = stringResource(R.string.settings_remember_last_folder_description),
                checked = preferences.rememberLastFolder,
                switchTag = "remember_last_folder_switch",
                rowTag = "remember_last_folder_setting_row",
                leadingIcon = Icons.Default.FolderOpen,
                onCheckedChange = actions.rememberLastFolderChange
            )
            SettingsSwitchRow(
                index = 5,
                count = 9,
                title = stringResource(R.string.settings_expandable_app_bars),
                description = stringResource(R.string.settings_expandable_app_bars_description),
                checked = !preferences.expandableAppBar,
                switchTag = "expandable_app_bars_switch",
                rowTag = "expandable_app_bars_setting_row",
                leadingIcon = Icons.Default.Expand,
                onCheckedChange = { keepCollapsed ->
                    actions.expandableAppBarChange(!keepCollapsed)
                }
            )
            SettingsSwitchRow(
                index = 6,
                count = 9,
                title = stringResource(R.string.settings_browser_scrollbar),
                description = stringResource(R.string.settings_browser_scrollbar_description),
                checked = preferences.browserScrollbarEnabled,
                switchTag = "browser_scrollbar_switch",
                rowTag = "browser_scrollbar_setting_row",
                leadingIcon = Icons.Default.SwapVert,
                onCheckedChange = actions.browserScrollbarEnabledChange
            )
            SettingsSwitchRow(
                index = 7,
                count = 9,
                title = stringResource(R.string.settings_gallery_scrollbar),
                description = stringResource(R.string.settings_gallery_scrollbar_description),
                checked = preferences.galleryScrollbarEnabled,
                switchTag = "gallery_scrollbar_switch",
                rowTag = "gallery_scrollbar_setting_row",
                leadingIcon = Icons.Default.Height,
                onCheckedChange = actions.galleryScrollbarEnabledChange
            )
            SettingsSwitchRow(
                index = 8,
                count = 9,
                title = stringResource(R.string.settings_landscape_dual_pane),
                description = stringResource(R.string.settings_landscape_dual_pane_description),
                checked = theme.landscapeDualPaneEnabled,
                switchTag = "landscape_dual_pane_switch",
                rowTag = "landscape_dual_pane_setting_row",
                leadingIcon = Icons.Default.ViewColumn,
                onCheckedChange = { checked ->
                    haptics.toggleMenu()
                    actions.themeChange(theme.withLandscapeDualPane(checked))
                }
            )
        }
    }
}

@Composable
internal fun SettingsActivityPrivacySection(
    recordingEnabled: Boolean,
    onRecordingChange: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        ArcileSectionHeader(text = stringResource(R.string.section_activity_privacy))
        SettingsSwitchRow(
            title = stringResource(R.string.settings_record_activity),
            description = stringResource(R.string.settings_record_activity_description),
            checked = recordingEnabled,
            switchTag = "record_activity_switch",
            rowTag = "record_activity_setting_row",
            leadingIcon = Icons.Default.History,
            onCheckedChange = onRecordingChange
        )
    }
}
