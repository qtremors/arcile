package dev.qtremors.arcile.core.runtime

import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ArcileDispatchersTest {
    @Test
    fun `standard set is one shared instance`() {
        assertSame(ArcileDispatchers.Standard, ArcileDispatchers.Standard)
        assertSame(ArcileDispatchers.Standard.storage, ArcileDispatchers.Standard.storage)
    }

    @Test
    fun `standard storage runs at most two blocking tasks at once`() = runBlocking {
        val running = AtomicInteger()
        val peak = AtomicInteger()
        val release = CountDownLatch(1)
        val tasks = List(5) {
            async(ArcileDispatchers.Standard.storage) {
                val now = running.incrementAndGet()
                peak.accumulateAndGet(now, ::maxOf)
                release.await(200, TimeUnit.MILLISECONDS)
                running.decrementAndGet()
            }
        }
        tasks.awaitAll()
        release.countDown()

        assertEquals(2, peak.get())
    }

    @Test
    fun `runCatchingCancellable wraps failures but rethrows cancellation`() {
        val failure = runCatchingCancellable { error("boom") }
        assertTrue(failure.exceptionOrNull() is IllegalStateException)
        assertEquals(4, runCatchingCancellable { 2 + 2 }.getOrNull())

        try {
            runCatchingCancellable { throw CancellationException("left the screen") }
            fail("Cancellation must propagate so coroutine scopes can stop")
        } catch (expected: CancellationException) {
            assertEquals("left the screen", expected.message)
        }
    }
}
