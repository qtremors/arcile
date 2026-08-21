package dev.qtremors.arcile.core.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.storage.domain.AppStartPage
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.rememberArcileHaptics
import dev.qtremors.arcile.core.ui.theme.LocalReducedMotionEnabled
import dev.qtremors.arcile.core.ui.theme.titleMediumBold

@Composable
fun AppStartPageSelector(
    currentPage: AppStartPage,
    onPageSelected: (AppStartPage) -> Unit
) {
    val haptics = rememberArcileHaptics()
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(
            text = stringResource(R.string.settings_app_start_page),
            style = MaterialTheme.typography.titleMediumBold,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            AppStartPage.entries.forEach { page ->
                val (label, icon) = when (page) {
                    AppStartPage.HOME -> stringResource(R.string.start_page_home) to Icons.Default.Home
                    AppStartPage.BROWSER -> stringResource(R.string.start_page_browser) to Icons.Default.Folder
                }

                AppStartPageCard(
                    page = page,
                    label = label,
                    icon = icon,
                    isSelected = currentPage == page,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        haptics.selectionStart()
                        onPageSelected(it)
                    }
                )
            }
        }
    }
}

@Composable
fun AppStartPageCard(
    page: AppStartPage,
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: (AppStartPage) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val reducedMotion = LocalReducedMotionEnabled.current

    val scale by animateFloatAsState(
        targetValue = if (isPressed && !reducedMotion) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = 0.8f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "appStartPageCardScale"
    )

    val containerColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        label = "appStartPageContainerColor"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "appStartPageContentColor"
    )

    Column(
        modifier = modifier
            .selectable(
                selected = isSelected,
                onClick = { onClick(page) },
                role = Role.RadioButton,
                interactionSource = interactionSource,
                indication = null
            )
            .semantics { contentDescription = label }
            .testTag("app_start_page_${page.name.lowercase()}")
            .padding(horizontal = 4.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = if (isSelected) MaterialTheme.shapes.extraLarge else MaterialTheme.shapes.large,
            color = containerColor,
            contentColor = contentColor,
            border = if (!isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant) else null,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
