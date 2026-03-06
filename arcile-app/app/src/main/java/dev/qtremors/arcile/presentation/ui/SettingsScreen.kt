package dev.qtremors.arcile.presentation.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.ui.theme.AccentColor
import dev.qtremors.arcile.ui.theme.ThemeMode
import dev.qtremors.arcile.ui.theme.ThemeState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentThemeState: ThemeState,
    onNavigateBack: () -> Unit,
    onThemeChange: (ThemeState) -> Unit
) {

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item {
                SectionHeader("Appearance")
            }

            item {
                ThemeModeSelector(
                    currentMode = currentThemeState.themeMode,
                    onModeSelected = {
                        onThemeChange(currentThemeState.copy(themeMode = it))
                    }
                )
            }

            item {
                AccentColorSelector(
                    currentAccent = currentThemeState.accentColor,
                    onAccentSelected = {
                        onThemeChange(currentThemeState.copy(accentColor = it))
                    }
                )
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                SectionHeader("About")
            }

            item {
                ListItem(
                    headlineContent = { Text("App Version") },
                    supportingContent = { Text(dev.qtremors.arcile.BuildConfig.VERSION_NAME) },
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = null) }
                )
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeModeSelector(
    currentMode: ThemeMode,
    onModeSelected: (ThemeMode) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text("Theme Mode") },
        supportingContent = { Text(currentMode.name) },
        leadingContent = { Icon(Icons.Default.DarkMode, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier.clickable { expanded = true }
    )

    if (expanded) {
        ModalBottomSheet(onDismissRequest = { expanded = false }) {
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                Text(
                    text = "Select Theme Mode",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp)
                )
                ThemeMode.entries.forEach { mode ->
                    ListItem(
                        headlineContent = { Text(mode.name) },
                        trailingContent = {
                            if (mode == currentMode) {
                                Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        modifier = Modifier.clickable {
                            onModeSelected(mode)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccentColorSelector(
    currentAccent: AccentColor,
    onAccentSelected: (AccentColor) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text("Accent Color") },
        supportingContent = { Text(currentAccent.name) },
        leadingContent = { Icon(Icons.Default.ColorLens, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier.clickable { expanded = true }
    )

    if (expanded) {
        ModalBottomSheet(onDismissRequest = { expanded = false }) {
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                Text(
                    text = "Select Accent Color",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp)
                )
                AccentColor.entries.forEach { accent ->
                    ListItem(
                        headlineContent = { Text(accent.name) },
                        leadingContent = {
                            if (accent.color != null) {
                                Surface(shape = MaterialTheme.shapes.small, color = accent.color, modifier = Modifier.size(24.dp)) {}
                            } else {
                                Icon(Icons.Default.ColorLens, contentDescription = null)
                            }
                        },
                        trailingContent = {
                            if (accent == currentAccent) {
                                Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        modifier = Modifier.clickable {
                            onAccentSelected(accent)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
