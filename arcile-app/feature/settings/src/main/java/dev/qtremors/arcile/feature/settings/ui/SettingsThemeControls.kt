package dev.qtremors.arcile.feature.settings.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.keyboardInputField
import dev.qtremors.arcile.core.ui.theme.ExpressiveShapes
import dev.qtremors.arcile.core.ui.theme.ThemePreset
import dev.qtremors.arcile.core.ui.theme.ThemeState
import dev.qtremors.arcile.core.ui.theme.bounceClickable
import dev.qtremors.arcile.core.ui.theme.titleMediumBold

@Composable
internal fun ThemePresetSelector(
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
                val border = if (isSelected) {
                    null
                } else {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                }
                val onPresetClick = { onPresetSelected(preset) }
                OutlinedButton(
                    onClick = onPresetClick,
                    colors = colors,
                    border = border,
                    shape = ExpressiveShapes.medium,
                    modifier = Modifier
                        .weight(1f)
                        .bounceClickable(onClick = onPresetClick),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Text(text = label, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
internal fun CustomThemeCreatorPanel(
    themeState: ThemeState,
    onThemeChange: (ThemeState) -> Unit
) {
    var primaryInput by remember(themeState.customPrimaryColorHex) {
        mutableStateOf(themeState.customPrimaryColorHex)
    }
    var backgroundInput by remember(themeState.customBackgroundColorHex) {
        mutableStateOf(themeState.customBackgroundColorHex)
    }
    var isPrimaryFocused by remember { mutableStateOf(false) }
    var isBackgroundFocused by remember { mutableStateOf(false) }
    val primaryScale by animateFloatAsState(
        targetValue = if (isPrimaryFocused) 1.03f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "primaryScale"
    )
    val backgroundScale by animateFloatAsState(
        targetValue = if (isBackgroundFocused) 1.03f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "backgroundScale"
    )
    val primaryParsed = remember(primaryInput) { primaryInput.parseColorOrNull() }
    val backgroundParsed = remember(backgroundInput) { backgroundInput.parseColorOrNull() }
    val colorsTooSimilar = remember(primaryParsed, backgroundParsed) {
        primaryParsed != null &&
            backgroundParsed != null &&
            kotlin.math.abs(backgroundParsed.luminanceEstimate() - primaryParsed.luminanceEstimate()) < 0.25f
    }

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            text = "Custom Theme Settings",
            style = MaterialTheme.typography.titleMediumBold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        ThemeColorInput(
            value = primaryInput,
            onValueChange = { input ->
                primaryInput = input
                if (input.isValidThemeColor()) {
                    onThemeChange(themeState.copy(customPrimaryColorHex = input))
                }
            },
            label = stringResource(R.string.custom_theme_primary_label),
            parsedColor = primaryParsed,
            scale = primaryScale,
            onFocusChange = { isPrimaryFocused = it },
            modifier = Modifier.padding(bottom = 16.dp)
        )
        ThemeColorInput(
            value = backgroundInput,
            onValueChange = { input ->
                backgroundInput = input
                if (input.isValidThemeColor()) {
                    onThemeChange(themeState.copy(customBackgroundColorHex = input))
                }
            },
            label = stringResource(R.string.custom_theme_bg_label),
            parsedColor = backgroundParsed,
            scale = backgroundScale,
            onFocusChange = { isBackgroundFocused = it },
            modifier = Modifier.padding(bottom = 16.dp)
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
private fun ThemeColorInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    parsedColor: Color?,
    scale: Float,
    onFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        trailingIcon = {
            parsedColor?.let { color ->
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                )
            }
        },
        shape = ExpressiveShapes.medium,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { onFocusChange(it.isFocused) }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .keyboardInputField()
    )
}

private fun String.isValidThemeColor(): Boolean =
    startsWith("#") && (length == 7 || length == 9) && parseColorOrNull() != null

private fun String.parseColorOrNull(): Color? =
    try {
        Color(android.graphics.Color.parseColor(this))
    } catch (_: IllegalArgumentException) {
        null
    }

private fun Color.luminanceEstimate(): Float = red * 0.299f + green * 0.587f + blue * 0.114f
