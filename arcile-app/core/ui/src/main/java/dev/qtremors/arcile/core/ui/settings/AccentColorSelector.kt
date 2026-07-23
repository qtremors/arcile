package dev.qtremors.arcile.core.ui.settings

// ============================================================================
// IMPORTS
// ============================================================================

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.theme.AccentColor
import dev.qtremors.arcile.core.ui.theme.ExpressiveShapes
import dev.qtremors.arcile.core.ui.theme.bounceClickable
import dev.qtremors.arcile.core.ui.theme.bounceCombinedClickable
import dev.qtremors.arcile.core.ui.theme.buildMonochromeScheme
import dev.qtremors.arcile.core.ui.theme.buildScheme
import dev.qtremors.arcile.core.ui.theme.sheet
import dev.qtremors.arcile.core.ui.theme.titleMediumBold

// ============================================================================
// ACCENT COLOR DATA HELPERS
// ============================================================================

/**
 * Helper function to retrieve all displayable accent colors in order.
 */
fun displayedAccentColors(): List<AccentColor> =
    buildList {
        add(AccentColor.DYNAMIC)
        add(AccentColor.MONOCHROME)
        AccentColor.entries
            .filter { it != AccentColor.DYNAMIC && it != AccentColor.MONOCHROME }
            .distinctBy { it.color?.value ?: it.name.hashCode().toULong() }
            .forEach(::add)
    }

/**
 * Returns a user-friendly display name for each [AccentColor] variant.
 */
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

/**
 * Resolves the primary visual display color for an [AccentColor] swatch.
 */
@Composable
private fun getAccentDisplayColor(accent: AccentColor): Color {
    val isDark = isSystemInDarkTheme()
    return when (accent) {
        AccentColor.DYNAMIC -> MaterialTheme.colorScheme.primary
        AccentColor.MONOCHROME -> if (isDark) Color.White else Color.Black
        else -> accent.color ?: Color.Gray
    }
}

/**
 * Resolves the full Material 3 ColorScheme matching Arcile's exact theme engine resolution.
 */
@Composable
private fun resolvePreviewColorScheme(
    accent: AccentColor,
    currentAccent: AccentColor
): ColorScheme {
    val currentScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()

    // If previewing the active accent, use active MaterialTheme.colorScheme directly for 100% fidelity
    if (accent == currentAccent) {
        return currentScheme
    }

    return remember(accent, isDark) {
        when {
            accent == AccentColor.MONOCHROME -> buildMonochromeScheme(isDark = isDark, isOled = false)
            accent == AccentColor.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            accent != AccentColor.DYNAMIC -> {
                val primaryColor = accent.color ?: Color(0xFF2196F3)
                buildScheme(primary = primaryColor, isDark = isDark)
            }
            else -> currentScheme
        }
    }
}

// ============================================================================
// MAIN SETTINGS ROW SELECTOR
// ============================================================================

/**
 * Main inline accent color selector row for Settings.
 * Displays horizontal quick swatches with long-press or tap to open full sheet.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AccentColorSelector(
    currentAccent: AccentColor,
    onAccentSelected: (AccentColor) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    val allAccents = remember { displayedAccentColors() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        // Section Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.accent_color),
                style = MaterialTheme.typography.titleMediumBold
            )

            // Open Full Sheet Icon Button
            IconButton(
                onClick = { showPicker = true },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Palette,
                    contentDescription = stringResource(R.string.select_accent_color),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Horizontal Quick Selection Swatches
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(allAccents, key = { it.name }) { accent ->
                val isSelected = currentAccent == accent
                val displayColor = getAccentDisplayColor(accent)
                val accentLabel = stringResource(accentLabelRes(accent))

                // Expressive morphing corner radius
                val animatedCornerRadius by animateDpAsState(
                    targetValue = if (isSelected) 14.dp else 26.dp,
                    animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
                    label = "inlineCornerRadius"
                )

                // Expressive scale on selection
                val animatedScale by animateFloatAsState(
                    targetValue = if (isSelected) 1.08f else 1f,
                    animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
                    label = "inlineScale"
                )

                // Soft contrast ring without harsh white borders
                val isDark = isSystemInDarkTheme()
                val animatedBorderColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        if (isDark) {
                            if (displayColor.luminance() > 0.6f) Color.Black.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.4f)
                        } else {
                            if (displayColor.luminance() > 0.6f) Color.Black.copy(alpha = 0.6f) else displayColor
                        }
                    } else {
                        displayColor.copy(alpha = 0.15f)
                    },
                    animationSpec = spring(stiffness = 300f),
                    label = "inlineBorder"
                )

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .scale(animatedScale)
                        .size(52.dp)
                        .clip(RoundedCornerShape(animatedCornerRadius))
                        .background(displayColor)
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = animatedBorderColor,
                            shape = RoundedCornerShape(animatedCornerRadius)
                        )
                        .bounceCombinedClickable(
                            onClick = { onAccentSelected(accent) },
                            onLongClick = { showPicker = true }
                        )
                        .semantics {
                            selected = isSelected
                            contentDescription = accentLabel
                        }
                ) {
                    when (accent) {
                        AccentColor.DYNAMIC -> {
                            Icon(
                                Icons.Default.ColorLens,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        AccentColor.MONOCHROME -> {
                            Icon(
                                Icons.Default.Contrast,
                                contentDescription = null,
                                tint = if (isSystemInDarkTheme()) Color.Black else Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        else -> {
                            androidx.compose.animation.AnimatedVisibility(
                                visible = isSelected,
                                enter = scaleIn() + fadeIn(),
                                exit = scaleOut() + fadeOut()
                            ) {
                                val iconTint = if (displayColor.luminance() > 0.5f) Color.Black else Color.White
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = iconTint,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom sheet with smooth drag handle & swipe dismissal support
        if (showPicker) {
            AccentColorPickerSheet(
                currentAccent = currentAccent,
                onAccentSelected = { accent ->
                    onAccentSelected(accent)
                },
                onDismiss = { showPicker = false }
            )
        }
    }
}

// ============================================================================
// EXPRESSIVE BOTTOM SHEET PICKER
// ============================================================================

/**
 * Material Design 3 Expressive Bottom Sheet for selecting accent colors.
 * Compact, lightweight, and easily dismissible by drag handle or downward swipe.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccentColorPickerSheet(
    currentAccent: AccentColor,
    onAccentSelected: (AccentColor) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = ExpressiveShapes.sheet
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Sheet Header Row with Title and Close Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.select_accent_color),
                    style = MaterialTheme.typography.titleLarge
                )
                TextButton(onClick = onDismiss) {
                    Text("Done", style = MaterialTheme.typography.labelLarge)
                }
            }

            // Compact Live Component & Tonal Palette Preview Card
            LiveAccentPreviewCard(
                accent = currentAccent,
                currentAccent = currentAccent
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Special System Options (Dynamic Material You & Monochrome)
            Text(
                text = "System Themes",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
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

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(12.dp))

            // Categorized Accent Color Swatches
            AccentCategorySection(
                title = "Warm Palette",
                colors = listOf(
                    AccentColor.RED, AccentColor.PINK, AccentColor.DEEP_ORANGE,
                    AccentColor.ORANGE, AccentColor.AMBER, AccentColor.YELLOW
                ),
                currentAccent = currentAccent,
                onSelect = onAccentSelected
            )

            Spacer(modifier = Modifier.height(10.dp))

            AccentCategorySection(
                title = "Cool & Vibrant Palette",
                colors = listOf(
                    AccentColor.BLUE, AccentColor.LIGHT_BLUE, AccentColor.CYAN,
                    AccentColor.INDIGO, AccentColor.PURPLE, AccentColor.DEEP_PURPLE
                ),
                currentAccent = currentAccent,
                onSelect = onAccentSelected
            )

            Spacer(modifier = Modifier.height(10.dp))

            AccentCategorySection(
                title = "Nature & Fresh Palette",
                colors = listOf(
                    AccentColor.TEAL, AccentColor.GREEN, AccentColor.LIGHT_GREEN, AccentColor.LIME
                ),
                currentAccent = currentAccent,
                onSelect = onAccentSelected
            )

            Spacer(modifier = Modifier.height(10.dp))

            AccentCategorySection(
                title = "Earth & Neutral Palette",
                colors = listOf(
                    AccentColor.BROWN, AccentColor.BLUE_GREY, AccentColor.GREY, AccentColor.BLACK
                ),
                currentAccent = currentAccent,
                onSelect = onAccentSelected
            )
        }
    }
}

// ============================================================================
// LIVE COMPONENT PREVIEW CARD
// ============================================================================

/**
 * Expressive preview card demonstrating live theme accent rendering on Arcile's actual UI components.
 */
@Composable
fun LiveAccentPreviewCard(
    accent: AccentColor,
    currentAccent: AccentColor,
    modifier: Modifier = Modifier
) {
    val scheme = resolvePreviewColorScheme(accent = accent, currentAccent = currentAccent)

    val primary by animateColorAsState(scheme.primary, spring(stiffness = 300f), label = "prevPrimary")
    val onPrimary by animateColorAsState(scheme.onPrimary, spring(stiffness = 300f), label = "prevOnPrimary")
    val primaryContainer by animateColorAsState(scheme.primaryContainer, spring(stiffness = 300f), label = "prevPrimaryContainer")
    val onPrimaryContainer by animateColorAsState(scheme.onPrimaryContainer, spring(stiffness = 300f), label = "prevOnPrimaryContainer")
    val secondaryContainer by animateColorAsState(scheme.secondaryContainer, spring(stiffness = 300f), label = "prevSecondaryContainer")
    val surfaceContainer by animateColorAsState(scheme.surfaceContainer, spring(stiffness = 300f), label = "prevSurfaceContainer")
    val surfaceContainerHigh by animateColorAsState(scheme.surfaceContainerHigh, spring(stiffness = 300f), label = "prevSurfaceContainerHigh")
    val onSurface by animateColorAsState(scheme.onSurface, spring(stiffness = 300f), label = "prevOnSurface")
    val onSurfaceVariant by animateColorAsState(scheme.onSurfaceVariant, spring(stiffness = 300f), label = "prevOnSurfaceVariant")

    Card(
        shape = ExpressiveShapes.medium,
        colors = CardDefaults.cardColors(containerColor = surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Compact Header + Accent Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = onPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Arcile Interface",
                        style = MaterialTheme.typography.titleSmall,
                        color = onSurface
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = primaryContainer
                ) {
                    Text(
                        text = stringResource(accentLabelRes(accent)),
                        style = MaterialTheme.typography.labelSmall,
                        color = onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Mini Segmented List Item Preview
            Surface(
                shape = ExpressiveShapes.small,
                color = surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                tint = onPrimaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "List Item & Controls",
                            style = MaterialTheme.typography.labelMedium,
                            color = onSurface
                        )
                    }

                    // Active Switch / Action Pill Sample
                    Surface(
                        shape = CircleShape,
                        color = primary
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = onPrimary,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Associated Tonal Scheme Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp)),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Box(modifier = Modifier.weight(1f).fillMaxHeight().background(primary))
                Box(modifier = Modifier.weight(1f).fillMaxHeight().background(primaryContainer))
                Box(modifier = Modifier.weight(1f).fillMaxHeight().background(secondaryContainer))
                Box(modifier = Modifier.weight(1f).fillMaxHeight().background(surfaceContainerHigh))
            }
        }
    }
}

// ============================================================================
// ACCENT CATEGORY & SWATCH COMPONENTS
// ============================================================================

/**
 * Grouped category section of color swatches.
 */
@Composable
private fun AccentCategorySection(
    title: String,
    colors: List<AccentColor>,
    currentAccent: AccentColor,
    onSelect: (AccentColor) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            colors.forEach { accent ->
                ExpressiveColorSwatch(
                    accent = accent,
                    isSelected = currentAccent == accent,
                    onSelect = { onSelect(accent) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Expressive color swatch item with morphing shape, adaptive ring border, and spring animations.
 */
@Composable
fun ExpressiveColorSwatch(
    accent: AccentColor,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val displayColor = accent.color ?: Color.Gray
    val accentLabel = stringResource(accentLabelRes(accent))
    val isDark = isSystemInDarkTheme()

    // Expressive morphing corner radius animation (Circle -> Rounded Squircle)
    val cornerRadius by animateDpAsState(
        targetValue = if (isSelected) 14.dp else 24.dp,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "swatchCornerRadius"
    )

    // Animated scale on selection
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.08f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "swatchScale"
    )

    // Dynamic border color tailored to theme & luminance without harsh white rings
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) {
            if (isDark) {
                if (displayColor.luminance() > 0.6f) Color.Black.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.4f)
            } else {
                if (displayColor.luminance() > 0.6f) Color.Black.copy(alpha = 0.6f) else displayColor
            }
        } else {
            displayColor.copy(alpha = 0.2f)
        },
        animationSpec = spring(stiffness = 300f),
        label = "swatchBorder"
    )

    val shape = RoundedCornerShape(cornerRadius)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .aspectRatio(1f)
            .scale(scale)
            .clip(shape)
            .background(displayColor)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = shape
            )
            .bounceClickable { onSelect() }
            .semantics {
                contentDescription = accentLabel
                selected = isSelected
            }
    ) {
        androidx.compose.animation.AnimatedVisibility(
            visible = isSelected,
            enter = scaleIn(spring(dampingRatio = 0.6f, stiffness = 500f)) + fadeIn(),
            exit = scaleOut() + fadeOut()
        ) {
            val iconTint = if (displayColor.luminance() > 0.5f) Color.Black else Color.White
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (displayColor.luminance() > 0.6f) Color.Black.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

/**
 * Expressive card item for system-wide accents (Dynamic Material You & Monochrome).
 */
@Composable
fun SpecialAccentItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedBg by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        animationSpec = spring(stiffness = 300f),
        label = "specialBg"
    )

    Surface(
        shape = ExpressiveShapes.medium,
        color = animatedBg,
        border = if (!isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)) else null,
        modifier = modifier.bounceClickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}
