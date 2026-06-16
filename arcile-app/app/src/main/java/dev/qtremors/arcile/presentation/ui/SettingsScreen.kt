package dev.qtremors.arcile.presentation.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
import dev.qtremors.arcile.ui.theme.ThemePreset
import dev.qtremors.arcile.ui.theme.titleMediumBold
import dev.qtremors.arcile.ui.theme.spacing
import dev.qtremors.arcile.shared.ui.rememberArcileHaptics
import dev.qtremors.arcile.presentation.utils.ExternalFileAccessHelper
import dev.qtremors.arcile.shared.ui.settings.ThemeModeSelector
import dev.qtremors.arcile.shared.ui.settings.AccentColorSelector
import dev.qtremors.arcile.shared.ui.settings.SettingsSection
import dev.qtremors.arcile.shared.ui.ArcileScreenScaffold
import dev.qtremors.arcile.shared.ui.ArcileSectionHeader
import dev.qtremors.arcile.shared.ui.ArcileListSurface
import dev.qtremors.arcile.shared.ui.keyboardInputField

import androidx.compose.ui.res.stringResource
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.storage.domain.BrowserPreferences
import dev.qtremors.arcile.backup.PreferencesBackupItem
import dev.qtremors.arcile.backup.PreferencesBackupItemStatus
import dev.qtremors.arcile.backup.PreferencesBackupOperationResult

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
    homeRecentCarouselLimit: Int,
    showHiddenFiles: Boolean,
    onShowThumbnailsChange: (Boolean) -> Unit,
    onHomeRecentCarouselLimitChange: (Int) -> Unit,
    onShowHiddenFilesChange: (Boolean) -> Unit,
    onNavigateBack: () -> Unit,
    onThemeChange: (ThemeState) -> Unit,
    onOpenStorageManagement: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
    onRestartApp: () -> Unit = {},
    backupState: PreferencesBackupUiState = PreferencesBackupUiState.Idle,
    onExportSettingsBackup: (android.net.Uri) -> Unit = {},
    onRestoreSettingsBackup: (android.net.Uri) -> Unit = {},
    onApplySettingsRestore: (android.net.Uri) -> Unit = {},
    onClearBackupState: () -> Unit = {}
) {
    val haptics = rememberArcileHaptics()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var externalAccessCacheStats by remember {
        mutableStateOf(ExternalFileAccessHelper.StagingCacheStats(fileCount = 0, sizeBytes = 0L))
    }
    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) onExportSettingsBackup(uri)
    }
    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onRestoreSettingsBackup(uri)
    }

    LaunchedEffect(context) {
        externalAccessCacheStats = withContext(Dispatchers.IO) {
            ExternalFileAccessHelper.getStagingCacheStats(context)
        }
    }

    when (val state = backupState) {
        PreferencesBackupUiState.Idle,
        PreferencesBackupUiState.Busy -> Unit
        is PreferencesBackupUiState.RestorePreview -> {
            AlertDialog(
                onDismissRequest = onClearBackupState,
                title = { Text(stringResource(R.string.settings_backup_restore_preview_title)) },
                text = {
                    BackupItemList(
                        description = stringResource(R.string.settings_backup_restore_preview_description),
                        items = state.preview.items
                    )
                },
                confirmButton = {
                    TextButton(onClick = { onApplySettingsRestore(state.uri) }) {
                        Text(stringResource(R.string.settings_backup_restore))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onClearBackupState) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
        is PreferencesBackupUiState.Exported -> {
            AlertDialog(
                onDismissRequest = onClearBackupState,
                title = { Text(stringResource(R.string.settings_backup_export_complete_title)) },
                text = {
                    BackupResultList(
                        description = stringResource(
                            R.string.settings_backup_export_complete_description,
                            state.result.successCount
                        ),
                        result = state.result
                    )
                },
                confirmButton = {
                    TextButton(onClick = onClearBackupState) {
                        Text(stringResource(R.string.ok))
                    }
                }
            )
        }
        is PreferencesBackupUiState.Restored -> {
            AlertDialog(
                onDismissRequest = onClearBackupState,
                title = { Text(stringResource(R.string.settings_backup_restore_complete_title)) },
                text = {
                    BackupResultList(
                        description = stringResource(
                            R.string.settings_backup_restore_complete_description,
                            state.result.successCount
                        ),
                        result = state.result
                    )
                },
                confirmButton = {
                    TextButton(onClick = onRestartApp) {
                        Text(stringResource(R.string.restart_now))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onClearBackupState) {
                        Text(stringResource(R.string.later))
                    }
                }
            )
        }
        is PreferencesBackupUiState.Failed -> {
            AlertDialog(
                onDismissRequest = onClearBackupState,
                title = { Text(stringResource(R.string.settings_backup_failed_title)) },
                text = { Text(state.message) },
                confirmButton = {
                    TextButton(onClick = onClearBackupState) {
                        Text(stringResource(R.string.ok))
                    }
                }
            )
        }
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
                        ThemePresetSelector(
                            currentPreset = currentThemeState.themePreset,
                            onPresetSelected = {
                                onThemeChange(currentThemeState.copy(themePreset = it))
                            }
                        )
                    }
                    if (currentThemeState.themePreset == ThemePreset.CUSTOM) {
                        ArcileListSurface {
                            CustomThemeCreatorPanel(
                                themeState = currentThemeState,
                                onThemeChange = onThemeChange
                            )
                        }
                    }
                    if (currentThemeState.themePreset == ThemePreset.NONE) {
                        ArcileListSurface {
                            AccentColorSelector(
                                currentAccent = currentThemeState.accentColor,
                                onAccentSelected = {
                                    onThemeChange(currentThemeState.copy(accentColor = it))
                                }
                            )
                        }
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
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                                    .testTag("home_recent_carousel_limit_setting")
                            ) {
                                Text(stringResource(R.string.settings_home_recent_carousel_limit))
                                Text(
                                    text = if (homeRecentCarouselLimit == 0) {
                                        stringResource(R.string.settings_home_recent_carousel_hidden)
                                    } else {
                                        stringResource(R.string.settings_home_recent_carousel_count, homeRecentCarouselLimit)
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Slider(
                                    value = homeRecentCarouselLimit.toFloat(),
                                    onValueChange = { value -> onHomeRecentCarouselLimitChange(value.toInt()) },
                                    valueRange = BrowserPreferences.MIN_HOME_RECENT_CAROUSEL_LIMIT.toFloat()..
                                        BrowserPreferences.MAX_HOME_RECENT_CAROUSEL_LIMIT.toFloat(),
                                    steps = BrowserPreferences.MAX_HOME_RECENT_CAROUSEL_LIMIT - 1,
                                    modifier = Modifier.testTag("home_recent_carousel_limit_slider")
                                )
                            }
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.settings_show_hidden_files)) },
                                supportingContent = { Text(stringResource(R.string.settings_show_hidden_files_description)) },
                                trailingContent = {
                                    Switch(
                                        checked = showHiddenFiles,
                                        onCheckedChange = onShowHiddenFilesChange,
                                        thumbContent = {
                                            Icon(
                                                imageVector = if (showHiddenFiles) Icons.Default.Check else Icons.Default.Close,
                                                contentDescription = null,
                                                modifier = Modifier.size(SwitchDefaults.IconSize)
                                            )
                                        },
                                        modifier = Modifier.testTag("hidden_files_switch")
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.medium)
                                    .clickable { onShowHiddenFilesChange(!showHiddenFiles) }
                                    .testTag("hidden_files_setting_row")
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
                        headlineContent = { Text(stringResource(R.string.settings_backup_export)) },
                        supportingContent = { Text(stringResource(R.string.settings_backup_export_description)) },
                        leadingContent = { Icon(Icons.Default.FileUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = {
                            if (backupState == PreferencesBackupUiState.Busy) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clip(MaterialTheme.shapes.medium).clickable(
                            enabled = backupState != PreferencesBackupUiState.Busy
                        ) {
                            exportBackupLauncher.launch("arcile-settings-backup.json")
                        }
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_backup_restore)) },
                        supportingContent = { Text(stringResource(R.string.settings_backup_restore_description)) },
                        leadingContent = { Icon(Icons.Default.FileDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clip(MaterialTheme.shapes.medium).clickable(
                            enabled = backupState != PreferencesBackupUiState.Busy
                        ) {
                            restoreBackupLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                        }
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

@Composable
fun ThemePresetSelector(
    currentPreset: ThemePreset,
    onPresetSelected: (ThemePreset) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(
            text = "Theme Preset",
            style = MaterialTheme.typography.titleMediumBold,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ThemePreset.values().forEach { preset ->
                val isSelected = currentPreset == preset
                val label = when (preset) {
                    ThemePreset.NONE -> stringResource(R.string.theme_preset_none)
                    ThemePreset.DRACULA -> stringResource(R.string.theme_preset_dracula)
                    ThemePreset.TOKYO_NIGHT -> stringResource(R.string.theme_preset_tokyo_night)
                    ThemePreset.CUSTOM -> stringResource(R.string.theme_preset_custom)
                }

                val colors = if (isSelected) {
                    ButtonDefaults.filledTonalButtonColors()
                } else {
                    ButtonDefaults.outlinedButtonColors()
                }

                val border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)

                OutlinedButton(
                    onClick = { onPresetSelected(preset) },
                    colors = colors,
                    border = border,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Text(text = label, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
fun CustomThemeCreatorPanel(
    themeState: ThemeState,
    onThemeChange: (ThemeState) -> Unit
) {
    var primaryInput by remember(themeState.customPrimaryColorHex) {
        mutableStateOf(themeState.customPrimaryColorHex)
    }
    var bgInput by remember(themeState.customBackgroundColorHex) {
        mutableStateOf(themeState.customBackgroundColorHex)
    }

    val primaryParsed = remember(primaryInput) {
        try { Color(android.graphics.Color.parseColor(primaryInput)) } catch (e: Exception) { null }
    }
    val bgParsed = remember(bgInput) {
        try { Color(android.graphics.Color.parseColor(bgInput)) } catch (e: Exception) { null }
    }

    val colorsTooSimilar = remember(primaryParsed, bgParsed) {
        if (primaryParsed != null && bgParsed != null) {
            val bgLuminance = bgParsed.red * 0.299f + bgParsed.green * 0.587f + bgParsed.blue * 0.114f
            val priLuminance = primaryParsed.red * 0.299f + primaryParsed.green * 0.587f + primaryParsed.blue * 0.114f
            kotlin.math.abs(bgLuminance - priLuminance) < 0.25f
        } else false
    }

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            text = "Custom Theme Settings",
            style = MaterialTheme.typography.titleMediumBold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        OutlinedTextField(
            value = primaryInput,
            onValueChange = { input ->
                primaryInput = input
                if (input.startsWith("#") && (input.length == 7 || input.length == 9)) {
                    try {
                        android.graphics.Color.parseColor(input)
                        onThemeChange(themeState.copy(customPrimaryColorHex = input))
                    } catch (e: Exception) { }
                }
            },
            label = { Text(stringResource(R.string.custom_theme_primary_label)) },
            trailingIcon = {
                primaryParsed?.let {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(it)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .keyboardInputField()
        )

        OutlinedTextField(
            value = bgInput,
            onValueChange = { input ->
                bgInput = input
                if (input.startsWith("#") && (input.length == 7 || input.length == 9)) {
                    try {
                        android.graphics.Color.parseColor(input)
                        onThemeChange(themeState.copy(customBackgroundColorHex = input))
                    } catch (e: Exception) { }
                }
            },
            label = { Text(stringResource(R.string.custom_theme_bg_label)) },
            trailingIcon = {
                bgParsed?.let {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(it)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .keyboardInputField()
        )

        if (colorsTooSimilar) {
            Text(
                text = stringResource(R.string.custom_theme_similarity_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
    }
}

@Composable
private fun BackupItemList(
    description: String,
    items: List<PreferencesBackupItem>
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(description, style = MaterialTheme.typography.bodyMedium)
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState())
        ) {
            items.forEach { item ->
                BackupItemRow(item)
            }
        }
    }
}

@Composable
private fun BackupResultList(
    description: String,
    result: PreferencesBackupOperationResult
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(description, style = MaterialTheme.typography.bodyMedium)
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState())
        ) {
            result.items.forEach { item ->
                BackupItemRow(item)
            }
            result.failures.forEach { failure ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Text(failure.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onErrorContainer)
                        Text(failure.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }
    }
}

@Composable
private fun BackupItemRow(item: PreferencesBackupItem) {
    val label = when (item.status) {
        PreferencesBackupItemStatus.Exported -> stringResource(R.string.settings_backup_status_exported)
        PreferencesBackupItemStatus.WillRestore -> stringResource(R.string.settings_backup_status_will_restore)
        PreferencesBackupItemStatus.WillReset -> stringResource(R.string.settings_backup_status_will_reset)
        PreferencesBackupItemStatus.Restored -> stringResource(R.string.settings_backup_status_restored)
        PreferencesBackupItemStatus.Reset -> stringResource(R.string.settings_backup_status_reset)
    }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(item.label, style = MaterialTheme.typography.bodyMedium)
            AssistChip(onClick = {}, label = { Text(label) })
        }
    }
}
