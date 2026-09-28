package dev.qtremors.arcile.feature.audio

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(UnstableApi::class)
internal class AudioPlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private lateinit var queueStore: AudioPlaybackQueueStore
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var positionJob: Job? = null
    private val visualizerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            AudioPlaybackSpectrum.setPlaying(isPlaying)
            positionJob?.cancel()
            mediaSession?.player?.let(queueStore::savePosition)
            if (isPlaying) {
                positionJob = serviceScope.launch {
                    while (isActive) {
                        delay(5_000L)
                        mediaSession?.player?.let(queueStore::savePosition)
                    }
                }
            }
        }

        override fun onEvents(player: Player, events: Player.Events) {
            if (events.contains(Player.EVENT_TIMELINE_CHANGED)) {
                queueStore.saveQueue(player)
            } else if (
                events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) ||
                events.contains(Player.EVENT_POSITION_DISCONTINUITY) ||
                events.contains(Player.EVENT_REPEAT_MODE_CHANGED) ||
                events.contains(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED)
            ) {
                queueStore.savePosition(player)
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            AudioPlaybackSpectrum.clear()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
                AudioPlaybackSpectrum.clear()
            }
        }
    }
    private val closePlayerCommand = SessionCommand(ACTION_CLOSE_AUDIO_PLAYER, Bundle.EMPTY)
    private val sessionCallback = object : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult =
            MediaSession.ConnectionResult.AcceptedResultBuilder()
                .setAvailableSessionCommands(
                    MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                        .buildUpon()
                        .add(closePlayerCommand)
                        .build()
                )
                .setAvailablePlayerCommands(MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS)
                .build()

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            if (customCommand == closePlayerCommand) {
                session.player.stop()
                session.player.clearMediaItems()
                queueStore.saveQueue(session.player)
                sendBroadcast(
                    Intent(ACTION_CLOSE_AUDIO_PLAYER).setPackage(packageName)
                )
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
        }
    }

    @UnstableApi
    override fun onCreate() {
        super.onCreate()
        queueStore = AudioPlaybackQueueStore(this)
        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .build()
            .apply { setSmallIcon(R.drawable.ic_arcile_notification) }
        setMediaNotificationProvider(notificationProvider)
        val player = ExoPlayer.Builder(this, AudioVisualizerRenderersFactory(this))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        queueStore.read()?.let { saved ->
            player.setMediaItems(saved.items, saved.index, saved.positionMs)
            player.repeatMode = saved.repeatMode
            player.shuffleModeEnabled = saved.shuffleEnabled
            player.prepare()
        }
        player.addListener(visualizerListener)
        val sessionActivity = packageManager.getLaunchIntentForPackage(packageName)?.let { intent ->
            PendingIntent.getActivity(
                this,
                0,
                intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra(AudioFeatureEntryPoint.EXTRA_OPEN_PLAYER, true),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        mediaSession = MediaSession.Builder(this, player)
            .apply { sessionActivity?.let(::setSessionActivity) }
            .setCallback(sessionCallback)
            .build()
            .apply {
                setMediaButtonPreferences(
                    listOf(
                        CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                            .setCustomIconResId(R.drawable.ic_audio_close_notification)
                            .setDisplayName(getString(R.string.audio_close_player))
                            .setSessionCommand(closePlayerCommand)
                            .setSlots(CommandButton.SLOT_OVERFLOW)
                            .build()
                    )
                )
            }
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaSession? = mediaSession

    override fun onDestroy() {
        positionJob?.cancel()
        AudioPlaybackSpectrum.setPlaying(false)
        AudioPlaybackSpectrum.clear()
        mediaSession?.run {
            queueStore.savePosition(player)
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
