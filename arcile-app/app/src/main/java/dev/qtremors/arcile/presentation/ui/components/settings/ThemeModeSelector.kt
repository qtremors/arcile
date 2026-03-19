package dev.qtremors.arcile.presentation.ui.components.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.ui.theme.ThemeMode
import dev.qtremors.arcile.ui.theme.titleMediumBold

import androidx.compose.ui.res.stringResource
import dev.qtremors.arcile.R

@Composable
fun ThemeModeSelector(
    currentMode: ThemeMode,
    onModeSelected: (ThemeMode) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            text = stringResource(R.string.theme_mode),
            style = MaterialTheme.typography.titleMediumBold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ThemeModeCard(ThemeMode.SYSTEM, stringResource(R.string.theme_system), Icons.Default.SettingsSuggest, currentMode == ThemeMode.SYSTEM, Modifier.weight(1f).selectable(selected = currentMode == ThemeMode.SYSTEM, onClick = { onModeSelected(ThemeMode.SYSTEM) }, role = androidx.compose.ui.semantics.Role.RadioButton), onModeSelected)
            ThemeModeCard(ThemeMode.LIGHT, stringResource(R.string.theme_light), Icons.Default.LightMode, currentMode == ThemeMode.LIGHT, Modifier.weight(1f).selectable(selected = currentMode == ThemeMode.LIGHT, onClick = { onModeSelected(ThemeMode.LIGHT) }, role = androidx.compose.ui.semantics.Role.RadioButton), onModeSelected)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ThemeModeCard(ThemeMode.DARK, stringResource(R.string.theme_dark), Icons.Default.DarkMode, currentMode == ThemeMode.DARK, Modifier.weight(1f).selectable(selected = currentMode == ThemeMode.DARK, onClick = { onModeSelected(ThemeMode.DARK) }, role = androidx.compose.ui.semantics.Role.RadioButton), onModeSelected)
            ThemeModeCard(ThemeMode.OLED, stringResource(R.string.theme_oled), Icons.Default.Contrast, currentMode == ThemeMode.OLED, Modifier.weight(1f).selectable(selected = currentMode == ThemeMode.OLED, onClick = { onModeSelected(ThemeMode.OLED) }, role = androidx.compose.ui.semantics.Role.RadioButton), onModeSelected)
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
            stiffness = Spring.StiffnessMedium
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
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor,
        contentColor = contentColor,
        border = if (!isSelected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(MaterialTheme.shapes.extraLarge)
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
            Text(text = label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
