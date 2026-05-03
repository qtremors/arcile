package dev.qtremors.arcile.presentation.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun SplitButtonGroup(
    actions: List<ToolbarAction>,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        actions.forEachIndexed { index, action ->
            val isFirst = index == 0
            val isLast = index == actions.size - 1 && trailingContent == null
            
            val shape = when {
                actions.size == 1 && trailingContent == null -> CircleShape
                isFirst -> RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50, topEndPercent = 15, bottomEndPercent = 15)
                isLast -> RoundedCornerShape(topStartPercent = 15, bottomStartPercent = 15, topEndPercent = 50, bottomEndPercent = 50)
                else -> RoundedCornerShape(15) // middle or followed by trailing
            }
            
            Surface(
                onClick = action.onClick,
                shape = shape,
                color = containerColor,
                contentColor = action.tint ?: contentColor,
                modifier = Modifier
                    .height(48.dp)
                    .widthIn(min = 48.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Icon(
                        imageVector = action.icon,
                        contentDescription = action.contentDescription,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
        
        if (trailingContent != null) {
            Box(
                modifier = Modifier
                    .height(48.dp)
                    .widthIn(min = 48.dp)
            ) {
                // We wrap the trailing content (usually an IconButton with a menu)
                // The trailing content should ideally handle its own shape if it wants to match
                trailingContent()
            }
        }
    }
}
