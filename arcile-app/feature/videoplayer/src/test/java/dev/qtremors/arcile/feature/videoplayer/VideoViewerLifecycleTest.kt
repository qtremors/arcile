package dev.qtremors.arcile.feature.videoplayer

import android.app.Activity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoViewerLifecycleTest {
    @Test
    fun `rotation stop preserves protected viewer but real background stop closes it`() {
        var rotating = false
        val activity = mockk<Activity> { every { isChangingConfigurations } answers { rotating } }
        val owner = mockk<LifecycleOwner>()
        var exits = 0
        var foregrounds = 0
        val observer = videoViewerLifecycleObserver(activity, { foregrounds++ }, { exits++ })

        observer.onStateChanged(owner, Lifecycle.Event.ON_START)
        observer.onStateChanged(owner, Lifecycle.Event.ON_RESUME)
        observer.onStateChanged(owner, Lifecycle.Event.ON_PAUSE)
        assertEquals(0, exits)
        rotating = true
        observer.onStateChanged(owner, Lifecycle.Event.ON_STOP)
        observer.onStateChanged(owner, Lifecycle.Event.ON_DESTROY)
        assertEquals(0, exits)
        rotating = false
        observer.onStateChanged(owner, Lifecycle.Event.ON_START)
        observer.onStateChanged(owner, Lifecycle.Event.ON_RESUME)
        observer.onStateChanged(owner, Lifecycle.Event.ON_STOP)
        assertEquals(1, exits)
        assertEquals(4, foregrounds)
    }
}
