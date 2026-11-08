package dev.qtremors.arcile.core.ui.audio

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AudioSleepTimer(
    private val scope: CoroutineScope,
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
    private val onElapsed: () -> Unit
) {
    private var job: Job? = null
    var endElapsedMs: Long? = null
        private set

    fun setMinutes(minutes: Int?) {
        cancel()
        val duration = minutes?.takeIf { it > 0 }?.toLong()?.times(60_000L) ?: return
        endElapsedMs = elapsedRealtime() + duration
        job = scope.launch {
            delay(duration)
            endElapsedMs = null
            job = null
            onElapsed()
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        endElapsedMs = null
    }
}
