package dev.qtremors.arcile.shared.ui.lists

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.storage.domain.FolderStats
import dev.qtremors.arcile.core.storage.domain.FolderStatsStatus
import dev.qtremors.arcile.utils.formatFileSize

@Composable
internal fun folderSubtitleText(folderStats: FolderStats?): String {
    if (folderStats == null) {
        return stringResource(R.string.folder_label)
    }

    val filesLabel = pluralStringResource(
        R.plurals.folder_stats_files,
        folderStats.fileCount.toInt(),
        folderStats.fileCount
    )
    val sizeLabel = formatFileSize(folderStats.totalBytes)

    return when (folderStats.status) {
        FolderStatsStatus.Ready -> "$filesLabel • $sizeLabel"
        FolderStatsStatus.Partial -> "$filesLabel • $sizeLabel"
        FolderStatsStatus.Unavailable -> stringResource(R.string.folder_label)
    }
}
