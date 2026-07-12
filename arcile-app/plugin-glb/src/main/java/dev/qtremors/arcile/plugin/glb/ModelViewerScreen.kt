package dev.qtremors.arcile.plugin.glb

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
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
import kotlinx.coroutines.CancellationException

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
    var viewerState by remember(reference) { mutableStateOf(ModelViewerState()) }
    val animatedZoomScale by animateFloatAsState(
        targetValue = viewerState.zoomScale,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "modelZoomScale"
    )
    val animatedLightBrightness by animateFloatAsState(
        targetValue = viewerState.lightBrightness,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "modelLightBrightness"
    )
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)
    val environment = rememberEnvironment(environmentLoader, isOpaque = false)
    val mainLightNode = rememberMainLightNode(engine) {
        intensity = MAIN_LIGHT_INTENSITY * animatedLightBrightness
    }
    val fillLightNode = rememberFillLightNode(engine) {
        intensity = FILL_LIGHT_INTENSITY * animatedLightBrightness
    }
    var modelInstance by remember(reference) { mutableStateOf<ModelInstance?>(null) }
    val modelViewerError = stringResource(R.string.model_viewer_error)
    val backgroundColor = when (viewerState.backgroundMode) {
        ModelViewerBackground.Theme -> MaterialTheme.colorScheme.surface
        ModelViewerBackground.White -> Color.White
        ModelViewerBackground.Black -> Color.Black
    }

    SideEffect {
        environment.indirectLight?.intensity = MAIN_LIGHT_INTENSITY * animatedLightBrightness
    }

    BackHandler {
        when {
            viewerState.infoVisible -> {
                viewerState = viewerState.copy(infoVisible = false)
            }
            viewerState.activeControl != ModelViewerControl.None -> {
                viewerState = viewerState.copy(activeControl = ModelViewerControl.None)
            }
            else -> onNavigateBack()
        }
    }

    LaunchedEffect(reference, modelLoader) {
        viewerState = viewerState.copy(loading = true, errorMessage = null)
        modelInstance = null
        try {
            modelInstance = loadSceneViewModelInstance(modelLoader, reference)
            viewerState = viewerState.copy(loading = false)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            viewerState = viewerState.copy(
                loading = false,
                errorMessage = error.localizedMessage ?: modelViewerError
            )
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
                        viewerState = if (viewerState.activeControl == ModelViewerControl.None) {
                            viewerState.copy(uiVisible = !viewerState.uiVisible)
                        } else {
                            viewerState.copy(activeControl = ModelViewerControl.None)
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

            if (viewerState.loading) {
                ViewerStatusCard(
                    title = stringResource(R.string.model_viewer_loading),
                    detail = title.ifBlank { reference.substringAfterLast('/') },
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            viewerState.errorMessage?.let { message ->
                ViewerErrorCard(
                    message = message.ifBlank { modelViewerError },
                    onOpenWith = onOpenWith,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            ModelViewerTopOverlay(
                visible = viewerState.uiVisible && !viewerState.infoVisible,
                title = title.ifBlank { reference.substringAfterLast('/') },
                modifier = Modifier.align(Alignment.TopCenter)
            )
            ModelViewerBottomOverlay(
                visible = viewerState.uiVisible && !viewerState.infoVisible,
                state = viewerState,
                onStateChange = { viewerState = it },
                onShare = onShare,
                onOpenWith = onOpenWith,
                modifier = Modifier.align(Alignment.BottomCenter)
            )

            if (viewerState.infoVisible) {
                ModelInfoDialog(
                    title = title,
                    reference = reference,
                    sizeBytes = sizeBytes,
                    mimeType = mimeType,
                    onDismiss = { viewerState = viewerState.copy(infoVisible = false) }
                )
            }
        }
    }
}

private const val MAIN_LIGHT_INTENSITY = 10_000f
private const val FILL_LIGHT_INTENSITY = 3_000f
