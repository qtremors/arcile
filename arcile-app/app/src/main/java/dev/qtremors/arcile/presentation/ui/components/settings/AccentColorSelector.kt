package dev.qtremors.arcile.presentation.ui.components.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.ui.theme.AccentColor
import dev.qtremors.arcile.ui.theme.titleMediumBold

import androidx.compose.ui.res.stringResource
import dev.qtremors.arcile.R
import androidx.annotation.StringRes

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AccentColorSelector(
    currentAccent: AccentColor,
    onAccentSelected: (AccentColor) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    
    val allAccents = remember {
        listOf(AccentColor.DYNAMIC, AccentColor.MONOCHROME) + 
        AccentColor.entries.filter { it != AccentColor.DYNAMIC && it != AccentColor.MONOCHROME }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(
            text = stringResource(R.string.accent_color),
            style = MaterialTheme.typography.titleMediumBold,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
        )

        androidx.compose.foundation.lazy.LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(allAccents) { accent ->
                val isSelected = currentAccent == accent
                val displayColor = when (accent) {
                    AccentColor.DYNAMIC -> MaterialTheme.colorScheme.primary
                    AccentColor.MONOCHROME -> if (isSystemInDarkTheme()) Color.White else Color.Black
                    else -> accent.color ?: Color.Gray
                }
                
                val shape = if (isSelected) MaterialTheme.shapes.medium else CircleShape
                
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(shape)
                        .background(displayColor)
                        .combinedClickable(
                            onClick = { onAccentSelected(accent) },
                            onLongClick = { showPicker = true }
                        )
                        .semantics {
                            selected = isSelected
                            contentDescription = accent.name
                        }
                ) {
                    when (accent) {
                        AccentColor.DYNAMIC -> {
                            Icon(
                                Icons.Default.ColorLens,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        AccentColor.MONOCHROME -> {
                            Icon(
                                Icons.Default.Contrast,
                                contentDescription = null,
                                tint = if (isSystemInDarkTheme()) Color.Black else Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        else -> {
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

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
                .verticalScroll(rememberScrollState())
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

                            val accentLabel = stringResource(accentLabelRes(accent))

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
                                    .semantics {
                                        contentDescription = accentLabel
                                        selected = isSelected
                                    }
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
