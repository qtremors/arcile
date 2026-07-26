@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.qtremors.arcile.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShortText
import androidx.compose.material.icons.automirrored.filled.WrapText
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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

@Composable
internal fun SettingsAppearanceSection(
    theme: ThemeState,
    preferences: dev.qtremors.arcile.feature.settings.SettingsPreferences,
    actions: SettingsPreferenceActions
) {
    val haptics = rememberArcileHaptics()
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
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
        Column(
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
        ) {
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
                    title = stringResource(R.string.settings_browser_scrollbar),
                    description = stringResource(R.string.settings_browser_scrollbar_description),
                    checked = preferences.browserScrollbarEnabled,
                    switchTag = "browser_scrollbar_switch",
                    rowTag = "browser_scrollbar_setting_row",
                    leadingIcon = Icons.Default.SwapVert,
                    onCheckedChange = actions.browserScrollbarEnabledChange
                )
                SettingsSwitchRow(
                    index = 4,
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
                    index = 5,
                    count = 9,
                    title = stringResource(R.string.settings_harmonize_colors),
                    description = stringResource(R.string.settings_harmonize_colors_description),
                    checked = theme.harmonizeColors,
                    switchTag = "harmonize_colors_switch",
                    rowTag = "harmonize_colors_setting_row",
                    leadingIcon = Icons.Default.Palette,
                    onCheckedChange = { checked ->
                        haptics.toggleMenu()
                        actions.themeChange(theme.copy(harmonizeColors = checked))
                    }
                )
                SettingsSwitchRow(
                    index = 6,
                    count = 9,
                    title = stringResource(R.string.settings_vibrations),
                    description = stringResource(R.string.settings_vibrations_description),
                    checked = theme.vibrationsEnabled,
                    switchTag = "vibrations_switch",
                    rowTag = "vibrations_setting_row",
                    leadingIcon = Icons.Default.Vibration,
                    onCheckedChange = { checked ->
                        haptics.toggleMenu()
                        actions.themeChange(theme.copy(vibrationsEnabled = checked))
                    }
                )
                SettingsSwitchRow(
                    index = 7,
                    count = 9,
                    title = stringResource(R.string.settings_double_line_filenames),
                    description = stringResource(R.string.settings_double_line_filenames_description),
                    checked = theme.doubleLineFilenames,
                    switchTag = "double_line_filenames_switch",
                    rowTag = "double_line_filenames_setting_row",
                    leadingIcon = Icons.AutoMirrored.Filled.WrapText,
                    onCheckedChange = { checked ->
                        haptics.toggleMenu()
                        actions.themeChange(theme.withDoubleLineFilenames(checked))
                    }
                )
                SettingsSwitchRow(
                    index = 8,
                    count = 9,
                    title = stringResource(R.string.settings_marquee_filenames),
                    description = stringResource(R.string.settings_marquee_filenames_description),
                    checked = theme.marqueeFilenames,
                    switchTag = "marquee_filenames_switch",
                    rowTag = "marquee_filenames_setting_row",
                    leadingIcon = Icons.AutoMirrored.Filled.ShortText,
                    onCheckedChange = { checked ->
                        haptics.toggleMenu()
                        actions.themeChange(theme.withMarqueeFilenames(checked))
                    }
                )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsSwitchRow(
    index: Int = 0,
    count: Int = 1,
    title: String,
    description: String,
    checked: Boolean,
    switchTag: String,
    rowTag: String,
    leadingIcon: ImageVector? = null,
    onCheckedChange: (Boolean) -> Unit
) {
    SegmentedListItem(
        checked = checked,
        onCheckedChange = onCheckedChange,
        shapes = dev.qtremors.arcile.core.ui.theme.expressiveSegmentedShapes(index = index, count = count),
        leadingContent = if (leadingIcon != null) {
            {
                Box(
                    modifier = Modifier.fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        } else null,
        content = { Text(title) },
        supportingContent = { Text(description) },
        trailingContent = {
            Box(
                modifier = Modifier.fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                ExpressiveSwitch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    modifier = Modifier.testTag(switchTag)
                )
            }
        },
        colors = ListItemDefaults.segmentedColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        modifier = Modifier
            .testTag(rowTag)
            .height(IntrinsicSize.Min)
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HomeRecentCarouselLimit(
    index: Int,
    count: Int,
    value: Int,
    onValueChange: (Int) -> Unit
) {
    val haptics = rememberArcileHaptics()
    SegmentedListItem(
        onClick = {},
        shapes = dev.qtremors.arcile.core.ui.theme.expressiveSegmentedShapes(index = index, count = count),
        colors = ListItemDefaults.segmentedColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        modifier = Modifier.testTag("home_recent_carousel_limit_setting"),
        content = {
            Column(modifier = Modifier.fillMaxWidth()) {
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
    )
}
