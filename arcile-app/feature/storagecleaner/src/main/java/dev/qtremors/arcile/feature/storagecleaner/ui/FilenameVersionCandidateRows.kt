package dev.qtremors.arcile.feature.storagecleaner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.presentation.formatFileSize
import dev.qtremors.arcile.core.storage.domain.CleanerCandidate
import dev.qtremors.arcile.core.storage.domain.FilenameVersionEvidence
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.rememberArcileHaptics
import dev.qtremors.arcile.core.ui.rememberDateOnlyFormatter
import dev.qtremors.arcile.core.ui.theme.ExpressiveShapes
import dev.qtremors.arcile.core.ui.theme.bodyLargeMedium
import dev.qtremors.arcile.core.ui.theme.bodyMediumBold
import dev.qtremors.arcile.core.ui.theme.bounceClickable
import java.util.Date

@Composable
internal fun FilenameVersionFamilyCard(
    filesInFamily: List<CleanerCandidate>,
    selectedFiles: Set<String>,
    onSelectedFilesChange: (Set<String>) -> Unit,
    onOpenFile: (String) -> Unit = {},
    onOpenContainingFolder: (String) -> Unit = {},
    onIgnoreFile: (String) -> Unit = {}
) {
    val firstFile = filesInFamily.firstOrNull() ?: return
    val metadata = firstFile.filenameVersionMetadata
    val displayTitle = metadata?.displayStem ?: firstFile.name.substringBeforeLast('.', firstFile.name)
    val evidenceType = metadata?.evidenceType ?: FilenameVersionEvidence.SemanticVersion
    val totalFamilySize = remember(filesInFamily) { filesInFamily.sumOf { it.size } }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
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
                        text = displayTitle,
                        style = MaterialTheme.typography.bodyLargeMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = pluralStringResource(R.plurals.cleaner_version_count, filesInFamily.size, filesInFamily.size) +
                            " • " + formatFileSize(LocalContext.current, totalFamilySize),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                EvidenceBadge(evidenceType = evidenceType)
            }
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(4.dp))
            filesInFamily.forEach { file ->
                FilenameVersionFileRow(
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
private fun EvidenceBadge(evidenceType: FilenameVersionEvidence) {
    val label = when (evidenceType) {
        FilenameVersionEvidence.SemanticVersion -> stringResource(R.string.cleaner_evidence_semantic_version)
        FilenameVersionEvidence.DuplicateSuffix -> stringResource(R.string.cleaner_evidence_duplicate_suffix)
        FilenameVersionEvidence.PackageVersion -> stringResource(R.string.cleaner_evidence_apk_package)
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun FilenameVersionFileRow(
    file: CleanerCandidate,
    selected: Boolean,
    isInSelectionMode: Boolean = false,
    onToggle: () -> Unit,
    onOpenFile: (String) -> Unit = {},
    onOpenContainingFolder: (String) -> Unit = {},
    onIgnoreFile: (String) -> Unit = {}
) {
    val haptics = rememberArcileHaptics()
    val dateFormatter = rememberDateOnlyFormatter()
    val isLikelyNewest = file.filenameVersionMetadata?.isLikelyNewest == true
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = file.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isLikelyNewest) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isLikelyNewest) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ) {
                                Text(
                                    text = stringResource(R.string.cleaner_likely_newest),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = cleanFilePath(file.absolutePath) + " • " +
                            dateFormatter.format(Date(file.lastModified)),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatFileSize(LocalContext.current, file.size),
                    style = MaterialTheme.typography.bodyMediumBold,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = CleanerRowContentStart),
                horizontalArrangement = Arrangement.End
            ) {
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
