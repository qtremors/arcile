package dev.qtremors.arcile.core.ui.lists

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.FolderStats
import dev.qtremors.arcile.core.ui.image.ThumbnailPolicy
import dev.qtremors.arcile.core.ui.image.ThumbnailTargetSize
import dev.qtremors.arcile.core.ui.rememberArcileHaptics
import dev.qtremors.arcile.core.ui.rememberDateTimeFormatter

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileList(
    files: List<FileModel>,
    selectedFiles: Set<String>,
    onNavigateTo: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
    onSelectMultiple: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    listState: androidx.compose.foundation.lazy.LazyListState =
        androidx.compose.foundation.lazy.rememberLazyListState(),
    presentation: FileItemPresentation = FileItemPresentation(),
    folderStatsByPath: Map<String, FolderStats> = emptyMap(),
    folderStatsLoadingPaths: Set<String> = emptySet(),
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val formatter = rememberDateTimeFormatter()
    val thumbnailSizePx = with(LocalDensity.current) {
        ThumbnailTargetSize.fromBounds((64.dp * presentation.zoom).roundToPx())
    }
    val rows = remember(files, folderStatsByPath, formatter, thumbnailSizePx) {
        files.map { file ->
            file.toFileRowUiModel(
                formatter = formatter,
                folderStats = folderStatsByPath[file.absolutePath],
                thumbnailSizePx = thumbnailSizePx
            )
        }
    }
    FileListRows(
        rows = rows,
        selectedFiles = selectedFiles,
        onNavigateTo = onNavigateTo,
        onOpenFile = onOpenFile,
        onToggleSelection = onToggleSelection,
        onSelectMultiple = onSelectMultiple,
        modifier = modifier,
        listState = listState,
        presentation = presentation,
        folderStatsLoadingPaths = folderStatsLoadingPaths,
        contentPadding = contentPadding
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileListRows(
    rows: List<FileRowUiModel>,
    selectedFiles: Set<String>,
    onNavigateTo: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
    onSelectMultiple: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    listState: androidx.compose.foundation.lazy.LazyListState =
        androidx.compose.foundation.lazy.rememberLazyListState(),
    presentation: FileItemPresentation = FileItemPresentation(),
    folderStatsLoadingPaths: Set<String> = emptySet(),
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val haptics = rememberArcileHaptics()
    val thumbnailPolicy = remember { ThumbnailPolicy() }
    val thumbnailSizePx = with(LocalDensity.current) {
        ThumbnailTargetSize.fromBounds((64.dp * presentation.zoom).roundToPx())
    }
    val displayRows = remember(rows, thumbnailSizePx) {
        rows.map { row ->
            if (row.thumbnailSizePx == thumbnailSizePx) {
                row
            } else {
                row.copy(thumbnailSizePx = thumbnailSizePx)
            }
        }
    }
    val visibleRange by remember {
        derivedStateOf {
            val visible = listState.layoutInfo.visibleItemsInfo
            if (visible.isEmpty()) null else visible.first().index..visible.last().index
        }
    }
    var lastInteractedIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(displayRows) { lastInteractedIndex = null }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        contentPadding = contentPadding
    ) {
        itemsIndexed(
            items = displayRows,
            key = { _, row -> row.absolutePath },
            contentType = { _, row -> if (row.isDirectory) "directory" else "file" }
        ) { index, row ->
            val file = row.file
            val isSelected = selectedFiles.contains(file.absolutePath)
            FileItemRow(
                modifier = Modifier.animateItem(),
                row = row,
                isSelected = isSelected,
                isInSelectionMode = selectedFiles.isNotEmpty(),
                presentation = presentation,
                itemIndex = index,
                visibleRange = visibleRange,
                thumbnailPolicy = thumbnailPolicy,
                isFolderStatsLoading = folderStatsLoadingPaths.contains(file.absolutePath),
                onClick = {
                    if (selectedFiles.isNotEmpty()) {
                        lastInteractedIndex = index
                        onToggleSelection(file.absolutePath)
                        haptics.selectionChanged()
                    } else if (file.isDirectory) {
                        onNavigateTo(file.absolutePath)
                    } else {
                        onOpenFile(file.absolutePath)
                    }
                },
                onLongClick = {
                    val anchor = lastInteractedIndex
                    if (selectedFiles.isNotEmpty() && anchor != null && anchor != index) {
                        val start = minOf(anchor, index)
                        val end = maxOf(anchor, index)
                        onSelectMultiple(
                            displayRows.subList(start, end + 1).map(FileRowUiModel::absolutePath)
                        )
                        haptics.selectionChanged()
                    } else {
                        val wasEmpty = selectedFiles.isEmpty()
                        onToggleSelection(file.absolutePath)
                        if (wasEmpty) haptics.selectionStart() else haptics.selectionChanged()
                    }
                    lastInteractedIndex = index
                },
                onOpenDirectly = {
                    if (file.isDirectory) {
                        onNavigateTo(file.absolutePath)
                    } else {
                        onOpenFile(file.absolutePath)
                    }
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
