package dev.qtremors.arcile.shared.ui.menus

import dev.qtremors.arcile.core.ui.R
import androidx.compose.ui.res.stringResource

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.shape.RoundedCornerShape
import dev.qtremors.arcile.ui.theme.ArcileMotion
import dev.qtremors.arcile.ui.theme.pressScale
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector

data class FabMenuItem(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

@Composable
fun ExpandableFabMenu(
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    fabIconRotation: Float,
    items: List<FabMenuItem>
) {
    val animatedCornerSize = animateDpAsState(
        targetValue = if (isExpanded) 28.dp else 24.dp,
        animationSpec = ArcileMotion.rememberSpring(stiffness = Spring.StiffnessMediumLow),
        label = "fab_corner_size"
    )
    val fabShape = RoundedCornerShape(animatedCornerSize.value)

    Column(horizontalAlignment = Alignment.End) {
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn() + slideInVertically(initialOffsetY = { 50 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { 50 })
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                items.forEach { item ->
                    val interactionSource = remember { MutableInteractionSource() }
                    ExtendedFloatingActionButton(
                        onClick = item.onClick,
                        interactionSource = interactionSource,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        shape = MaterialTheme.shapes.extraLarge,
                        elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(
                            defaultElevation = 2.dp,
                            pressedElevation = 4.dp
                        ),
                        text = { Text(item.label, style = MaterialTheme.typography.labelLarge) },
                        icon = { Icon(item.icon, contentDescription = null) },
                        modifier = androidx.compose.ui.Modifier.padding(end = 2.dp).pressScale(interactionSource)
                    )
                }
            }
        }
        val haptics = dev.qtremors.arcile.shared.ui.rememberArcileHaptics()
        val mainInteractionSource = remember { MutableInteractionSource() }
        FloatingActionButton(
            onClick = {
                haptics.toggleMenu()
                onToggleExpand()
            },
            interactionSource = mainInteractionSource,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            shape = fabShape,
            modifier = Modifier.pressScale(mainInteractionSource)
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = stringResource(R.string.action_create_new),
                modifier = Modifier.rotate(fabIconRotation)
            )
        }
    }
}
