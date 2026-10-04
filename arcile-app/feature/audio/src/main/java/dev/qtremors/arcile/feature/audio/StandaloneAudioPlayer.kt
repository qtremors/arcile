package dev.qtremors.arcile.feature.audio

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.storage.domain.AudioTrack
import dev.qtremors.arcile.core.storage.domain.FileModel

@OptIn(ExperimentalSharedTransitionApi::class)
@androidx.compose.runtime.Composable
internal fun StandaloneAudioPlayer(
    track: AudioTrack,
    queue: List<AudioTrack>,
    playbackState: AudioPlaybackState,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    playbackController: AudioPlaybackController,
    tagEditor: AudioTagEditor,
    listeningStore: AudioListeningStore,
    launchId: Int,
    allowMini: Boolean,
    miniPlayerBottomClearanceDp: Int,
    onWindowModeChange: (Boolean) -> Unit,
    onFinish: () -> Unit,
    onShare: (AudioTrack) -> Unit,
    onEdit: (AudioTrack) -> Unit,
    onTagSaved: (AudioTrack) -> Unit,
    onOpenWith: (AudioTrack) -> Unit,
    onFileRenamed: (String, FileModel) -> Unit,
    onFileDeleted: (String) -> Unit,
    onRemoveQueueTrack: (String) -> Unit,
    onSaveQueue: (String, List<String>) -> Unit
) {
    var presentation by remember(launchId) {
        mutableStateOf(if (allowMini) AudioPlayerPresentation.MINI else AudioPlayerPresentation.EXPANDED)
    }
    val playerPreferences = rememberAudioPlayerPreferences()
    val visualizerEnabled by playerPreferences.visualizerEnabledState()
    val expanded = presentation == AudioPlayerPresentation.EXPANDED
    val playerTransition = updateTransition(
        targetState = expanded,
        label = "audio-player-expansion"
    )
    val usesFullWindow = presentation != AudioPlayerPresentation.MINI
    val backdropAlpha by playerTransition.animateFloat(
        transitionSpec = { tween(durationMillis = 220) },
        label = "audio-player-backdrop"
    ) { isExpanded ->
        if (isExpanded) 1f else 0f
    }

    LaunchedEffect(
        presentation,
        playerTransition.currentState,
        playerTransition.targetState
    ) {
        if (
            presentation == AudioPlayerPresentation.COLLAPSING &&
            !playerTransition.currentState &&
            !playerTransition.targetState
        ) {
            presentation = AudioPlayerPresentation.MINI
            onWindowModeChange(false)
        }
    }
    BackHandler {
        if (!allowMini) {
            onFinish()
        } else if (presentation != AudioPlayerPresentation.MINI) {
            presentation = AudioPlayerPresentation.COLLAPSING
        }
    }
    SharedTransitionLayout {
        Box(
            modifier = if (usesFullWindow) {
                Modifier
                    .fillMaxSize()
                    .background(
                        MaterialTheme.colorScheme.background.copy(alpha = backdropAlpha)
                    )
            } else {
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            }
        ) {
            playerTransition.AnimatedContent(
                modifier = if (usesFullWindow) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                },
                contentAlignment = Alignment.BottomCenter,
                transitionSpec = {
                    EnterTransition.None togetherWith ExitTransition.None
                },
                contentKey = { it }
            ) { isExpanded ->
                val visibilityScope = this
                if (!isExpanded) {
                    Box(
                        modifier = if (usesFullWindow) {
                            Modifier
                                .fillMaxSize()
                                .navigationBarsPadding()
                                .padding(horizontal = 12.dp, vertical = 12.dp)
                                .padding(bottom = miniPlayerBottomClearanceDp.dp)
                        } else {
                            Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 12.dp, vertical = 12.dp)
                        }.onGloballyPositioned { coordinates ->
                            val parentHeight = coordinates.parentCoordinates?.size?.height
                                ?: return@onGloballyPositioned
                            if (
                                shouldStartAudioPlayerExpansion(
                                    presentation = presentation,
                                    parentHeightPx = parentHeight,
                                    miniPlayerHeightPx = coordinates.size.height
                                )
                            ) {
                                presentation = AudioPlayerPresentation.EXPANDED
                            }
                        },
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        AudioMiniPlayer(
                            track = track,
                            playback = playbackState,
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedVisibilityScope = visibilityScope,
                            onExpand = {
                                if (presentation == AudioPlayerPresentation.MINI) {
                                    presentation =
                                        AudioPlayerPresentation.PREPARING_EXPANSION
                                    onWindowModeChange(true)
                                }
                            },
                            onDismiss = onFinish,
                            onTogglePlayback = playbackController::togglePlayback,
                            onPrevious = playbackController::seekToPrevious,
                            onNext = playbackController::seekToNext
                        )
                    }
                } else {
                    AudioNowPlayingScreen(
                        track = track,
                        queue = queue,
                        playback = playbackState,
                        visualizerEnabled = visualizerEnabled,
                        onToggleVisualizer = {
                            playerPreferences.visualizerEnabled = !playerPreferences.visualizerEnabled
                        },
                        isFavorite = isFavorite,
                        onToggleFavorite = onToggleFavorite,
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = visibilityScope,
                        onCollapse = {
                            if (allowMini) presentation = AudioPlayerPresentation.COLLAPSING
                            else onFinish()
                        },
                        onTogglePlayback = playbackController::togglePlayback,
                        onPrevious = playbackController::seekToPrevious,
                        onNext = playbackController::seekToNext,
                        onQueueTrack = playbackController::seekToQueueMediaId,
                        onMoveQueueTrack = playbackController::moveQueueItem,
                        onRemoveQueueTrack = onRemoveQueueTrack,
                        onClearQueue = {
                            playbackController.closePlayer()
                            onFinish()
                        },
                        onSaveQueue = onSaveQueue,
                        onToggleRepeat = playbackController::toggleRepeatMode,
                        onToggleShuffle = playbackController::toggleShuffle,
                        onPlaybackParametersChange = playbackController::setPlaybackParameters,
                        onSleepTimerChange = playbackController::setSleepTimerMinutes,
                        onSeek = playbackController::seekTo,
                        onShare = { onShare(track) },
                        onEdit = { onEdit(track) },
                        tagEditor = tagEditor,
                        listeningStore = listeningStore,
                        onTagSaved = onTagSaved,
                        onOpenWith = { onOpenWith(track) },
                        onFileRenamed = onFileRenamed,
                        onFileDeleted = onFileDeleted
                    )
                }
            }
        }
    }
}
