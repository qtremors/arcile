package dev.qtremors.arcile.presentation.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dev.qtremors.arcile.ui.theme.ThemeState
import dev.qtremors.arcile.ui.theme.spacing
import dev.qtremors.arcile.shared.ui.rememberArcileHaptics
import dev.qtremors.arcile.presentation.utils.ExternalFileAccessHelper
import dev.qtremors.arcile.shared.ui.settings.ThemeModeSelector
import dev.qtremors.arcile.shared.ui.settings.AccentColorSelector
import dev.qtremors.arcile.shared.ui.settings.SettingsSection
import dev.qtremors.arcile.shared.ui.ArcileScreenScaffold
import dev.qtremors.arcile.shared.ui.ArcileSectionHeader
import dev.qtremors.arcile.shared.ui.ArcileListSurface

import androidx.compose.ui.res.stringResource
import dev.qtremors.arcile.R

/**
 * Settings screen for theme and appearance preferences.
 *
 * Displays a theme mode selector (System / Light / Dark / OLED) and an accent color picker.
 * Theme state is persisted via [dev.qtremors.arcile.ui.theme.ThemePreferences] (DataStore).
 * Also includes a link to the About page with app version, developer, privacy policy, and changelogs.
 *
 * @param currentThemeState Current [ThemeState] reflecting the active theme mode and accent color.
 * @param onNavigateBack Called when the user navigates back.
 * @param onThemeChange Called with the updated [ThemeState] whenever the user changes a setting.
 * @param onNavigateToAbout Called when the user wants to navigate to the About page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentThemeState: ThemeState,
    showThumbnails: Boolean,
    onShowThumbnailsChange: (Boolean) -> Unit,
    onNavigateBack: () -> Unit,
    onThemeChange: (ThemeState) -> Unit,
    onOpenStorageManagement: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
    onRunOnboardingAgain: suspend () -> Unit = {},
    onRestartApp: () -> Unit = {}
) {
    val haptics = rememberArcileHaptics()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var externalAccessCacheStats by remember {
        mutableStateOf(ExternalFileAccessHelper.StagingCacheStats(fileCount = 0, sizeBytes = 0L))
    }
    var showResetOnboardingDialog by remember { mutableStateOf(false) }
    var showRestartDialog by remember { mutableStateOf(false) }

    LaunchedEffect(context) {
        externalAccessCacheStats = withContext(Dispatchers.IO) {
            ExternalFileAccessHelper.getStagingCacheStats(context)
        }
    }

    if (showResetOnboardingDialog) {
        AlertDialog(
            onDismissRequest = { showResetOnboardingDialog = false },
            title = { Text(stringResource(R.string.restart_onboarding_title)) },
            text = { Text(stringResource(R.string.restart_onboarding_description)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            onRunOnboardingAgain()
                            showResetOnboardingDialog = false
                            showRestartDialog = true
                        }
                    }
                ) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetOnboardingDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showRestartDialog) {
        AlertDialog(
            onDismissRequest = { showRestartDialog = false },
            title = { Text(stringResource(R.string.restart_onboarding_title)) },
            text = { Text(stringResource(R.string.run_onboarding_again_description)) },
            confirmButton = {
                TextButton(onClick = onRestartApp) {
                    Text(stringResource(R.string.restart_now))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestartDialog = false }) {
                    Text(stringResource(R.string.later))
                }
            }
        )
    }

    ArcileScreenScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
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
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + MaterialTheme.spacing.screenGutter
            ),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ArcileSectionHeader(text = stringResource(R.string.section_appearance))
                    ArcileListSurface {
                        ThemeModeSelector(
                            currentMode = currentThemeState.themeMode,
                            onModeSelected = {
                                onThemeChange(currentThemeState.copy(themeMode = it))
                            }
                        )
                    }
                    ArcileListSurface {
                        AccentColorSelector(
                            currentAccent = currentThemeState.accentColor,
                            onAccentSelected = {
                                onThemeChange(currentThemeState.copy(accentColor = it))
                            }
                        )
                    }
                    ArcileListSurface {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.settings_show_thumbnails)) },
                                supportingContent = { Text(stringResource(R.string.settings_show_thumbnails_description)) },
                                trailingContent = {
                                    Switch(
                                        checked = showThumbnails,
                                        onCheckedChange = onShowThumbnailsChange,
                                        thumbContent = {
                                            Icon(
                                                imageVector = if (showThumbnails) Icons.Default.Check else Icons.Default.Close,
                                                contentDescription = null,
                                                modifier = Modifier.size(SwitchDefaults.IconSize)
                                            )
                                        },
                                        modifier = Modifier.testTag("thumbnail_switch")
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.medium)
                                    .clickable { onShowThumbnailsChange(!showThumbnails) }
                                    .testTag("thumbnail_setting_row")
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.settings_harmonize_colors)) },
                                supportingContent = { Text(stringResource(R.string.settings_harmonize_colors_description)) },
                                trailingContent = {
                                    Switch(
                                        checked = currentThemeState.harmonizeColors,
                                        onCheckedChange = { isChecked ->
                                            haptics.toggleMenu()
                                            onThemeChange(currentThemeState.copy(harmonizeColors = isChecked))
                                        },
                                        thumbContent = {
                                            Icon(
                                                imageVector = if (currentThemeState.harmonizeColors) Icons.Default.Check else Icons.Default.Close,
                                                contentDescription = null,
                                                modifier = Modifier.size(SwitchDefaults.IconSize)
                                            )
                                        },
                                        modifier = Modifier.testTag("harmonize_colors_switch")
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.medium)
                                    .clickable {
                                        haptics.toggleMenu()
                                        onThemeChange(currentThemeState.copy(harmonizeColors = !currentThemeState.harmonizeColors))
                                    }
                                    .testTag("harmonize_colors_setting_row")
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.settings_vibrations)) },
                                supportingContent = { Text(stringResource(R.string.settings_vibrations_description)) },
                                trailingContent = {
                                    Switch(
                                        checked = currentThemeState.vibrationsEnabled,
                                        onCheckedChange = { isChecked ->
                                            haptics.toggleMenu()
                                            onThemeChange(currentThemeState.copy(vibrationsEnabled = isChecked))
                                        },
                                        thumbContent = {
                                            Icon(
                                                imageVector = if (currentThemeState.vibrationsEnabled) Icons.Default.Check else Icons.Default.Close,
                                                contentDescription = null,
                                                modifier = Modifier.size(SwitchDefaults.IconSize)
                                            )
                                        },
                                        modifier = Modifier.testTag("vibrations_switch")
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.medium)
                                    .clickable {
                                        haptics.toggleMenu()
                                        onThemeChange(currentThemeState.copy(vibrationsEnabled = !currentThemeState.vibrationsEnabled))
                                    }
                                    .testTag("vibrations_setting_row")
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.settings_double_line_filenames)) },
                                supportingContent = { Text(stringResource(R.string.settings_double_line_filenames_description)) },
                                trailingContent = {
                                    Switch(
                                        checked = currentThemeState.doubleLineFilenames,
                                        onCheckedChange = { isChecked ->
                                            haptics.toggleMenu()
                                            val newMarquee = if (isChecked) false else currentThemeState.marqueeFilenames
                                            onThemeChange(
                                                currentThemeState.copy(
                                                    doubleLineFilenames = isChecked,
                                                    marqueeFilenames = newMarquee
                                                )
                                            )
                                        },
                                        thumbContent = {
                                            Icon(
                                                imageVector = if (currentThemeState.doubleLineFilenames) Icons.Default.Check else Icons.Default.Close,
                                                contentDescription = null,
                                                modifier = Modifier.size(SwitchDefaults.IconSize)
                                            )
                                        },
                                        modifier = Modifier.testTag("double_line_filenames_switch")
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.medium)
                                    .clickable {
                                        haptics.toggleMenu()
                                        val isChecked = !currentThemeState.doubleLineFilenames
                                        val newMarquee = if (isChecked) false else currentThemeState.marqueeFilenames
                                        onThemeChange(
                                            currentThemeState.copy(
                                                doubleLineFilenames = isChecked,
                                                marqueeFilenames = newMarquee
                                            )
                                        )
                                    }
                                    .testTag("double_line_filenames_setting_row")
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.settings_marquee_filenames)) },
                                supportingContent = { Text(stringResource(R.string.settings_marquee_filenames_description)) },
                                trailingContent = {
                                    Switch(
                                        checked = currentThemeState.marqueeFilenames,
                                        onCheckedChange = { isChecked ->
                                            haptics.toggleMenu()
                                            val newDouble = if (isChecked) false else currentThemeState.doubleLineFilenames
                                            onThemeChange(
                                                currentThemeState.copy(
                                                    marqueeFilenames = isChecked,
                                                    doubleLineFilenames = newDouble
                                                )
                                            )
                                        },
                                        thumbContent = {
                                            Icon(
                                                imageVector = if (currentThemeState.marqueeFilenames) Icons.Default.Check else Icons.Default.Close,
                                                contentDescription = null,
                                                modifier = Modifier.size(SwitchDefaults.IconSize)
                                            )
                                        },
                                        modifier = Modifier.testTag("marquee_filenames_switch")
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.medium)
                                    .clickable {
                                        haptics.toggleMenu()
                                        val isChecked = !currentThemeState.marqueeFilenames
                                        val newDouble = if (isChecked) false else currentThemeState.doubleLineFilenames
                                        onThemeChange(
                                            currentThemeState.copy(
                                                marqueeFilenames = isChecked,
                                                doubleLineFilenames = newDouble
                                            )
                                        )
                                    }
                                    .testTag("marquee_filenames_setting_row")
                            )
                        }
                    }
                }
            }

            item {
                SettingsSection(title = stringResource(R.string.section_storage)) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.manage_classification)) },
                        supportingContent = { Text(stringResource(R.string.manage_classification_description)) },
                        leadingContent = { Icon(Icons.Default.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clip(MaterialTheme.shapes.medium).clickable(onClick = onOpenStorageManagement)
                    )
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.clear_external_access_cache)) },
                        supportingContent = {
                            Text(
                                stringResource(
                                    R.string.clear_external_access_cache_description,
                                    externalAccessCacheStats.fileCount
                                )
                            )
                        },
                        leadingContent = { Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clip(MaterialTheme.shapes.medium).clickable {
                            coroutineScope.launch {
                                externalAccessCacheStats = withContext(Dispatchers.IO) {
                                    ExternalFileAccessHelper.clearStagingArea(context)
                                }
                            }
                        }
                    )
                }
            }

            item {
                SettingsSection(title = stringResource(R.string.section_setup)) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.run_onboarding_again)) },
                        supportingContent = { Text(stringResource(R.string.run_onboarding_again_description)) },
                        leadingContent = { Icon(Icons.Default.RestartAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clip(MaterialTheme.shapes.medium).clickable { showResetOnboardingDialog = true }
                    )
                }
            }

            item {
                SettingsSection(title = stringResource(R.string.section_info)) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.about_headline)) },
                        supportingContent = { Text(stringResource(R.string.about_description)) },
                        leadingContent = { Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.padding(horizontal = 4.dp).clip(MaterialTheme.shapes.medium).clickable(onClick = onNavigateToAbout)
                    )
                }
            }
        }
    }
}
