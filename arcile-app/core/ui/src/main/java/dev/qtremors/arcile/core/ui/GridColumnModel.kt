package dev.qtremors.arcile.core.ui

import dev.qtremors.arcile.core.storage.domain.FileListingPreferences
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object GridColumnModel {
    const val MIN_CELL_SIZE_DP: Float = FileListingPreferences.MIN_GRID_MIN_CELL_SIZE
    const val MAX_CELL_SIZE_DP: Float = FileListingPreferences.MAX_GRID_MIN_CELL_SIZE
    const val DEFAULT_HORIZONTAL_PADDING_DP: Float = 32f
    const val DEFAULT_ITEM_SPACING_DP: Float = 16f

    fun validColumnRange(
        contentWidthDp: Float,
        horizontalPaddingDp: Float = DEFAULT_HORIZONTAL_PADDING_DP,
        itemSpacingDp: Float = DEFAULT_ITEM_SPACING_DP
    ): IntRange {
        val availableWidth = max(0f, contentWidthDp - horizontalPaddingDp)
        if (availableWidth <= 0f) return 1..1

        val minCols = max(
            1,
            ceil((availableWidth + itemSpacingDp) / (MAX_CELL_SIZE_DP + itemSpacingDp)).toInt()
        )
        val maxCols = max(
            minCols,
            floor((availableWidth + itemSpacingDp) / (MIN_CELL_SIZE_DP + itemSpacingDp)).toInt()
        )
        return minCols..max(minCols, maxCols)
    }

    fun options(
        contentWidthDp: Float,
        horizontalPaddingDp: Float = DEFAULT_HORIZONTAL_PADDING_DP,
        itemSpacingDp: Float = DEFAULT_ITEM_SPACING_DP
    ): List<Int> = validColumnRange(contentWidthDp, horizontalPaddingDp, itemSpacingDp).toList()

    fun cellSizeForColumnCount(
        columnCount: Int,
        contentWidthDp: Float,
        horizontalPaddingDp: Float = DEFAULT_HORIZONTAL_PADDING_DP,
        itemSpacingDp: Float = DEFAULT_ITEM_SPACING_DP
    ): Float {
        val availableWidth = max(0f, contentWidthDp - horizontalPaddingDp)
        val count = max(1, columnCount)
        val computedSize = (availableWidth - (count - 1) * itemSpacingDp) / count
        return computedSize.coerceIn(MIN_CELL_SIZE_DP, MAX_CELL_SIZE_DP)
    }

    fun columnCountForCellSize(
        cellSizeDp: Float,
        contentWidthDp: Float,
        horizontalPaddingDp: Float = DEFAULT_HORIZONTAL_PADDING_DP,
        itemSpacingDp: Float = DEFAULT_ITEM_SPACING_DP
    ): Int {
        val range = validColumnRange(contentWidthDp, horizontalPaddingDp, itemSpacingDp)
        val availableWidth = max(0f, contentWidthDp - horizontalPaddingDp)
        if (availableWidth <= 0f) return 1

        val targetCount = ((availableWidth + itemSpacingDp) / (cellSizeDp + itemSpacingDp)).roundToInt()
        return targetCount.coerceIn(range.first, range.last)
    }

    fun snapCellSize(
        currentCellSize: Float,
        contentWidthDp: Float,
        horizontalPaddingDp: Float = DEFAULT_HORIZONTAL_PADDING_DP,
        itemSpacingDp: Float = DEFAULT_ITEM_SPACING_DP
    ): Float {
        val columns = columnCountForCellSize(currentCellSize, contentWidthDp, horizontalPaddingDp, itemSpacingDp)
        return cellSizeForColumnCount(columns, contentWidthDp, horizontalPaddingDp, itemSpacingDp)
    }
}
