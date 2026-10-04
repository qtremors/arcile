package dev.qtremors.arcile.feature.videoplayer

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
internal fun VideoViewerWindowEffects(
    keepScreenOn: Boolean,
    immersive: Boolean = true,
    onBackgrounded: (() -> Unit)? = null
) {
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnBackgrounded = rememberUpdatedState(onBackgrounded)

    DisposableEffect(view, keepScreenOn) {
        view.keepScreenOn = keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    DisposableEffect(view, lifecycleOwner, immersive) {
        val activity = view.context.findActivity()
        val window = activity?.window
        val systemUiView = window?.decorView ?: view
        val controller = window?.let { WindowInsetsControllerCompat(it, systemUiView) }
        val previousSystemBarsBehavior = controller?.systemBarsBehavior
        fun enterImmersiveMode() {
            if (!immersive) return
            controller?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        }
        val observer = videoViewerLifecycleObserver(
            activity = activity,
            onForegrounded = ::enterImmersiveMode,
            onBackgrounded = { currentOnBackgrounded.value?.invoke() }
        )
        lifecycleOwner.lifecycle.addObserver(observer)
        enterImmersiveMode()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (immersive) {
                previousSystemBarsBehavior?.let { controller.systemBarsBehavior = it }
                controller?.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}

internal fun videoViewerLifecycleObserver(
    activity: Activity?,
    onForegrounded: () -> Unit,
    onBackgrounded: () -> Unit
): LifecycleEventObserver = LifecycleEventObserver { _, event ->
    when (event) {
        Lifecycle.Event.ON_START,
        Lifecycle.Event.ON_RESUME -> onForegrounded()
        Lifecycle.Event.ON_STOP -> if (activity?.isChangingConfigurations != true) onBackgrounded()
        else -> Unit
    }
}
