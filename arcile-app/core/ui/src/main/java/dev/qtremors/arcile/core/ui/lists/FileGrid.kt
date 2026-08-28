package dev.qtremors.arcile.core.ui.lists

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.FolderStats
import dev.qtremors.arcile.core.ui.image.ThumbnailPolicy
import dev.qtremors.arcile.core.ui.image.ThumbnailTargetSize
import dev.qtremors.arcile.core.ui.rememberArcileHaptics
import dev.qtremors.arcile.core.ui.rememberDateTimeFormatter

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileGrid(
    files: List<FileModel>,
    selectedFiles: Set<String>,
    onNavigateTo: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
    onSelectMultiple: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
    minCellSize: Dp = 100.dp,
    presentation: FileItemPresentation = FileItemPresentation(),
    folderStatsByPath: Map<String, FolderStats> = emptyMap(),
    contentPadding: PaddingValues = PaddingValues(16.dp),
    highlightedPath: String? = null
) {
    val formatter = rememberDateTimeFormatter()
    val thumbnailSizePx = with(LocalDensity.current) {
        ThumbnailTargetSize.fromBounds(minCellSize.roundToPx())
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val rows = remember(files, folderStatsByPath, formatter, thumbnailSizePx, context) {
        files.map { file ->
            file.toFileRowUiModel(
                formatter = formatter,
                folderStats = folderStatsByPath[file.absolutePath],
                thumbnailSizePx = thumbnailSizePx,
                context = context
            )
        }
    }
    FileGridRows(
        rows = rows,
        selectedFiles = selectedFiles,
        highlightedPath = highlightedPath,
        onNavigateTo = { onNavigateTo(it.absolutePath) },
        onOpenFile = onOpenFile,
        onToggleSelection = onToggleSelection,
        onSelectMultiple = onSelectMultiple,
        modifier = modifier,
        gridState = gridState,
        minCellSize = minCellSize,
        presentation = presentation,
        contentPadding = contentPadding
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileGridRows(
    rows: List<FileRowUiModel>,
    selectedFiles: Set<String>,
    onNavigateTo: (FileModel) -> Unit,
    onOpenFile: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
    onSelectMultiple: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
    minCellSize: Dp = 100.dp,
    presentation: FileItemPresentation = FileItemPresentation(),
    contentPadding: PaddingValues = PaddingValues(16.dp),
    highlightedPath: String? = null
) {
    val haptics = rememberArcileHaptics()
    val thumbnailPolicy = remember { ThumbnailPolicy() }
    val thumbnailSizePx = with(LocalDensity.current) {
        ThumbnailTargetSize.fromBounds(minCellSize.roundToPx())
    }
    val displayRows = remember(rows, thumbnailSizePx) {
        rows.map { row ->
            if (row.thumbnailSizePx == thumbnailSizePx) row else row.copy(thumbnailSizePx = thumbnailSizePx)
        }
    }
    val visibleRange by remember {
        derivedStateOf {
            val visible = gridState.layoutInfo.visibleItemsInfo
            if (visible.isEmpty()) null else visible.first().index..visible.last().index
        }
    }
    var lastInteractedIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(displayRows) {
        lastInteractedIndex = null
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val layoutDirection = androidx.compose.ui.platform.LocalLayoutDirection.current
        val horizontalPaddingDp = with(density) {
            (contentPadding.calculateLeftPadding(layoutDirection) +
                contentPadding.calculateRightPadding(layoutDirection)).toPx() / density.density
        }
        val columnCount = remember(minCellSize, maxWidth, horizontalPaddingDp) {
            dev.qtremors.arcile.core.ui.GridColumnModel.columnCountForCellSize(
                cellSizeDp = minCellSize.value,
                contentWidthDp = maxWidth.value,
                horizontalPaddingDp = horizontalPaddingDp,
                itemSpacingDp = 16f
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(columnCount),
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            state = gridState,
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(
                items = displayRows,
                key = { _, row -> row.absolutePath },
                contentType = { _, row -> if (row.isDirectory) "directory" else "file" }
            ) { index, row ->
                val file = row.file
                FileGridItem(
                    modifier = Modifier.animateItem(),
                    row = row,
                    isSelected = file.absolutePath in selectedFiles,
                    isHighlighted = file.absolutePath == highlightedPath,
                    isInSelectionMode = selectedFiles.isNotEmpty(),
                    presentation = presentation,
                    itemIndex = index,
                    visibleRange = visibleRange,
                    thumbnailPolicy = thumbnailPolicy,
                    onClick = {
                        if (selectedFiles.isNotEmpty()) {
                            lastInteractedIndex = index
                            onToggleSelection(file.absolutePath)
                            haptics.selectionChanged()
                        } else if (file.isDirectory) {
                            onNavigateTo(file)
                        } else {
                            onOpenFile(file.absolutePath)
                        }
                    },
                    onLongClick = {
                        val previousIndex = lastInteractedIndex?.takeIf { it in displayRows.indices }
                        if (selectedFiles.isNotEmpty() && previousIndex != null && previousIndex != index) {
                            val start = minOf(previousIndex, index)
                            val end = maxOf(previousIndex, index)
                            onSelectMultiple(displayRows.subList(start, end + 1).map { it.absolutePath })
                            haptics.selectionChanged()
                        } else {
                            val wasEmpty = selectedFiles.isEmpty()
                            onToggleSelection(file.absolutePath)
                            if (wasEmpty) haptics.selectionStart() else haptics.selectionChanged()
                        }
                        lastInteractedIndex = index
                    },
                    onOpenDirectly = {
                        if (file.isDirectory) onNavigateTo(file) else onOpenFile(file.absolutePath)
                    },
                    onToggleSelectionDirectly = {
                        val wasEmpty = selectedFiles.isEmpty()
                        onToggleSelection(file.absolutePath)
                        if (wasEmpty) haptics.selectionStart() else haptics.selectionChanged()
                    }
                )
            }
        }
    }
}
