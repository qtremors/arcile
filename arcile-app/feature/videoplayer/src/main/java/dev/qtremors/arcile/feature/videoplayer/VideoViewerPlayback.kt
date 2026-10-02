package dev.qtremors.arcile.feature.videoplayer

import android.net.Uri
import androidx.core.net.toUri
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.dialogs.DeleteConfirmationDialog
import dev.qtremors.arcile.core.ui.metadata.MediaMetadataDetailLabels
import dev.qtremors.arcile.core.ui.metadata.MediaMetadataSections
import dev.qtremors.arcile.core.ui.image.ThumbnailKey
import dev.qtremors.arcile.core.ui.image.buildThumbnailImageRequest
import dev.qtremors.arcile.core.ui.rememberArcileHaptics
import dev.qtremors.arcile.core.ui.theme.LocalMarqueeFilenames
import dev.qtremors.arcile.core.ui.video.VideoPlaybackSession
import android.app.Activity
import android.content.Context
import android.media.AudioManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.statusBars
import dev.qtremors.arcile.core.ui.video.VideoPlaybackItem
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val DOUBLE_TAP_SEEK_MILLIS = 10_000L
private const val VIDEO_LOADING_INDICATOR_DELAY_MILLIS = 350L
private const val VIDEO_PLAYER_PLACEHOLDER_SIZE_PX = 128

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun VideoPlayerItemView(
    player: Player,
    file: FileModel,
    isPageFocused: Boolean,
    attachPlayerSurface: Boolean,
    isBuffering: Boolean,
    showPlaceholder: Boolean,
    playbackError: PlaybackException?,
    resizeMode: Int,
    onTap: () -> Unit,
    onPlayPauseToggle: () -> Unit = {},
    onToggleMetadata: (Boolean) -> Unit = {},
    onDragDismiss: (Float) -> Unit = {},
    onDragDismissEnd: () -> Unit = {},
    onZoomStateChanged: (Boolean) -> Unit = {}
) {
    var gestureFeedback by remember { mutableStateOf<GestureHudState?>(null) }
    val seekForwardFeedback = stringResource(R.string.video_player_seek_forward)
    val seekBackwardFeedback = stringResource(R.string.video_player_seek_backward)
    val playerDescription = stringResource(R.string.video_player_content_description, file.name)
    val playbackFailed = stringResource(R.string.video_player_playback_failed)

    val context = LocalContext.current
    val activity = remember(context) {
        context as? ComponentActivity ?: context as? Activity
    }
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val minimumSystemGestureInset = with(density) { 32.dp.toPx() }
    val leftSystemGestureInset = max(
        WindowInsets.systemGestures.getLeft(density, layoutDirection).toFloat(),
        minimumSystemGestureInset
    )
    val rightSystemGestureInset = max(
        WindowInsets.systemGestures.getRight(density, layoutDirection).toFloat(),
        minimumSystemGestureInset
    )
    val topSystemGestureInset = max(
        WindowInsets.statusBars.getTop(density).toFloat(),
        minimumSystemGestureInset
    )
    val bottomSystemGestureInset = max(
        WindowInsets.systemGestures.getBottom(density).toFloat(),
        minimumSystemGestureInset
    )
    val thumbnailRequest = remember(context, file) {
        val thumbnailKey = ThumbnailKey.from(file)
        val cacheKey = thumbnailKey.variantKey(VIDEO_PLAYER_PLACEHOLDER_SIZE_PX).cacheKey
        buildThumbnailImageRequest(
            context = context,
            data = thumbnailKey,
            cacheKey = cacheKey,
            sizePx = VIDEO_PLAYER_PLACEHOLDER_SIZE_PX,
            useMemoryCachePlaceholder = true
        )
    }

    if (isPageFocused) {
        var attachedView by remember(file.reference) { mutableStateOf<PlayerView?>(null) }
        DisposableEffect(player, file.reference) {
            onDispose {
                attachedView?.player = null
                attachedView = null
            }
        }

        var activeDragZone by remember { mutableStateOf<GestureZone?>(null) }
        var centerDragAccumulator by remember { mutableFloatStateOf(0f) }
        var currentVolumeFraction by remember { mutableFloatStateOf(0f) }
        var currentBrightnessFraction by remember { mutableFloatStateOf(0f) }
        var zoomScale by remember(file.reference) { mutableFloatStateOf(1f) }
        var zoomOffsetX by remember(file.reference) { mutableFloatStateOf(0f) }
        var zoomOffsetY by remember(file.reference) { mutableFloatStateOf(0f) }
        var showDelayedLoading by remember(file.reference) { mutableStateOf(false) }

        LaunchedEffect(isBuffering, showPlaceholder, file.reference) {
            showDelayedLoading = false
            if (isBuffering && showPlaceholder) {
                kotlinx.coroutines.delay(VIDEO_LOADING_INDICATOR_DELAY_MILLIS)
                showDelayedLoading = true
            }
        }
        DisposableEffect(file.reference) {
            onDispose { onZoomStateChanged(false) }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(player, file.reference, seekForwardFeedback, seekBackwardFeedback) {
                    detectTapGestures(
                        onTap = { onTap() },
                        onDoubleTap = { offset ->
                            val zone = videoPlayerGestureZone(offset.x, size.width.toFloat())
                            when (zone) {
                                GestureZone.LEFT -> {
                                    val duration = player.duration.takeIf { it != C.TIME_UNSET } ?: Long.MAX_VALUE
                                    player.seekTo(max(0L, player.currentPosition - DOUBLE_TAP_SEEK_MILLIS))
                                    gestureFeedback = GestureHudState.Seek(seekBackwardFeedback, isForward = false)
                                }
                                GestureZone.RIGHT -> {
                                    val duration = player.duration.takeIf { it != C.TIME_UNSET } ?: Long.MAX_VALUE
                                    player.seekTo(min(duration, max(0L, player.currentPosition + DOUBLE_TAP_SEEK_MILLIS)))
                                    gestureFeedback = GestureHudState.Seek(seekForwardFeedback, isForward = true)
                                }
                                GestureZone.CENTER -> {
                                    val wasPlaying = player.isPlaying
                                    onPlayPauseToggle()
                                    gestureFeedback = GestureHudState.PlayPause(!wasPlaying)
                                }
                            }
                        }
                    )
                }
                .pointerInput(file.reference) {
                    awaitEachGesture {
                        do {
                            val event = awaitPointerEvent()
                            val pressedPointers = event.changes.count { it.pressed }
                            if (pressedPointers >= 2 || zoomScale > 1f) {
                                val pan = event.calculatePan()
                                val transform = videoZoomTransformAfterGesture(
                                    scale = zoomScale,
                                    offsetX = zoomOffsetX,
                                    offsetY = zoomOffsetY,
                                    zoomChange = event.calculateZoom(),
                                    panX = pan.x,
                                    panY = pan.y,
                                    viewportWidth = size.width.toFloat(),
                                    viewportHeight = size.height.toFloat()
                                )
                                val wasZoomed = zoomScale > 1f
                                zoomScale = transform.scale
                                zoomOffsetX = transform.offsetX
                                zoomOffsetY = transform.offsetY
                                val isZoomed = zoomScale > 1f
                                if (wasZoomed != isZoomed) onZoomStateChanged(isZoomed)
                                event.changes.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .pointerInput(context, activity, audioManager) {
                    detectVerticalDragGestures(
                        onDragStart = { startOffset ->
                            val requestedZone = videoPlayerGestureZone(startOffset.x, size.width.toFloat())
                            activeDragZone = requestedZone.takeUnless {
                                zoomScale > 1f || !videoVerticalGestureAllowed(
                                    startX = startOffset.x,
                                    startY = startOffset.y,
                                    viewportWidth = size.width.toFloat(),
                                    viewportHeight = size.height.toFloat(),
                                    leftGestureInset = leftSystemGestureInset,
                                    rightGestureInset = rightSystemGestureInset,
                                    topGestureInset = topSystemGestureInset,
                                    bottomGestureInset = bottomSystemGestureInset
                                )
                            }
                            centerDragAccumulator = 0f

                            audioManager?.let { am ->
                                val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                val currentVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                                currentVolumeFraction = if (maxVol > 0) currentVol.toFloat() / maxVol.toFloat() else 0f
                            }
                            activity?.window?.let { window ->
                                var b = window.attributes.screenBrightness
                                if (b < 0f) {
                                    b = try {
                                        android.provider.Settings.System.getInt(
                                            context.contentResolver,
                                            android.provider.Settings.System.SCREEN_BRIGHTNESS
                                        ) / 255f
                                    } catch (e: Exception) {
                                        0.5f
                                    }
                                }
                                currentBrightnessFraction = b.coerceIn(0.01f, 1f)
                            }
                        },
                        onVerticalDrag = { _, dragAmount ->
                            val zone = activeDragZone ?: return@detectVerticalDragGestures
                            when (zone) {
                                GestureZone.LEFT -> {
                                    activity?.window?.let { window ->
                                        val sensitivity = 1.2f
                                        val delta = (-dragAmount / size.height.toFloat()) * sensitivity
                                        currentBrightnessFraction = (currentBrightnessFraction + delta).coerceIn(0.01f, 1f)
                                        val layoutParams = window.attributes
                                        layoutParams.screenBrightness = currentBrightnessFraction
                                        window.attributes = layoutParams
                                        gestureFeedback = GestureHudState.Brightness((currentBrightnessFraction * 100).roundToInt())
                                    }
                                }
                                GestureZone.RIGHT -> {
                                    audioManager?.let { am ->
                                        val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                        if (maxVol > 0) {
                                            val sensitivity = 1.2f
                                            val delta = (-dragAmount / size.height.toFloat()) * sensitivity
                                            currentVolumeFraction = (currentVolumeFraction + delta).coerceIn(0f, 1f)
                                            val newVol = (currentVolumeFraction * maxVol).roundToInt().coerceIn(0, maxVol)
                                            am.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                                            val percent = (newVol.toFloat() / maxVol.toFloat() * 100).roundToInt()
                                            gestureFeedback = GestureHudState.Volume(percent)
                                        }
                                    }
                                }
                                GestureZone.CENTER -> {
                                    if (dragAmount < 0f && centerDragAccumulator <= 0f) {
                                        centerDragAccumulator += dragAmount
                                        val threshold = 50.dp.toPx()
                                        if (centerDragAccumulator < -threshold) {
                                            onToggleMetadata(true)
                                            centerDragAccumulator = 0f
                                        }
                                    } else {
                                        centerDragAccumulator = (centerDragAccumulator + dragAmount).coerceAtLeast(0f)
                                        onDragDismiss(centerDragAccumulator)
                                    }
                                }
                            }
                        },
                        onDragEnd = {
                            if (activeDragZone == GestureZone.CENTER) {
                                onDragDismissEnd()
                            }
                            activeDragZone = null
                        },
                        onDragCancel = {
                            if (activeDragZone == GestureZone.CENTER) {
                                onDragDismissEnd()
                            }
                            activeDragZone = null
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = zoomScale
                        scaleY = zoomScale
                        translationX = zoomOffsetX
                        translationY = zoomOffsetY
                    }
            ) {
                if (attachPlayerSurface) {
                    AndroidView(
                        factory = { viewContext ->
                            PlayerView(viewContext).apply {
                                this.player = player
                                useController = false
                                setKeepContentOnPlayerReset(true)
                                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                                this.resizeMode = resizeMode
                                contentDescription = playerDescription
                                attachedView = this
                            }
                        },
                        update = {
                            it.player = player
                            it.setKeepContentOnPlayerReset(true)
                            it.setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                            it.contentDescription = playerDescription
                            it.resizeMode = resizeMode
                            attachedView = it
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (showPlaceholder) {
                    AsyncImage(
                        model = thumbnailRequest,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                    )
                }
            }

            if (shouldShowVideoLoadingIndicator(isBuffering, showPlaceholder, showDelayedLoading)) {
                LoadingIndicator(color = Color.White)
            }

            playbackError?.let { error ->
                Row(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.8f))
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        error.localizedMessage ?: playbackFailed,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    TextButton(
                        onClick = {
                            player.prepare()
                            player.play()
                        }
                    ) {
                        Icon(Icons.Default.Replay, contentDescription = null, tint = Color.White)
                        Text(
                            stringResource(R.string.video_player_retry),
                            color = Color.White,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }

            gestureFeedback?.let { feedback ->
                VideoPlayerGestureHud(
                    state = feedback,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }
                )
                DisposableEffect(feedback) {
                    val callback = Runnable { gestureFeedback = null }
                    val handler = android.os.Handler(android.os.Looper.getMainLooper())
                    handler.postDelayed(callback, 800L)
                    onDispose { handler.removeCallbacks(callback) }
                }
            }
        }
    } else {
        // Render simple thumbnail placeholder when page is not focused
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = thumbnailRequest,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

internal class VideoPlaybackItemResolver(session: VideoPlaybackSession) {
    private val contextFiles = session.files
    private val itemsByContextPath: Map<String, VideoPlaybackItem> =
        if (contextFiles?.size == session.items.size) {
            contextFiles.zip(session.items).associate { (file, item) ->
                normalizedVideoReference(file.reference) to item
            }
        } else {
            emptyMap()
        }
    private val itemsByMediaPath: Map<String, VideoPlaybackItem> = session.items
        .flatMap { item ->
            videoPlaybackReferenceKeys(item).map { reference ->
                normalizedVideoReference(reference) to item
            }
        }
        .toMap()

    fun resolve(file: FileModel, fallbackIndex: Int): VideoPlaybackItem {
        val normalizedPath = normalizedVideoReference(file.reference)
        return itemsByContextPath[normalizedPath]
            ?: itemsByMediaPath[normalizedPath]
            ?: VideoPlaybackItem(
                mediaItem = MediaItem.Builder()
                    .setUri(videoPlaybackUri(file.openableReference()))
                    .setMimeType(file.mimeType)
                    .setMediaId(file.reference)
                    .build(),
                title = file.name
            )
    }
}

internal fun videoPlaybackItemFor(
    file: FileModel,
    fallbackIndex: Int,
    session: VideoPlaybackSession
): VideoPlaybackItem = VideoPlaybackItemResolver(session).resolve(file, fallbackIndex)

internal fun videoPlaybackInitialPath(session: VideoPlaybackSession): String {
    val initialItem = session.items[session.startIndex]
    val contextFiles = session.files
    if (contextFiles?.size == session.items.size) {
        return contextFiles[session.startIndex].reference
    }

    val itemReferences = videoPlaybackReferenceKeys(initialItem)
        .map(::normalizedVideoReference)
        .toSet()
    return contextFiles
        ?.firstOrNull { normalizedVideoReference(it.reference) in itemReferences }
        ?.reference
        ?: videoPlaybackReference(initialItem)
}

internal fun videoPlaybackReference(item: VideoPlaybackItem): String {
    val uri = item.mediaItem.localConfiguration?.uri ?: return ""
    return if (uri.scheme.isNullOrBlank() || uri.scheme == "file") {
        uri.path ?: uri.toString()
    } else {
        uri.toString()
    }
}

private fun videoPlaybackReferenceKeys(item: VideoPlaybackItem): Set<String> {
    val uri = item.mediaItem.localConfiguration?.uri ?: return emptySet()
    return buildSet {
        item.mediaItem.mediaId.takeIf(String::isNotBlank)?.let(::add)
        uri.toString().takeIf(String::isNotBlank)?.let(::add)
        uri.path?.takeIf(String::isNotBlank)?.let(::add)
    }
}

private fun videoPlaybackUri(reference: String): Uri {
    val parsed = reference.toUri()
    return if (parsed.scheme.isNullOrBlank()) Uri.fromFile(File(reference)) else parsed
}

internal fun videoReferencesMatch(first: String, second: String): Boolean =
    normalizedVideoReference(first) == normalizedVideoReference(second)

internal fun videoPlaybackNeedsMediaSwitch(loadedPath: String?, targetPath: String): Boolean =
    loadedPath != targetPath

internal fun nextVideoResizeModeIndex(currentIndex: Int): Int = (currentIndex + 1).mod(3)

internal fun MediaItem.withVideoTitle(title: String): MediaItem = buildUpon()
    .setMediaMetadata(
        mediaMetadata.buildUpon()
            .setTitle(title)
            .setIsPlayable(true)
            .build()
    )
    .build()

private fun normalizedVideoReference(reference: String): String = Uri.decode(reference)

internal data class VideoViewerFileContext(
    val files: List<FileModel>,
    val initialPage: Int
)

internal fun videoViewerFileContextForInitialPath(
    initialPath: String,
    displayedFiles: List<FileModel>,
    allFiles: List<FileModel>
): VideoViewerFileContext {
    val displayedIndex = displayedFiles.indexOfFirst { it.reference == initialPath }
    if (displayedIndex >= 0) {
        return VideoViewerFileContext(displayedFiles, displayedIndex)
    }

    val allIndex = allFiles.indexOfFirst { it.reference == initialPath }
    if (allIndex >= 0) {
        return VideoViewerFileContext(allFiles, allIndex)
    }

    return VideoViewerFileContext(listOf(fileModelFromPath(initialPath)), 0)
}

internal fun videoViewerFileContextAfterInitialization(
    initialPath: String,
    displayedFiles: List<FileModel>,
    allFiles: List<FileModel>
): VideoViewerFileContext {
    val files = displayedFiles.ifEmpty { allFiles }
    if (files.isEmpty()) return VideoViewerFileContext(emptyList(), 0)
    val initialPage = files.indexOfFirst { it.reference == initialPath }
        .takeIf { it >= 0 }
        ?: 0
    return VideoViewerFileContext(files, initialPage)
}

internal fun videoViewerInitialPageForSession(
    initialPath: String,
    viewerSessionInitialPath: String?,
    viewerCurrentPath: String?,
    viewerContext: VideoViewerFileContext
): Int {
    val restoredPath = viewerCurrentPath.takeIf { viewerSessionInitialPath == initialPath }
    return restoredPath
        ?.let { path -> viewerContext.files.indexOfFirst { it.reference == path } }
        ?.takeIf { it >= 0 }
        ?: viewerContext.initialPage
}

internal fun videoViewerPageAfterDatasetChange(
    currentPath: String?,
    currentPage: Int,
    files: List<FileModel>
): Int {
    if (files.isEmpty()) return 0
    val currentPathIndex = files.indexOfFirst { it.reference == currentPath }
    return currentPathIndex.takeIf { it >= 0 } ?: currentPage.coerceIn(files.indices)
}
