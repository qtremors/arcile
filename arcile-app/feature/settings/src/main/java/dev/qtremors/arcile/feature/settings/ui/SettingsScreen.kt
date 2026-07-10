package dev.qtremors.arcile.feature.settings.ui
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
import androidx.compose.material.icons.filled.Extension
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
import dev.qtremors.arcile.core.ui.theme.ThemeState
import dev.qtremors.arcile.core.ui.theme.ThemePreset
import dev.qtremors.arcile.core.ui.theme.titleMediumBold
import dev.qtremors.arcile.core.ui.theme.spacing
import dev.qtremors.arcile.core.ui.rememberArcileHaptics
import dev.qtremors.arcile.core.ui.theme.ExpressiveShapes
import dev.qtremors.arcile.core.ui.theme.bounceClickable
import dev.qtremors.arcile.core.ui.externalfile.ExternalFileAccessHelper
import dev.qtremors.arcile.core.ui.settings.ThemeModeSelector
import dev.qtremors.arcile.core.ui.settings.AccentColorSelector
import dev.qtremors.arcile.core.ui.settings.SettingsSection
import dev.qtremors.arcile.core.ui.ArcileScreenScaffold
import dev.qtremors.arcile.core.ui.ArcileSectionHeader
import dev.qtremors.arcile.core.ui.ArcileListSurface
import dev.qtremors.arcile.core.ui.ExpressiveSwitch
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import dev.qtremors.arcile.core.ui.keyboardInputField
import androidx.compose.ui.res.stringResource
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.asString
import dev.qtremors.arcile.core.storage.domain.BrowserPreferences
import dev.qtremors.arcile.core.ui.backup.PreferencesBackupItem
import dev.qtremors.arcile.core.ui.backup.PreferencesBackupItemStatus
import dev.qtremors.arcile.core.ui.backup.PreferencesBackupOperationResult
import dev.qtremors.arcile.feature.settings.PreferencesBackupUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    state: SettingsScreenState,
    navigationActions: SettingsNavigationActions,
    preferenceActions: SettingsPreferenceActions,
    backupActions: SettingsBackupActions
) {
    val currentThemeState = state.theme
    val showThumbnails = state.preferences.globalPresentation.showThumbnails
    val homeRecentCarouselLimit = state.preferences.homeRecentCarouselLimit
    val showHiddenFiles = state.preferences.showHiddenFiles
    val browserScrollbarEnabled = state.preferences.browserScrollbarEnabled
    val galleryScrollbarEnabled = state.preferences.galleryScrollbarEnabled
    val backupState = state.backup
    val onNavigateBack = navigationActions.navigateBack
    val onOpenStorageManagement = navigationActions.openStorageManagement
    val onNavigateToPlugins = navigationActions.navigateToPlugins
    val onNavigateToAbout = navigationActions.navigateToAbout
    val onRestartApp = navigationActions.restartApp
    val onThemeChange = preferenceActions.themeChange
    val onShowThumbnailsChange = preferenceActions.showThumbnailsChange
    val onHomeRecentCarouselLimitChange = preferenceActions.homeRecentCarouselLimitChange
    val onShowHiddenFilesChange = preferenceActions.showHiddenFilesChange
    val onBrowserScrollbarEnabledChange = preferenceActions.browserScrollbarEnabledChange
    val onGalleryScrollbarEnabledChange = preferenceActions.galleryScrollbarEnabledChange
    val onExportSettingsBackup = backupActions.export
    val onRestoreSettingsBackup = backupActions.previewRestore
    val onApplySettingsRestore = backupActions.applyRestore
    val onClearBackupState = backupActions.clearState
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

    SettingsBackupDialogs(
        state = backupState,
        onApplyRestore = onApplySettingsRestore,
        onClear = onClearBackupState,
        onRestart = onRestartApp
    )

    ArcileScreenScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .clip(CircleShape)
                            .bounceClickable(onClick = onNavigateBack)
                    ) {
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
                                    ExpressiveSwitch(
                                        checked = showThumbnails,
                                        onCheckedChange = onShowThumbnailsChange,
                                        modifier = Modifier.testTag("thumbnail_switch")
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.medium)
                                    .bounceClickable { onShowThumbnailsChange(!showThumbnails) }
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
                                    onValueChange = { value ->
                                        val intVal = value.toInt()
                                        if (intVal != homeRecentCarouselLimit) {
                                            haptics.selectionChanged()
                                            onHomeRecentCarouselLimitChange(intVal)
                                        }
                                    },
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
                                    ExpressiveSwitch(
                                        checked = showHiddenFiles,
                                        onCheckedChange = onShowHiddenFilesChange,
                                        modifier = Modifier.testTag("hidden_files_switch")
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.medium)
                                    .bounceClickable { onShowHiddenFilesChange(!showHiddenFiles) }
                                    .testTag("hidden_files_setting_row")
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.settings_browser_scrollbar)) },
                                supportingContent = { Text(stringResource(R.string.settings_browser_scrollbar_description)) },
                                trailingContent = {
                                    ExpressiveSwitch(
                                        checked = browserScrollbarEnabled,
                                        onCheckedChange = onBrowserScrollbarEnabledChange,
                                        modifier = Modifier.testTag("browser_scrollbar_switch")
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.medium)
                                    .bounceClickable { onBrowserScrollbarEnabledChange(!browserScrollbarEnabled) }
                                    .testTag("browser_scrollbar_setting_row")
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.settings_gallery_scrollbar)) },
                                supportingContent = { Text(stringResource(R.string.settings_gallery_scrollbar_description)) },
                                trailingContent = {
                                    ExpressiveSwitch(
                                        checked = galleryScrollbarEnabled,
                                        onCheckedChange = onGalleryScrollbarEnabledChange,
                                        modifier = Modifier.testTag("gallery_scrollbar_switch")
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.medium)
                                    .bounceClickable { onGalleryScrollbarEnabledChange(!galleryScrollbarEnabled) }
                                    .testTag("gallery_scrollbar_setting_row")
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.settings_harmonize_colors)) },
                                supportingContent = { Text(stringResource(R.string.settings_harmonize_colors_description)) },
                                trailingContent = {
                                    ExpressiveSwitch(
                                        checked = currentThemeState.harmonizeColors,
                                        onCheckedChange = { isChecked ->
                                            haptics.toggleMenu()
                                            onThemeChange(currentThemeState.copy(harmonizeColors = isChecked))
                                        },
                                        modifier = Modifier.testTag("harmonize_colors_switch")
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.medium)
                                    .bounceClickable {
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
                                    ExpressiveSwitch(
                                        checked = currentThemeState.vibrationsEnabled,
                                        onCheckedChange = { isChecked ->
                                            haptics.toggleMenu()
                                            onThemeChange(currentThemeState.copy(vibrationsEnabled = isChecked))
                                        },
                                        modifier = Modifier.testTag("vibrations_switch")
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.medium)
                                    .bounceClickable {
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
                                    ExpressiveSwitch(
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
                                        modifier = Modifier.testTag("double_line_filenames_switch")
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.medium)
                                    .bounceClickable {
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
                                    ExpressiveSwitch(
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
                                        modifier = Modifier.testTag("marquee_filenames_switch")
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.medium)
                                    .bounceClickable {
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
                SettingsPluginSection(onOpen = onNavigateToPlugins)
            }

            item {
                SettingsSection(title = stringResource(R.string.section_storage)) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.manage_classification)) },
                        supportingContent = { Text(stringResource(R.string.manage_classification_description)) },
                        leadingContent = { Icon(Icons.Default.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clip(MaterialTheme.shapes.medium).bounceClickable(onClick = onOpenStorageManagement)
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
                        modifier = Modifier.clip(MaterialTheme.shapes.medium).bounceClickable {
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
                SettingsBackupSection(
                    state = backupState,
                    onExport = {
                        exportBackupLauncher.launch("arcile-settings-backup.json")
                    },
                    onRestore = {
                        restoreBackupLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                    }
                )
            }

            item {
                SettingsAboutSection(onOpen = onNavigateToAbout)
            }
        }
    }
}
