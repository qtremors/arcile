package dev.qtremors.arcile.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GridColumnModelTest {

    @Test
    fun `validColumnRange at compact width 360dp produces expected column bounds`() {
        val range = GridColumnModel.validColumnRange(contentWidthDp = 360f, horizontalPaddingDp = 32f, itemSpacingDp = 16f)
        assertTrue(range.first >= 1)
        assertTrue(range.last >= range.first)
        val minCell = GridColumnModel.cellSizeForColumnCount(range.first, 360f, 32f, 16f)
        val maxCell = GridColumnModel.cellSizeForColumnCount(range.last, 360f, 32f, 16f)
        assertTrue(minCell <= GridColumnModel.MAX_CELL_SIZE_DP)
        assertTrue(maxCell >= GridColumnModel.MIN_CELL_SIZE_DP)
    }

    @Test
    fun `validColumnRange at medium width 600dp and expanded width 840dp scales column options`() {
        val compactRange = GridColumnModel.validColumnRange(contentWidthDp = 360f, horizontalPaddingDp = 32f, itemSpacingDp = 16f)
        val mediumRange = GridColumnModel.validColumnRange(contentWidthDp = 600f, horizontalPaddingDp = 32f, itemSpacingDp = 16f)
        val expandedRange = GridColumnModel.validColumnRange(contentWidthDp = 840f, horizontalPaddingDp = 32f, itemSpacingDp = 16f)

        assertTrue(mediumRange.last > compactRange.last)
        assertTrue(expandedRange.last > mediumRange.last)
    }

    @Test
    fun `columnCountForCellSize maps stored cell size to nearest discrete option`() {
        val options = GridColumnModel.options(contentWidthDp = 360f, horizontalPaddingDp = 32f, itemSpacingDp = 16f)
        val targetCols = options.first()
        val cellSize = GridColumnModel.cellSizeForColumnCount(targetCols, 360f, 32f, 16f)

        val resolvedCols = GridColumnModel.columnCountForCellSize(cellSize, 360f, 32f, 16f)
        assertEquals(targetCols, resolvedCols)
    }

    @Test
    fun `snapCellSize returns valid cell size matching discrete column count`() {
        val snapped = GridColumnModel.snapCellSize(
            currentCellSize = 150f,
            contentWidthDp = 360f,
            horizontalPaddingDp = 32f,
            itemSpacingDp = 16f
        )
        assertTrue(snapped in GridColumnModel.MIN_CELL_SIZE_DP..GridColumnModel.MAX_CELL_SIZE_DP)
    }

    @Test
    fun `grid controls prefer the listing width over the modal sheet width`() {
        assertEquals(400f, resolveGridControlWidthDp(sheetWidthDp = 800f, gridContentWidthDp = 400f))
        assertEquals(800f, resolveGridControlWidthDp(sheetWidthDp = 800f, gridContentWidthDp = null))
        assertEquals(800f, resolveGridControlWidthDp(sheetWidthDp = 800f, gridContentWidthDp = 0f))
    }
}
