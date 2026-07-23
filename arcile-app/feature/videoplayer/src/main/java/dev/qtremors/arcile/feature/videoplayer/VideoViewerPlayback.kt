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
import dev.qtremors.arcile.core.ui.video.VideoPlaybackItem
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File
import kotlin.math.max
import kotlin.math.min

private const val DOUBLE_TAP_SEEK_MILLIS = 10_000L

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
    onTap: () -> Unit
) {
    var seekFeedback by remember { mutableStateOf<String?>(null) }
    val seekForwardFeedback = stringResource(R.string.video_player_seek_forward)
    val seekBackwardFeedback = stringResource(R.string.video_player_seek_backward)
    val playerDescription = stringResource(R.string.video_player_content_description, file.name)
    val playbackFailed = stringResource(R.string.video_player_playback_failed)

    if (isPageFocused) {
        var attachedView by remember(file.absolutePath) { mutableStateOf<PlayerView?>(null) }
        DisposableEffect(player, file.absolutePath) {
            onDispose {
                attachedView?.player = null
                attachedView = null
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(player, file.absolutePath, seekForwardFeedback, seekBackwardFeedback) {
                    detectTapGestures(
                        onTap = { onTap() },
                        onDoubleTap = { offset ->
                            val forward = offset.x >= size.width / 2f
                            val delta = if (forward) DOUBLE_TAP_SEEK_MILLIS else -DOUBLE_TAP_SEEK_MILLIS
                            val duration = player.duration.takeIf { it != C.TIME_UNSET } ?: Long.MAX_VALUE
                            player.seekTo(min(duration, max(0L, player.currentPosition + delta)))
                            seekFeedback = if (forward) seekForwardFeedback else seekBackwardFeedback
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

            seekFeedback?.let { feedback ->
                Text(
                    feedback,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .semantics { liveRegion = LiveRegionMode.Assertive }
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(12.dp)
                )
                DisposableEffect(feedback) {
                    val callback = Runnable { seekFeedback = null }
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(callback, 700L)
                    onDispose { android.os.Handler(android.os.Looper.getMainLooper()).removeCallbacks(callback) }
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
