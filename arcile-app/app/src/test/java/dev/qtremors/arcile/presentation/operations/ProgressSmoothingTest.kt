package dev.qtremors.arcile.presentation.operations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressSmoothingTest {

    @Test
    fun `updateTarget clamps progress and never moves backward`() {
        val state = SmoothedProgressState()

        state.updateTarget(0.6f)
        state.updateTarget(0.4f)
        state.updateTarget(2f)

        assertEquals(1f, state.targetProgress, 0.0001f)
        assertEquals(0f, state.displayedProgress, 0.0001f)
        assertFalse(state.isComplete)
    }

    @Test
    fun `markComplete snaps progress and stores terminal status`() {
        val state = SmoothedProgressState()
        state.updateTarget(0.35f)

        state.markComplete(OperationCompletionStatus.FAILED)

        assertTrue(state.isComplete)
        assertEquals(1f, state.targetProgress, 0.0001f)
        assertEquals(1f, state.displayedProgress, 0.0001f)
        assertEquals(OperationCompletionStatus.FAILED, state.completionStatus)
    }

    @Test
    fun `reset clears progress and records start time`() {
        val state = SmoothedProgressState()
        state.updateTarget(0.8f)
        state.markComplete(OperationCompletionStatus.CANCELLED)

        state.reset(startTime = 1234L)

        assertEquals(0f, state.targetProgress, 0.0001f)
        assertEquals(0f, state.displayedProgress, 0.0001f)
        assertFalse(state.isComplete)
        assertFalse(state.isAnimationFinished)
        assertFalse(state.isVisible)
        assertEquals(1234L, state.operationStartTime)
        assertNull(state.completionStatus)
    }
}
