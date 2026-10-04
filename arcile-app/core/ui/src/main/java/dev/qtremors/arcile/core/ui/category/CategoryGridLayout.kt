package dev.qtremors.arcile.core.ui.category

import dev.qtremors.arcile.core.ui.GridColumnModel

internal enum class CategoryGridLayout(val sidePaddingDp: Float, val spacingDp: Float) {
    Files(12f, 8f),
    Folders(16f, 16f);

    fun options(widthDp: Float): List<Int> = GridColumnModel.options(
        contentWidthDp = widthDp,
        horizontalPaddingDp = sidePaddingDp * 2,
        itemSpacingDp = spacingDp
    )

    fun columnCount(cellSizeDp: Float, widthDp: Float): Int = GridColumnModel.columnCountForCellSize(
        cellSizeDp = cellSizeDp,
        contentWidthDp = widthDp,
        horizontalPaddingDp = sidePaddingDp * 2,
        itemSpacingDp = spacingDp
    )

    fun cellSize(columnCount: Int, widthDp: Float): Float = GridColumnModel.cellSizeForColumnCount(
        columnCount = columnCount,
        contentWidthDp = widthDp,
        horizontalPaddingDp = sidePaddingDp * 2,
        itemSpacingDp = spacingDp
    )
}
