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
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.ui.ArcileGestureAxis
import dev.qtremors.arcile.core.ui.ArcileGestureDefaults
import dev.qtremors.arcile.core.ui.ArcileSwipeDirection
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.arcileGestureAxis
import dev.qtremors.arcile.core.ui.arcileSwipeDirection
import dev.qtremors.arcile.core.ui.arcileTapEligible
import dev.qtremors.arcile.core.ui.dialogs.DeleteConfirmationDialog
import dev.qtremors.arcile.core.ui.rememberArcileHaptics
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlin.math.abs
import kotlin.math.roundToInt
import java.util.Locale

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
    modifier: Modifier = Modifier,
    imageModifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val screenHeightPx = LocalWindowInfo.current.containerSize.height.toFloat()

    // Animation states for scale & offsets
    val scale = remember { Animatable(1f) }
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    val pointerTransform = remember { MutableStateFlow(ViewerPointerTransform()) }

    LaunchedEffect(pointerTransform) {
        pointerTransform.collect { transform ->
            scale.snapTo(transform.scale)
            offsetX.snapTo(transform.offset.x)
            offsetY.snapTo(transform.offset.y)
        }
    }

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
        pointerTransform.value = ViewerPointerTransform()
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
                val doubleTapTimeout = viewConfiguration.doubleTapTimeoutMillis
                val tapTimeout = viewConfiguration.longPressTimeoutMillis
                var lastTapTime = 0L
                var lastTapPosition = Offset.Zero
                fun fittedContentSize() = viewerFittedContentSize(
                    viewportWidth = size.width.toFloat(),
                    viewportHeight = size.height.toFloat(),
                    imageWidth = imageSize.width.toFloat(),
                    imageHeight = imageSize.height.toFloat(),
                    rotationDegrees = rotation
                )
                
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downTime = down.uptimeMillis
                    val downPos = down.position
                    var lastPosition = downPos
                    var releaseTime = downTime
                    val velocityTracker = VelocityTracker().apply {
                        addPosition(down.uptimeMillis, down.position)
                    }
                    var gestureScale = scale.value
                    var gestureOffset = Offset(offsetX.value, offsetY.value)
                    
                    var isMultiTouch = false
                    var dragStarted = false
                    var dragDirection: ArcileGestureAxis? = null
                    var movementConsumedByAnotherOwner = false
                    
                    while (true) {
                        val event = awaitPointerEvent()
                        val pointers = event.changes
                        pointers.firstOrNull { it.id == down.id }?.let { primary ->
                            lastPosition = primary.position
                            releaseTime = primary.uptimeMillis
                            velocityTracker.addPosition(primary.uptimeMillis, primary.position)
                        }
                        if (pointers.isEmpty() || pointers.all { !it.pressed }) {
                            break
                        }
                        
                        if (pointers.any { pointer ->
                                pointer.id != down.id && (pointer.pressed || pointer.previousPressed)
                            }
                        ) {
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
                            pointerTransform.value = ViewerPointerTransform(constrainedScale, nextOffset)
                            if ((oldScale > 1.05f) != (constrainedScale > 1.05f)) {
                                onScaleChanged(constrainedScale)
                            }
                        } else if (pointers.size == 1 && !isMultiTouch) {
                            val change = pointers[0]
                            if (change.pressed) {
                                val currentPos = change.position
                                val delta = currentPos - change.previousPosition
                                val totalDelta = currentPos - downPos
                                if (!dragStarted && change.isConsumed && delta != Offset.Zero) {
                                    movementConsumedByAnotherOwner = true
                                }
                                
                                if (!dragStarted) {
                                    arcileGestureAxis(
                                        deltaX = totalDelta.x,
                                        deltaY = totalDelta.y,
                                        touchSlop = touchSlop
                                    )?.let { lockedAxis ->
                                        dragStarted = true
                                        dragDirection = lockedAxis
                                    }
                                }
                                
                                if (dragStarted) {
                                    if (gestureScale > 1.05f) {
                                        change.consume()
                                        val contentSize = fittedContentSize()
                                        val maxX = viewerPanLimit(gestureScale, contentSize.width, size.width.toFloat())
                                        val maxY = viewerPanLimit(gestureScale, contentSize.height, size.height.toFloat())

                                        val targetX = gestureOffset.x + delta.x
                                        val targetY = gestureOffset.y + delta.y
                                            
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
                                            
                                        gestureOffset = Offset(newX, newY)
                                        pointerTransform.value = ViewerPointerTransform(gestureScale, gestureOffset)
                                    } else {
                                        if (dragDirection == ArcileGestureAxis.Vertical) {
                                            change.consume()
                                            gestureOffset += Offset(delta.x * 0.3f, delta.y)
                                            pointerTransform.value = ViewerPointerTransform(gestureScale, gestureOffset)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    val dragY = gestureOffset.y
                    val totalMovement = lastPosition - downPos
                    
                    if (
                        !dragStarted && arcileTapEligible(
                            deltaX = totalMovement.x,
                            deltaY = totalMovement.y,
                            durationMillis = releaseTime - downTime,
                            touchSlop = touchSlop,
                            tapTimeoutMillis = tapTimeout,
                            hadMultiplePointers = isMultiTouch,
                            movementConsumedByAnotherOwner = movementConsumedByAnotherOwner
                        )
                    ) {
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
                    } else if (isMultiTouch || gestureScale > 1.05f) {
                        coroutineScope.launch {
                            val targetScale = viewerReleaseScale(gestureScale)
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
                    } else if (dragStarted && dragDirection == ArcileGestureAxis.Vertical) {
                        val velocity = velocityTracker.calculateVelocity()
                        val minimumDistance = if (totalMovement.y >= 0f) {
                            screenHeightPx * 0.15f
                        } else {
                            screenHeightPx * 0.08f
                        }
                        when (
                            arcileSwipeDirection(
                                axis = ArcileGestureAxis.Vertical,
                                deltaX = totalMovement.x,
                                deltaY = totalMovement.y,
                                velocityX = velocity.x,
                                velocityY = velocity.y,
                                minimumDistance = minimumDistance,
                                minimumVelocity = ArcileGestureDefaults.MinimumSwipeVelocity.toPx()
                            )
                        ) {
                            ArcileSwipeDirection.Down -> coroutineScope.launch {
                                offsetY.animateTo(screenHeightPx, spring(stiffness = Spring.StiffnessMedium))
                                onDismiss()
                            }
                            ArcileSwipeDirection.Up -> {
                                coroutineScope.launch {
                                    launch { offsetY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) }
                                    launch { offsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) }
                                }
                                onSwipeUp()
                            }
                            ArcileSwipeDirection.Left,
                            ArcileSwipeDirection.Right,
                            null -> coroutineScope.launch {
                                launch { offsetY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) }
                                launch { offsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) }
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Backdrop fade overlay on vertical drag
        Box(
            modifier = imageModifier
                .fillMaxSize()
                .drawBehind {
                    val dragFraction = (kotlin.math.abs(offsetY.value) / screenHeightPx).coerceIn(0f, 1f)
                    val backdropAlpha = (1f - dragFraction * 0.8f).coerceIn(0.1f, 1f)
                    drawRect(Color.Black.copy(alpha = backdropAlpha))
                }
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
                    val dragFraction = (kotlin.math.abs(offsetY.value) / screenHeightPx).coerceIn(0f, 1f)
                    val viewScale = viewerRenderScale(scale.value, dragFraction)
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

private data class ViewerPointerTransform(
    val scale: Float = 1f,
    val offset: Offset = Offset.Zero
)
