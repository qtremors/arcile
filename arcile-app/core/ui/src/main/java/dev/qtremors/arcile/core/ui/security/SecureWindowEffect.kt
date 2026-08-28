package dev.qtremors.arcile.core.ui.security

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import java.util.WeakHashMap

/**
 * Keeps screenshot protection enabled until every sensitive destination using the window is gone.
 * This prevents a departing destination from clearing protection still required by its successor.
 */
@Composable
fun SecureWindowEffect(enabled: Boolean) {
    val window = LocalView.current.context.findActivity()?.window
    DisposableEffect(window, enabled) {
        if (!enabled || window == null) return@DisposableEffect onDispose {}

        val owner = Any()
        secureWindowOwners.acquire(window, owner)
        onDispose { secureWindowOwners.release(window, owner) }
    }
}

internal class SecureFlagOwnership<T : Any>(
    private val isSecure: (T) -> Boolean,
    private val setSecure: (T, Boolean) -> Unit
) {
    private data class Entry(
        val wasSecureBeforeFirstOwner: Boolean,
        val owners: MutableSet<Any>
    )

    private val entries = WeakHashMap<T, Entry>()

    @Synchronized
    fun acquire(target: T, owner: Any) {
        val entry = entries.getOrPut(target) {
            Entry(wasSecureBeforeFirstOwner = isSecure(target), owners = mutableSetOf())
        }
        entry.owners += owner
        if (!isSecure(target)) setSecure(target, true)
    }

    @Synchronized
    fun release(target: T, owner: Any) {
        val entry = entries[target] ?: return
        entry.owners -= owner
        if (entry.owners.isNotEmpty()) return

        entries.remove(target)
        if (!entry.wasSecureBeforeFirstOwner && isSecure(target)) setSecure(target, false)
    }
}

private val secureWindowOwners = SecureFlagOwnership<Window>(
    isSecure = { window ->
        window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
    },
    setSecure = { window, secure ->
        if (secure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
