package dev.qtremors.arcile.shared.ui.scrollbar

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

interface ScrollbarState {
    val firstVisibleItemIndex: Int
    val totalItemsCount: Int
    val firstVisibleItemScrollOffset: Int
    val isScrollInProgress: Boolean
    suspend fun scrollToItem(index: Int)
    suspend fun scrollBy(value: Float): Float
}

class LazyListScrollbarState(val state: LazyListState) : ScrollbarState {
    override val firstVisibleItemIndex: Int get() = state.firstVisibleItemIndex
    override val totalItemsCount: Int get() = state.layoutInfo.totalItemsCount
    override val firstVisibleItemScrollOffset: Int get() = state.firstVisibleItemScrollOffset
    override val isScrollInProgress: Boolean get() = state.isScrollInProgress
    override suspend fun scrollToItem(index: Int) = state.scrollToItem(index)
    override suspend fun scrollBy(value: Float): Float = state.scrollBy(value)
}

class LazyGridScrollbarState(val state: LazyGridState) : ScrollbarState {
    override val firstVisibleItemIndex: Int get() = state.firstVisibleItemIndex
    override val totalItemsCount: Int get() = state.layoutInfo.totalItemsCount
    override val firstVisibleItemScrollOffset: Int get() = state.firstVisibleItemScrollOffset
    override val isScrollInProgress: Boolean get() = state.isScrollInProgress
    override suspend fun scrollToItem(index: Int) = state.scrollToItem(index)
    override suspend fun scrollBy(value: Float): Float = state.scrollBy(value)
}

class LazyStaggeredGridScrollbarState(val state: LazyStaggeredGridState) : ScrollbarState {
    override val firstVisibleItemIndex: Int get() = state.firstVisibleItemIndex
    override val totalItemsCount: Int get() = state.layoutInfo.totalItemsCount
    override val firstVisibleItemScrollOffset: Int get() = state.firstVisibleItemScrollOffset
    override val isScrollInProgress: Boolean get() = state.isScrollInProgress
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
    if (!enabled) return
    val totalItems = scrollbarState.totalItemsCount
    if (totalItems <= 1) return

    val firstVisibleIndex = scrollbarState.firstVisibleItemIndex
    val firstVisibleOffset = scrollbarState.firstVisibleItemScrollOffset

    var isDragging by remember { mutableStateOf(false) }
    var isPressed by remember { mutableStateOf(false) }
    var dragPositionFraction by remember { mutableFloatStateOf(0f) }
    val coroutineScope = rememberCoroutineScope()

    val scrollFraction = remember(firstVisibleIndex, firstVisibleOffset, totalItems) {
        if (totalItems > 1) firstVisibleIndex.toFloat() / (totalItems - 1).toFloat() else 0f
    }
    val isDirectScrollbarInteraction = isDragging || isPressed
    val activeFraction = if (isDragging) dragPositionFraction else scrollFraction
    val targetIndex = (activeFraction * (totalItems - 1)).toInt().coerceIn(0, totalItems - 1)
    val tooltipText = remember(targetIndex, labelForIndex) { labelForIndex(targetIndex) }

    val thumbWidth by animateDpAsState(
        targetValue = if (isDirectScrollbarInteraction) 24.dp else 6.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "scrollbarThumbWidth"
    )
    val thumbColor by animateColorAsState(
        targetValue = if (isDirectScrollbarInteraction) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
        },
        label = "scrollbarThumbColor"
    )
    val scrollbarAlpha by animateFloatAsState(
        targetValue = if (isDirectScrollbarInteraction || scrollbarState.isScrollInProgress) 1f else 0f,
        label = "scrollbarAlpha"
    )
    val tooltipAlpha by animateFloatAsState(
        targetValue = if (isDirectScrollbarInteraction && tooltipText.isNotBlank()) 1f else 0f,
        label = "scrollbarTooltipAlpha"
    )

    BoxWithConstraints(
        modifier = modifier
            .width(32.dp)
            .padding(contentPadding)
            .graphicsLayer { alpha = scrollbarAlpha }
            .testTag("arcile_fast_scrollbar")
    ) {
        val trackHeight = maxHeight
        val dragModifier = Modifier.pointerInput(totalItems) {
            fun scrollToFraction(fraction: Float) {
                dragPositionFraction = fraction.coerceIn(0f, 1f)
                val targetIdx = (dragPositionFraction * (totalItems - 1)).toInt().coerceIn(0, totalItems - 1)
                coroutineScope.launch { scrollbarState.scrollToItem(targetIdx) }
            }

            detectDragGestures(
                onDragStart = { offset ->
                    isDragging = true
                    isPressed = true
                    scrollToFraction(offset.y / size.height.toFloat())
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
                    scrollToFraction(change.position.y / size.height.toFloat())
                }
            )
        }.pointerInput(totalItems) {
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
                    val tapFraction = (offset.y / size.height.toFloat()).coerceIn(0f, 1f)
                    val targetIdx = (tapFraction * (totalItems - 1)).toInt().coerceIn(0, totalItems - 1)
                    coroutineScope.launch { scrollbarState.scrollToItem(targetIdx) }
                }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(32.dp)
                .align(Alignment.CenterEnd)
                .then(dragModifier)
        ) {
            if (isDirectScrollbarInteraction) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(4.dp)
                        .align(Alignment.CenterEnd)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f), CircleShape)
                )
            }

            val thumbHeight = 24.dp
            val maxOffset = trackHeight - thumbHeight
            val thumbOffset = maxOffset * activeFraction

            if (isDirectScrollbarInteraction) {
                Canvas(
                    modifier = Modifier
                        .offset(y = thumbOffset)
                        .size(thumbWidth)
                        .align(Alignment.TopEnd)
                        .testTag("arcile_fast_scrollbar_hex_thumb")
                ) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val maxRadius = size.minDimension / 2f
                    val numLobes = 10
                    val totalPoints = numLobes * 2
                    val points = List(totalPoints) { index ->
                        val angle = (index * 2.0 * PI / totalPoints).toFloat() + (activeFraction * 2.0 * PI).toFloat()
                        val r = if (index % 2 == 0) maxRadius else maxRadius * 0.72f
                        val x = cx + r * cos(angle)
                        val y = cy + r * sin(angle)
                        Offset(x, y)
                    }
                    val path = Path().apply {
                        val firstMid = Offset(
                            (points[0].x + points[totalPoints - 1].x) / 2f,
                            (points[0].y + points[totalPoints - 1].y) / 2f
                        )
                        moveTo(firstMid.x, firstMid.y)
                        
                        for (i in 0 until totalPoints) {
                            val current = points[i]
                            val next = points[(i + 1) % totalPoints]
                            val mid = Offset((current.x + next.x) / 2f, (current.y + next.y) / 2f)
                            quadraticTo(current.x, current.y, mid.x, mid.y)
                        }
                        close()
                    }
                    drawPath(path, color = thumbColor)
                }
            } else {
                Box(
                    modifier = Modifier
                        .offset(y = thumbOffset)
                        .width(thumbWidth)
                        .size(width = thumbWidth, height = thumbHeight)
                        .align(Alignment.TopEnd)
                        .background(thumbColor, RoundedCornerShape(50))
                        .testTag("arcile_fast_scrollbar_normal_thumb")
                )
            }

            if (tooltipAlpha > 0f) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = MaterialTheme.shapes.extraLarge,
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    modifier = Modifier
                        .offset(x = (-136).dp, y = thumbOffset + (thumbHeight / 2) - 20.dp)
                        .requiredWidthIn(min = 112.dp, max = 160.dp)
                        .graphicsLayer {
                            alpha = tooltipAlpha
                            scaleX = tooltipAlpha
                            scaleY = tooltipAlpha
                        }
                        .align(Alignment.TopEnd)
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
