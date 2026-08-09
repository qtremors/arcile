package dev.qtremors.arcile.feature.videoplayer

import android.view.View
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
        val previousSystemUiVisibility = systemUiView.systemUiVisibility
        val previousSystemBarsBehavior = controller?.systemBarsBehavior
        fun enterImmersiveMode() {
            if (!immersive) return
            @Suppress("DEPRECATION")
            run {
                systemUiView.systemUiVisibility =
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                        View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            }
            controller?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START,
                Lifecycle.Event.ON_RESUME -> enterImmersiveMode()
                Lifecycle.Event.ON_STOP -> currentOnBackgrounded.value?.invoke()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        enterImmersiveMode()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (immersive) {
                @Suppress("DEPRECATION")
                run { systemUiView.systemUiVisibility = previousSystemUiVisibility }
                previousSystemBarsBehavior?.let { controller?.systemBarsBehavior = it }
                controller?.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}
