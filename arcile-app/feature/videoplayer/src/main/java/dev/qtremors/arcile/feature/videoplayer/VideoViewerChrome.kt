package dev.qtremors.arcile.feature.videoplayer

import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.ArcileDropdownMenuItem
import dev.qtremors.arcile.core.ui.SplitButtonGroup
import dev.qtremors.arcile.core.ui.ToolbarAction
import dev.qtremors.arcile.core.ui.image.ThumbnailKey
import dev.qtremors.arcile.core.ui.image.buildThumbnailImageRequest
import dev.qtremors.arcile.core.ui.rememberArcileHaptics
import dev.qtremors.arcile.core.ui.theme.bounceClickable
import dev.qtremors.arcile.core.ui.theme.menuGroupFirst
import dev.qtremors.arcile.core.ui.theme.menuGroupLast
import dev.qtremors.arcile.core.ui.theme.menuGroupMiddle
import dev.qtremors.arcile.core.ui.theme.menuGroupSingle
import kotlinx.coroutines.launch

internal data class VideoViewerChromeActions(
    val onPageSelected: suspend (Int) -> Unit,
    val onToggleFavorite: (String) -> Unit,
    val onDelete: (String) -> Unit,
    val onToggleSelection: (String) -> Unit,
    val onShowMetadata: (String) -> Unit,
    val onOpenWith: (FileModel) -> Unit,
    val onShare: (FileModel) -> Unit,
    val onPlayPauseToggle: () -> Unit,
    val onSeek: (Long) -> Unit,
    val onScrubStateChange: (Boolean) -> Unit,
    val onResizeModeToggle: () -> Unit,
    val onToggleThumbnails: () -> Unit = {}
)

@Composable
internal fun VideoViewerTopChrome(
    visible: Boolean,
    currentFile: FileModel?,
    positionText: String,
    dateText: String,
    resolutionText: String,
    sizeText: String,
    marqueeEnabled: Boolean,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)),
        exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow)),
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = Color.White
                )
            }
            if (currentFile != null) {
                Column(
                    horizontalAlignment = Alignment.Start,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(start = 56.dp, end = 4.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    val titleText = currentFile.name
                    Text(
                        text = if (titleText.isBlank()) positionText else "$positionText • $titleText",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = if (marqueeEnabled) TextOverflow.Clip else TextOverflow.Ellipsis,
                        modifier = if (marqueeEnabled) Modifier.basicMarquee() else Modifier
                    )
                    if (dateText.isNotEmpty() || resolutionText.isNotEmpty() || sizeText.isNotEmpty()) {
                        Text(
                            text = listOf(resolutionText, sizeText, dateText)
                                .filter { it.isNotBlank() }
                                .joinToString(" • "),
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
    }
}

// ──────────────────────────────────────────────────────────────
// Bottom Chrome – matches ImageViewerBottomChrome layout:
// thumbnail carousel + seek bar + SplitButtonGroup + overflow
// ──────────────────────────────────────────────────────────────
@Composable
internal fun VideoViewerBottomChrome(
    visible: Boolean,
    files: List<FileModel>,
    currentPage: Int,
    currentFile: FileModel?,
    favoriteFiles: Set<String>,
    selectedFiles: Set<String>,
    selectionModeEnabled: Boolean,
    readOnly: Boolean,
    isPlaying: Boolean,
    playbackPosition: Long,
    playbackDuration: Long,
    canOpenWith: Boolean,
    canShare: Boolean,
    resizeModeIndex: Int,
    showThumbnails: Boolean = true,
    actions: VideoViewerChromeActions,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val haptics = rememberArcileHaptics()
    val context = LocalContext.current
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val thumbnailEntries = remember(context, files) {
        files.associate { file ->
            val thumbnailKey = ThumbnailKey.from(file)
            val cacheKey = thumbnailKey.variantKey(VIDEO_STRIP_THUMBNAIL_SIZE_PX).cacheKey
            file.absolutePath to VideoStripThumbnailEntry(
                cacheKey = cacheKey,
                request = buildThumbnailImageRequest(
                    context = context,
                    data = thumbnailKey,
                    cacheKey = cacheKey,
                    sizePx = VIDEO_STRIP_THUMBNAIL_SIZE_PX,
                    useMemoryCachePlaceholder = true
                )
            )
        }
    }
    val thumbnailPainterCache = remember { VideoStripLoadedValueCache<Painter>() }
    val thumbnailListState = rememberLazyListState()
    var previousThumbnailPage by remember(files) { mutableStateOf<Int?>(null) }
    var thumbnailStripWasVisible by remember { mutableStateOf(false) }

    LaunchedEffect(visible, showThumbnails, currentPage, files) {
        if (!visible || !showThumbnails) {
            thumbnailStripWasVisible = false
            return@LaunchedEffect
        }
        if (files.isNotEmpty() && currentPage in files.indices) {
            val scrollAction = if (thumbnailStripWasVisible) {
                viewerThumbnailScrollAction(previousThumbnailPage, currentPage)
            } else {
                ViewerThumbnailScrollAction.Jump
            }
            when (scrollAction) {
                ViewerThumbnailScrollAction.Jump -> thumbnailListState.scrollToItem(currentPage)
                ViewerThumbnailScrollAction.Animate -> thumbnailListState.animateScrollToItem(currentPage)
                ViewerThumbnailScrollAction.None -> Unit
            }
            previousThumbnailPage = currentPage
            thumbnailStripWasVisible = true
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)),
        exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow)),
        modifier = if (isLandscape) modifier.fillMaxSize() else modifier.fillMaxWidth()
    ) {
        Box(
            modifier = if (isLandscape) Modifier.fillMaxSize() else Modifier.fillMaxWidth()
        ) {
        if (isLandscape && showThumbnails) {
            LazyColumn(
                state = thumbnailListState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(56.dp)
                    .background(Color.Black.copy(alpha = 0.5f)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                itemsIndexed(files, key = { _, file -> file.absolutePath }) { index, file ->
                    VideoViewerStripThumbnail(
                        file = file,
                        selected = currentPage == index,
                        entry = thumbnailEntries[file.absolutePath],
                        painterCache = thumbnailPainterCache,
                        onClick = { coroutineScope.launch { actions.onPageSelected(index) } }
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // 1. Thumbnail carousel – identical to image viewer
            if (showThumbnails && !isLandscape) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(vertical = 8.dp)
                ) {
                    val thumbnailWidth = 28.dp
                    val thumbnailSidePadding = ((maxWidth - thumbnailWidth) / 2).coerceAtLeast(16.dp)
                    LazyRow(
                        state = thumbnailListState,
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                        contentPadding = PaddingValues(horizontal = thumbnailSidePadding)
                    ) {
                        itemsIndexed(
                            items = files,
                            key = { _, file -> file.absolutePath }
                    ) { index, file ->
                            VideoViewerStripThumbnail(
                                file = file,
                                selected = currentPage == index,
                                entry = thumbnailEntries[file.absolutePath],
                                painterCache = thumbnailPainterCache,
                                onClick = { coroutineScope.launch { actions.onPageSelected(index) } }
                            )
                        }
                    }
                }
            }

            // 2. Playback seek bar (video-specific addition)
            if (currentFile != null) {
                var isSeeking by remember(currentFile.absolutePath) { mutableStateOf(false) }
                var sliderValue by remember(currentFile.absolutePath) { mutableFloatStateOf(0f) }
                var showRemainingTime by remember(currentFile.absolutePath) { mutableStateOf(false) }
                val seekState = videoSeekState(playbackPosition, playbackDuration)

                DisposableEffect(currentFile.absolutePath, actions) {
                    onDispose { actions.onScrubStateChange(false) }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val displayedPosition = if (isSeeking) {
                        videoScrubTargetPosition(sliderValue, playbackDuration)
                    } else {
                        playbackPosition
                    }
                    val currentText = formatPlaybackTime(displayedPosition)
                    val totalText = videoPlaybackEndTimeText(
                        position = displayedPosition,
                        duration = playbackDuration,
                        showRemaining = showRemainingTime
                    )
                    val timerToggleDescription = stringResource(
                        if (showRemainingTime) {
                            R.string.video_player_show_total_time
                        } else {
                            R.string.video_player_show_remaining_time
                        }
                    )

                    Text(
                        text = currentText,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f)
                    )

                    FastVideoScrubSlider(
                        value = if (isSeeking) sliderValue else seekState.progress,
                        enabled = seekState.canSeek,
                        onScrubStart = {
                            isSeeking = true
                            actions.onScrubStateChange(true)
                        },
                        onScrub = { progress ->
                            sliderValue = progress
                            actions.onSeek(videoScrubTargetPosition(progress, playbackDuration))
                        },
                        onScrubEnd = { progress ->
                            sliderValue = progress
                            val targetPosition = videoScrubTargetPosition(progress, playbackDuration)
                            actions.onSeek(targetPosition)
                            isSeeking = false
                            actions.onScrubStateChange(false)
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.24f),
                            disabledThumbColor = Color.White,
                            disabledActiveTrackColor = Color.White,
                            disabledInactiveTrackColor = Color.White.copy(alpha = 0.24f)
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    Box(
                        modifier = Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .semantics { contentDescription = timerToggleDescription }
                            .bounceClickable(enabled = seekState.canSeek) {
                                showRemainingTime = !showRemainingTime
                            }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = totalText,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            // 3. Action buttons row – matches image viewer layout:
            //    SplitButtonGroup (left) + overflow CircleShape button (right)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isFavorite = currentFile != null && currentFile.absolutePath in favoriteFiles
                val favoriteDescription = stringResource(R.string.action_favorite)
                val deleteDescription = stringResource(R.string.action_delete_selected)
                val isSelected = currentFile != null && currentFile.absolutePath in selectedFiles
                val selectionDescription = stringResource(
                    if (isSelected) R.string.deselect_video else R.string.select_video
                )
                val deleteTint = MaterialTheme.colorScheme.error
                val playDescription = stringResource(R.string.video_player_play)
                val pauseDescription = stringResource(R.string.video_player_pause)
                val resizeDescription = stringResource(
                    when (resizeModeIndex) {
                        0 -> R.string.video_player_resize_fit
                        1 -> R.string.video_player_resize_zoom
                        else -> R.string.video_player_resize_fill
                    }
                )
                val nextResizeDescription = stringResource(
                    when (nextVideoResizeModeIndex(resizeModeIndex)) {
                        0 -> R.string.video_player_resize_fit
                        1 -> R.string.video_player_resize_zoom
                        else -> R.string.video_player_resize_fill
                    }
                )
                val toolbarActions = remember(
                    currentFile,
                    isFavorite,
                    favoriteDescription,
                    deleteDescription,
                    isSelected,
                    selectionDescription,
                    selectionModeEnabled,
                    readOnly,
                    deleteTint,
                    isPlaying,
                    playDescription,
                    pauseDescription,
                    resizeDescription,
                    nextResizeDescription,
                    context,
                    actions
                ) {
                    buildList {
                        if (selectionModeEnabled) {
                            add(
                                ToolbarAction(
                                    icon = if (isSelected) {
                                        Icons.Default.CheckCircle
                                    } else {
                                        Icons.Default.RadioButtonUnchecked
                                    },
                                    contentDescription = selectionDescription,
                                    tint = Color.White,
                                    onClick = {
                                        currentFile?.let {
                                            haptics.selectionChanged()
                                            actions.onToggleSelection(it.absolutePath)
                                        }
                                    }
                                )
                            )
                        }
                        // Play/Pause action in the split button group
                        add(
                            ToolbarAction(
                                icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) pauseDescription else playDescription,
                                tint = Color.White,
                                onClick = {
                                    haptics.selectionChanged()
                                    actions.onPlayPauseToggle()
                                }
                            )
                        )
                        // Fit/Resize screen action in main buttons
                        add(
                            ToolbarAction(
                                icon = Icons.Default.AspectRatio,
                                contentDescription = resizeDescription,
                                tint = Color.White,
                                onClick = {
                                    haptics.selectionChanged()
                                    actions.onResizeModeToggle()
                                    Toast.makeText(
                                        context,
                                        nextResizeDescription,
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )
                        )
                        if (!readOnly) {
                            add(
                                ToolbarAction(
                                    icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = favoriteDescription,
                                    tint = if (isFavorite) Color.Red else Color.White,
                                    onClick = {
                                        currentFile?.let {
                                            haptics.selectionChanged()
                                            actions.onToggleFavorite(it.absolutePath)
                                        }
                                    }
                                )
                            )
                        }
                        if (!readOnly) {
                            add(
                                ToolbarAction(
                                    icon = Icons.Default.Delete,
                                    contentDescription = deleteDescription,
                                    tint = deleteTint,
                                    onClick = {
                                        currentFile?.let {
                                            haptics.selectionStart()
                                            actions.onDelete(it.absolutePath)
                                        }
                                    }
                                )
                            )
                        }
                    }
                }

                SplitButtonGroup(
                    actions = toolbarActions,
                    containerColor = Color.Black.copy(alpha = 0.5f),
                    contentColor = Color.White,
                    height = 48.dp,
                    minWidth = 48.dp,
                    iconSize = 24.dp
                )

                VideoViewerOverflowMenu(
                    currentFile = currentFile,
                    readOnly = readOnly,
                    canOpenWith = canOpenWith,
                    canShare = canShare,
                    showThumbnails = showThumbnails,
                    actions = actions
                )
            }
        }
        }
    }
}

@Composable
private fun VideoViewerStripThumbnail(
    file: FileModel,
    selected: Boolean,
    entry: VideoStripThumbnailEntry?,
    painterCache: VideoStripLoadedValueCache<Painter>,
    onClick: () -> Unit
) {
    val animElevation by animateDpAsState(
        targetValue = if (selected) 6.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "thumbnailElevation"
    )
    val animScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.82f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "thumbnailScale"
    )
    Box(
        modifier = Modifier
            .width(36.dp)
            .height(54.dp)
            .zIndex(if (selected) 1f else 0f)
            .graphicsLayer {
                scaleX = animScale
                scaleY = animScale
            }
            .shadow(elevation = animElevation, shape = RoundedCornerShape(4.dp))
            .clip(RoundedCornerShape(4.dp))
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = if (selected) Color.White else Color.Transparent,
                shape = RoundedCornerShape(4.dp)
            )
            .semantics {
                contentDescription = file.name
                this.selected = selected
            }
            .bounceClickable(onClick = onClick)
    ) {
        val loadedPainter = entry?.let { painterCache[it.cacheKey] }
        if (loadedPainter != null) {
            Image(
                painter = loadedPainter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else if (entry != null) {
            AsyncImage(
                model = entry.request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onSuccess = { result -> painterCache.put(entry.cacheKey, result.painter) },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private const val VIDEO_STRIP_THUMBNAIL_SIZE_PX = 128
private const val VIDEO_STRIP_PAINTER_CACHE_SIZE = 64

private data class VideoStripThumbnailEntry(
    val cacheKey: String,
    val request: ImageRequest
)

internal class VideoStripLoadedValueCache<T>(
    private val maxEntries: Int = VIDEO_STRIP_PAINTER_CACHE_SIZE
) {
    init {
        require(maxEntries > 0)
    }

    private val values = mutableStateMapOf<String, T>()
    private val insertionOrder = ArrayDeque<String>()

    operator fun get(cacheKey: String): T? = values[cacheKey]

    fun put(cacheKey: String, value: T) {
        if (values.containsKey(cacheKey)) {
            values[cacheKey] = value
            return
        }
        while (insertionOrder.size >= maxEntries) {
            values.remove(insertionOrder.removeFirst())
        }
        insertionOrder.addLast(cacheKey)
        values[cacheKey] = value
    }
}
