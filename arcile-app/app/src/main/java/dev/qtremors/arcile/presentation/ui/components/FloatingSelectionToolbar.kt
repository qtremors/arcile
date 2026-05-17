package dev.qtremors.arcile.presentation.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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

data class ToolbarAction(
    val icon: ImageVector,
    val contentDescription: String,
    val tint: Color? = null,
    val containerColor: Color? = null,
    val onClick: () -> Unit
)

@Composable
fun FloatingSelectionToolbar(
    isVisible: Boolean,
    modifier: Modifier = Modifier,
    actions: List<ToolbarAction>,
    startContent: (@Composable () -> Unit)? = null,
    moreContent: (@Composable () -> Unit)? = null
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { it * 2 }),
        exit = slideOutVertically(targetOffsetY = { it * 2 }),
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (startContent != null || moreContent != null) Arrangement.SpaceBetween else Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (startContent != null) {
                startContent()
            }
            
            // Action buttons (left group in selection mode)
            if (actions.isNotEmpty()) {
                SplitButtonGroup(
                    actions = actions,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    height = 56.dp,
                    minWidth = 56.dp,
                    iconSize = 28.dp
                )
            }

            // Overflow menu (right-aligned in selection mode)
            if (moreContent != null) {
                moreContent()
            }
        }
    }
}
