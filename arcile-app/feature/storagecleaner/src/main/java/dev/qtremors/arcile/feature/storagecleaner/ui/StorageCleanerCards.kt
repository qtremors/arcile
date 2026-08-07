package dev.qtremors.arcile.feature.storagecleaner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.storage.domain.CleanerGroup
import dev.qtremors.arcile.core.storage.domain.StorageCleanerScanProgress
import dev.qtremors.arcile.core.storage.domain.StorageCleanerScanPhase
import dev.qtremors.arcile.core.ui.theme.bodyLargeMedium
import dev.qtremors.arcile.core.presentation.formatFileSize
import dev.qtremors.arcile.core.ui.theme.bounceCombinedClickable

@Composable
internal fun CleanerCategoryCard(
    group: CleanerGroup,
    isScanning: Boolean = false,
    scanProgress: StorageCleanerScanProgress? = null,
    isLoaded: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val type = group.type
    val color = cleanerColor(type)
    val refreshLabel = stringResource(R.string.cleaner_refresh_category, cleanerTitle(type))
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .bounceCombinedClickable(
                onLongClick = onLongClick,
                onLongClickLabel = refreshLabel.takeIf { onLongClick != null },
                onClick = onClick
            ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = cleanerIcon(type),
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = cleanerTitle(type),
                    style = MaterialTheme.typography.bodyLargeMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = cleanerDescription(type),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isScanning) {
                        scanProgressText(scanProgress)
                    } else if (group.candidates.isNotEmpty()) {
                        stringResource(R.string.cleaner_group_stat, group.candidates.size, formatFileSize(group.totalBytes))
                    } else if (!isLoaded) {
                        stringResource(R.string.cleaner_tap_to_scan)
                    } else {
                        stringResource(R.string.cleaner_empty)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = color,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CleanerScanStatus(
    progress: StorageCleanerScanProgress?,
    modifier: Modifier = Modifier
) {
    val fraction = progress?.progressFraction
    val phaseText = when (progress?.phase) {
        StorageCleanerScanPhase.CheckingDuplicates -> stringResource(R.string.cleaner_checking_duplicates)
        else -> stringResource(R.string.cleaner_scanning)
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LoadingIndicator(modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = phaseText,
                        style = MaterialTheme.typography.bodyLargeMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = scanProgressText(progress),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (fraction != null) {
                LinearWavyProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun scanProgressText(progress: StorageCleanerScanProgress?): String {
    val scanned = progress?.scannedFiles ?: 0
    val percent = progress?.progressFraction?.let { (it * 100f).toInt().coerceIn(0, 100) }
    val etaMillis = progress?.estimatedRemainingMillis
    return when {
        etaMillis != null -> stringResource(
            R.string.cleaner_scan_progress_eta,
            scanned,
            percent ?: 0,
            formatEta(etaMillis)
        )
        percent != null -> stringResource(R.string.cleaner_scan_progress, scanned, percent)
        else -> stringResource(R.string.cleaner_scan_count, scanned)
    }
}

private fun formatEta(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1_000L).coerceAtLeast(1L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return if (minutes > 0L) "${minutes}m ${seconds}s" else "${seconds}s"
}

