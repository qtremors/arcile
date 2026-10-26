package dev.qtremors.arcile.feature.onlyfiles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import dev.qtremors.arcile.core.storage.domain.AudioTrack
import dev.qtremors.arcile.core.ui.audio.AudioArtwork
import dev.qtremors.arcile.core.ui.audio.AudioMetadataSheet
import dev.qtremors.arcile.core.ui.audio.AudioNowPlayingScreen
import dev.qtremors.arcile.core.ui.audio.AudioPlaybackSpectrum
import dev.qtremors.arcile.core.ui.audio.AudioPlaybackState
import dev.qtremors.arcile.core.ui.audio.AudioQueueSheet
import dev.qtremors.arcile.core.ui.audio.AudioSleepTimer
import dev.qtremors.arcile.core.ui.audio.AudioVisualizerRenderersFactory
import dev.qtremors.arcile.core.ui.audio.audioPlaybackSnapshot
import dev.qtremors.arcile.core.ui.audio.rememberAudioPlayerPreferences
import dev.qtremors.arcile.core.ui.audio.removeAudioQueueItems
import dev.qtremors.arcile.core.ui.audio.visualizerEnabledState
import dev.qtremors.arcile.core.ui.viewer.ViewerFileAction
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import dev.qtremors.arcile.core.vault.domain.VaultNodeRef
import dev.qtremors.arcile.core.vault.domain.VaultSeekableReader
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.delay

/** Owns the private player and delegates every control and gesture to the shared audio UI. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun VaultAudioPreview(
    node: VaultNodeMetadata,
    queue: List<VaultNodeMetadata>,
    openReader: (VaultNodeRef) -> Result<VaultSeekableReader>,
    onTrackChanged: (VaultNodeRef) -> Unit,
    onQueueEmpty: () -> Unit,
    onBack: () -> Unit,
    allowedActions: Set<ViewerFileAction>,
    onAction: (ViewerFileAction) -> Unit,
    location: String,
    nextPageToken: String?,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val playbackQueue = remember(node.ref.vaultId) { OnlyFilesAudioQueue(queue.ifEmpty { listOf(node) }) }
    val tracks by playbackQueue.nodes.collectAsStateWithLifecycle()
    val latestNode by rememberUpdatedState(node)
    val latestOnTrackChanged by rememberUpdatedState(onTrackChanged)
    val scope = rememberCoroutineScope()
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var playback by remember { mutableStateOf(AudioPlaybackState()) }
    var showMetadata by remember { mutableStateOf(false) }
    val preferences = rememberAudioPlayerPreferences()
    val visualizer by preferences.visualizerEnabledState()
    val spectrum = remember { AudioPlaybackSpectrum() }
    val levels by spectrum.levels.collectAsStateWithLifecycle()
    val timer = remember(scope) {
        AudioSleepTimer(scope) {
            player?.pause()
            playback = playback.copy(sleepTimerEndElapsedMs = null)
        }
    }
    val synchronizingQueue = remember { AtomicBoolean(false) }
    val loadedMetadata by produceState<Pair<VaultNodeMetadata, VaultAudioMetadata>?>(null, node) {
        value = node to readVaultAudioMetadata(node, openReader)
    }
    val metadata = loadedMetadata?.takeIf { it.first == node }?.second ?: VaultAudioMetadata(vaultAudioTrack(node), null)
    val sharedTracks = remember(tracks, metadata) {
        tracks.map { if (it.ref == node.ref) metadata.track else vaultAudioTrack(it) }
    }
    val nodesByReference = remember(tracks) { tracks.associateBy { it.toSharedFileModel().reference } }
    val artwork: @Composable (AudioTrack, Modifier) -> Unit = { track, artworkModifier ->
        AudioArtwork(
            track, artworkModifier,
            model = nodesByReference[track.file.reference]?.thumbnailRequest(512)
        )
    }

    LaunchedEffect(queue) { playbackQueue.merge(queue) }
    DisposableEffect(playbackQueue, lifecycle) {
        val sourceFactory = DefaultMediaSourceFactory(context).setDataSourceFactory(DataSource.Factory {
            VaultMediaDataSource(playbackQueue.refs, openReader)
        })
        val activePlayer = ExoPlayer.Builder(context, AudioVisualizerRenderersFactory(context, spectrum))
            .setMediaSourceFactory(sourceFactory).build()
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                spectrum.setPlaying(player.isPlaying)
                playback = audioPlaybackSnapshot(player, playback)
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                playback = playback.copy(error = false)
                if (synchronizingQueue.get()) return
                val target = playbackQueue.nodes.value.firstOrNull {
                    it.toSharedFileModel().reference == mediaItem?.mediaId
                } ?: return
                if (target.ref != latestNode.ref) latestOnTrackChanged(target.ref)
            }
            override fun onPlayerError(error: PlaybackException) { playback = playback.copy(error = true) }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    timer.cancel()
                    playback = playback.copy(sleepTimerEndElapsedMs = null)
                    activePlayer.playWhenReady = false
                    activePlayer.stop()
                    spectrum.clear()
                }
                Lifecycle.Event.ON_START -> if (activePlayer.playbackState == Player.STATE_IDLE && !playback.error) activePlayer.prepare()
                else -> Unit
            }
        }
        activePlayer.addListener(listener)
        activePlayer.setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true
        )
        activePlayer.setHandleAudioBecomingNoisy(true)
        activePlayer.playbackParameters = PlaybackParameters(preferences.playbackSpeed, preferences.playbackPitch)
        activePlayer.playWhenReady = true
        lifecycle.addObserver(observer)
        player = activePlayer
        onDispose {
            timer.cancel()
            lifecycle.removeObserver(observer)
            activePlayer.removeListener(listener)
            activePlayer.release()
            spectrum.setPlaying(false)
            spectrum.clear()
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
        if (index >= 0 && activePlayer.currentMediaItem?.mediaId != node.toSharedFileModel().reference) activePlayer.seekTo(index, 0L)
    }
    LaunchedEffect(playback.currentMediaId, playback.queueMediaIds, playback.shuffleMediaIds, nextPageToken) {
        val order = playback.shuffleMediaIds.takeIf { playback.shuffleEnabled && it.isNotEmpty() } ?: playback.queueMediaIds
        val index = order.indexOf(playback.currentMediaId)
        if (nextPageToken != null && index >= 0 && index >= order.lastIndex - 1) onLoadMore()
    }
    LaunchedEffect(player) {
        val activePlayer = player ?: return@LaunchedEffect
        while (true) {
            if (activePlayer.isPlaying && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                playback = audioPlaybackSnapshot(activePlayer, playback, refreshQueue = false)
            }
            delay(250)
        }
    }

    AudioNowPlayingScreen(
        track = metadata.track, playback = playback, onCollapse = onBack,
        onTogglePlayback = {
            player?.let {
                playback = playback.copy(error = false)
                if (it.isPlaying) it.pause() else {
                    if (it.playbackState == Player.STATE_IDLE) it.prepare()
                    if (it.playbackState == Player.STATE_ENDED) it.seekToDefaultPosition()
                    it.play()
                }
            }
        },
        onPrevious = { player?.seekToPreviousMediaItem() }, onNext = { player?.seekToNextMediaItem() },
        onToggleRepeat = { player?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        } },
        onToggleShuffle = { player?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } },
        onPlaybackParametersChange = { speed, pitch ->
            preferences.setPlaybackParameters(speed, pitch)
            player?.playbackParameters = PlaybackParameters(preferences.playbackSpeed, preferences.playbackPitch)
        },
        onSleepTimerChange = {
            timer.setMinutes(it)
            playback = playback.copy(sleepTimerEndElapsedMs = timer.endElapsedMs)
        },
        onSeek = { player?.seekTo(it) }, onShowMetadata = { showMetadata = true },
        artwork = { track, artworkModifier ->
            AudioArtwork(track, artworkModifier, RoundedCornerShape(24.dp), model = nodesByReference[track.file.reference]?.thumbnailRequest(512))
        },
        queueContent = { dismiss ->
            val byId = sharedTracks.associateBy { it.file.reference }
            val order = playback.shuffleMediaIds.takeIf { playback.shuffleEnabled && it.isNotEmpty() } ?: playback.queueMediaIds
            AudioQueueSheet(
                queue = order.mapNotNull(byId::get), currentMediaId = playback.currentMediaId,
                shuffleEnabled = playback.shuffleEnabled,
                onTrackClick = { reference ->
                    val index = sharedTracks.indexOfFirst { it.file.reference == reference }
                    if (index >= 0) { player?.seekToDefaultPosition(index); player?.play() }
                    dismiss()
                },
                onMoveTrack = playbackQueue::move,
                onRemoveTrack = { reference ->
                    player?.let { removeAudioQueueItems(it, setOf(reference)) }
                    playbackQueue.remove(reference)
                },
                onClearQueue = { player?.clearMediaItems(); playbackQueue.clear() },
                onDismiss = dismiss, artwork = artwork
            )
        },
        modifier = modifier, onOpenWith = { onAction(ViewerFileAction.OpenWith) }.takeIf { ViewerFileAction.OpenWith in allowedActions },
        onShare = { onAction(ViewerFileAction.Share) }.takeIf { ViewerFileAction.Share in allowedActions },
        viewerActions = allowedActions, onViewerAction = onAction,
        visualizerEnabled = visualizer, onToggleVisualizer = { preferences.visualizerEnabled = !visualizer },
        spectrumLevels = levels, onSpectrumVisibility = spectrum::setSpectrumVisible,
        privateSession = true
    )
    if (showMetadata) AudioMetadataSheet(
        metadata.track, metadata.details, { showMetadata = false }, artwork,
        location = "$location / ${node.name}"
    )
}
