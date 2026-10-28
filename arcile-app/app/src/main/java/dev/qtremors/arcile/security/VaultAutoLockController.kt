package dev.qtremors.arcile.security

import dev.qtremors.arcile.core.vault.domain.VaultSecuritySettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Called on the main dispatcher. Uses elapsed time so clock changes cannot extend an unlock. */
internal class VaultAutoLockController(
    private val settings: StateFlow<VaultSecuritySettings>,
    private val scope: CoroutineScope,
    private val elapsedRealtime: () -> Long,
    private val lockAll: suspend () -> Unit,
    private val clearSensitiveMemory: () -> Unit
) {
    private var foreground = true
    private var screenOff = false
    private var backgroundAt: Long? = null
    private var backgroundLocked = false
    private var timer: Job? = null
    private var locking: Job? = null
    private val blocked = MutableStateFlow(false)
    private val failed = MutableStateFlow(false)
    val blockContent: StateFlow<Boolean> = blocked.asStateFlow()
    val lockFailed: StateFlow<Boolean> = failed.asStateFlow()

    init {
        scope.launch {
            settings.collect {
                if (screenOff && it.lockOnScreenOff) requestLock()
                else if (!foreground) scheduleBackgroundLock()
            }
        }
    }

    fun onBackground() {
        foreground = false
        blocked.value = true
        clearSensitiveMemory()
        if (backgroundAt == null) {
            backgroundAt = elapsedRealtime()
            backgroundLocked = false
        }
        scheduleBackgroundLock()
    }

    fun onForeground() {
        timer?.cancel()
        timer = null
        val expired = backgroundAt?.let { elapsedSince(it) >= settings.value.backgroundLockDelay.milliseconds } == true
        foreground = true
        screenOff = false
        if (expired && !backgroundLocked || failed.value) requestLock()
        else if (locking?.isActive != true) blocked.value = false
        backgroundAt = null
    }

    fun onScreenOff() {
        screenOff = true
        blocked.value = true
        clearSensitiveMemory()
        if (settings.value.lockOnScreenOff) requestLock()
    }

    fun onScreenOn() {
        screenOff = false
        // The process may remain STARTED through a short screen-off interval.
        if (foreground && locking?.isActive != true && !failed.value) blocked.value = false
    }

    fun retryLock() = requestLock()

    private fun scheduleBackgroundLock() {
        timer?.cancel()
        timer = null
        if (backgroundLocked || foreground) return
        val start = backgroundAt ?: return
        val remaining = settings.value.backgroundLockDelay.milliseconds - elapsedSince(start)
        if (remaining <= 0) requestLock()
        else timer = scope.launch {
            delay(remaining)
            timer = null
            if (!foreground) requestLock()
        }
    }

    private fun requestLock() {
        timer?.cancel()
        timer = null
        if (locking?.isActive == true) return
        blocked.value = true
        failed.value = false
        clearSensitiveMemory()
        locking = scope.launch {
            try {
                lockAll()
                if (!foreground) backgroundLocked = true
                blocked.value = !foreground || screenOff
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Keep the privacy curtain up until locking succeeds. Retry does not unlock anything.
                failed.value = true
                blocked.value = true
            }
        }
    }

    private fun elapsedSince(start: Long): Long = (elapsedRealtime() - start).coerceAtLeast(0L)
}
