package dev.qtremors.arcile.presentation.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CopyAll
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import dev.qtremors.arcile.R
import dev.qtremors.arcile.core.storage.domain.CleanerGroupType

@Composable
internal fun cleanerTitle(type: CleanerGroupType): String = when (type) {
    CleanerGroupType.LargeFiles -> stringResource(R.string.cleaner_large_files)
    CleanerGroupType.OldDownloads -> stringResource(R.string.cleaner_old_downloads)
    CleanerGroupType.Duplicates -> stringResource(R.string.cleaner_duplicates)
    CleanerGroupType.Apks -> stringResource(R.string.cleaner_apks)
    CleanerGroupType.Videos -> stringResource(R.string.cleaner_videos)
    CleanerGroupType.Junk -> stringResource(R.string.cleaner_junk)
}

@Composable
internal fun cleanerDescription(type: CleanerGroupType): String = when (type) {
    CleanerGroupType.LargeFiles -> stringResource(R.string.cleaner_large_files_desc)
    CleanerGroupType.OldDownloads -> stringResource(R.string.cleaner_old_downloads_desc)
    CleanerGroupType.Duplicates -> stringResource(R.string.cleaner_duplicates_desc)
    CleanerGroupType.Apks -> stringResource(R.string.cleaner_apks_desc)
    CleanerGroupType.Videos -> stringResource(R.string.cleaner_videos_desc)
    CleanerGroupType.Junk -> stringResource(R.string.cleaner_junk_desc)
}

@Composable
internal fun cleanerColor(type: CleanerGroupType): Color = when (type) {
    CleanerGroupType.LargeFiles -> MaterialTheme.colorScheme.primary
    CleanerGroupType.OldDownloads -> MaterialTheme.colorScheme.secondary
    CleanerGroupType.Duplicates -> MaterialTheme.colorScheme.tertiary
    CleanerGroupType.Apks -> MaterialTheme.colorScheme.primary
    CleanerGroupType.Videos -> MaterialTheme.colorScheme.secondary
    CleanerGroupType.Junk -> MaterialTheme.colorScheme.error
}

internal fun cleanerIcon(type: CleanerGroupType): ImageVector = when (type) {
    CleanerGroupType.LargeFiles -> Icons.Default.Storage
    CleanerGroupType.OldDownloads -> Icons.Default.Download
    CleanerGroupType.Duplicates -> Icons.Default.CopyAll
    CleanerGroupType.Apks -> Icons.Default.Android
    CleanerGroupType.Videos -> Icons.Default.VideoFile
    CleanerGroupType.Junk -> Icons.Default.DeleteSweep
}
