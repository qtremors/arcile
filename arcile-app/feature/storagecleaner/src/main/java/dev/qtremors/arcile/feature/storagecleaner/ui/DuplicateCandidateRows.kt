package dev.qtremors.arcile.feature.storagecleaner.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.presentation.formatFileSize
import dev.qtremors.arcile.core.storage.domain.CleanerCandidate
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.rememberArcileHaptics
import dev.qtremors.arcile.core.ui.rememberDateFormatter
import dev.qtremors.arcile.core.ui.theme.ExpressiveShapes
import dev.qtremors.arcile.core.ui.theme.bodyLargeMedium
import dev.qtremors.arcile.core.ui.theme.bounceClickable
import java.util.Date

@Composable
internal fun DuplicateGroupCard(
    filesInGroup: List<CleanerCandidate>,
    selectedFiles: Set<String>,
    onSelectedFilesChange: (Set<String>) -> Unit,
    onOpenFile: (String) -> Unit = {},
    onOpenContainingFolder: (String) -> Unit = {},
    onCompare: () -> Unit,
    onIgnoreFile: (String) -> Unit = {}
) {
    val firstFile = filesInGroup.firstOrNull() ?: return
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = firstFile.name,
                        style = MaterialTheme.typography.bodyLargeMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = androidx.compose.ui.res.pluralStringResource(R.plurals.cleaner_duplicate_count, filesInGroup.size, filesInGroup.size) +
                            " • " + formatFileSize(androidx.compose.ui.platform.LocalContext.current, firstFile.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onCompare, shape = ExpressiveShapes.medium) {
                    Text(stringResource(R.string.cleaner_compare))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(4.dp))
            filesInGroup.forEach { file ->
                DuplicateFileRow(
                    file = file,
                    selected = file.absolutePath in selectedFiles,
                    isInSelectionMode = selectedFiles.isNotEmpty(),
                    onToggle = {
                        onSelectedFilesChange(
                            if (file.absolutePath in selectedFiles) {
                                selectedFiles - file.absolutePath
                            } else {
                                selectedFiles + file.absolutePath
                            }
                        )
                    },
                    onOpenFile = onOpenFile,
                    onOpenContainingFolder = onOpenContainingFolder,
                    onIgnoreFile = onIgnoreFile
                )
            }
        }
    }
}

@Composable
internal fun DuplicateFileRow(
    file: CleanerCandidate,
    selected: Boolean,
    isInSelectionMode: Boolean = false,
    onToggle: () -> Unit,
    onOpenFile: (String) -> Unit = {},
    onOpenContainingFolder: (String) -> Unit = {},
    onIgnoreFile: (String) -> Unit = {}
) {
    val haptics = rememberArcileHaptics()
    val formatter = rememberDateFormatter("MMM dd, yyyy  h:mm:ss a")
    val dateString = remember(file.lastModified) {
        runCatching { formatter.format(Date(file.lastModified)) }.getOrDefault("")
    }
    val appContext = rememberCleanerAppContext(file)
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }

    Surface(
        shape = ExpressiveShapes.medium,
        color = containerColor,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(ExpressiveShapes.medium)
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
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                CleanerFilePreview(
                    file = file,
                    badgeBgColor = if (selected) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
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
                        text = cleanFilePath(file.absolutePath),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (dateString.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = dateString,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.testTag("cleaner_duplicate_timestamp_${file.absolutePath}")
                        )
                    }
                }
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
