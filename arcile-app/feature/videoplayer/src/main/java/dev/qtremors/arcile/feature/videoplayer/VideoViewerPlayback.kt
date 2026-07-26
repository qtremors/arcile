package dev.qtremors.arcile.feature.videoplayer

import android.net.Uri
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
import dev.qtremors.arcile.core.ui.metadata.ImageMetadataDetailLabels
import dev.qtremors.arcile.core.ui.metadata.ImageMetadataSections
import dev.qtremors.arcile.core.ui.metadata.formatImageFileSize
import dev.qtremors.arcile.core.ui.rememberArcileHaptics
import dev.qtremors.arcile.core.ui.theme.LocalMarqueeFilenames
import dev.qtremors.arcile.core.ui.video.VideoPlaybackSession
import android.app.Activity
import android.content.Context
import android.media.AudioManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.WbSunny
import dev.qtremors.arcile.core.ui.video.VideoPlaybackItem
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val DOUBLE_TAP_SEEK_MILLIS = 10_000L

// Gesture compatibility section: horizontal player partitioning
internal enum class GestureZone { LEFT, CENTER, RIGHT }

internal fun videoPlayerGestureZone(x: Float, totalWidth: Float): GestureZone {
    if (totalWidth <= 0f) return GestureZone.CENTER
    return when {
        x < totalWidth / 3f -> GestureZone.LEFT
        x >= 2f * totalWidth / 3f -> GestureZone.RIGHT
        else -> GestureZone.CENTER
    }
}
// HUD feedback state for video player gesture controls
private sealed interface GestureHudState {
    data class Seek(val text: String, val isForward: Boolean) : GestureHudState
    data class PlayPause(val isPlaying: Boolean) : GestureHudState
    data class Brightness(val percentage: Int) : GestureHudState
    data class Volume(val percentage: Int) : GestureHudState
}

@Composable
private fun VideoPlayerGestureHud(
    state: GestureHudState,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color.Black.copy(alpha = 0.75f),
        contentColor = Color.White
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (state) {
                is GestureHudState.Seek -> {
                    Icon(
                        imageVector = if (state.isForward) Icons.Default.FastForward else Icons.Default.FastRewind,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = state.text,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }
                is GestureHudState.PlayPause -> {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                is GestureHudState.Brightness -> {
                    Icon(
                        imageVector = Icons.Default.WbSunny,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "${state.percentage}%",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        LinearProgressIndicator(
                            progress = { state.percentage / 100f },
                            modifier = Modifier
                                .width(70.dp)
                                .height(4.dp),
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.3f)
                        )
                    }
                }
                is GestureHudState.Volume -> {
                    val icon = when {
                        state.percentage == 0 -> Icons.AutoMirrored.Filled.VolumeOff
                        state.percentage < 50 -> Icons.AutoMirrored.Filled.VolumeDown
                        else -> Icons.AutoMirrored.Filled.VolumeUp
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "${state.percentage}%",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        LinearProgressIndicator(
                            progress = { state.percentage / 100f },
                            modifier = Modifier
                                .width(70.dp)
                                .height(4.dp),
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.3f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun VideoPlayerItemView(
    player: Player,
    file: FileModel,
    isPageFocused: Boolean,
    isBuffering: Boolean,
    playbackError: PlaybackException?,
    resizeMode: Int,
    onTap: () -> Unit,
    onPlayPauseToggle: () -> Unit = {},
    onToggleMetadata: (Boolean) -> Unit = {},
    onDragDismiss: (Float) -> Unit = {},
    onDragDismissEnd: () -> Unit = {}
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

    if (isPageFocused) {
        var attachedView by remember(file.absolutePath) { mutableStateOf<PlayerView?>(null) }
        DisposableEffect(player, file.absolutePath) {
            onDispose {
                attachedView?.player = null
                attachedView = null
            }
        }

        var activeDragZone by remember { mutableStateOf<GestureZone?>(null) }
        var centerDragAccumulator by remember { mutableFloatStateOf(0f) }
        var currentVolumeFraction by remember { mutableFloatStateOf(0f) }
        var currentBrightnessFraction by remember { mutableFloatStateOf(0f) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(player, file.absolutePath, seekForwardFeedback, seekBackwardFeedback) {
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
                .pointerInput(context, activity, audioManager) {
                    detectVerticalDragGestures(
                        onDragStart = { startOffset ->
                            activeDragZone = videoPlayerGestureZone(startOffset.x, size.width.toFloat())
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
            AndroidView(
                factory = { viewContext ->
                    PlayerView(viewContext).apply {
                        this.player = player
                        useController = false
                        this.resizeMode = resizeMode
                        contentDescription = playerDescription
                        attachedView = this
                    }
                },
                update = {
                    it.player = player
                    it.contentDescription = playerDescription
                    it.resizeMode = resizeMode
                    attachedView = it
                },
                modifier = Modifier.fillMaxSize()
            )

            if (isBuffering) {
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
                model = ImageRequest.Builder(LocalContext.current)
                    .data(file.absolutePath)
                    .crossfade(true)
                    .build(),
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
                normalizedVideoReference(file.absolutePath) to item
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
        val normalizedPath = normalizedVideoReference(file.absolutePath)
        return itemsByContextPath[normalizedPath]
            ?: itemsByMediaPath[normalizedPath]
            ?: VideoPlaybackItem(
                mediaItem = MediaItem.Builder()
                    .setUri(videoPlaybackUri(file.openableReference()))
                    .setMimeType(file.mimeType)
                    .setMediaId("$fallbackIndex:${file.absolutePath}")
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
        return contextFiles[session.startIndex].absolutePath
    }

    val itemReferences = videoPlaybackReferenceKeys(initialItem)
        .map(::normalizedVideoReference)
        .toSet()
    return contextFiles
        ?.firstOrNull { normalizedVideoReference(it.absolutePath) in itemReferences }
        ?.absolutePath
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
        uri.toString().takeIf(String::isNotBlank)?.let(::add)
        uri.path?.takeIf(String::isNotBlank)?.let(::add)
    }
}

private fun videoPlaybackUri(reference: String): Uri {
    val parsed = Uri.parse(reference)
    return if (parsed.scheme.isNullOrBlank()) Uri.fromFile(File(reference)) else parsed
}

internal fun videoReferencesMatch(first: String, second: String): Boolean =
    normalizedVideoReference(first) == normalizedVideoReference(second)

internal fun videoPlaybackNeedsMediaSwitch(loadedPath: String?, targetPath: String): Boolean =
    loadedPath != targetPath

internal fun nextVideoResizeModeIndex(currentIndex: Int): Int = (currentIndex + 1).mod(3)

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
    val displayedIndex = displayedFiles.indexOfFirst { it.absolutePath == initialPath }
    if (displayedIndex >= 0) {
        return VideoViewerFileContext(displayedFiles, displayedIndex)
    }

    val allIndex = allFiles.indexOfFirst { it.absolutePath == initialPath }
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
    val initialPage = files.indexOfFirst { it.absolutePath == initialPath }
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
        ?.let { path -> viewerContext.files.indexOfFirst { it.absolutePath == path } }
        ?.takeIf { it >= 0 }
        ?: viewerContext.initialPage
}

internal fun videoViewerPageAfterDatasetChange(
    currentPath: String?,
    currentPage: Int,
    files: List<FileModel>
): Int {
    if (files.isEmpty()) return 0
    val currentPathIndex = files.indexOfFirst { it.absolutePath == currentPath }
    return currentPathIndex.takeIf { it >= 0 } ?: currentPage.coerceIn(files.indices)
}
