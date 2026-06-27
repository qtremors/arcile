package dev.qtremors.arcile

import org.junit.Assert.assertEquals
import org.junit.Test

class AppSessionTrackerTest {
    @Test
    fun `first activity in a new process is a cold launcher even with restored state`() {
        val tracker = AppSessionTracker(navigationSessionIdFactory = { "process-one" })

        val launch = tracker.onMainActivityCreated(hasSavedInstanceState = true)

        assertEquals(AppLaunchMode.ColdLauncher, launch.mode)
        assertEquals("process-one", launch.navigationSessionId)
    }

    @Test
    fun `configuration recreation preserves the process navigation session`() {
        val tracker = AppSessionTracker(navigationSessionIdFactory = { "process-one" })
        tracker.onMainActivityCreated(hasSavedInstanceState = false)

        val recreation = tracker.onMainActivityCreated(hasSavedInstanceState = true)

        assertEquals(AppLaunchMode.ConfigurationRecreation, recreation.mode)
        assertEquals("process-one", recreation.navigationSessionId)
    }

    @Test
    fun `new activity without restored state starts from home`() {
        val tracker = AppSessionTracker(navigationSessionIdFactory = { "process-one" })
        tracker.onMainActivityCreated(hasSavedInstanceState = false)

        val launch = tracker.onMainActivityCreated(hasSavedInstanceState = false)

        assertEquals(AppLaunchMode.ColdLauncher, launch.mode)
    }

    @Test
    fun `new cold activity in the same process receives a new navigation session`() {
        val ids = ArrayDeque(listOf("process-one", "process-two"))
        val tracker = AppSessionTracker(navigationSessionIdFactory = { ids.removeFirst() })
        tracker.onMainActivityCreated(hasSavedInstanceState = false)

        val launch = tracker.onMainActivityCreated(hasSavedInstanceState = false)

        assertEquals(AppLaunchMode.ColdLauncher, launch.mode)
        assertEquals("process-two", launch.navigationSessionId)
    }
}
