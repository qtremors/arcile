package dev.qtremors.arcile.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArcileGesturePolicyTest {
    @Test
    fun `axis stays undecided inside touch slop and on ambiguous diagonals`() {
        assertNull(arcileGestureAxis(7f, 0f, touchSlop = 8f))
        assertNull(arcileGestureAxis(20f, 18f, touchSlop = 8f))
        assertNull(arcileGestureAxis(Float.NaN, 0f, touchSlop = 8f))
    }

    @Test
    fun `axis locks only when one direction clearly wins`() {
        assertEquals(
            ArcileGestureAxis.Horizontal,
            arcileGestureAxis(20f, 4f, touchSlop = 8f)
        )
        assertEquals(
            ArcileGestureAxis.Vertical,
            arcileGestureAxis(4f, -20f, touchSlop = 8f)
        )
    }

    @Test
    fun `terminal swipe resolves all four distance directions`() {
        assertEquals(
            ArcileSwipeDirection.Left,
            direction(ArcileGestureAxis.Horizontal, deltaX = -80f)
        )
        assertEquals(
            ArcileSwipeDirection.Right,
            direction(ArcileGestureAxis.Horizontal, deltaX = 80f)
        )
        assertEquals(
            ArcileSwipeDirection.Up,
            direction(ArcileGestureAxis.Vertical, deltaY = -80f)
        )
        assertEquals(
            ArcileSwipeDirection.Down,
            direction(ArcileGestureAxis.Vertical, deltaY = 80f)
        )
    }

    @Test
    fun `short gesture resolves only from same-axis fling velocity`() {
        assertEquals(
            ArcileSwipeDirection.Left,
            direction(
                axis = ArcileGestureAxis.Horizontal,
                deltaX = -20f,
                velocityX = -900f
            )
        )
        assertNull(
            direction(
                axis = ArcileGestureAxis.Horizontal,
                deltaX = -20f,
                velocityY = -2_000f
            )
        )
    }

    @Test
    fun `distance wins over an opposing release velocity`() {
        assertEquals(
            ArcileSwipeDirection.Right,
            direction(
                axis = ArcileGestureAxis.Horizontal,
                deltaX = 80f,
                velocityX = -1_500f
            )
        )
    }

    @Test
    fun `tap eligibility accepts slight single pointer movement`() {
        assertTrue(
            arcileTapEligible(
                deltaX = 3f,
                deltaY = 4f,
                durationMillis = 180L,
                touchSlop = 8f,
                tapTimeoutMillis = 500L,
                hadMultiplePointers = false,
                movementConsumedByAnotherOwner = false
            )
        )
    }

    @Test
    fun `tap eligibility rejects drag long press nested ownership and multi touch`() {
        val base = TapInput()
        assertFalse(base.copy(deltaX = 9f).eligible())
        assertFalse(base.copy(durationMillis = 500L).eligible())
        assertFalse(base.copy(hadMultiplePointers = true).eligible())
        assertFalse(base.copy(movementConsumedByAnotherOwner = true).eligible())
    }

    @Test
    fun `invalid terminal thresholds never resolve a swipe`() {
        assertNull(direction(ArcileGestureAxis.Horizontal, deltaX = 100f, minimumDistance = 0f))
        assertNull(direction(ArcileGestureAxis.Horizontal, deltaX = 100f, minimumVelocity = 0f))
    }

    private fun direction(
        axis: ArcileGestureAxis,
        deltaX: Float = 0f,
        deltaY: Float = 0f,
        velocityX: Float = 0f,
        velocityY: Float = 0f,
        minimumDistance: Float = 72f,
        minimumVelocity: Float = 800f
    ) = arcileSwipeDirection(
        axis = axis,
        deltaX = deltaX,
        deltaY = deltaY,
        velocityX = velocityX,
        velocityY = velocityY,
        minimumDistance = minimumDistance,
        minimumVelocity = minimumVelocity
    )

    private data class TapInput(
        val deltaX: Float = 3f,
        val deltaY: Float = 4f,
        val durationMillis: Long = 180L,
        val touchSlop: Float = 8f,
        val tapTimeoutMillis: Long = 500L,
        val hadMultiplePointers: Boolean = false,
        val movementConsumedByAnotherOwner: Boolean = false
    ) {
        fun eligible() = arcileTapEligible(
            deltaX = deltaX,
            deltaY = deltaY,
            durationMillis = durationMillis,
            touchSlop = touchSlop,
            tapTimeoutMillis = tapTimeoutMillis,
            hadMultiplePointers = hadMultiplePointers,
            movementConsumedByAnotherOwner = movementConsumedByAnotherOwner
        )
    }
}
