package dev.qtremors.arcile.feature.videoplayer

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
    var controller by remember { mutableStateOf<MediaController?>(null) }
    val controllerFuture = remember(context) {
        MediaController.Builder(
            context,
            SessionToken(context, ComponentName(context, VideoPlaybackService::class.java))
        ).buildAsync()
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
