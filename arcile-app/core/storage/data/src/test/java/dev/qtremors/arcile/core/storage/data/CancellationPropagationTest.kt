package dev.qtremors.arcile.core.storage.data

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CancellationPropagationTest {
    @Test
    fun `cancellation preserving result wrapper rethrows cancellation`() {
        val cancellation = CancellationException("cancel storage work")

        val thrown = assertThrows(CancellationException::class.java) {
            runCatchingPreservingCancellation<Unit> { throw cancellation }
        }

        assertEquals(cancellation, thrown)
    }

    @Test
    fun `cancellation preserving result wrapper retains ordinary failures`() {
        val failure = IllegalStateException("disk unavailable")

        val result = runCatchingPreservingCancellation<Unit> { throw failure }

        assertEquals(failure, result.exceptionOrNull())
    }
}
