@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package dev.qtremors.arcile.feature.imagegallery

import androidx.activity.compose.PredictiveBackHandler
import dev.qtremors.arcile.core.storage.domain.FileModel
import androidx.compose.foundation.layout.aspectRatio
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.Folder
import androidx.compose.foundation.clickable
import dev.qtremors.arcile.ui.theme.bounceClickable
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilledTonalButton
import dev.qtremors.arcile.core.storage.domain.FileSortOption
import kotlin.math.roundToInt
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredWidthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import dev.qtremors.arcile.shared.ui.SplitButtonGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import dev.qtremors.arcile.core.storage.domain.BrowserPresentationPreferences
import dev.qtremors.arcile.core.storage.domain.BrowserViewMode
import dev.qtremors.arcile.core.storage.domain.ClipboardState
import dev.qtremors.arcile.core.storage.domain.ClipboardOperation
import dev.qtremors.arcile.core.storage.domain.ImageGalleryGrouping
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.image.ArchiveEntryThumbnailData
import dev.qtremors.arcile.image.ThumbnailKey
import dev.qtremors.arcile.image.ThumbnailPolicy
import dev.qtremors.arcile.image.ThumbnailTargetSize
import dev.qtremors.arcile.shared.ui.ArcileFeedbackEvent
import dev.qtremors.arcile.shared.ui.ArcileFeedbackSeverity
import dev.qtremors.arcile.shared.ui.ArcilePullRefreshIndicator
import dev.qtremors.arcile.shared.ui.EmptyState
import dev.qtremors.arcile.shared.ui.EmptyStateVariant
import dev.qtremors.arcile.shared.ui.FloatingSelectionToolbar
import dev.qtremors.arcile.shared.ui.ToolbarAction
import dev.qtremors.arcile.shared.ui.rememberArcileHaptics
import dev.qtremors.arcile.shared.ui.rememberDateTimeFormatter
import dev.qtremors.arcile.shared.ui.dialogs.DeleteConfirmationDialog
import dev.qtremors.arcile.shared.ui.dialogs.PropertiesDialog
import dev.qtremors.arcile.shared.ui.dialogs.RenameDialog
import dev.qtremors.arcile.ui.theme.spacing
import dev.qtremors.arcile.ui.theme.menuGroupFirst
import dev.qtremors.arcile.ui.theme.menuGroupLast
import dev.qtremors.arcile.ui.theme.menuGroupMiddle
import dev.qtremors.arcile.ui.theme.menuGroupSingle
import dev.qtremors.arcile.utils.formatFileSize
import kotlinx.coroutines.flow.SharedFlow

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

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
fun FastScrollbar(
    scrollbarState: ScrollbarState,
    displayedFiles: List<FileModel>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues()
) {
    val totalItems = scrollbarState.totalItemsCount
    if (totalItems <= 1) return

    val firstVisibleIndex = scrollbarState.firstVisibleItemIndex
    val firstVisibleOffset = scrollbarState.firstVisibleItemScrollOffset

    var isDragging by remember { mutableStateOf(false) }
    var isPressed by remember { mutableStateOf(false) }
    var dragPositionFraction by remember { mutableStateOf(0f) }

    val coroutineScope = rememberCoroutineScope()

    val scrollFraction = remember(firstVisibleIndex, firstVisibleOffset, totalItems) {
        if (totalItems > 1) {
            firstVisibleIndex.toFloat() / (totalItems - 1).toFloat()
        } else {
            0f
        }
    }

    val activeFraction = if (isDragging) dragPositionFraction else scrollFraction

    val targetIndex = (activeFraction * (displayedFiles.size - 1)).toInt().coerceIn(0, displayedFiles.size - 1)
    val targetFile = displayedFiles.getOrNull(targetIndex)
    val formatter = remember { java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault()) }
    val dateText = remember(targetFile) {
        if (targetFile != null) {
            formatter.format(java.util.Date(targetFile.lastModified))
        } else {
            ""
        }
    }

    val thumbWidth by animateDpAsState(
        targetValue = if (isDragging) 10.dp else 6.dp,
        label = "scrollbarThumbWidth"
    )
    val thumbColor by animateColorAsState(
        targetValue = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        label = "scrollbarThumbColor"
    )

    val tooltipAlpha by animateFloatAsState(
        targetValue = if ((isDragging || isPressed) && dateText.isNotEmpty()) 1f else 0f,
        label = "scrollbarTooltipAlpha"
    )
    val scrollbarAlpha by animateFloatAsState(
        targetValue = if (isDragging || isPressed || scrollbarState.isScrollInProgress) 1f else 0f,
        label = "scrollbarAlpha"
    )

    BoxWithConstraints(
        modifier = modifier
            .width(24.dp)
            .padding(contentPadding)
            .graphicsLayer { alpha = scrollbarAlpha }
    ) {
        val trackHeight = maxHeight

        val dragModifier = Modifier.pointerInput(totalItems) {
            detectDragGestures(
                onDragStart = { offset ->
                    isDragging = true
                    isPressed = true
                    val y = offset.y.coerceIn(0f, size.height.toFloat())
                    dragPositionFraction = y / size.height.toFloat()
                    val targetIdx = (dragPositionFraction * (totalItems - 1)).toInt().coerceIn(0, totalItems - 1)
                    coroutineScope.launch {
                        scrollbarState.scrollToItem(targetIdx)
                    }
                },
                onDragEnd = {
                    isDragging = false
                    isPressed = false
                },
                onDragCancel = {
                    isDragging = false
                    isPressed = false
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    val currentY = change.position.y.coerceIn(0f, size.height.toFloat())
                    dragPositionFraction = currentY / size.height.toFloat()
                    val targetIdx = (dragPositionFraction * (totalItems - 1)).toInt().coerceIn(0, totalItems - 1)
                    coroutineScope.launch {
                        scrollbarState.scrollToItem(targetIdx)
                    }
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
                    val y = offset.y.coerceIn(0f, size.height.toFloat())
                    val tapFraction = y / size.height.toFloat()
                    val targetIdx = (tapFraction * (totalItems - 1)).toInt().coerceIn(0, totalItems - 1)
                    coroutineScope.launch {
                        scrollbarState.scrollToItem(targetIdx)
                    }
                }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(24.dp)
                .align(Alignment.CenterEnd)
                .then(dragModifier)
        ) {
            if (isDragging || isPressed) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(4.dp)
                        .align(Alignment.CenterEnd)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f), CircleShape)
                )
            }

            val thumbHeight = 24.dp
            val maxOffset = trackHeight - thumbHeight
            val thumbOffset = maxOffset * activeFraction

            Canvas(
                modifier = Modifier
                    .offset(y = thumbOffset)
                    .size(24.dp)
                    .align(Alignment.TopEnd)
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
            ) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val outerRadiusPx = size.width / 2f
                val holeRadiusPx = outerRadiusPx * 0.2f
                
                val rotationAngle = activeFraction * 360f * 4f
                
                rotate(degrees = rotationAngle, pivot = Offset(cx, cy)) {
                    val path = Path()
                    val numLobes = 12
                    val amplitude = outerRadiusPx * 0.08f
                    val steps = 120
                    for (i in 0..steps) {
                        val angle = (i * 2f * Math.PI / steps).toFloat()
                        val r = outerRadiusPx - amplitude + amplitude * cos(numLobes * angle)
                        val x = cx + r * cos(angle)
                        val y = cy + r * sin(angle)
                        if (i == 0) {
                            path.moveTo(x, y)
                        } else {
                            path.lineTo(x, y)
                        }
                    }
                    path.close()
                    drawPath(path, color = thumbColor)
                    
                    drawCircle(
                        color = Color.Transparent,
                        radius = holeRadiusPx,
                        center = Offset(cx, cy),
                        blendMode = BlendMode.Clear
                    )
                }
            }

            if (tooltipAlpha > 0f) {
                val tooltipOffset = thumbOffset + (thumbHeight / 2) - 20.dp
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = WavyShape(numLobes = 12, amplitudeFraction = 0.06f),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    modifier = Modifier
                        .offset(
                            x = (-132).dp,
                            y = tooltipOffset
                        )
                        .requiredWidthIn(min = 112.dp, max = 156.dp)
                        .graphicsLayer {
                            alpha = tooltipAlpha
                            scaleX = tooltipAlpha
                            scaleY = tooltipAlpha
                        }
                        .align(Alignment.TopEnd)
                ) {
                    Text(
                        text = dateText,
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

enum class GalleryViewState {
    PHOTOS_TAB, ALBUMS_TAB_GRID, ALBUM_PHOTOS
}

fun Modifier.pinchToResize(
    currentCellSize: Float,
    minSize: Float = 96f,
    maxSize: Float = 256f,
    onSizeChanged: (Float) -> Unit,
    onSizeFinalized: (Float) -> Unit
): Modifier = this.pointerInput(currentCellSize) {
    var accumulatedScale = 1f
    var startCellSize = currentCellSize
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        accumulatedScale = 1f
        startCellSize = currentCellSize
        var isPinching = false

        do {
            val event = awaitPointerEvent()
            val zoom = event.calculateZoom()
            if (event.changes.size >= 2) {
                isPinching = true
                accumulatedScale *= zoom
                val nextSize = (startCellSize * accumulatedScale).coerceIn(minSize, maxSize)
                onSizeChanged(nextSize)
                event.changes.forEach { it.consume() }
            }
        } while (event.changes.any { it.pressed })

        if (isPinching) {
            val finalSize = (startCellSize * accumulatedScale).coerceIn(minSize, maxSize)
            onSizeFinalized(finalSize)
        }
    }
}

class WavyShape(
    private val numLobes: Int = 12,
    private val amplitudeFraction: Float = 0.08f
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val rBase = minOf(size.width, size.height) / 2f
        val amplitude = rBase * amplitudeFraction
        val path = Path()
        val steps = 180
        for (i in 0..steps) {
            val angle = (i * 2f * Math.PI / steps).toFloat()
            val r = rBase - amplitude + amplitude * cos(numLobes * angle)
            val rx = r * cos(angle)
            val ry = r * sin(angle)
            val x = cx + rx * (size.width / (rBase * 2f))
            val y = cy + ry * (size.height / (rBase * 2f))
            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        path.close()
        return Outline.Generic(path)
    }
}
