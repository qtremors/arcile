package dev.qtremors.arcile.core.ui

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView

@Composable
fun ArcileVideoPlayer(
    mediaItem: MediaItem,
    modifier: Modifier = Modifier,
    dataSourceFactory: DataSource.Factory? = null,
    autoPlay: Boolean = true
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var playbackError by remember(mediaItem) { mutableStateOf<PlaybackException?>(null) }
    val player = remember(mediaItem, dataSourceFactory) {
        val builder = ExoPlayer.Builder(context)
        if (dataSourceFactory != null) {
            builder.setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory))
        }
        builder.build().apply {
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = autoPlay
        }
    }
    DisposableEffect(player, lifecycleOwner) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                playbackError = error
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> player.pause()
                Lifecycle.Event.ON_DESTROY -> player.release()
                else -> Unit
            }
        }
        player.addListener(listener)
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.removeListener(listener)
            player.release()
        }
    }

    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { PlayerView(it).apply { this.player = player } },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize()
        )
        playbackError?.let {
            Text(
                text = it.localizedMessage ?: context.getString(R.string.video_playback_failed),
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(24.dp)
            )
        }
    }
}

@Composable
fun StandaloneVideoViewer(
    mediaItem: MediaItem,
    title: String,
    onNavigateBack: () -> Unit,
    onShare: (() -> Unit)? = null,
    onOpenWith: (() -> Unit)? = null,
    dataSourceFactory: DataSource.Factory? = null
) {
    BackHandler(onBack = onNavigateBack)
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        ArcileVideoPlayer(mediaItem, Modifier.fillMaxSize(), dataSourceFactory)
        Row(
            Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.62f)).statusBarsPadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
            }
            Text(
                title,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            onShare?.let { action ->
                IconButton(onClick = action) { Icon(Icons.Default.Share, null, tint = Color.White) }
            }
            onOpenWith?.let { action ->
                IconButton(onClick = action) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = Color.White)
                }
            }
        }
    }
}
