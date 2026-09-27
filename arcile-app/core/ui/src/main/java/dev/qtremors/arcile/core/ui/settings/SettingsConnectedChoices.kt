@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.qtremors.arcile.core.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun SettingsChoiceHeader(title: String, description: String, icon: ImageVector) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SettingsConnectedChoices(
    options: List<String>,
    isSelected: (Int) -> Boolean,
    onSelectionChanged: (Int, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    rowTag: String? = null,
    testTagForIndex: ((Int) -> String)? = null,
    dynamicExpand: Boolean = false
) {
    if (options.isEmpty()) return
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val minItemWidth = if (dynamicExpand && maxWidth != Dp.Infinity && maxWidth > 0.dp) {
            val totalSpacing = 4.dp * (options.size - 1)
            val available = maxWidth - totalSpacing
            if (available > 0.dp) available / options.size else Dp.Unspecified
        } else {
            Dp.Unspecified
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .then(if (rowTag == null) Modifier else Modifier.testTag(rowTag)),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            options.forEachIndexed { index, option ->
                val shapes = when {
                    options.size == 1 -> ToggleButtonShapes(CircleShape, CircleShape, CircleShape)
                    index == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    index == options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                }
                ToggleButton(
                    checked = isSelected(index),
                    onCheckedChange = { onSelectionChanged(index, it) },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .then(
                            if (minItemWidth != Dp.Unspecified) {
                                Modifier.widthIn(min = minItemWidth)
                            } else {
                                Modifier
                            }
                        )
                        .then(
                            if (testTagForIndex == null) Modifier else Modifier.testTag(testTagForIndex(index))
                        ),
                    shapes = shapes,
                    colors = ToggleButtonDefaults.toggleButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    Text(option, maxLines = 1, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
