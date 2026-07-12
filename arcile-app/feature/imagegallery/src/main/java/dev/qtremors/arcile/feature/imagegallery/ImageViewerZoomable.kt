package dev.qtremors.arcile.feature.imagegallery

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.dialogs.DeleteConfirmationDialog
import dev.qtremors.arcile.core.ui.rememberArcileHaptics
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)

@Composable
internal fun ZoomableImageViewer(
    file: FileModel,
    rotation: Float,
    onDismiss: () -> Unit,
    onTap: () -> Unit,
    onScaleChanged: (Float) -> Unit,
    onSwipeUp: () -> Unit,
    onOpenWith: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    val screenHeightPx = remember(configuration, density) {
        with(density) { configuration.screenHeightDp.dp.toPx() }
    }

    // Animation states for scale & offsets
    val scale = remember { Animatable(1f) }
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }

    // Visual rotation degree transition
    val animatedRotation by animateFloatAsState(
        targetValue = rotation,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "rotation"
    )

    // Reset offsets and scales when the file changes
    LaunchedEffect(file) {
        scale.snapTo(1f)
        offsetX.snapTo(0f)
        offsetY.snapTo(0f)
    }

    val requestData = remember(file) { imageRequestDataFor(file) }
    val request = remember(context, requestData) {
        ImageRequest.Builder(context)
            .data(requestData)
            .build()
    }
    var renderFailed by remember(file.absolutePath) { mutableStateOf(false) }
    var imageSize by remember(file.absolutePath) { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(file, imageSize, rotation) {
                val touchSlop = viewConfiguration.touchSlop
                val doubleTapTimeout = 300L
                var lastTapTime = 0L
                var lastTapPosition = Offset.Zero
                var transformJob: Job? = null

                fun fittedContentSize() = viewerFittedContentSize(
                    viewportWidth = size.width.toFloat(),
                    viewportHeight = size.height.toFloat(),
                    imageWidth = imageSize.width.toFloat(),
                    imageHeight = imageSize.height.toFloat(),
                    rotationDegrees = rotation
                )
                
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downTime = System.currentTimeMillis()
                    val downPos = down.position
                    var gestureScale = scale.value
                    var gestureOffset = Offset(offsetX.value, offsetY.value)
                    
                    var isMultiTouch = false
                    var dragStarted = false
                    var dragDirection: DragDirection? = null
                    
                    while (true) {
                        val event = awaitPointerEvent()
                        val pointers = event.changes
                        if (pointers.isEmpty() || pointers.all { !it.pressed }) {
                            break
                        }
                        
                        if (pointers.size >= 2) {
                            isMultiTouch = true
                            pointers.forEach { it.consume() }
                            
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            val centroid = event.calculateCentroid()
                            val oldScale = gestureScale
                            val rawScale = oldScale * zoomChange
                            val constrainedScale = rawScale.coerceIn(VIEWER_MIN_STABLE_SCALE, VIEWER_MAX_STABLE_SCALE)
                            val nextOffset = viewerOffsetForScale(
                                currentOffset = gestureOffset,
                                oldScale = oldScale,
                                newScale = constrainedScale,
                                centroid = centroid,
                                viewportCenter = Offset(size.width / 2f, size.height / 2f),
                                pan = panChange
                            )
                            gestureScale = constrainedScale
                            gestureOffset = nextOffset
                            transformJob?.cancel()
                            transformJob = coroutineScope.launch {
                                scale.snapTo(constrainedScale)
                                offsetX.snapTo(nextOffset.x)
                                offsetY.snapTo(nextOffset.y)
                            }
                            onScaleChanged(constrainedScale)
                        } else if (pointers.size == 1 && !isMultiTouch) {
                            val change = pointers[0]
                            if (change.pressed) {
                                val currentPos = change.position
                                val delta = currentPos - change.previousPosition
                                val totalDelta = currentPos - downPos
                                
                                if (!dragStarted) {
                                    if (totalDelta.getDistance() > touchSlop) {
                                        dragStarted = true
                                        dragDirection = if (abs(totalDelta.y) > abs(totalDelta.x)) {
                                            DragDirection.VERTICAL
                                        } else {
                                            DragDirection.HORIZONTAL
                                        }
                                    }
                                }
                                
                                if (dragStarted) {
                                    if (scale.value > 1.05f) {
                                        change.consume()
                                        val contentSize = fittedContentSize()
                                        coroutineScope.launch {
                                            val maxX = viewerPanLimit(scale.value, contentSize.width, size.width.toFloat())
                                            val maxY = viewerPanLimit(scale.value, contentSize.height, size.height.toFloat())
                                            
                                            val targetX = offsetX.value + delta.x
                                            val targetY = offsetY.value + delta.y
                                            
                                            val newX = if (targetX < -maxX) {
                                                -maxX - (-maxX - targetX) * 0.4f
                                            } else if (targetX > maxX) {
                                                maxX + (targetX - maxX) * 0.4f
                                            } else {
                                                targetX
                                            }
                                            
                                            val newY = if (targetY < -maxY) {
                                                -maxY - (-maxY - targetY) * 0.4f
                                            } else if (targetY > maxY) {
                                                maxY + (targetY - maxY) * 0.4f
                                            } else {
                                                targetY
                                            }
                                            
                                            offsetX.snapTo(newX)
                                            offsetY.snapTo(newY)
                                        }
                                    } else {
                                        if (dragDirection == DragDirection.VERTICAL) {
                                            change.consume()
                                            coroutineScope.launch {
                                                offsetY.snapTo(offsetY.value + delta.y)
                                                offsetX.snapTo(offsetX.value + delta.x * 0.3f)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    val releaseTime = System.currentTimeMillis()
                    val dragY = offsetY.value
                    
                    if (!isMultiTouch && !dragStarted && (releaseTime - downTime) < 300L) {
                        val timeDiff = releaseTime - lastTapTime
                        val distDiff = (downPos - lastTapPosition).getDistance()
                        if (timeDiff < doubleTapTimeout && distDiff < touchSlop * 2) {
                            coroutineScope.launch {
                                if (abs(scale.value - 1f) > 0.05f) {
                                    launch { scale.animateTo(1f, spring(stiffness = Spring.StiffnessMedium)) }
                                    launch { offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) }
                                    launch { offsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) }
                                    onScaleChanged(1f)
                                } else {
                                    val targetScale = 2.5f
                                    val targetOffset = viewerOffsetForScale(
                                        currentOffset = Offset(offsetX.value, offsetY.value),
                                        oldScale = scale.value,
                                        newScale = targetScale,
                                        centroid = downPos,
                                        viewportCenter = Offset(size.width / 2f, size.height / 2f)
                                    )
                                    val contentSize = fittedContentSize()
                                    val maxX = viewerPanLimit(targetScale, contentSize.width, size.width.toFloat())
                                    val maxY = viewerPanLimit(targetScale, contentSize.height, size.height.toFloat())
                                    launch { scale.animateTo(targetScale, spring(stiffness = Spring.StiffnessMedium)) }
                                    launch {
                                        offsetX.animateTo(
                                            targetOffset.x.coerceIn(-maxX, maxX),
                                            spring(stiffness = Spring.StiffnessMedium)
                                        )
                                    }
                                    launch {
                                        offsetY.animateTo(
                                            targetOffset.y.coerceIn(-maxY, maxY),
                                            spring(stiffness = Spring.StiffnessMedium)
                                        )
                                    }
                                    onScaleChanged(targetScale)
                                }
                            }
                            lastTapTime = 0L
                            lastTapPosition = Offset.Zero
                        } else {
                            lastTapTime = releaseTime
                            lastTapPosition = downPos
                            coroutineScope.launch {
                                delay(doubleTapTimeout)
                                if (lastTapTime == releaseTime) {
                                    lastTapTime = 0L
                                    lastTapPosition = Offset.Zero
                                    onTap()
                                }
                            }
                        }
                    } else if (isMultiTouch || scale.value > 1.05f) {
                        coroutineScope.launch {
                            val targetScale = viewerReleaseScale(scale.value)
                            if (scale.value != targetScale) {
                                launch { scale.animateTo(targetScale, spring(stiffness = Spring.StiffnessMedium)) }
                                onScaleChanged(targetScale)
                            }
                            
                            val contentSize = fittedContentSize()
                            val maxX = viewerPanLimit(targetScale, contentSize.width, size.width.toFloat())
                            val maxY = viewerPanLimit(targetScale, contentSize.height, size.height.toFloat())
                            val targetX = offsetX.value.coerceIn(-maxX, maxX)
                            val targetY = offsetY.value.coerceIn(-maxY, maxY)
                            
                            if (offsetX.value != targetX) {
                                launch { offsetX.animateTo(targetX, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) }
                            }
                            if (offsetY.value != targetY) {
                                launch { offsetY.animateTo(targetY, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) }
                            }
                        }
                    } else if (dragStarted && dragDirection == DragDirection.VERTICAL) {
                        if (dragY > screenHeightPx * 0.15f) {
                            coroutineScope.launch {
                                offsetY.animateTo(screenHeightPx, spring(stiffness = Spring.StiffnessMedium))
                                onDismiss()
                            }
                        } else if (dragY < -screenHeightPx * 0.08f) {
                            coroutineScope.launch {
                                launch { offsetY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) }
                                launch { offsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) }
                            }
                            onSwipeUp()
                        } else {
                            coroutineScope.launch {
                                launch { offsetY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) }
                                launch { offsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) }
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val dragFraction = (abs(offsetY.value) / screenHeightPx).coerceIn(0f, 1f)
        val backdropAlpha = (1f - dragFraction * 0.8f).coerceIn(0.1f, 1f)
        val viewScale = viewerRenderScale(scale.value, dragFraction)

        // Backdrop fade overlay on vertical drag
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = backdropAlpha))
        )

        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            onSuccess = { success ->
                renderFailed = false
                val intrinsic = success.painter.intrinsicSize
                if (
                    intrinsic.width.isFinite() &&
                    intrinsic.height.isFinite() &&
                    intrinsic.width > 0f &&
                    intrinsic.height > 0f
                ) {
                    imageSize = IntSize(intrinsic.width.roundToInt(), intrinsic.height.roundToInt())
                }
            },
            onError = { renderFailed = true },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = viewScale
                    scaleY = viewScale
                    translationX = offsetX.value
                    translationY = offsetY.value
                    rotationZ = animatedRotation
                }
        )

        if (renderFailed) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.cannot_render_image),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onOpenWith) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.image_gallery_open_with))
                }
            }
        }
    }
}

// Reusable utility method for format size
internal fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format("%.2f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

@OptIn(ExperimentalMaterial3Api::class)
internal enum class DragDirection {
    VERTICAL, HORIZONTAL
}

