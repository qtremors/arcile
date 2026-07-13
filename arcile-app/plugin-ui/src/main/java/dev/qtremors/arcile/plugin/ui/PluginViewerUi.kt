package dev.qtremors.arcile.plugin.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MenuItemColors
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val LocalViewerMarqueeFilenames = staticCompositionLocalOf { false }

data class ViewerToolbarAction(
    val icon: ImageVector,
    val contentDescription: String,
    val tint: Color? = null,
    val containerColor: Color? = null,
    val onClick: () -> Unit
)

@Composable
fun ArcilePluginTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
        content = content
    )
}

@Composable
fun ViewerSplitButtonGroup(
    actions: List<ViewerToolbarAction>,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    height: Dp = 48.dp,
    minWidth: Dp = 48.dp,
    iconSize: Dp = 24.dp,
    trailingContent: (@Composable () -> Unit)? = null,
    actionModifier: @Composable (Modifier, () -> Unit) -> Modifier = { base, onClick ->
        base.clickable(onClick = onClick)
    }
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        actions.forEachIndexed { index, action ->
            val shape = when {
                actions.size == 1 && trailingContent == null -> CircleShape
                index == 0 -> RoundedCornerShape(50, 15, 15, 50)
                index == actions.lastIndex && trailingContent == null -> RoundedCornerShape(15, 50, 50, 15)
                else -> RoundedCornerShape(15)
            }
            Surface(
                shape = shape,
                color = action.containerColor ?: containerColor,
                contentColor = action.tint ?: contentColor,
                modifier = actionModifier(
                    Modifier.height(height).widthIn(min = minWidth).clip(shape),
                    action.onClick
                )
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Icon(action.icon, action.contentDescription, modifier = Modifier.widthIn(max = iconSize))
                }
            }
        }
        if (trailingContent != null) {
            Box(modifier = Modifier.height(height).widthIn(min = minWidth)) {
                trailingContent()
            }
        }
    }
}

@Composable
fun ViewerDropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    colors: MenuItemColors = MenuDefaults.itemColors(),
    contentPadding: PaddingValues = MenuDefaults.DropdownMenuItemContentPadding,
    shape: Shape = MaterialTheme.shapes.medium
) {
    DropdownMenuItem(
        text = text,
        onClick = onClick,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        enabled = enabled,
        colors = colors,
        contentPadding = contentPadding,
        modifier = modifier.clip(shape)
    )
}

val androidx.compose.material3.Shapes.viewerMenuFirst: Shape
    get() = RoundedCornerShape(20.dp, 20.dp, 6.dp, 6.dp)
val androidx.compose.material3.Shapes.viewerMenuMiddle: Shape
    get() = RoundedCornerShape(6.dp)
val androidx.compose.material3.Shapes.viewerMenuLast: Shape
    get() = RoundedCornerShape(6.dp, 6.dp, 20.dp, 20.dp)
val androidx.compose.material3.Shapes.viewerMenuSingle: Shape
    get() = RoundedCornerShape(20.dp)

fun formatViewerFileSize(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = -1
    do {
        value /= 1024.0
        unit++
    } while (value >= 1024.0 && unit < units.lastIndex)
    return if (value >= 10.0) "%.0f %s".format(value, units[unit]) else "%.1f %s".format(value, units[unit])
}
