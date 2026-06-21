package dev.qtremors.arcile.feature.storagecleaner.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.storage.domain.CleanerCandidate
import dev.qtremors.arcile.core.storage.domain.CleanerRiskReason
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.shared.ui.rememberDateFormatter
import dev.qtremors.arcile.ui.theme.titleMediumBold
import dev.qtremors.arcile.utils.formatFileSize
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DuplicateCompareSheet(
    files: List<CleanerCandidate>,
    selectedFiles: Set<String>,
    onSelectedFilesChange: (Set<String>) -> Unit,
    onRequestClean: (Set<String>) -> Unit,
    onOpenFile: (String) -> Unit,
    onOpenContainingFolder: (String) -> Unit,
    onIgnoreFile: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = stringResource(R.string.cleaner_compare_title),
                style = MaterialTheme.typography.titleMediumBold
            )
            Spacer(modifier = Modifier.height(16.dp))
            if (files.size < 2) {
                Text(
                    text = stringResource(R.string.cleaner_compare_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val comparePaths = files.take(2).map { it.absolutePath }.toSet()
                val selectedComparePaths = selectedFiles.intersect(comparePaths)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    files.take(2).forEach { file ->
                        DuplicateComparePane(
                            file = file,
                            selected = file.absolutePath in selectedFiles,
                            onToggle = {
                                onSelectedFilesChange(if (file.absolutePath in selectedFiles) {
                                    selectedFiles - file.absolutePath
                                } else {
                                    selectedFiles + file.absolutePath
                                })
                            },
                            onDelete = { onRequestClean(setOf(file.absolutePath)) },
                            onOpenFile = onOpenFile,
                            onOpenContainingFolder = onOpenContainingFolder,
                            onIgnoreFile = onIgnoreFile,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { onRequestClean(selectedComparePaths) },
                    enabled = selectedComparePaths.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(
                            R.string.clean_selected_summary,
                            selectedComparePaths.size,
                            formatFileSize(files.filter { it.absolutePath in selectedComparePaths }.sumOf { it.size })
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DuplicateComparePane(
    file: CleanerCandidate,
    selected: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onOpenFile: (String) -> Unit,
    onOpenContainingFolder: (String) -> Unit,
    onIgnoreFile: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val formatter = rememberDateFormatter("MMM dd, yyyy  h:mm:ss a")
    val dateString = remember(file.lastModified) {
        runCatching { formatter.format(Date(file.lastModified)) }.getOrDefault("")
    }
    val deleteDescription = stringResource(R.string.cleaner_delete_this_file)
    val appContext = rememberCleanerAppContext(file)
    val reasonLabels = mutableListOf<String>()
    for (reason in file.riskReasons.take(2)) {
        if (reason != CleanerRiskReason.AppLikeFolder || appContext?.icon == null) {
            reasonLabels += cleanerRiskReason(reason)
        }
    }
    val reasons = reasonLabels.joinToString(", ")

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CleanerFilePreview(
                    file = file,
                    size = 56.dp,
                    badgeBgColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable {
                            if (file.isDirectory) {
                                onOpenContainingFolder(file.absolutePath)
                            } else {
                                onOpenFile(file.absolutePath)
                            }
                        }
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${formatFileSize(file.size)}  •  $dateString",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("cleaner_duplicate_timestamp_${file.absolutePath}")
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (appContext?.icon != null) {
                            Image(
                                bitmap = appContext.icon.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                            )
                        }
                        Surface(
                            shape = CircleShape,
                            color = cleanerRiskColor(file.riskLevel).copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = cleanerRiskLabel(file.riskLevel),
                                style = MaterialTheme.typography.labelSmall,
                                color = cleanerRiskColor(file.riskLevel),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        if (reasons.isNotBlank()) {
                            Text(
                                text = reasons,
                                style = MaterialTheme.typography.labelSmall,
                                color = cleanerRiskColor(file.riskLevel),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { onOpenContainingFolder(file.absolutePath) },
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.properties_location),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = cleanFilePath(file.absolutePath),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onToggle)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(
                            if (selected) R.string.cleaner_move_file_to_trash else R.string.cleaner_keep_file
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .semantics { contentDescription = deleteDescription }
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                }
                TextButton(
                    onClick = { onIgnoreFile(file.absolutePath) },
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text(stringResource(R.string.cleaner_ignore))
                }
            }
        }
    }
}
