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
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloatingSelectionToolbar(
    isVisible: Boolean,
    modifier: Modifier = Modifier,
    actions: List<ToolbarAction>,
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
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (actions.isNotEmpty()) {
                SplitButtonGroup(
                    actions = actions,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            }
            if (moreContent != null) {
                if (actions.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                }
                moreContent()
            }
        }
    }
}
