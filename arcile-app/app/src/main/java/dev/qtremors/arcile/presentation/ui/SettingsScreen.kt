package dev.qtremors.arcile.presentation.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import kotlinx.coroutines.launch
import dev.qtremors.arcile.ui.theme.ThemeState
import dev.qtremors.arcile.presentation.ui.components.settings.ThemeModeSelector
import dev.qtremors.arcile.presentation.ui.components.settings.AccentColorSelector
import dev.qtremors.arcile.presentation.ui.components.settings.SettingsSection

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
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val coroutineScope = rememberCoroutineScope()
    var showResetOnboardingDialog by remember { mutableStateOf(false) }
    var showRestartDialog by remember { mutableStateOf(false) }

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

    Scaffold(
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
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                SettingsSection(title = stringResource(R.string.section_appearance)) {
                    ThemeModeSelector(
                        currentMode = currentThemeState.themeMode,
                        onModeSelected = {
                            onThemeChange(currentThemeState.copy(themeMode = it))
                        }
                    )
                    AccentColorSelector(
                        currentAccent = currentThemeState.accentColor,
                        onAccentSelected = {
                            onThemeChange(currentThemeState.copy(accentColor = it))
                        }
                    )
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_show_thumbnails)) },
                        supportingContent = { Text(stringResource(R.string.settings_show_thumbnails_description)) },
                        trailingContent = {
                            Switch(
                                checked = showThumbnails,
                                onCheckedChange = onShowThumbnailsChange
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clip(MaterialTheme.shapes.medium).clickable { onShowThumbnailsChange(!showThumbnails) }
                    )
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
