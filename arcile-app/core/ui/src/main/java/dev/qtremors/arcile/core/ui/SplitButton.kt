package dev.qtremors.arcile.core.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.plugin.ui.ViewerSplitButtonGroup
import dev.qtremors.arcile.plugin.ui.ViewerToolbarAction
import dev.qtremors.arcile.core.ui.theme.bounceClickable

@Composable
fun SplitButtonGroup(
    actions: List<ToolbarAction>,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    height: androidx.compose.ui.unit.Dp = 48.dp,
    minWidth: androidx.compose.ui.unit.Dp = 48.dp,
    iconSize: androidx.compose.ui.unit.Dp = 24.dp,
    trailingContent: (@Composable () -> Unit)? = null
) {
    ViewerSplitButtonGroup(
        actions = actions.map { action ->
            ViewerToolbarAction(
                icon = action.icon,
                contentDescription = action.contentDescription,
                tint = action.tint,
                containerColor = action.containerColor,
                onClick = action.onClick
            )
        },
        modifier = modifier,
        containerColor = containerColor,
        contentColor = contentColor,
        height = height,
        minWidth = minWidth,
        iconSize = iconSize,
        trailingContent = trailingContent,
        actionModifier = { base, onClick -> base.bounceClickable(onClick = onClick) }
    )
}
