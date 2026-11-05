package dev.qtremors.arcile.feature.videoplayer

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken

private object ActiveVideoBackgroundPlayback {
    var player: Player? = null

    fun attach(connectedPlayer: Player) {
        player = connectedPlayer
    }

    fun detach(connectedPlayer: Player) {
        if (player === connectedPlayer) player = null
    }
}

@Composable
internal fun rememberVideoBackgroundPlayer(
    enabled: Boolean,
    onCloseRequested: () -> Unit = {}
): Player? {
    if (!enabled) return null
    val context = LocalContext.current.applicationContext
    val currentOnCloseRequested by rememberUpdatedState(onCloseRequested)
    val hostActivity = LocalContext.current.findActivity()
    var controller by remember { mutableStateOf<MediaController?>(null) }
    val controllerFuture = remember(context, hostActivity) {
        val hints = Bundle()
        hostActivity?.let(::standaloneVideoSessionActivity)?.let { pendingIntent ->
            hints.putParcelable(CONNECTION_HINT_SESSION_ACTIVITY, pendingIntent)
        }
        MediaController.Builder(
            context,
            SessionToken(context, ComponentName(context, VideoPlaybackService::class.java))
        ).setConnectionHints(hints).buildAsync()
    }

    DisposableEffect(controllerFuture) {
        var disposed = false
        val closeReceiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent?.action == ACTION_CLOSE_VIDEO_PLAYER) currentOnCloseRequested()
            }
        }
        ContextCompat.registerReceiver(
            context,
            closeReceiver,
            IntentFilter(ACTION_CLOSE_VIDEO_PLAYER),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        controllerFuture.addListener(
            {
                runCatching { controllerFuture.get() }.onSuccess { connected ->
                    if (disposed) {
                        connected.release()
                    } else {
                        ActiveVideoBackgroundPlayback.attach(connected)
                        controller = connected
                    }
                }
            },
            ContextCompat.getMainExecutor(context)
        )
        onDispose {
            disposed = true
            context.unregisterReceiver(closeReceiver)
            controller?.let { connected ->
                ActiveVideoBackgroundPlayback.detach(connected)
                connected.release()
            }
            controller = null
            controllerFuture.cancel(true)
        }
    }
    return controller
}

/**
 * Connection hint carrying the [PendingIntent] the playback notification should open. Only a
 * standalone viewer (opened through ACTION_VIEW from another app) sends one: the in-app viewer
 * lives inside the launcher activity, so the service's default launcher intent already returns
 * to it.
 */
internal const val CONNECTION_HINT_SESSION_ACTIVITY =
    "dev.qtremors.arcile.feature.videoplayer.hint.SESSION_ACTIVITY"

/**
 * Reopens the standalone viewer that started playback. The copy keeps the original data, type
 * and read grant, so if the old instance is gone the new one resolves the same video and
 * reattaches to the item already playing instead of restarting it.
 */
internal fun standaloneVideoSessionActivity(activity: Activity): PendingIntent? {
    val source = activity.intent ?: return null
    if (source.action != Intent.ACTION_VIEW || source.data == null) return null
    val reopen = Intent(source)
        .setComponent(ComponentName(activity, activity.javaClass))
        .setFlags(
            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                (source.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    return PendingIntent.getActivity(
        activity,
        STANDALONE_SESSION_REQUEST_CODE,
        reopen,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

private const val STANDALONE_SESSION_REQUEST_CODE = 1

internal const val ACTION_CLOSE_VIDEO_PLAYER =
    "dev.qtremors.arcile.feature.videoplayer.action.CLOSE_PLAYER"

internal fun stopVideoBackgroundPlayback(context: Context) {
    ActiveVideoBackgroundPlayback.player?.run {
        stop()
        clearMediaItems()
    }
    context.applicationContext.stopService(
        Intent(context.applicationContext, VideoPlaybackService::class.java)
    )
}
