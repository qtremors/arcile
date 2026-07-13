package dev.qtremors.arcile.core.ui.scrollbar

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.theme.LocalReducedMotionEnabled
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs

interface ScrollbarState {
    val firstVisibleItemIndex: Int
    val totalItemsCount: Int
    val firstVisibleItemScrollOffset: Int
    val isScrollInProgress: Boolean
    fun scrollFraction(): Float
    fun indexForFraction(fraction: Float): Int
    suspend fun scrollToItem(index: Int)
    suspend fun scrollBy(value: Float): Float
}

class LazyListScrollbarState(val state: LazyListState) : ScrollbarState {
    override val firstVisibleItemIndex: Int get() = state.firstVisibleItemIndex
    override val totalItemsCount: Int get() = state.layoutInfo.totalItemsCount
    override val firstVisibleItemScrollOffset: Int get() = state.firstVisibleItemScrollOffset
    override val isScrollInProgress: Boolean get() = state.isScrollInProgress

    override fun scrollFraction(): Float {
        val info = state.layoutInfo
        val visible = info.visibleItemsInfo
        if (visible.isEmpty() || info.totalItemsCount <= 1) return 0f
        val stride = representativeStride(
            offsets = visible.map { it.offset },
            sizes = visible.map { it.size },
            spacing = info.mainAxisItemSpacing
        )
        val viewportSize = (info.viewportEndOffset - info.viewportStartOffset).coerceAtLeast(1)
        val estimatedVisibleItems = viewportSize / stride
        return continuousScrollFraction(
            firstVisibleIndex = state.firstVisibleItemIndex.toFloat(),
            firstVisibleOffset = state.firstVisibleItemScrollOffset.toFloat(),
            stride = stride,
            totalItems = info.totalItemsCount,
            estimatedVisibleItems = estimatedVisibleItems,
            canScrollBackward = state.canScrollBackward,
            canScrollForward = state.canScrollForward
        )
    }

    override fun indexForFraction(fraction: Float): Int =
        fractionToIndex(fraction, totalItemsCount)

    override suspend fun scrollToItem(index: Int) = state.scrollToItem(index)
    override suspend fun scrollBy(value: Float): Float = state.scrollBy(value)
}

class LazyGridScrollbarState(val state: LazyGridState) : ScrollbarState {
    override val firstVisibleItemIndex: Int get() = state.firstVisibleItemIndex
    override val totalItemsCount: Int get() = state.layoutInfo.totalItemsCount
    override val firstVisibleItemScrollOffset: Int get() = state.firstVisibleItemScrollOffset
    override val isScrollInProgress: Boolean get() = state.isScrollInProgress

    override fun scrollFraction(): Float {
        val info = state.layoutInfo
        val visible = info.visibleItemsInfo
        if (visible.isEmpty() || info.totalItemsCount <= 1) return 0f
        val vertical = info.orientation == androidx.compose.foundation.gestures.Orientation.Vertical
        val lineOffsets = visible
            .groupBy { if (vertical) it.row else it.column }
            .filterKeys { it >= 0 }
            .values
            .map { line -> line.minOf { if (vertical) it.offset.y else it.offset.x } }
            .sorted()
        val lineSizes = visible
            .groupBy { if (vertical) it.row else it.column }
            .filterKeys { it >= 0 }
            .values
            .map { line -> line.maxOf { if (vertical) it.size.height else it.size.width } }
        val stride = representativeStride(lineOffsets, lineSizes, info.mainAxisItemSpacing)
        val span = info.maxSpan.coerceAtLeast(1)
        val viewportSize = (
            if (vertical) info.viewportSize.height else info.viewportSize.width
            ).coerceAtLeast(1)
        val estimatedVisibleItems = (viewportSize / stride) * span
        return continuousScrollFraction(
            firstVisibleIndex = state.firstVisibleItemIndex.toFloat(),
            firstVisibleOffset = state.firstVisibleItemScrollOffset.toFloat() * span,
            stride = stride,
            totalItems = info.totalItemsCount,
            estimatedVisibleItems = estimatedVisibleItems,
            canScrollBackward = state.canScrollBackward,
            canScrollForward = state.canScrollForward
        )
    }

    override fun indexForFraction(fraction: Float): Int =
        fractionToIndex(fraction, totalItemsCount)

    override suspend fun scrollToItem(index: Int) = state.scrollToItem(index)
    override suspend fun scrollBy(value: Float): Float = state.scrollBy(value)
}

class LazyStaggeredGridScrollbarState(val state: LazyStaggeredGridState) : ScrollbarState {
    override val firstVisibleItemIndex: Int get() = state.firstVisibleItemIndex
    override val totalItemsCount: Int get() = state.layoutInfo.totalItemsCount
    override val firstVisibleItemScrollOffset: Int get() = state.firstVisibleItemScrollOffset
    override val isScrollInProgress: Boolean get() = state.isScrollInProgress

    override fun scrollFraction(): Float {
        val info = state.layoutInfo
        val visible = info.visibleItemsInfo
        if (visible.isEmpty() || info.totalItemsCount <= 1) return 0f
        val vertical = info.orientation == androidx.compose.foundation.gestures.Orientation.Vertical
        val offsets = visible.map { if (vertical) it.offset.y else it.offset.x }.sorted()
        val sizes = visible.map { if (vertical) it.size.height else it.size.width }
        val stride = representativeStride(offsets, sizes, info.mainAxisItemSpacing)
        val viewportSize = (
            if (vertical) info.viewportSize.height else info.viewportSize.width
            ).coerceAtLeast(1)
        val lanes = visible
            .map { it.lane }
            .distinct()
            .size
            .coerceAtLeast(1)
        val estimatedVisibleItems = (viewportSize / stride) * lanes
        return continuousScrollFraction(
            firstVisibleIndex = state.firstVisibleItemIndex.toFloat(),
            firstVisibleOffset = state.firstVisibleItemScrollOffset.toFloat() * lanes,
            stride = stride,
            totalItems = info.totalItemsCount,
            estimatedVisibleItems = estimatedVisibleItems,
            canScrollBackward = state.canScrollBackward,
            canScrollForward = state.canScrollForward
        )
    }

    override fun indexForFraction(fraction: Float): Int =
        fractionToIndex(fraction, totalItemsCount)

    override suspend fun scrollToItem(index: Int) = state.scrollToItem(index)
    override suspend fun scrollBy(value: Float): Float = state.scrollBy(value)
}

@Composable
fun ArcileFastScrollbar(
    scrollbarState: ScrollbarState,
    labelForIndex: (Int) -> String,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    enabled: Boolean = true
) {
    if (!enabled || scrollbarState.totalItemsCount <= 1) return

    var isDragging by remember { mutableStateOf(false) }
    var isPressed by remember { mutableStateOf(false) }
    var dragPositionFraction by remember { mutableFloatStateOf(0f) }
    val pendingTarget = remember(scrollbarState) {
        MutableSharedFlow<Int>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
    }
    val totalItems = scrollbarState.totalItemsCount
    val scrollFraction = scrollbarState.scrollFraction()
    val isDirectInteraction = isDragging || isPressed
    val activeFraction = if (isDragging) dragPositionFraction else scrollFraction
    val targetIndex = scrollbarState.indexForFraction(activeFraction)
    val tooltipText = remember(targetIndex, labelForIndex) { labelForIndex(targetIndex) }
    val reducedMotion = LocalReducedMotionEnabled.current
    val motionStretch = remember { Animatable(1f) }
    var previousFraction by remember { mutableFloatStateOf(activeFraction) }
    var motionDirection by remember { mutableIntStateOf(1) }

    LaunchedEffect(scrollbarState) {
        pendingTarget.collectLatest { index ->
            scrollbarState.scrollToItem(index)
        }
    }
    LaunchedEffect(activeFraction, reducedMotion) {
        val delta = activeFraction - previousFraction
        previousFraction = activeFraction
        if (reducedMotion) {
            motionStretch.snapTo(1f)
            return@LaunchedEffect
        }
        val stretch = scrollbarStretchForDelta(delta)
        if (stretch == 1f) return@LaunchedEffect
        motionDirection = if (delta > 0f) 1 else -1
        motionStretch.snapTo(stretch)
        motionStretch.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }

    val thumbColor by animateColorAsState(
        targetValue = if (isDirectInteraction) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
        },
        label = "scrollbarThumbColor"
    )
    val scrollbarAlphaState = animateFloatAsState(
        targetValue = if (isDirectInteraction || scrollbarState.isScrollInProgress) 1f else 0f,
        label = "scrollbarAlpha"
    )
    val fastScrollAlphaState = animateFloatAsState(
        targetValue = if (isDirectInteraction && tooltipText.isNotBlank()) 1f else 0f,
        label = "fastScrollAlpha"
    )

    val isInteractive by remember {
        derivedStateOf { scrollbarAlphaState.value > 0f }
    }
    val showTooltip by remember {
        derivedStateOf { fastScrollAlphaState.value > 0f }
    }

    BoxWithConstraints(
        modifier = modifier
            .width(32.dp)
            .padding(contentPadding)
            .graphicsLayer { alpha = scrollbarAlphaState.value }
            .testTag("arcile_fast_scrollbar")
    ) {
        val thumbHeight = 24.dp
        val maxOffset = (maxHeight - thumbHeight).coerceAtLeast(0.dp)
        val thumbOffset = maxOffset * activeFraction
        val stretchX = 1f - ((motionStretch.value - 1f) * 0.25f)
        val stretchOrigin = TransformOrigin(
            pivotFractionX = 0.5f,
            pivotFractionY = if (motionDirection > 0) 0f else 1f
        )
        var grabOffsetPx by remember { mutableFloatStateOf(0f) }

        fun updateFromTouch(touchY: Float, heightPx: Float, thumbHeightPx: Float) {
            val available = (heightPx - thumbHeightPx).coerceAtLeast(1f)
            val fraction = ((touchY - grabOffsetPx) / available).coerceIn(0f, 1f)
            dragPositionFraction = fraction
            pendingTarget.tryEmit(scrollbarState.indexForFraction(fraction))
        }

        val dragModifier = if (isInteractive) {
            Modifier
                .pointerInput(totalItems) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            isPressed = true
                            val thumbHeightPx = thumbHeight.toPx()
                            val handleTop = activeFraction * (size.height - thumbHeightPx).coerceAtLeast(1f)
                            grabOffsetPx = if (offset.y in handleTop..(handleTop + thumbHeightPx)) {
                                offset.y - handleTop
                            } else {
                                thumbHeightPx / 2f
                            }
                            updateFromTouch(offset.y, size.height.toFloat(), thumbHeightPx)
                        },
                        onDragEnd = {
                            isDragging = false
                            isPressed = false
                        },
                        onDragCancel = {
                            isDragging = false
                            isPressed = false
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            updateFromTouch(change.position.y, size.height.toFloat(), thumbHeight.toPx())
                        }
                    )
                }
                .pointerInput(totalItems) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            try {
                                awaitRelease()
                            } finally {
                                isPressed = false
                            }
                        },
                        onTap = { offset ->
                            grabOffsetPx = thumbHeight.toPx() / 2f
                            updateFromTouch(offset.y, size.height.toFloat(), thumbHeight.toPx())
                        }
                    )
                }
        } else {
            Modifier
        }

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(32.dp)
                .align(Alignment.CenterEnd)
                .then(dragModifier)
        ) {
            if (isDirectInteraction) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(4.dp)
                        .align(Alignment.CenterEnd)
                        .background(
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f),
                            CircleShape
                        )
                )
            }

            Box(
                modifier = Modifier
                    .offset(y = thumbOffset)
                    .size(width = 6.dp, height = thumbHeight)
                    .align(Alignment.TopEnd)
                    .background(thumbColor, RoundedCornerShape(50))
                    .graphicsLayer {
                        scaleX = stretchX
                        scaleY = motionStretch.value
                        transformOrigin = stretchOrigin
                    }
                    .testTag("arcile_fast_scrollbar_normal_thumb")
            )

            if (showTooltip) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = MaterialTheme.shapes.extraLarge,
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    modifier = Modifier
                        .offset(x = (-136).dp, y = thumbOffset + (thumbHeight / 2) - 20.dp)
                        .requiredWidthIn(min = 112.dp, max = 140.dp)
                        .graphicsLayer { alpha = fastScrollAlphaState.value }
                        .align(Alignment.TopEnd)
                        .testTag("arcile_fast_scrollbar_label")
                ) {
                    Text(
                        text = tooltipText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

internal fun continuousScrollFraction(
    firstVisibleIndex: Float,
    firstVisibleOffset: Float,
    stride: Float,
    totalItems: Int,
    estimatedVisibleItems: Float,
    canScrollBackward: Boolean,
    canScrollForward: Boolean
): Float {
    if (totalItems <= 1 || !canScrollBackward) return 0f
    if (!canScrollForward) return 1f
    val safeStride = stride.coerceAtLeast(1f)
    val current = firstVisibleIndex + firstVisibleOffset / safeStride
    val maximum = (totalItems - estimatedVisibleItems).coerceAtLeast(1f)
    return (current / maximum).coerceIn(0f, 1f)
}

internal fun fractionToIndex(fraction: Float, totalItems: Int): Int {
    if (totalItems <= 1) return 0
    return (fraction.coerceIn(0f, 1f) * (totalItems - 1)).toInt()
        .coerceIn(0, totalItems - 1)
}

internal fun scrollbarStretchForDelta(delta: Float): Float {
    if (abs(delta) < 0.0001f) return 1f
    return (1.06f + abs(delta) * 5f).coerceAtMost(1.18f)
}

private fun representativeStride(
    offsets: List<Int>,
    sizes: List<Int>,
    spacing: Int
): Float {
    val offsetSamples = offsets.zipWithNext()
        .map { (first, second) -> (second - first).toFloat() }
        .filter { it > 0f }
    return median(offsetSamples)
        ?: median(sizes.map { it.toFloat() + spacing })
        ?: 1f
}

private fun median(values: List<Float>): Float? {
    val sorted = values.filter { it.isFinite() && it > 0f }.sorted()
    if (sorted.isEmpty()) return null
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 0) {
        (sorted[middle - 1] + sorted[middle]) / 2f
    } else {
        sorted[middle]
    }
}
