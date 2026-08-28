package dev.qtremors.arcile.feature.storagecleaner.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.presentation.formatFileSize
import dev.qtremors.arcile.core.storage.domain.CleanerCandidate
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.rememberArcileHaptics
import dev.qtremors.arcile.core.ui.theme.ExpressiveShapes
import dev.qtremors.arcile.core.ui.theme.bodyLargeMedium
import dev.qtremors.arcile.core.ui.theme.bodyMediumBold
import dev.qtremors.arcile.core.ui.theme.bounceClickable

@Composable
internal fun CleanerCandidateRow(
    file: CleanerCandidate,
    selected: Boolean,
    index: Int = 0,
    count: Int = 1,
    isInSelectionMode: Boolean = false,
    onToggle: () -> Unit,
    onOpenFile: (String) -> Unit = {},
    onOpenContainingFolder: (String) -> Unit = {},
    onIgnoreFile: (String) -> Unit = {}
) {
    val haptics = rememberArcileHaptics()
    val appContext = rememberCleanerAppContext(file)
    val shape = cleanerCandidateShape(index, count)
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .testTag("cleaner_row_${file.absolutePath}")
            .semantics { this.selected = selected }
            .combinedClickable(
                onClick = {
                    if (isInSelectionMode) {
                        onToggle()
                        haptics.selectionChanged()
                    } else {
                        onOpenContainingFolder(file.absolutePath)
                    }
                },
                onLongClick = {
                    onToggle()
                    haptics.selectionStart()
                }
            ),
        shape = shape,
        color = containerColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CleanerFilePreview(
                    file = file,
                    badgeBgColor = if (selected) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .clip(CircleShape)
                        .testTag("cleaner_thumbnail_${file.absolutePath}")
                        .bounceClickable {
                            if (file.isDirectory) {
                                onOpenContainingFolder(file.absolutePath)
                            } else {
                                onOpenFile(file.absolutePath)
                            }
                        }
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("cleaner_location_${file.absolutePath}")
                ) {
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.bodyLargeMedium,
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = cleanFilePath(file.absolutePath),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = formatFileSize(androidx.compose.ui.platform.LocalContext.current, file.size),
                    style = MaterialTheme.typography.bodyMediumBold,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = CleanerRowContentStart),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CleanerRiskSummary(
                    file = file,
                    appContext = appContext,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    onClick = { onIgnoreFile(file.absolutePath) },
                    shape = ExpressiveShapes.medium
                ) {
                    Text(stringResource(R.string.cleaner_ignore_file))
                }
            }
        }
    }
}

internal const val DAY_MS = 24L * 60L * 60L * 1000L
internal val CleanerRowContentStart = 56.dp

private fun cleanerCandidateShape(index: Int, count: Int) = when {
    count <= 1 -> RoundedCornerShape(28.dp)
    index == 0 -> RoundedCornerShape(
        topStart = 28.dp,
        topEnd = 28.dp,
        bottomStart = 4.dp,
        bottomEnd = 4.dp
    )
    index == count - 1 -> RoundedCornerShape(
        topStart = 4.dp,
        topEnd = 4.dp,
        bottomStart = 28.dp,
        bottomEnd = 28.dp
    )
    else -> RoundedCornerShape(4.dp)
}
