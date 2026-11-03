package dev.qtremors.arcile.feature.audio

import dev.qtremors.arcile.core.ui.audio.AudioLyricsScreen
import dev.qtremors.arcile.core.ui.audio.AudioMetadataEditorSheet
import dev.qtremors.arcile.core.ui.audio.AudioPlayerLyrics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.storage.domain.AudioTrack
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.ui.viewer.ViewerActionHost
import dev.qtremors.arcile.core.ui.viewer.ViewerSourceScope
import dev.qtremors.arcile.core.ui.viewer.rememberViewerActionController

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalSharedTransitionApi::class
)
@Composable
internal fun AudioNowPlayingScreen(
    track: AudioTrack,
    queue: List<AudioTrack>,
    playback: AudioPlaybackState,
    visualizerEnabled: Boolean,
    onToggleVisualizer: () -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    predictiveBackEnabled: Boolean = true,
    onCollapse: () -> Unit,
    onTogglePlayback: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onQueueTrack: (String) -> Unit,
    onMoveQueueTrack: (String, Int) -> Unit,
    onRemoveQueueTrack: (String) -> Unit,
    onClearQueue: () -> Unit,
    onSaveQueue: (String, List<String>) -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
    onPlaybackParametersChange: (Float, Float) -> Unit,
    onSleepTimerChange: (Int?) -> Unit,
    onSeek: (Long) -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    tagEditor: AudioTagEditor,
    listeningStore: AudioListeningStore,
    onTagSaved: (AudioTrack) -> Unit,
    onOpenWith: () -> Unit,
    onFileRenamed: (String, FileModel) -> Unit = { _, _ -> },
    onFileDeleted: (String) -> Unit = {}
) {
    var showMetadata by remember { mutableStateOf(false) }
    var showTags by remember { mutableStateOf(false) }
    var lyricsEditorOnly by remember { mutableStateOf(false) }
    var lyricsRevision by remember { mutableIntStateOf(0) }
    var showLyrics by rememberSaveable { mutableStateOf(false) }
    var spectrumVisible by remember { mutableStateOf(false) }
    val levels by AudioPlaybackSpectrum.levels.collectAsStateWithLifecycle()
    val viewerActionController = rememberViewerActionController(
        currentFile = track.file,
        sourceScope = if (track.file.nodeRef.contentUri != null) ViewerSourceScope.External else ViewerSourceScope.Normal,
        onFileRenamed = onFileRenamed, onFileDeleted = onFileDeleted
    )
    DisposableEffect(spectrumVisible, showLyrics, showTags, lyricsEditorOnly) {
        AudioPlaybackSpectrum.setSpectrumVisible(spectrumVisible && !showLyrics && !(showTags && lyricsEditorOnly))
        onDispose { AudioPlaybackSpectrum.setSpectrumVisible(false) }
    }
    val containerModifier = with(sharedTransitionScope) {
        Modifier.fillMaxSize().sharedBounds(
            rememberSharedContentState(AUDIO_PLAYER_CONTAINER_TRANSITION_KEY), animatedVisibilityScope,
            boundsTransform = { _, _ -> spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow) }
        )
    }
    dev.qtremors.arcile.core.ui.audio.AudioNowPlayingScreen(
        track, playback, onCollapse, onTogglePlayback, onPrevious, onNext, onToggleRepeat, onToggleShuffle,
        onPlaybackParametersChange, onSleepTimerChange, onSeek,
        onShowMetadata = { showMetadata = true },
        artwork = { item, artworkModifier -> AudioArtwork(item, artworkModifier, RoundedCornerShape(24.dp)) },
        queueContent = { dismissQueue ->
        val queueById = queue.associateBy { it.file.reference }
        val orderedIds = playback.shuffleMediaIds.takeIf { playback.shuffleEnabled && it.isNotEmpty() }
            ?: playback.queueMediaIds
        AudioQueueSheet(
            queue = orderedIds.mapNotNull(queueById::get),
            currentMediaId = playback.currentMediaId,
            shuffleEnabled = playback.shuffleEnabled,
            onTrackClick = onQueueTrack,
            onMoveTrack = onMoveQueueTrack,
            onRemoveTrack = onRemoveQueueTrack,
            onClearQueue = onClearQueue,
            onSaveQueue = onSaveQueue,
            onDismiss = dismissQueue
        )
        },
        modifier = containerModifier,
        artworkModifier = { modifier -> with(sharedTransitionScope) {
            modifier.sharedBounds(
                rememberSharedContentState(AUDIO_PLAYER_ARTWORK_TRANSITION_KEY), animatedVisibilityScope,
                boundsTransform = { _, _ -> spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow) }
            )
        } },
        coverLyrics = { modifier, showArtwork, expand ->
            key(lyricsRevision) { AudioPlayerLyrics(track, playback.positionMs, tagEditor, onSeek, showArtwork, expand, modifier) }
        },
        onShowLyrics = { showLyrics = true }, onEdit = onEdit,
        onEditTags = { lyricsEditorOnly = false; showTags = true }, onOpenWith = onOpenWith,
        onShare = onShare, viewerActions = viewerActionController.state.allowedActions,
        onViewerAction = viewerActionController::onAction,
        visualizerEnabled = visualizerEnabled, onToggleVisualizer = onToggleVisualizer,
        spectrumLevels = levels, onSpectrumVisibility = { spectrumVisible = it },
        isFavorite = isFavorite, onToggleFavorite = onToggleFavorite,
        predictiveBackEnabled = predictiveBackEnabled
    )
    ViewerActionHost(viewerActionController)
    if (showMetadata) {
        AudioMetadataSheet(
            track = track,
            listeningStore = listeningStore,
            tagEditor = tagEditor,
            onEditTags = {
                showMetadata = false
                lyricsEditorOnly = false
                showTags = true
            },
            onEditLyrics = {
                showMetadata = false
                lyricsEditorOnly = true
                showTags = true
            },
            onDismiss = { showMetadata = false }
        )
    }
    if (showTags) {
        AudioMetadataEditorSheet(
            track = track,
            editor = tagEditor,
            lyricsOnly = lyricsEditorOnly,
            onSaved = { updated ->
                onTagSaved(updated)
                lyricsRevision += 1
                showTags = false
                if (lyricsEditorOnly) showLyrics = true
            },
            onDismiss = {
                showTags = false
                if (lyricsEditorOnly) showLyrics = true
            }
        )
    }
    if (showLyrics) {
        AudioLyricsScreen(
            track = track,
            playback = playback,
            editor = tagEditor,
            onSeek = onSeek,
            onTogglePlayback = onTogglePlayback,
            onPrevious = onPrevious,
            onNext = onNext,
            onEdit = {
                showLyrics = false
                lyricsEditorOnly = true
                showTags = true
            },
            onDismiss = { showLyrics = false }
        )
    }
}
