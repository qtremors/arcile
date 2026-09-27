@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.qtremors.arcile.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
import dev.qtremors.arcile.core.ui.settings.SettingsChoiceHeader
import dev.qtremors.arcile.core.ui.settings.SettingsConnectedChoices
import dev.qtremors.arcile.core.ui.settings.ThemeModeSelector
import dev.qtremors.arcile.core.ui.theme.ThemePreset
import dev.qtremors.arcile.core.ui.theme.ThemeState
import dev.qtremors.arcile.core.ui.theme.titleMediumBold

enum class FilenameDisplayMode {
    SINGLE_LINE,
    TWO_LINES,
    AUTO_SCROLL
}

internal val ThemeState.filenameDisplayMode: FilenameDisplayMode
    get() = when {
        doubleLineFilenames -> FilenameDisplayMode.TWO_LINES
        marqueeFilenames -> FilenameDisplayMode.AUTO_SCROLL
        else -> FilenameDisplayMode.SINGLE_LINE
    }

internal fun ThemeState.withFilenameDisplayMode(mode: FilenameDisplayMode): ThemeState = when (mode) {
    FilenameDisplayMode.SINGLE_LINE -> copy(doubleLineFilenames = false, marqueeFilenames = false)
    FilenameDisplayMode.TWO_LINES -> copy(doubleLineFilenames = true, marqueeFilenames = false)
    FilenameDisplayMode.AUTO_SCROLL -> copy(marqueeFilenames = true, doubleLineFilenames = false)
}

@Composable
internal fun FilenameDisplaySelector(
    currentMode: FilenameDisplayMode,
    onModeSelected: (FilenameDisplayMode) -> Unit
) {
    val haptics = rememberArcileHaptics()
    val labels = mapOf(
        FilenameDisplayMode.SINGLE_LINE to stringResource(R.string.filename_display_single),
        FilenameDisplayMode.TWO_LINES to stringResource(R.string.filename_display_double),
        FilenameDisplayMode.AUTO_SCROLL to stringResource(R.string.filename_display_scroll)
    )
    val modes = FilenameDisplayMode.entries
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
        SettingsChoiceHeader(
            title = stringResource(R.string.settings_filename_display),
            description = stringResource(R.string.settings_filename_display_description),
            icon = Icons.Default.TextFields
        )
        SettingsConnectedChoices(
            options = modes.map(labels::getValue),
            isSelected = { modes[it] == currentMode },
            onSelectionChanged = { index, checked ->
                if (checked) {
                    haptics.toggleMenu()
                    onModeSelected(modes[index])
                }
            },
            dynamicExpand = true,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)
        )
    }
}

@Composable
internal fun SettingsAppearanceSection(
    theme: ThemeState,
    preferences: dev.qtremors.arcile.feature.settings.SettingsPreferences,
    actions: SettingsPreferenceActions,
    showHeading: Boolean = true
) {
    val haptics = rememberArcileHaptics()
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        if (showHeading) ArcileSectionHeader(text = stringResource(R.string.section_appearance))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ArcileSectionHeader(text = stringResource(R.string.settings_appearance_theme_section))
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
            SettingsSwitchRow(
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
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ArcileSectionHeader(text = stringResource(R.string.settings_appearance_names_section))
            ArcileListSurface {
                FilenameDisplaySelector(
                    currentMode = theme.filenameDisplayMode,
                    onModeSelected = { actions.themeChange(theme.withFilenameDisplayMode(it)) }
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ArcileSectionHeader(text = stringResource(R.string.settings_appearance_feedback_section))
            SettingsSwitchRow(
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
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsSwitchRow(
    index: Int = 0,
    count: Int = 1,
    title: String,
    description: String? = null,
    checked: Boolean,
    switchTag: String,
    rowTag: String,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    SegmentedListItem(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
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
        supportingContent = if (!description.isNullOrBlank()) { { Text(description) } } else null,
        trailingContent = {
            Box(
                modifier = Modifier.fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                ExpressiveSwitch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    enabled = enabled,
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

internal fun ThemeState.withLandscapeDualPane(enabled: Boolean): ThemeState = copy(
    landscapeDualPaneEnabled = enabled
)

internal fun ThemeState.withFolderIcons(enabled: Boolean): ThemeState = copy(
    folderIconsEnabled = enabled
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun HomeRecentCarouselLimit(
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
                        androidx.compose.ui.res.pluralStringResource(R.plurals.settings_home_recent_carousel_count, value, value)
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
