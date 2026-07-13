package dev.qtremors.arcile.feature.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.storage.domain.BrowserPreferences
import dev.qtremors.arcile.core.ui.ArcileListSurface
import dev.qtremors.arcile.core.ui.ArcileSectionHeader
import dev.qtremors.arcile.core.ui.ExpressiveSwitch
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.rememberArcileHaptics
import dev.qtremors.arcile.core.ui.settings.AccentColorSelector
import dev.qtremors.arcile.core.ui.settings.ThemeModeSelector
import dev.qtremors.arcile.core.ui.theme.ThemePreset
import dev.qtremors.arcile.core.ui.theme.ThemeState
import dev.qtremors.arcile.core.ui.theme.bounceClickable

@Composable
internal fun SettingsAppearanceSection(
    theme: ThemeState,
    preferences: dev.qtremors.arcile.feature.settings.SettingsPreferences,
    actions: SettingsPreferenceActions
) {
    val haptics = rememberArcileHaptics()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ArcileSectionHeader(text = stringResource(R.string.section_appearance))
        ArcileListSurface {
            ThemeModeSelector(
                currentMode = theme.themeMode,
                onModeSelected = { actions.themeChange(theme.copy(themeMode = it)) }
            )
        }
        ArcileListSurface {
            ThemePresetSelector(
                currentPreset = theme.themePreset,
                onPresetSelected = { actions.themeChange(theme.copy(themePreset = it)) }
            )
        }
        if (theme.themePreset == ThemePreset.CUSTOM) {
            ArcileListSurface {
                CustomThemeCreatorPanel(
                    themeState = theme,
                    onThemeChange = actions.themeChange
                )
            }
        }
        if (theme.themePreset == ThemePreset.NONE) {
            ArcileListSurface {
                AccentColorSelector(
                    currentAccent = theme.accentColor,
                    onAccentSelected = { actions.themeChange(theme.copy(accentColor = it)) }
                )
            }
        }
        ArcileListSurface {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_show_thumbnails),
                    description = stringResource(R.string.settings_show_thumbnails_description),
                    checked = preferences.globalPresentation.showThumbnails,
                    switchTag = "thumbnail_switch",
                    rowTag = "thumbnail_setting_row",
                    onCheckedChange = actions.showThumbnailsChange
                )
                SettingsDivider()
                HomeRecentCarouselLimit(
                    value = preferences.homeRecentCarouselLimit,
                    onValueChange = actions.homeRecentCarouselLimitChange
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_show_hidden_files),
                    description = stringResource(R.string.settings_show_hidden_files_description),
                    checked = preferences.showHiddenFiles,
                    switchTag = "hidden_files_switch",
                    rowTag = "hidden_files_setting_row",
                    onCheckedChange = actions.showHiddenFilesChange
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_browser_scrollbar),
                    description = stringResource(R.string.settings_browser_scrollbar_description),
                    checked = preferences.browserScrollbarEnabled,
                    switchTag = "browser_scrollbar_switch",
                    rowTag = "browser_scrollbar_setting_row",
                    onCheckedChange = actions.browserScrollbarEnabledChange
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_gallery_scrollbar),
                    description = stringResource(R.string.settings_gallery_scrollbar_description),
                    checked = preferences.galleryScrollbarEnabled,
                    switchTag = "gallery_scrollbar_switch",
                    rowTag = "gallery_scrollbar_setting_row",
                    onCheckedChange = actions.galleryScrollbarEnabledChange
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_harmonize_colors),
                    description = stringResource(R.string.settings_harmonize_colors_description),
                    checked = theme.harmonizeColors,
                    switchTag = "harmonize_colors_switch",
                    rowTag = "harmonize_colors_setting_row",
                    onCheckedChange = { checked ->
                        haptics.toggleMenu()
                        actions.themeChange(theme.copy(harmonizeColors = checked))
                    }
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_vibrations),
                    description = stringResource(R.string.settings_vibrations_description),
                    checked = theme.vibrationsEnabled,
                    switchTag = "vibrations_switch",
                    rowTag = "vibrations_setting_row",
                    onCheckedChange = { checked ->
                        haptics.toggleMenu()
                        actions.themeChange(theme.copy(vibrationsEnabled = checked))
                    }
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_double_line_filenames),
                    description = stringResource(R.string.settings_double_line_filenames_description),
                    checked = theme.doubleLineFilenames,
                    switchTag = "double_line_filenames_switch",
                    rowTag = "double_line_filenames_setting_row",
                    onCheckedChange = { checked ->
                        haptics.toggleMenu()
                        actions.themeChange(theme.withDoubleLineFilenames(checked))
                    }
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_marquee_filenames),
                    description = stringResource(R.string.settings_marquee_filenames_description),
                    checked = theme.marqueeFilenames,
                    switchTag = "marquee_filenames_switch",
                    rowTag = "marquee_filenames_setting_row",
                    onCheckedChange = { checked ->
                        haptics.toggleMenu()
                        actions.themeChange(theme.withMarqueeFilenames(checked))
                    }
                )
            }
        }
    }
}

@Composable
internal fun SettingsSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    switchTag: String,
    rowTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        trailingContent = {
            ExpressiveSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.testTag(switchTag)
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .bounceClickable { onCheckedChange(!checked) }
            .testTag(rowTag)
    )
}

internal fun ThemeState.withDoubleLineFilenames(enabled: Boolean): ThemeState = copy(
    doubleLineFilenames = enabled,
    marqueeFilenames = if (enabled) false else marqueeFilenames
)

internal fun ThemeState.withMarqueeFilenames(enabled: Boolean): ThemeState = copy(
    marqueeFilenames = enabled,
    doubleLineFilenames = if (enabled) false else doubleLineFilenames
)

@Composable
private fun HomeRecentCarouselLimit(
    value: Int,
    onValueChange: (Int) -> Unit
) {
    val haptics = rememberArcileHaptics()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("home_recent_carousel_limit_setting")
    ) {
        Text(stringResource(R.string.settings_home_recent_carousel_limit))
        Text(
            text = if (value == 0) {
                stringResource(R.string.settings_home_recent_carousel_hidden)
            } else {
                stringResource(R.string.settings_home_recent_carousel_count, value)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { changed ->
                val rounded = changed.toInt()
                if (rounded != value) {
                    haptics.selectionChanged()
                    onValueChange(rounded)
                }
            },
            valueRange = BrowserPreferences.MIN_HOME_RECENT_CAROUSEL_LIMIT.toFloat()..
                BrowserPreferences.MAX_HOME_RECENT_CAROUSEL_LIMIT.toFloat(),
            steps = BrowserPreferences.MAX_HOME_RECENT_CAROUSEL_LIMIT - 1,
            modifier = Modifier.testTag("home_recent_carousel_limit_slider")
        )
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    )
}
