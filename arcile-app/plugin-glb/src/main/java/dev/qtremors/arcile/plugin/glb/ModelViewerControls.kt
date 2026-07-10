package dev.qtremors.arcile.plugin.glb

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.plugin.ui.LocalViewerMarqueeFilenames
import dev.qtremors.arcile.plugin.ui.ViewerDropdownMenuItem
import dev.qtremors.arcile.plugin.ui.ViewerSplitButtonGroup
import dev.qtremors.arcile.plugin.ui.ViewerToolbarAction
import dev.qtremors.arcile.plugin.ui.formatViewerFileSize
import dev.qtremors.arcile.plugin.ui.viewerMenuFirst
import dev.qtremors.arcile.plugin.ui.viewerMenuLast
import dev.qtremors.arcile.plugin.ui.viewerMenuMiddle
import dev.qtremors.arcile.plugin.ui.viewerMenuSingle
import io.github.sceneview.SceneView
import io.github.sceneview.SurfaceType
import io.github.sceneview.math.Position
import io.github.sceneview.math.Scale
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironment
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberFillLightNode
import io.github.sceneview.rememberMainLightNode
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberOnGestureListener
import io.github.sceneview.loaders.ModelLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.roundToInt

@Composable
internal fun ModelViewerControlDrawer(
    activeControl: ModelViewerControl,
    zoomScale: Float,
    onZoomScaleChange: (Float) -> Unit,
    lightBrightness: Float,
    onLightBrightnessChange: (Float) -> Unit,
    backgroundMode: ModelViewerBackground,
    onBackgroundModeChange: (ModelViewerBackground) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.Black.copy(alpha = 0.72f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (activeControl) {
                ModelViewerControl.Zoom -> {
                    DrawerHeader(
                        title = stringResource(R.string.model_viewer_zoom),
                        value = stringResource(R.string.model_viewer_zoom_value, (zoomScale * 100).roundToInt())
                    )
                    Slider(
                        value = zoomScale,
                        onValueChange = onZoomScaleChange,
                        valueRange = 0.5f..3f
                    )
                }
                ModelViewerControl.Brightness -> {
                    DrawerHeader(
                        title = stringResource(R.string.model_viewer_brightness),
                        value = stringResource(R.string.model_viewer_brightness_value, (lightBrightness * 100).roundToInt())
                    )
                    Slider(
                        value = lightBrightness,
                        onValueChange = onLightBrightnessChange,
                        valueRange = 0.35f..2.5f
                    )
                }
                ModelViewerControl.Background -> {
                    DrawerHeader(
                        title = stringResource(R.string.model_viewer_background),
                        value = backgroundMode.label()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ModelViewerBackground.values().forEach { mode ->
                            val selected = mode == backgroundMode
                            Surface(
                                onClick = { onBackgroundModeChange(mode) },
                                shape = RoundedCornerShape(16.dp),
                                color = if (selected) Color.White else Color.White.copy(alpha = 0.12f),
                                contentColor = if (selected) Color.Black else Color.White,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = mode.label(),
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)
                                )
                            }
                        }
                    }
                }
                ModelViewerControl.None -> Unit
            }
        }
    }
}

@Composable
private fun DrawerHeader(title: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            color = Color.White.copy(alpha = 0.72f),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ModelViewerBackground.label(): String =
    when (this) {
        ModelViewerBackground.Theme -> stringResource(R.string.model_viewer_background_theme)
        ModelViewerBackground.White -> stringResource(R.string.model_viewer_background_white)
        ModelViewerBackground.Black -> stringResource(R.string.model_viewer_background_black)
    }

internal fun ModelViewerControl.toggled(control: ModelViewerControl): ModelViewerControl =
    if (this == control) ModelViewerControl.None else control

@Composable
internal fun ViewerStatusCard(
    title: String,
    detail: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black.copy(alpha = 0.7f))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, color = Color.White, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(detail, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun ViewerErrorCard(
    message: String,
    onOpenWith: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black.copy(alpha = 0.7f))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(message, color = Color.White, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onOpenWith) {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.image_gallery_open_with))
        }
    }
}

@Composable
internal fun ModelInfoDialog(
    title: String,
    reference: String,
    sizeBytes: Long,
    mimeType: String?,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.model_viewer_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title.ifBlank { reference.substringAfterLast('/') })
                if (sizeBytes > 0L) Text(formatViewerFileSize(sizeBytes))
                Text(mimeType ?: "model/gltf-binary")
                Text(reference, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        }
    )
}
