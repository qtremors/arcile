package dev.qtremors.arcile.feature.storagecleaner.ui

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
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.storage.domain.CleanerGroupType
import dev.qtremors.arcile.core.storage.domain.CleanerRiskLevel
import dev.qtremors.arcile.core.storage.domain.CleanerRiskReason

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

@Composable
internal fun cleanerRiskLabel(level: CleanerRiskLevel): String = when (level) {
    CleanerRiskLevel.Low -> stringResource(R.string.cleaner_risk_low)
    CleanerRiskLevel.Review -> stringResource(R.string.cleaner_risk_review)
    CleanerRiskLevel.High -> stringResource(R.string.cleaner_risk_high)
}

@Composable
internal fun cleanerRiskReason(reason: CleanerRiskReason): String = when (reason) {
    CleanerRiskReason.TemporaryOrCache -> stringResource(R.string.cleaner_reason_temporary_cache)
    CleanerRiskReason.LogFile -> stringResource(R.string.cleaner_reason_log)
    CleanerRiskReason.BackupFile -> stringResource(R.string.cleaner_reason_backup)
    CleanerRiskReason.DumpFile -> stringResource(R.string.cleaner_reason_dump)
    CleanerRiskReason.UserFolder -> stringResource(R.string.cleaner_reason_user_folder)
    CleanerRiskReason.MediaFolder -> stringResource(R.string.cleaner_reason_media_folder)
    CleanerRiskReason.AppLikeFolder -> stringResource(R.string.cleaner_reason_app_like)
    CleanerRiskReason.ArcileInternal -> stringResource(R.string.cleaner_reason_arcile_internal)
    CleanerRiskReason.SystemOwnedPath -> stringResource(R.string.cleaner_reason_system_owned)
}

@Composable
internal fun cleanerRiskColor(level: CleanerRiskLevel): Color = when (level) {
    CleanerRiskLevel.Low -> MaterialTheme.colorScheme.primary
    CleanerRiskLevel.Review -> MaterialTheme.colorScheme.tertiary
    CleanerRiskLevel.High -> MaterialTheme.colorScheme.error
}
