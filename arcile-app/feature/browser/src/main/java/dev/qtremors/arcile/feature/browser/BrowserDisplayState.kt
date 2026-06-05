package dev.qtremors.arcile.feature.browser

import androidx.compose.runtime.Immutable
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.StorageVolume
import dev.qtremors.arcile.core.storage.domain.FileSortOption
import dev.qtremors.arcile.core.storage.domain.FolderStats
import dev.qtremors.arcile.image.ThumbnailTargetSize
import dev.qtremors.arcile.shared.presentation.FolderTab
import dev.qtremors.arcile.shared.presentation.buildFolderTabs
import dev.qtremors.arcile.shared.presentation.filterAndSortFiles
import dev.qtremors.arcile.shared.presentation.filterFilesByFolderTab
import dev.qtremors.arcile.shared.ui.lists.FileRowUiModel
import dev.qtremors.arcile.shared.ui.lists.toFileRowUiModel
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentSet
import java.text.DateFormat
import java.util.Locale
import kotlin.math.roundToInt

@Immutable
data class BrowserDisplayState(
    val visibleFiles: PersistentList<FileModel> = persistentListOf(),
    val visibleListRows: PersistentList<FileRowUiModel> = persistentListOf(),
    val visibleGridRows: PersistentList<FileRowUiModel> = persistentListOf(),
    val sortedCategoryFiles: PersistentList<FileModel> = persistentListOf(),
    val categoryFolderTabs: PersistentList<FolderTab> = persistentListOf(),
    val selectedCategoryFolderTabIndex: Int = 0,
    val currentVolume: StorageVolume? = null,
    val visiblePaths: PersistentList<String> = persistentListOf(),
    val existingNames: PersistentSet<String> = persistentSetOf()
)

fun buildBrowserDisplayState(
    files: List<FileModel>,
    sortOption: FileSortOption,
    selectedFolderTabPath: String?,
    isCategoryScreen: Boolean,
    currentVolumeId: String?,
    storageVolumes: List<StorageVolume>,
    showHiddenFiles: Boolean,
    allFilesLabel: String,
    folderStatsByPath: Map<String, FolderStats> = emptyMap(),
    browserListZoom: Float = 1f,
    browserGridMinCellSize: Float = 100f,
    previousDisplayState: BrowserDisplayState? = null
): BrowserDisplayState {
    val baseFiles = if (showHiddenFiles) files else files.filterNot { it.isHidden }
    val tabFilteredFiles = filterFilesByFolderTab(baseFiles, selectedFolderTabPath)
    val visibleFiles = filterAndSortFiles(tabFilteredFiles, "", sortOption)
    val sortedCategoryFiles = filterAndSortFiles(baseFiles, "", sortOption)
    val formatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault())
    val listThumbnailSizePx = ThumbnailTargetSize.fromBounds((64f * browserListZoom).roundToInt())
    val gridThumbnailSizePx = ThumbnailTargetSize.fromBounds(browserGridMinCellSize.roundToInt())
    val visibleListRows = buildRows(
        files = visibleFiles,
        folderStatsByPath = folderStatsByPath,
        thumbnailSizePx = listThumbnailSizePx,
        formatter = formatter,
        previousRows = previousDisplayState?.visibleListRows.orEmpty()
    )
    val visibleGridRows = buildRows(
        files = visibleFiles,
        folderStatsByPath = folderStatsByPath,
        thumbnailSizePx = gridThumbnailSizePx,
        formatter = formatter,
        previousRows = previousDisplayState?.visibleGridRows.orEmpty()
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
        visiblePaths = visibleFiles.map { it.absolutePath }.toPersistentList(),
        existingNames = files.map { it.name }.toPersistentSet()
    )
}

private fun buildRows(
    files: List<FileModel>,
    folderStatsByPath: Map<String, FolderStats>,
    thumbnailSizePx: Int,
    formatter: DateFormat,
    previousRows: List<FileRowUiModel>
): List<FileRowUiModel> {
    val previousByPath = previousRows.associateBy { it.absolutePath }
    return files.map { file ->
        val stats = folderStatsByPath[file.absolutePath]
        val previous = previousByPath[file.absolutePath]
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
                thumbnailSizePx = thumbnailSizePx
            )
        }
    }
}
