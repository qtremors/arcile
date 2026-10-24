package dev.qtremors.arcile.feature.onlyfiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import dev.qtremors.arcile.core.storage.domain.AudioTrack
import dev.qtremors.arcile.core.ui.audio.AudioQueueSheet
import dev.qtremors.arcile.core.ui.R as UiR
import dev.qtremors.arcile.core.ui.audio.AudioPlaybackProgress
import dev.qtremors.arcile.core.ui.audio.AudioTransportControls
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import dev.qtremors.arcile.core.vault.domain.VaultNodeRef
import dev.qtremors.arcile.core.vault.domain.VaultSeekableReader
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicBoolean

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun VaultAudioPreview(
    node: VaultNodeMetadata,
    queue: List<VaultNodeMetadata>,
    openReader: (VaultNodeRef) -> Result<VaultSeekableReader>,
    onTrackChanged: (VaultNodeRef) -> Unit,
    onQueueEmpty: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val playbackQueue = remember(node.ref.vaultId) { OnlyFilesAudioQueue(queue.ifEmpty { listOf(node) }) }
    val tracks by playbackQueue.nodes.collectAsStateWithLifecycle()
    val latestNode by rememberUpdatedState(node)
    val latestOnTrackChanged by rememberUpdatedState(onTrackChanged)
    var currentIndex by remember { mutableStateOf(0) }
    var showQueue by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var failed by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var duration by remember { mutableStateOf(0L) }
    var position by remember { mutableStateOf(0L) }
    var seekPosition by remember { mutableStateOf<Float?>(null) }
    val synchronizingQueue = remember { AtomicBoolean(false) }

    LaunchedEffect(queue) { playbackQueue.merge(queue) }
    DisposableEffect(playbackQueue, lifecycle) {
        val sourceFactory = DefaultMediaSourceFactory(context).setDataSourceFactory(DataSource.Factory {
            VaultMediaDataSource(playbackQueue.refs, openReader)
        })
        val activePlayer = ExoPlayer.Builder(context).setMediaSourceFactory(sourceFactory).build()
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                playing = player.isPlaying
                ready = player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_ENDED
                duration = player.duration.coerceAtLeast(0L)
                currentIndex = player.currentMediaItemIndex
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                failed = false
                seekPosition = null
                position = 0L
                if (synchronizingQueue.get()) return
                val target = playbackQueue.nodes.value.firstOrNull {
                    it.toSharedFileModel().reference == mediaItem?.mediaId
                } ?: return
                if (target.ref != latestNode.ref) latestOnTrackChanged(target.ref)
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
        lifecycle.addObserver(observer)
        player = activePlayer
        onDispose {
            lifecycle.removeObserver(observer)
            activePlayer.removeListener(listener)
            activePlayer.release()
            playbackQueue.clear()
        }
    }
    LaunchedEffect(tracks, player) {
        val activePlayer = player ?: return@LaunchedEffect
        if (tracks.isEmpty()) {
            activePlayer.clearMediaItems()
            onQueueEmpty()
            return@LaunchedEffect
        }
        synchronizingQueue.set(true)
        try {
            syncVaultAudioPlaylist(activePlayer, tracks, latestNode.toSharedFileModel().reference)
        } finally {
            synchronizingQueue.set(false)
        }
        val selected = tracks.firstOrNull { it.toSharedFileModel().reference == activePlayer.currentMediaItem?.mediaId }
        if (selected != null && selected.ref != latestNode.ref) latestOnTrackChanged(selected.ref)
        if (activePlayer.playbackState == Player.STATE_IDLE) activePlayer.prepare()
    }
    LaunchedEffect(node.ref, player) {
        val activePlayer = player ?: return@LaunchedEffect
        val index = playbackQueue.nodes.value.indexOfFirst { it.ref == node.ref }
        if (index >= 0 && activePlayer.currentMediaItem?.mediaId != node.toSharedFileModel().reference) {
            activePlayer.seekTo(index, 0L)
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
        TextButton(onClick = { showQueue = true }) { Text(stringResource(UiR.string.audio_queue)) }
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
                        onPrevious = if (currentIndex > 0) ({ player?.seekToPreviousMediaItem() }) else null,
                        onTogglePlayback = {
                            player?.let { activePlayer ->
                                if (playing) activePlayer.pause() else {
                                    if (activePlayer.playbackState == Player.STATE_ENDED) activePlayer.seekTo(0)
                                    activePlayer.play()
                                }
                            }
                        },
                        onNext = if (currentIndex in 0 until tracks.lastIndex) ({ player?.seekToNextMediaItem() }) else null
                    )
                }
            }
        }
    }
    if (showQueue) {
        AudioQueueSheet(
            queue = tracks.map { AudioTrack(it.toSharedFileModel(), it.name.substringBeforeLast('.')) },
            currentMediaId = player?.currentMediaItem?.mediaId, shuffleEnabled = false,
            onTrackClick = { reference ->
                val index = tracks.indexOfFirst { it.toSharedFileModel().reference == reference }
                if (index >= 0) player?.seekTo(index, 0L)
                showQueue = false
            },
            onMoveTrack = playbackQueue::move, onRemoveTrack = playbackQueue::remove,
            onClearQueue = playbackQueue::clear, onDismiss = { showQueue = false },
            artwork = { _, artworkModifier -> Icon(Icons.Default.MusicNote, null, artworkModifier) }
        )
    }

}
