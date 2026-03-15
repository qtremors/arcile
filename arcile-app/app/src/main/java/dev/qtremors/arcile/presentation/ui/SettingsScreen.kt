package dev.qtremors.arcile.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.animateColorAsState
import dev.qtremors.arcile.ui.theme.ExpressiveShapes
import dev.qtremors.arcile.ui.theme.ExpressiveSquircleShape
import dev.qtremors.arcile.ui.theme.AccentColor
import dev.qtremors.arcile.ui.theme.ThemeMode
import dev.qtremors.arcile.ui.theme.ThemeState

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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    currentThemeState: ThemeState,
    onNavigateBack: () -> Unit,
    onThemeChange: (ThemeState) -> Unit,
    onOpenStorageManagement: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {}
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Settings") },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                SettingsSection(title = "Appearance") {
                    ThemeModeSelector(
                        currentMode = currentThemeState.themeMode,
                        onModeSelected = {
                            onThemeChange(currentThemeState.copy(themeMode = it))
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    AccentColorSelector(
                        currentAccent = currentThemeState.accentColor,
                        onAccentSelected = {
                            onThemeChange(currentThemeState.copy(accentColor = it))
                        }
                    )
                }
            }

            item {
                SettingsSection(title = "Storage") {
                    ListItem(
                        headlineContent = { Text("Manage Storage Classification") },
                        supportingContent = { Text("Classify external volumes as SD card, OTG, or reset them.") },
                        leadingContent = { Icon(Icons.Default.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clip(MaterialTheme.shapes.medium).clickable(onClick = onOpenStorageManagement)
                    )
                }
            }

            item {
                SettingsSection(title = "Info") {
                    ListItem(
                        headlineContent = { Text("About") },
                        supportingContent = { Text("App info, privacy policy, and changelogs") },
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
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp)
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = ExpressiveShapes.large
        ) {
            Column(content = content, modifier = Modifier.padding(vertical = 4.dp))
        }
    }
}

@Composable
fun ThemeModeSelector(
    currentMode: ThemeMode,
    onModeSelected: (ThemeMode) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            text = "Theme Mode",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ThemeModeCard(ThemeMode.SYSTEM, "System", Icons.Default.SettingsSuggest, currentMode == ThemeMode.SYSTEM, Modifier.weight(1f).selectable(selected = currentMode == ThemeMode.SYSTEM, onClick = { onModeSelected(ThemeMode.SYSTEM) }, role = androidx.compose.ui.semantics.Role.RadioButton), onModeSelected)
            ThemeModeCard(ThemeMode.LIGHT, "Light", Icons.Default.LightMode, currentMode == ThemeMode.LIGHT, Modifier.weight(1f).selectable(selected = currentMode == ThemeMode.LIGHT, onClick = { onModeSelected(ThemeMode.LIGHT) }, role = androidx.compose.ui.semantics.Role.RadioButton), onModeSelected)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ThemeModeCard(ThemeMode.DARK, "Dark", Icons.Default.DarkMode, currentMode == ThemeMode.DARK, Modifier.weight(1f).selectable(selected = currentMode == ThemeMode.DARK, onClick = { onModeSelected(ThemeMode.DARK) }, role = androidx.compose.ui.semantics.Role.RadioButton), onModeSelected)
            ThemeModeCard(ThemeMode.OLED, "OLED", Icons.Default.Contrast, currentMode == ThemeMode.OLED, Modifier.weight(1f).selectable(selected = currentMode == ThemeMode.OLED, onClick = { onModeSelected(ThemeMode.OLED) }, role = androidx.compose.ui.semantics.Role.RadioButton), onModeSelected)
        }
    }
}

@Composable
fun ThemeModeCard(
    mode: ThemeMode,
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: (ThemeMode) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = 0.8f,
            stiffness = 380f
        ), label = "cardScale"
    )

    val containerColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        label = "containerColor"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        label = "contentColor"
    )

    Surface(
        shape = ExpressiveSquircleShape,
        color = containerColor,
        contentColor = contentColor,
        border = if (!isSelected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(ExpressiveSquircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = { onClick(mode) }
            )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(imageVector = icon, contentDescription = label, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun AccentColorSelector(
    currentAccent: AccentColor,
    onAccentSelected: (AccentColor) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(
            text = "Accent Color",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
        )
        
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(AccentColor.entries) { accent ->
                val isSelected = currentAccent == accent
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                
                val scale by animateFloatAsState(
                    targetValue = if (isPressed) 0.85f else if (isSelected) 1.1f else 1f,
                    animationSpec = spring(
                        dampingRatio = 0.8f,
                        stiffness = 380f
                    ), label = "chipScale"
                )

                val displayColor = accent.color ?: MaterialTheme.colorScheme.outlineVariant

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(CircleShape)
                        .background(displayColor)
                        .then(
                            if (!isSelected) {
                                Modifier.border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    shape = CircleShape
                                )
                            } else {
                                Modifier
                            }
                        )
                        .clickable(
                            interactionSource = interactionSource,
                            indication = androidx.compose.foundation.LocalIndication.current,
                            onClick = { onAccentSelected(accent) }
                        )
                ) {
                    if (isSelected) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(4.dp)
                            )
                        }
                    } else if (accent == AccentColor.DYNAMIC) {
                        Icon(
                            imageVector = Icons.Default.ColorLens,
                            contentDescription = "Dynamic",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Selected: ${accentLabel(currentAccent)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

/** Returns a user-friendly display name for each [AccentColor] variant. */
fun accentLabel(accent: AccentColor): String = when (accent) {
    AccentColor.DYNAMIC -> "Dynamic"
    AccentColor.MONOCHROME -> "Monochrome"
    AccentColor.BLUE -> "Blue"
    AccentColor.CYAN -> "Cyan"
    AccentColor.GREEN -> "Green"
    AccentColor.RED -> "Red"
    AccentColor.PURPLE -> "Purple"
}
