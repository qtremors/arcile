package dev.qtremors.arcile.feature.videoplayer

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.RepeatModeUtil
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import dev.qtremors.arcile.core.ui.video.VideoPlaybackSession
import kotlin.math.max
import kotlin.math.min

@Composable
internal fun GlobalVideoPlayer(
    mediaItems: List<MediaItem>,
    startIndex: Int = 0,
    modifier: Modifier = Modifier,
    dataSourceFactory: DataSource.Factory? = null,
    autoPlay: Boolean = true,
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT,
    onMediaItemChanged: (Int) -> Unit = {}
) {
    require(mediaItems.isNotEmpty())
    require(startIndex in mediaItems.indices)
    val context = LocalContext.current
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val active = lifecycle.isAtLeast(Lifecycle.State.STARTED)
    var savedIndex by rememberSaveable(mediaItems) { mutableIntStateOf(startIndex) }
    var savedPosition by rememberSaveable(mediaItems) { mutableLongStateOf(0L) }
    var resumeWhenReady by rememberSaveable(mediaItems) { mutableStateOf(autoPlay) }
    var playbackError by remember(mediaItems) { mutableStateOf<PlaybackException?>(null) }
    var playbackState by remember(mediaItems) { mutableIntStateOf(Player.STATE_IDLE) }
    var seekFeedback by remember { mutableStateOf<String?>(null) }

    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        if (active) {
            val player = remember(mediaItems, dataSourceFactory, active) {
                val builder = ExoPlayer.Builder(context)
                if (dataSourceFactory != null) {
                    builder.setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory))
                }
                builder.build().apply {
                    setMediaItems(mediaItems, savedIndex.coerceIn(mediaItems.indices), savedPosition)
                    prepare()
                    playWhenReady = resumeWhenReady
                }
            }
            DisposableEffect(player) {
                val listener = object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) { playbackError = error }
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        onMediaItemChanged(player.currentMediaItemIndex.coerceAtLeast(0))
                    }
                    override fun onPlaybackStateChanged(state: Int) {
                        playbackState = state
                        if (state == Player.STATE_READY) playbackError = null
                    }
                }
                player.addListener(listener)
                onMediaItemChanged(player.currentMediaItemIndex.coerceAtLeast(0))
                onDispose {
                    savedIndex = player.currentMediaItemIndex.coerceAtLeast(0)
                    savedPosition = player.currentPosition.coerceAtLeast(0L)
                    resumeWhenReady = player.playWhenReady
                    player.removeListener(listener)
                    player.release()
                }
            }
            AndroidView(
                factory = { viewContext ->
                    PlayerView(viewContext).apply {
                        this.player = player
                        useController = true
                        controllerAutoShow = true
                        controllerHideOnTouch = true
                        setShowPreviousButton(true)
                        setShowNextButton(true)
                        setRepeatToggleModes(RepeatModeUtil.REPEAT_TOGGLE_MODE_ONE or RepeatModeUtil.REPEAT_TOGGLE_MODE_ALL)
                        this.resizeMode = resizeMode
                        contentDescription = "Video player"
                        val detector = GestureDetector(viewContext, object : GestureDetector.SimpleOnGestureListener() {
                            override fun onDown(event: MotionEvent): Boolean = true
                            override fun onDoubleTap(event: MotionEvent): Boolean {
                                val forward = event.x >= width / 2f
                                val delta = if (forward) DOUBLE_TAP_SEEK_MILLIS else -DOUBLE_TAP_SEEK_MILLIS
                                val duration = player.duration.takeIf { it != C.TIME_UNSET } ?: Long.MAX_VALUE
                                player.seekTo(min(duration, max(0L, player.currentPosition + delta)))
                                seekFeedback = if (forward) "+10 seconds" else "−10 seconds"
                                announceForAccessibility(seekFeedback)
                                return true
                            }
                        })
                        setOnTouchListener { _, event -> detector.onTouchEvent(event); false }
                    }
                },
                update = {
                    it.player = player
                    it.resizeMode = resizeMode
                },
                modifier = Modifier.fillMaxSize()
            )
            if (playbackState == Player.STATE_BUFFERING) {
                CircularProgressIndicator(color = Color.White)
            }
            if (playbackState == Player.STATE_ENDED) {
                TextButton(onClick = {
                    player.seekTo(player.currentMediaItemIndex.coerceAtLeast(0), 0L)
                    player.play()
                }) {
                    Icon(Icons.Default.Replay, "Replay", tint = Color.White)
                    Text("Replay", color = Color.White, modifier = Modifier.padding(start = 8.dp))
                }
            }
            playbackError?.let { error ->
                Row(
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.8f)).padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        error.localizedMessage ?: "Video playback failed",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    TextButton(onClick = {
                        playbackError = null
                        player.prepare()
                        player.play()
                    }) { Text("Retry", color = Color.White) }
                }
            }
        }
        seekFeedback?.let { feedback ->
            Text(
                feedback,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.65f)).padding(12.dp)
            )
            DisposableEffect(feedback) {
                val callback = Runnable { seekFeedback = null }
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(callback, 700L)
                onDispose { android.os.Handler(android.os.Looper.getMainLooper()).removeCallbacks(callback) }
            }
        }
    }
}

@Composable
internal fun GlobalVideoViewer(
    session: VideoPlaybackSession,
    onNavigateBack: () -> Unit,
) {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowInsetsControllerCompat(it, view) }
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
    var activeIndex by rememberSaveable(session) { mutableIntStateOf(session.startIndex) }
    var resizeModeIndex by rememberSaveable(session) { mutableIntStateOf(0) }
    val resizeModes = remember { intArrayOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT,
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
        AspectRatioFrameLayout.RESIZE_MODE_FILL
    ) }
    val activeItem = session.items[activeIndex.coerceIn(session.items.indices)]
    BackHandler(onBack = onNavigateBack)
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        GlobalVideoPlayer(
            mediaItems = session.items.map { it.mediaItem },
            startIndex = session.startIndex,
            modifier = Modifier.fillMaxSize(),
            dataSourceFactory = session.dataSourceFactory,
            resizeMode = resizeModes[resizeModeIndex],
            onMediaItemChanged = { activeIndex = it.coerceIn(session.items.indices) }
        )
        Row(
            Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.62f)).statusBarsPadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
            }
            Text(
                activeItem.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            activeItem.onShare?.let { action ->
                IconButton(onClick = action) { Icon(Icons.Default.Share, "Share", tint = Color.White) }
            }
            activeItem.onOpenWith?.let { action ->
                IconButton(onClick = action) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, "Open with", tint = Color.White)
                }
            }
            IconButton(onClick = { resizeModeIndex = (resizeModeIndex + 1) % resizeModes.size }) {
                Icon(
                    Icons.Default.AspectRatio,
                    when (resizeModeIndex) {
                        0 -> "Resize: fit"
                        1 -> "Resize: zoom"
                        else -> "Resize: fill"
                    },
                    tint = Color.White
                )
            }
        }
    }
}

private const val DOUBLE_TAP_SEEK_MILLIS = 10_000L

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
