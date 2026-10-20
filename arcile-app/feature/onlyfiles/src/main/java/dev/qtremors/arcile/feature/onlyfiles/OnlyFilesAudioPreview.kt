package dev.qtremors.arcile.feature.onlyfiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import dev.qtremors.arcile.core.ui.audio.AudioPlaybackProgress
import dev.qtremors.arcile.core.ui.audio.AudioTransportControls
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import dev.qtremors.arcile.core.vault.domain.VaultNodeRef
import dev.qtremors.arcile.core.vault.domain.VaultSeekableReader
import kotlinx.coroutines.delay

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun VaultAudioPreview(
    node: VaultNodeMetadata,
    openReader: (VaultNodeRef) -> Result<VaultSeekableReader>,
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var failed by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var duration by remember { mutableStateOf(0L) }
    var position by remember { mutableStateOf(0L) }
    var seekPosition by remember { mutableStateOf<Float?>(null) }

    DisposableEffect(node.ref, lifecycle) {
        val source = ProgressiveMediaSource.Factory(DataSource.Factory {
            VaultMediaDataSource(mapOf(node.ref.nodeId.value to node.ref), openReader)
        }).createMediaSource(MediaItem.Builder()
            .setUri("onlyfiles://playback/${node.ref.nodeId.value}")
            .setMimeType(node.mimeType)
            .build())
        val activePlayer = ExoPlayer.Builder(context).build()
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                playing = player.isPlaying
                ready = player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_ENDED
                duration = player.duration.coerceAtLeast(0L)
            }
            override fun onPlayerError(error: PlaybackException) { failed = true }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    activePlayer.playWhenReady = false
                    activePlayer.stop()
                }
                Lifecycle.Event.ON_START -> if (activePlayer.playbackState == Player.STATE_IDLE && !failed) {
                    activePlayer.prepare()
                }
                else -> Unit
            }
        }
        activePlayer.addListener(listener)
        activePlayer.setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
            true
        )
        activePlayer.setHandleAudioBecomingNoisy(true)
        activePlayer.setMediaSource(source)
        activePlayer.prepare()
        lifecycle.addObserver(observer)
        player = activePlayer
        onDispose {
            lifecycle.removeObserver(observer)
            activePlayer.removeListener(listener)
            activePlayer.release()
        }
    }
    LaunchedEffect(player) {
        val activePlayer = player ?: return@LaunchedEffect
        while (true) {
            position = activePlayer.currentPosition.coerceAtLeast(0L)
            delay(250)
        }
    }

    Column(
        modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when {
            failed -> Text(stringResource(R.string.onlyfiles_preview_unavailable), color = Color.White)
            !ready -> CircularProgressIndicator()
            else -> Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AudioPlaybackProgress(
                        positionMs = seekPosition ?: position.toFloat(),
                        durationMs = duration,
                        onPositionChange = { seekPosition = it },
                        onPositionChangeFinished = {
                            seekPosition?.let { player?.seekTo(it.toLong()) }
                            seekPosition = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        seekLabel = stringResource(R.string.onlyfiles_audio_seek)
                    )
                    AudioTransportControls(
                        isPlaying = playing,
                        previousLabel = stringResource(R.string.onlyfiles_audio_previous),
                        playPauseLabel = stringResource(if (playing) R.string.onlyfiles_audio_pause else R.string.onlyfiles_audio_play),
                        nextLabel = stringResource(R.string.onlyfiles_audio_next),
                        onPrevious = onPrevious,
                        onTogglePlayback = {
                            player?.let { activePlayer ->
                                if (playing) activePlayer.pause() else {
                                    if (activePlayer.playbackState == Player.STATE_ENDED) activePlayer.seekTo(0)
                                    activePlayer.play()
                                }
                            }
                        },
                        onNext = onNext
                    )
                }
            }
        }
    }
}
