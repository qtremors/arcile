package dev.qtremors.arcile.presentation.ui.components.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.ui.theme.AccentColor
import dev.qtremors.arcile.ui.theme.titleMediumBold

import androidx.compose.ui.res.stringResource
import dev.qtremors.arcile.R
import androidx.annotation.StringRes

@Composable
fun AccentColorSelector(
    currentAccent: AccentColor,
    onAccentSelected: (AccentColor) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.accent_color), style = MaterialTheme.typography.titleMediumBold) },
            supportingContent = { Text(stringResource(accentLabelRes(currentAccent))) },
            trailingContent = {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(currentAccent.color ?: MaterialTheme.colorScheme.primary)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickable { showPicker = true }
        )

        if (showPicker) {
            AccentColorPickerSheet(
                currentAccent = currentAccent,
                onAccentSelected = {
                    onAccentSelected(it)
                    showPicker = false
                },
                onDismiss = { showPicker = false }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccentColorPickerSheet(
    currentAccent: AccentColor,
    onAccentSelected: (AccentColor) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.select_accent_color),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Special Options Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SpecialAccentItem(
                    title = stringResource(R.string.accent_dynamic),
                    subtitle = stringResource(R.string.accent_dynamic_description),
                    icon = Icons.Default.ColorLens,
                    isSelected = currentAccent == AccentColor.DYNAMIC,
                    onClick = { onAccentSelected(AccentColor.DYNAMIC) },
                    modifier = Modifier.weight(1f)
                )
                SpecialAccentItem(
                    title = stringResource(R.string.accent_monochrome),
                    subtitle = stringResource(R.string.accent_monochrome_description),
                    icon = Icons.Default.Contrast,
                    isSelected = currentAccent == AccentColor.MONOCHROME,
                    onClick = { onAccentSelected(AccentColor.MONOCHROME) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(24.dp))

            // Presets Grid
            val presets = remember { 
                AccentColor.entries.filter { it != AccentColor.DYNAMIC && it != AccentColor.MONOCHROME } 
            }
            
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                presets.chunked(4).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        row.forEach { accent ->
                            val isSelected = currentAccent == accent
                            val displayColor = accent.color ?: Color.Gray

                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(displayColor)
                                    .border(
                                        width = 2.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else displayColor.copy(alpha = 0.2f),
                                        shape = CircleShape
                                    )
                                    .clickable { onAccentSelected(accent) }
                            ) {
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                        if (row.size < 4) {
                            repeat(4 - row.size) { Spacer(modifier = Modifier.size(64.dp)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SpecialAccentItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        border = if (!isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(title, style = MaterialTheme.typography.labelLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
        }
    }
}

/** Returns a user-friendly display name for each [AccentColor] variant. */
@StringRes
fun accentLabelRes(accent: AccentColor): Int = when (accent) {
    AccentColor.DYNAMIC -> R.string.accent_dynamic
    AccentColor.RED -> R.string.color_red
    AccentColor.PINK -> R.string.color_pink
    AccentColor.PURPLE -> R.string.color_purple
    AccentColor.DEEP_PURPLE -> R.string.color_deep_purple
    AccentColor.CYAN -> R.string.color_cyan
    AccentColor.LIGHT_BLUE -> R.string.color_light_blue
    AccentColor.BLUE -> R.string.color_blue
    AccentColor.INDIGO -> R.string.color_indigo
    AccentColor.TEAL -> R.string.color_teal
    AccentColor.GREEN -> R.string.color_green
    AccentColor.LIGHT_GREEN -> R.string.color_light_green
    AccentColor.LIME -> R.string.color_lime
    AccentColor.DEEP_ORANGE -> R.string.color_deep_orange
    AccentColor.ORANGE -> R.string.color_orange
    AccentColor.AMBER -> R.string.color_amber
    AccentColor.YELLOW -> R.string.color_yellow
    AccentColor.BROWN -> R.string.color_brown
    AccentColor.BLUE_GREY -> R.string.color_blue_grey
    AccentColor.GREY -> R.string.color_grey
    AccentColor.BLACK -> R.string.color_black
    AccentColor.MONOCHROME -> R.string.accent_monochrome
}
