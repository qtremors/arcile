package dev.qtremors.arcile.security

import dev.qtremors.arcile.core.vault.domain.VaultBackgroundLockDelay
import dev.qtremors.arcile.core.vault.domain.VaultSecuritySettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VaultAutoLockControllerTest {
    @Test
    fun `default background transition locks immediately and clears sensitive caches`() = runTest {
        val f = Fixture(this)
        f.controller.onBackground()
        assertEquals(1, f.locks)
        assertTrue(f.clears > 0)
        assertTrue(f.controller.blockContent.value)
        f.controller.onForeground()
        assertEquals(1, f.locks)
        assertFalse(f.controller.blockContent.value)
    }

    @Test
    fun `return before the selected deadline retains the unlocked session`() = runTest {
        val f = Fixture(this, VaultBackgroundLockDelay.ONE_MINUTE)
        f.controller.onBackground()
        advanceTimeBy(59_999)
        f.controller.onForeground()
        runCurrent()
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(0, f.locks)
        assertFalse(f.controller.blockContent.value)
    }

    @Test
    fun `background timer locks at the exact selected deadline`() = runTest {
        val f = Fixture(this, VaultBackgroundLockDelay.THIRTY_SECONDS)
        f.controller.onBackground()
        advanceTimeBy(29_999)
        runCurrent()
        assertEquals(0, f.locks)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, f.locks)
        assertTrue(f.controller.blockContent.value)
        f.controller.onForeground()
        assertEquals(1, f.locks)
        assertFalse(f.controller.blockContent.value)
    }

    @Test
    fun `resume catches expiry even when Android suspended the timer`() = runTest {
        val f = Fixture(this, VaultBackgroundLockDelay.ONE_MINUTE)
        val gate = CompletableDeferred<Unit>()
        f.lockGate = gate
        f.controller.onBackground()
        f.elapsedOffset = 60_000
        f.controller.onForeground()
        assertEquals(1, f.locks)
        assertTrue(f.controller.blockContent.value)
        gate.complete(Unit)
        runCurrent()
        assertFalse(f.controller.blockContent.value)
    }

    @Test
    fun `duplicate background events cannot extend the deadline`() = runTest {
        val f = Fixture(this, VaultBackgroundLockDelay.ONE_MINUTE)
        f.controller.onBackground()
        advanceTimeBy(40_000)
        f.controller.onBackground()
        advanceTimeBy(20_000)
        runCurrent()
        assertEquals(1, f.locks)
    }

    @Test
    fun `shorter policy recalculates from the original background time`() = runTest {
        val f = Fixture(this, VaultBackgroundLockDelay.FIVE_MINUTES)
        f.controller.onBackground()
        advanceTimeBy(20_000)
        f.settings.value = f.settings.value.copy(backgroundLockDelay = VaultBackgroundLockDelay.THIRTY_SECONDS)
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(1, f.locks)
    }

    @Test
    fun `shortening policy after its new deadline locks immediately`() = runTest {
        val f = Fixture(this, VaultBackgroundLockDelay.FIVE_MINUTES)
        f.controller.onBackground()
        advanceTimeBy(40_000)
        f.settings.value = f.settings.value.copy(backgroundLockDelay = VaultBackgroundLockDelay.THIRTY_SECONDS)
        assertEquals(1, f.locks)
    }

    @Test
    fun `longer policy replaces the old timer without resetting elapsed background time`() = runTest {
        val f = Fixture(this, VaultBackgroundLockDelay.THIRTY_SECONDS)
        f.controller.onBackground()
        advanceTimeBy(20_000)
        f.settings.value = f.settings.value.copy(backgroundLockDelay = VaultBackgroundLockDelay.ONE_MINUTE)
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(0, f.locks)
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(1, f.locks)
    }

    @Test
    fun `changing preferences after an automatic lock never reopens a session`() = runTest {
        val f = Fixture(this)
        f.controller.onBackground()
        f.settings.value = f.settings.value.copy(backgroundLockDelay = VaultBackgroundLockDelay.FIVE_MINUTES)
        assertEquals(1, f.locks)
        assertTrue(f.controller.blockContent.value)
    }

    @Test
    fun `a fresh background visit receives its own deadline after a retained foreground visit`() = runTest {
        val f = Fixture(this, VaultBackgroundLockDelay.ONE_MINUTE)
        f.controller.onBackground()
        advanceTimeBy(20_000)
        f.controller.onForeground()
        advanceTimeBy(20_000)
        f.controller.onBackground()
        advanceTimeBy(59_999)
        runCurrent()
        assertEquals(0, f.locks)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, f.locks)
    }

    @Test
    fun `screen off overrides a long background retention interval`() = runTest {
        val f = Fixture(this, VaultBackgroundLockDelay.FIVE_MINUTES)
        f.controller.onBackground()
        f.controller.onScreenOff()
        assertEquals(1, f.locks)
        assertTrue(f.controller.blockContent.value)
        advanceTimeBy(300_000)
        runCurrent()
        assertEquals(1, f.locks)
    }

    @Test
    fun `disabled screen-off locking preserves the background timeout`() = runTest {
        val f = Fixture(this, VaultBackgroundLockDelay.ONE_MINUTE, screenOff = false)
        f.controller.onBackground()
        f.controller.onScreenOff()
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(1, f.locks)
    }

    @Test
    fun `short screen-off interval in a started process still uses the privacy curtain`() = runTest {
        val f = Fixture(this, VaultBackgroundLockDelay.ONE_MINUTE, screenOff = false)
        f.controller.onScreenOff()
        assertTrue(f.controller.blockContent.value)
        assertEquals(0, f.locks)
        f.controller.onScreenOn()
        assertFalse(f.controller.blockContent.value)
    }

    @Test
    fun `screen on cannot reveal private content before an in-flight lock completes`() = runTest {
        val f = Fixture(this)
        f.lockGate = CompletableDeferred()
        f.controller.onScreenOff()
        f.controller.onScreenOn()
        assertTrue(f.controller.blockContent.value)
        f.lockGate!!.complete(Unit)
        runCurrent()
        assertFalse(f.controller.blockContent.value)
    }

    @Test
    fun `foreground transitions cannot cancel a lock already in progress`() = runTest {
        val f = Fixture(this)
        f.lockGate = CompletableDeferred()
        f.controller.onBackground()
        f.controller.onForeground()
        f.controller.onBackground()
        f.controller.onForeground()
        assertEquals(1, f.locks)
        assertTrue(f.controller.blockContent.value)
        f.lockGate!!.complete(Unit)
        runCurrent()
        assertFalse(f.controller.blockContent.value)
    }

    @Test
    fun `failed locking keeps content hidden and supports an explicit retry`() = runTest {
        val f = Fixture(this)
        f.failLock = true
        f.controller.onBackground()
        f.controller.onForeground()
        assertTrue(f.controller.lockFailed.value)
        assertTrue(f.controller.blockContent.value)
        f.failLock = false
        f.controller.retryLock()
        assertFalse(f.controller.lockFailed.value)
        assertFalse(f.controller.blockContent.value)
    }

    @Test
    fun `enabling screen-off locking while the display is off locks immediately`() = runTest {
        val f = Fixture(this, VaultBackgroundLockDelay.FIVE_MINUTES, screenOff = false)
        f.controller.onScreenOff()
        f.settings.value = f.settings.value.copy(lockOnScreenOff = true)
        assertEquals(1, f.locks)
        assertTrue(f.controller.blockContent.value)
    }

    private class Fixture(scope: TestScope, delay: VaultBackgroundLockDelay = VaultBackgroundLockDelay.IMMEDIATE, screenOff: Boolean = true) {
        val settings = MutableStateFlow(VaultSecuritySettings(backgroundLockDelay = delay, lockOnScreenOff = screenOff))
        var locks = 0
        var clears = 0
        var elapsedOffset = 0L
        var lockGate: CompletableDeferred<Unit>? = null
        var failLock = false
        val controller = VaultAutoLockController(
            settings, CoroutineScope(scope.backgroundScope.coroutineContext + UnconfinedTestDispatcher(scope.testScheduler)),
            { scope.testScheduler.currentTime + elapsedOffset },
            { locks++; if (failLock) error("Lock failed"); lockGate?.await(); Unit },
            { clears++ }
        )
    }
}
