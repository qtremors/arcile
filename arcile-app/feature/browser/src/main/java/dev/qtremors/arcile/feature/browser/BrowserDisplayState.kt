package dev.qtremors.arcile.feature.browser

import androidx.compose.runtime.Immutable
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.FileListingPreferences
import dev.qtremors.arcile.core.storage.domain.StorageVolume
import dev.qtremors.arcile.core.storage.domain.FileSortOption
import dev.qtremors.arcile.core.storage.domain.FolderStats
import dev.qtremors.arcile.core.storage.domain.FileViewMode
import dev.qtremors.arcile.core.ui.image.ThumbnailTargetSize
import dev.qtremors.arcile.core.presentation.FolderTab
import dev.qtremors.arcile.core.presentation.buildFolderTabs
import dev.qtremors.arcile.core.presentation.filterAndSortFiles
import dev.qtremors.arcile.core.presentation.filterFilesByFolderTab
import dev.qtremors.arcile.core.ui.lists.FileRowUiModel
import dev.qtremors.arcile.core.ui.lists.toFileRowUiModel
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentSet
import java.text.DateFormat
import java.util.Locale
import kotlin.math.roundToInt

@Immutable
internal data class BrowserDisplayState(
    val visibleFiles: PersistentList<FileModel> = persistentListOf(),
    val visibleListRows: PersistentList<FileRowUiModel> = persistentListOf(),
    val visibleGridRows: PersistentList<FileRowUiModel> = persistentListOf(),
    val sortedCategoryFiles: PersistentList<FileModel> = persistentListOf(),
    val categoryFolderTabs: PersistentList<FolderTab> = persistentListOf(),
    val selectedCategoryFolderTabIndex: Int = 0,
    val currentVolume: StorageVolume? = null,
    val visiblePaths: PersistentList<String> = persistentListOf(),
    val existingNames: PersistentSet<String> = persistentSetOf(),
    val rowIndices: PersistentMap<String, Int> = persistentMapOf(),
    val inputs: BrowserDisplayInputs? = null,
    val folderStats: Map<String, FolderStats> = emptyMap()
)

internal data class BrowserDisplayInputs(
    val files: List<FileModel>, val sortOption: FileSortOption, val foldersFirst: Boolean,
    val tab: String?, val category: Boolean, val volumeId: String?, val volumes: List<StorageVolume>,
    val hidden: Boolean, val label: String, val listZoom: Float, val gridSize: Float,
    val mode: FileViewMode?, val locale: Locale,
    val context: android.content.Context?, val sizeFormatter: ((Long) -> String)?
)

internal fun buildBrowserDisplayState(
    files: List<FileModel>,
    sortOption: FileSortOption,
    foldersFirst: Boolean = FileListingPreferences.DEFAULT_FOLDERS_FIRST,
    selectedFolderTabPath: String?,
    isCategoryScreen: Boolean,
    currentVolumeId: String?,
    storageVolumes: List<StorageVolume>,
    showHiddenFiles: Boolean,
    allFilesLabel: String,
    folderStatsByPath: Map<String, FolderStats> = emptyMap(),
    browserListZoom: Float = 1f,
    browserGridMinCellSize: Float = 100f,
    previousDisplayState: BrowserDisplayState? = null,
    context: android.content.Context? = null,
    fileSizeFormatter: ((Long) -> String)? = null,
    viewMode: FileViewMode? = null,
    updatedFolderPaths: Set<String>? = null
): BrowserDisplayState {
    val inputs = BrowserDisplayInputs(files, sortOption, foldersFirst, selectedFolderTabPath,
        isCategoryScreen, currentVolumeId, storageVolumes, showHiddenFiles, allFilesLabel,
        browserListZoom, browserGridMinCellSize, viewMode, Locale.getDefault(), context, fileSizeFormatter)
    if (previousDisplayState?.inputs == inputs) {
        val unchanged = if (updatedFolderPaths == null) previousDisplayState.folderStats == folderStatsByPath
            else updatedFolderPaths.all { previousDisplayState.folderStats[it] == folderStatsByPath[it] }
        if (unchanged) return previousDisplayState
        if (sortOption != FileSortOption.FILE_COUNT_HIGHEST && sortOption != FileSortOption.FILE_COUNT_LOWEST) {
            val changed = updatedFolderPaths ?: (previousDisplayState.folderStats.keys + folderStatsByPath.keys)
                .filter { previousDisplayState.folderStats[it] != folderStatsByPath[it] }.toSet()
            val formatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, inputs.locale)
            fun refresh(rows: PersistentList<FileRowUiModel>): PersistentList<FileRowUiModel> {
                if (rows.isEmpty()) return rows
                var refreshed = rows
                changed.forEach { path ->
                    val index = previousDisplayState.rowIndices[path] ?: return@forEach
                    val row = rows[index]
                    val stats = folderStatsByPath[path]
                    if (row.folderStats != stats) refreshed = refreshed.set(index, row.file.toFileRowUiModel(
                        formatter = formatter, folderStats = stats, thumbnailSizePx = row.thumbnailSizePx,
                        context = context, fileSizeFormatter = fileSizeFormatter
                    ))
                }
                return refreshed
            }
            return previousDisplayState.copy(visibleListRows = refresh(previousDisplayState.visibleListRows),
                visibleGridRows = refresh(previousDisplayState.visibleGridRows), folderStats = folderStatsByPath)
        }
    }
    val baseFiles = if (showHiddenFiles) files else files.filterNot { it.isHidden }
    val fileCountFor: (FileModel) -> Long? = { file ->
        if (file.isDirectory) folderStatsByPath[file.reference]?.fileCount else 1L
    }
    val sortedCategoryFiles = filterAndSortFiles(baseFiles, "", sortOption, fileCountFor, foldersFirst)
    val visibleFiles = filterFilesByFolderTab(sortedCategoryFiles, selectedFolderTabPath)
    val formatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault())
    val listThumbnailSizePx = ThumbnailTargetSize.fromBounds((64f * browserListZoom).roundToInt())
    val gridThumbnailSizePx = ThumbnailTargetSize.fromBounds(browserGridMinCellSize.roundToInt())
    val visibleListRows = if (viewMode == FileViewMode.GRID) emptyList() else buildRows(
        files = visibleFiles,
        folderStatsByPath = folderStatsByPath,
        thumbnailSizePx = listThumbnailSizePx,
        formatter = formatter,
        previousRows = previousDisplayState?.visibleListRows.orEmpty(),
        context = context,
        fileSizeFormatter = fileSizeFormatter
    )
    val visibleGridRows = if (viewMode == FileViewMode.LIST) emptyList() else buildRows(
        files = visibleFiles,
        folderStatsByPath = folderStatsByPath,
        thumbnailSizePx = gridThumbnailSizePx,
        formatter = formatter,
        previousRows = previousDisplayState?.visibleGridRows.orEmpty(),
        context = context,
        fileSizeFormatter = fileSizeFormatter
    )
    val categoryFolderTabs = if (isCategoryScreen) {
        buildFolderTabs(sortedCategoryFiles, allFilesLabel)
    } else {
        emptyList()
    }
    val selectedCategoryFolderTabIndex = categoryFolderTabs
        .indexOfFirst { it.path == selectedFolderTabPath }
        .takeIf { it >= 0 }
        ?: 0

    return BrowserDisplayState(
        visibleFiles = visibleFiles.toPersistentList(),
        visibleListRows = visibleListRows.toPersistentList(),
        visibleGridRows = visibleGridRows.toPersistentList(),
        sortedCategoryFiles = sortedCategoryFiles.toPersistentList(),
        categoryFolderTabs = categoryFolderTabs.toPersistentList(),
        selectedCategoryFolderTabIndex = selectedCategoryFolderTabIndex,
        currentVolume = storageVolumes.firstOrNull { it.id == currentVolumeId },
        visiblePaths = visibleFiles.map { it.reference }.toPersistentList(),
        existingNames = files.map { it.name }.toPersistentSet(),
        rowIndices = visibleFiles.mapIndexed { index, file -> file.reference to index }.toMap().toPersistentMap(),
        inputs = inputs,
        folderStats = folderStatsByPath
    )
}

private fun buildRows(
    files: List<FileModel>,
    folderStatsByPath: Map<String, FolderStats>,
    thumbnailSizePx: Int,
    formatter: DateFormat,
    previousRows: List<FileRowUiModel>,
    context: android.content.Context? = null,
    fileSizeFormatter: ((Long) -> String)? = null
): List<FileRowUiModel> {
    val previousByPath = previousRows.associateBy { it.absolutePath }
    return files.map { file ->
        val stats = folderStatsByPath[file.reference]
        val previous = previousByPath[file.reference]
        if (previous != null &&
            previous.file == file &&
            previous.folderStats == stats &&
            previous.thumbnailSizePx == thumbnailSizePx
        ) {
            previous
        } else {
            file.toFileRowUiModel(
                formatter = formatter,
                folderStats = stats,
                thumbnailSizePx = thumbnailSizePx,
                context = context,
                fileSizeFormatter = fileSizeFormatter
            )
        }
    }
}
