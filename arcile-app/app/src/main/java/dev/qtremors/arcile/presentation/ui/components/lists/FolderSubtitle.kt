package dev.qtremors.arcile.presentation.ui.components.lists

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import dev.qtremors.arcile.R
import dev.qtremors.arcile.domain.FolderStats
import dev.qtremors.arcile.domain.FolderStatsStatus
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
        FolderStatsStatus.Partial -> {
            if (folderStats.fileCount > 0L || folderStats.totalBytes > 0L) {
                "$filesLabel • $sizeLabel • ${stringResource(R.string.folder_stats_limited_access)}"
            } else {
                stringResource(R.string.folder_stats_unavailable)
            }
        }
        FolderStatsStatus.Unavailable -> stringResource(R.string.folder_stats_unavailable)
    }
}
