package dev.qtremors.arcile.plugin.glb

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.plugin.ui.LocalViewerMarqueeFilenames
import dev.qtremors.arcile.plugin.ui.ViewerDropdownMenuItem
import dev.qtremors.arcile.plugin.ui.ViewerSplitButtonGroup
import dev.qtremors.arcile.plugin.ui.ViewerToolbarAction
import dev.qtremors.arcile.plugin.ui.viewerMenuFirst
import dev.qtremors.arcile.plugin.ui.viewerMenuLast
import dev.qtremors.arcile.plugin.ui.viewerMenuMiddle
import dev.qtremors.arcile.plugin.ui.viewerMenuSingle

@Composable
internal fun ModelViewerTopOverlay(
    visible: Boolean,
    title: String,
    modifier: Modifier = Modifier
) {
    val marqueeEnabled = LocalViewerMarqueeFilenames.current
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)),
        exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow)),
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 4.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = if (marqueeEnabled) TextOverflow.Clip else TextOverflow.Ellipsis,
                    modifier = if (marqueeEnabled) Modifier.basicMarquee() else Modifier
                )
                Text(
                    text = "GLB • ${stringResource(R.string.model_viewer_hint)}",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = if (marqueeEnabled) TextOverflow.Clip else TextOverflow.Ellipsis,
                    modifier = if (marqueeEnabled) Modifier.basicMarquee() else Modifier
                )
            }
        }
    }
}

@Composable
internal fun ModelViewerBottomOverlay(
    visible: Boolean,
    state: ModelViewerState,
    onStateChange: (ModelViewerState) -> Unit,
    onShare: () -> Unit,
    onOpenWith: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)),
        exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
            AnimatedVisibility(
                visible = state.activeControl != ModelViewerControl.None,
                enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)),
                exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow))
            ) {
                ModelViewerControlDrawer(
                    activeControl = state.activeControl,
                    zoomScale = state.zoomScale,
                    onZoomScaleChange = { onStateChange(state.copy(zoomScale = it)) },
                    lightBrightness = state.lightBrightness,
                    onLightBrightnessChange = { onStateChange(state.copy(lightBrightness = it)) },
                    backgroundMode = state.backgroundMode,
                    onBackgroundModeChange = { onStateChange(state.copy(backgroundMode = it)) }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ModelViewerControlButtons(state, onStateChange)
                Spacer(Modifier.weight(1f))
                ModelViewerOverflowMenu(
                    onInfo = {
                        onStateChange(state.copy(activeControl = ModelViewerControl.None, infoVisible = true))
                    },
                    onOpenWith = onOpenWith,
                    onShare = onShare
                )
            }
        }
    }
}

@Composable
private fun ModelViewerControlButtons(
    state: ModelViewerState,
    onStateChange: (ModelViewerState) -> Unit
) {
    ViewerSplitButtonGroup(
        actions = listOf(
            ViewerToolbarAction(
                icon = Icons.Default.ZoomIn,
                contentDescription = stringResource(R.string.model_viewer_zoom),
                tint = Color.White,
                onClick = {
                    onStateChange(state.copy(activeControl = state.activeControl.toggled(ModelViewerControl.Zoom)))
                }
            ),
            ViewerToolbarAction(
                icon = Icons.Default.WbSunny,
                contentDescription = stringResource(R.string.model_viewer_brightness),
                tint = Color.White,
                onClick = {
                    onStateChange(state.copy(activeControl = state.activeControl.toggled(ModelViewerControl.Brightness)))
                }
            ),
            ViewerToolbarAction(
                icon = Icons.Default.Palette,
                contentDescription = stringResource(R.string.model_viewer_background),
                tint = Color.White,
                onClick = {
                    onStateChange(state.copy(activeControl = state.activeControl.toggled(ModelViewerControl.Background)))
                }
            )
        ),
        containerColor = Color.Black.copy(alpha = 0.5f),
        contentColor = Color.White,
        height = 56.dp,
        minWidth = 64.dp,
        iconSize = 28.dp
    )
}

@Composable
private fun ModelViewerOverflowMenu(
    onInfo: () -> Unit,
    onOpenWith: () -> Unit,
    onShare: () -> Unit
) {
    var menuVisible by remember { mutableStateOf(false) }
    Box {
        Surface(
            onClick = { menuVisible = true },
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.5f),
            modifier = Modifier.size(56.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.action_more_options),
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        DropdownMenu(
            expanded = menuVisible,
            onDismissRequest = { menuVisible = false },
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.width(200.dp)
        ) {
            val menuActions = listOf<@Composable () -> Unit>(
                { ModelViewerMenuItem(R.string.action_info, Icons.Default.Info) { menuVisible = false; onInfo() } },
                {
                    ModelViewerMenuItem(R.string.image_gallery_open_with, Icons.AutoMirrored.Filled.OpenInNew) {
                        menuVisible = false
                        onOpenWith()
                    }
                },
                { ModelViewerMenuItem(R.string.share, Icons.Default.Share) { menuVisible = false; onShare() } }
            )
            menuActions.forEachIndexed { index, action ->
                val shape = when {
                    menuActions.size == 1 -> MaterialTheme.shapes.viewerMenuSingle
                    index == 0 -> MaterialTheme.shapes.viewerMenuFirst
                    index == menuActions.lastIndex -> MaterialTheme.shapes.viewerMenuLast
                    else -> MaterialTheme.shapes.viewerMenuMiddle
                }
                Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)
                        .clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHighest)
                ) { action() }
            }
        }
    }
}

@Composable
private fun ModelViewerMenuItem(
    textRes: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    ViewerDropdownMenuItem(
        text = { Text(stringResource(textRes), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    )
}
