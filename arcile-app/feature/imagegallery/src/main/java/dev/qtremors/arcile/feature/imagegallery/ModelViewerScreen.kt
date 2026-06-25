package dev.qtremors.arcile.feature.imagegallery

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
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.shared.ui.ArcileDropdownMenuItem
import dev.qtremors.arcile.shared.ui.SplitButtonGroup
import dev.qtremors.arcile.shared.ui.ToolbarAction
import dev.qtremors.arcile.shared.ui.metadata.formatImageFileSize
import dev.qtremors.arcile.ui.theme.LocalMarqueeFilenames
import dev.qtremors.arcile.ui.theme.menuGroupFirst
import dev.qtremors.arcile.ui.theme.menuGroupLast
import dev.qtremors.arcile.ui.theme.menuGroupMiddle
import dev.qtremors.arcile.ui.theme.menuGroupSingle
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

private enum class ModelViewerControl {
    None,
    Zoom,
    Brightness,
    Background
}

private enum class ModelViewerBackground {
    Theme,
    White,
    Black
}

@Composable
fun ModelViewerScreen(
    reference: String,
    title: String,
    sizeBytes: Long,
    mimeType: String?,
    onNavigateBack: () -> Unit,
    onShare: () -> Unit,
    onOpenWith: () -> Unit
) {
    val context = LocalContext.current
    var uiVisible by remember { mutableStateOf(true) }
    var infoVisible by remember { mutableStateOf(false) }
    var activeControl by remember { mutableStateOf(ModelViewerControl.None) }
    var zoomScale by remember(reference) { mutableFloatStateOf(1f) }
    var lightBrightness by remember(reference) { mutableFloatStateOf(1f) }
    var backgroundMode by remember(reference) { mutableStateOf(ModelViewerBackground.Theme) }
    val animatedZoomScale by animateFloatAsState(
        targetValue = zoomScale,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "modelZoomScale"
    )
    val animatedLightBrightness by animateFloatAsState(
        targetValue = lightBrightness,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "modelLightBrightness"
    )
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)
    val environment = rememberEnvironment(environmentLoader, isOpaque = false)
    val mainLightNode = rememberMainLightNode(engine) {
        intensity = 10_000f * animatedLightBrightness
    }
    val fillLightNode = rememberFillLightNode(engine) {
        intensity = 3_000f * animatedLightBrightness
    }
    var modelInstance by remember(reference) { mutableStateOf<ModelInstance?>(null) }
    var loading by remember(reference) { mutableStateOf(true) }
    var errorMessage by remember(reference) { mutableStateOf<String?>(null) }
    val marqueeEnabled = LocalMarqueeFilenames.current
    val modelViewerError = stringResource(R.string.model_viewer_error)
    val backgroundColor = when (backgroundMode) {
        ModelViewerBackground.Theme -> MaterialTheme.colorScheme.surface
        ModelViewerBackground.White -> Color.White
        ModelViewerBackground.Black -> Color.Black
    }

    SideEffect {
        environment.indirectLight?.intensity = 10_000f * animatedLightBrightness
    }

    BackHandler(enabled = infoVisible || activeControl != ModelViewerControl.None) {
        when {
            infoVisible -> infoVisible = false
            activeControl != ModelViewerControl.None -> activeControl = ModelViewerControl.None
        }
    }

    LaunchedEffect(reference, modelLoader) {
        loading = true
        modelInstance = null
        errorMessage = null
        try {
            modelInstance = loadSceneViewModelInstance(modelLoader, reference)
        } catch (error: Throwable) {
            errorMessage = error.localizedMessage ?: modelViewerError
        } finally {
            loading = false
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = backgroundColor) {
        Box(modifier = Modifier.fillMaxSize()) {
            SceneView(
                modifier = Modifier.fillMaxSize(),
                surfaceType = SurfaceType.TextureSurface,
                engine = engine,
                modelLoader = modelLoader,
                isOpaque = false,
                environment = environment,
                mainLightNode = mainLightNode,
                fillLightNode = fillLightNode,
                autoFitContent = true,
                onGestureListener = rememberOnGestureListener(
                    onSingleTapConfirmed = { _, _ ->
                        if (activeControl == ModelViewerControl.None) {
                            uiVisible = !uiVisible
                        } else {
                            activeControl = ModelViewerControl.None
                        }
                    }
                )
            ) {
                modelInstance?.let { instance ->
                    Node(scale = Scale(animatedZoomScale)) {
                        ModelNode(
                            modelInstance = instance,
                            autoAnimate = true,
                            scaleToUnits = 2f,
                            centerOrigin = Position(0f, 0f, 0f)
                        )
                    }
                }
            }

            if (loading) {
                ViewerStatusCard(
                    title = stringResource(R.string.model_viewer_loading),
                    detail = title.ifBlank { reference.substringAfterLast('/') },
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            val currentError = errorMessage
            if (currentError != null) {
                ViewerErrorCard(
                    message = currentError.ifBlank { stringResource(R.string.model_viewer_error) },
                    onOpenWith = onOpenWith,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            AnimatedVisibility(
                visible = uiVisible && !infoVisible,
                enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)),
                exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow)),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
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
                            text = title.ifBlank { reference.substringAfterLast('/') },
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

            AnimatedVisibility(
                visible = uiVisible && !infoVisible,
                enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)),
                exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow)),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    AnimatedVisibility(
                        visible = activeControl != ModelViewerControl.None,
                        enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)),
                        exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow))
                    ) {
                        ModelViewerControlDrawer(
                            activeControl = activeControl,
                            zoomScale = zoomScale,
                            onZoomScaleChange = { zoomScale = it },
                            lightBrightness = lightBrightness,
                            onLightBrightnessChange = { lightBrightness = it },
                            backgroundMode = backgroundMode,
                            onBackgroundModeChange = { backgroundMode = it }
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SplitButtonGroup(
                            actions = listOf(
                                ToolbarAction(
                                    icon = Icons.Default.ZoomIn,
                                    contentDescription = stringResource(R.string.model_viewer_zoom),
                                    tint = Color.White,
                                    onClick = { activeControl = activeControl.toggled(ModelViewerControl.Zoom) }
                                ),
                                ToolbarAction(
                                    icon = Icons.Default.WbSunny,
                                    contentDescription = stringResource(R.string.model_viewer_brightness),
                                    tint = Color.White,
                                    onClick = { activeControl = activeControl.toggled(ModelViewerControl.Brightness) }
                                ),
                                ToolbarAction(
                                    icon = Icons.Default.Palette,
                                    contentDescription = stringResource(R.string.model_viewer_background),
                                    tint = Color.White,
                                    onClick = { activeControl = activeControl.toggled(ModelViewerControl.Background) }
                                )
                            ),
                            containerColor = Color.Black.copy(alpha = 0.5f),
                            contentColor = Color.White,
                            height = 56.dp,
                            minWidth = 64.dp,
                            iconSize = 28.dp
                        )

                        Spacer(Modifier.weight(1f))
                        Box {
                            var menuVisible by remember { mutableStateOf(false) }
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
                                    {
                                        ArcileDropdownMenuItem(
                                            text = { Text(stringResource(R.string.action_info), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                            onClick = {
                                                menuVisible = false
                                                activeControl = ModelViewerControl.None
                                                infoVisible = true
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                        )
                                    },
                                    {
                                        ArcileDropdownMenuItem(
                                            text = { Text(stringResource(R.string.image_gallery_open_with), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
                                            onClick = {
                                                menuVisible = false
                                                onOpenWith()
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                        )
                                    },
                                    {
                                        ArcileDropdownMenuItem(
                                            text = { Text(stringResource(R.string.share), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                            onClick = {
                                                menuVisible = false
                                                onShare()
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                        )
                                    }
                                )

                                menuActions.forEachIndexed { index, action ->
                                    val shape = when {
                                        menuActions.size == 1 -> MaterialTheme.shapes.menuGroupSingle
                                        index == 0 -> MaterialTheme.shapes.menuGroupFirst
                                        index == menuActions.size - 1 -> MaterialTheme.shapes.menuGroupLast
                                        else -> MaterialTheme.shapes.menuGroupMiddle
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                            .clip(shape)
                                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                    ) {
                                        action()
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (infoVisible) {
                ModelInfoDialog(
                    title = title,
                    reference = reference,
                    sizeBytes = sizeBytes,
                    mimeType = mimeType,
                    onDismiss = { infoVisible = false }
                )
            }
        }
    }
}

@Composable
private fun ModelViewerControlDrawer(
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

private fun ModelViewerControl.toggled(control: ModelViewerControl): ModelViewerControl =
    if (this == control) ModelViewerControl.None else control

@Composable
private fun ViewerStatusCard(
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
private fun ViewerErrorCard(
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
private fun ModelInfoDialog(
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
                if (sizeBytes > 0L) Text(formatImageFileSize(sizeBytes))
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

private suspend fun loadSceneViewModelInstance(
    modelLoader: ModelLoader,
    reference: String
): ModelInstance {
    val uri = runCatching { Uri.parse(reference) }.getOrNull()
    val modelInstance = when (uri?.scheme) {
        "content", "file", "http", "https", "android.resource" -> modelLoader.loadModelInstance(reference)
        null, "" -> {
            val file = File(reference)
            if (file.isAbsolute || file.exists()) {
                val bytes = withContext(Dispatchers.IO) { file.readBytes() }
                withContext(Dispatchers.Main) { modelLoader.createModelInstance(ByteBuffer.wrap(bytes)) }
            } else {
                modelLoader.loadModelInstance(reference)
            }
        }
        else -> modelLoader.loadModelInstance(reference)
    }
    return modelInstance ?: error("Unable to load GLB file")
}
