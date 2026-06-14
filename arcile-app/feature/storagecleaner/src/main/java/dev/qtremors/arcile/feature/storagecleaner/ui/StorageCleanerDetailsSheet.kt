package dev.qtremors.arcile.feature.storagecleaner.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.storage.domain.CleanerCandidate
import dev.qtremors.arcile.core.storage.domain.CleanerGroup
import dev.qtremors.arcile.core.storage.domain.CleanerGroupType
import dev.qtremors.arcile.core.storage.domain.CleanerRiskLevel
import dev.qtremors.arcile.shared.ui.rememberDateFormatter
import dev.qtremors.arcile.ui.theme.bodyLargeMedium
import dev.qtremors.arcile.ui.theme.bodyMediumBold
import dev.qtremors.arcile.ui.theme.titleMediumBold
import dev.qtremors.arcile.utils.formatFileSize
import java.util.Date
import java.util.Locale
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CleanerDetailsSheet(
    group: CleanerGroup,
    isCleaning: Boolean,
    selectedFiles: Set<String>,
    onSelectedFilesChange: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
    onRequestClean: (Set<String>) -> Unit,
    onOpenFile: (String) -> Unit = {},
    onOpenContainingFolder: (String) -> Unit = {}
) {
    var showRiskInfo by remember { mutableStateOf(false) }
    var compareFiles by remember(group) { mutableStateOf<List<CleanerCandidate>?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = cleanerTitle(group.type),
                    style = MaterialTheme.typography.titleMediumBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showRiskInfo = true }) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = stringResource(R.string.cleaner_risk_info_title)
                        )
                    }
                    val selectableCandidates = remember(group.candidates) {
                        group.candidates.filterNot { it.riskLevel == CleanerRiskLevel.High }
                    }
                    Checkbox(
                        checked = selectableCandidates.isNotEmpty() &&
                            selectableCandidates.all { it.absolutePath in selectedFiles },
                        onCheckedChange = { checked ->
                            onSelectedFilesChange(if (checked) {
                                selectedFiles + selectableCandidates.map { it.absolutePath }
                            } else {
                                selectedFiles - selectableCandidates.map { it.absolutePath }.toSet()
                            })
                        }
                    )
                    Text(
                        text = stringResource(R.string.cleaner_select_all),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable {
                            val selectablePaths = selectableCandidates.map { it.absolutePath }.toSet()
                            onSelectedFilesChange(if (selectablePaths.isNotEmpty() && selectablePaths.all { it in selectedFiles }) {
                                selectedFiles - selectablePaths
                            } else {
                                selectedFiles + selectablePaths
                            })
                        }
                    )
                }
            }

            HorizontalDivider()

            if (group.candidates.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.cleaner_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (group.type == CleanerGroupType.Duplicates) {
                val duplicateGroups = remember(group.candidates) {
                    group.candidates.groupBy { it.name.lowercase(Locale.ROOT) to it.size }.values.toList()
                }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(duplicateGroups, key = { it.first().absolutePath }) { filesInGroup ->
                        DuplicateGroupCard(
                            filesInGroup = filesInGroup,
                            selectedFiles = selectedFiles,
                            onSelectedFilesChange = onSelectedFilesChange,
                            onOpenFile = onOpenFile,
                            onOpenContainingFolder = onOpenContainingFolder,
                            onCompare = {
                                val selectedInGroup = filesInGroup.filter { it.absolutePath in selectedFiles }
                                compareFiles = if (selectedInGroup.size == 2) selectedInGroup else filesInGroup.take(2)
                            }
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(group.candidates, key = { it.absolutePath }) { file ->
                        CleanerCandidateRow(
                            file = file,
                            selected = file.absolutePath in selectedFiles,
                            onToggle = {
                                onSelectedFilesChange(if (file.absolutePath in selectedFiles) {
                                    selectedFiles - file.absolutePath
                                } else {
                                    selectedFiles + file.absolutePath
                                })
                            },
                            onOpenFile = onOpenFile,
                            onOpenContainingFolder = onOpenContainingFolder
                        )
                    }
                }
            }

            val totalSelectedSize = remember(selectedFiles, group.candidates) {
                group.candidates.filter { it.absolutePath in selectedFiles }.sumOf { it.size }
            }

            Button(
                onClick = { onRequestClean(selectedFiles) },
                enabled = selectedFiles.isNotEmpty() && !isCleaning,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
                    .height(56.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.clean_selected_summary, selectedFiles.size, formatFileSize(totalSelectedSize))
                )
            }
        }
    }

    if (showRiskInfo) {
        AlertDialog(
            onDismissRequest = { showRiskInfo = false },
            title = { Text(stringResource(R.string.cleaner_risk_info_title)) },
            text = { Text(stringResource(R.string.cleaner_risk_info_description)) },
            confirmButton = {
                TextButton(onClick = { showRiskInfo = false }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }

    compareFiles?.let { files ->
        DuplicateCompareSheet(
            files = files,
            selectedFiles = selectedFiles,
            onSelectedFilesChange = onSelectedFilesChange,
            onRequestClean = { paths ->
                compareFiles = null
                onRequestClean(paths)
            },
            onOpenFile = onOpenFile,
            onOpenContainingFolder = onOpenContainingFolder,
            onDismiss = { compareFiles = null }
        )
    }
}

@Composable
internal fun CleanerCandidateRow(
    file: CleanerCandidate,
    selected: Boolean,
    onToggle: () -> Unit,
    onOpenFile: (String) -> Unit = {},
    onOpenContainingFolder: (String) -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = selected, onCheckedChange = { onToggle() })
        Spacer(modifier = Modifier.width(8.dp))
        CleanerFilePreview(file = file)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyLargeMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = file.absolutePath,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            RiskSummary(file)
            CleanerFileActions(
                file = file,
                onOpenFile = onOpenFile,
                onOpenContainingFolder = onOpenContainingFolder
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = formatFileSize(file.size),
            style = MaterialTheme.typography.bodyMediumBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
internal fun DuplicateGroupCard(
    filesInGroup: List<CleanerCandidate>,
    selectedFiles: Set<String>,
    onSelectedFilesChange: (Set<String>) -> Unit,
    onOpenFile: (String) -> Unit = {},
    onOpenContainingFolder: (String) -> Unit = {},
    onCompare: () -> Unit
) {
    val firstFile = filesInGroup.first()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
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
                        text = stringResource(R.string.cleaner_duplicate_count, filesInGroup.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatFileSize(firstFile.size),
                        style = MaterialTheme.typography.bodyMediumBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = onCompare) {
                        Text(stringResource(R.string.cleaner_compare))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(4.dp))

            filesInGroup.forEach { file ->
                DuplicateFileRow(
                    file = file,
                    selected = file.absolutePath in selectedFiles,
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
                    onOpenContainingFolder = onOpenContainingFolder
                )
            }
        }
    }
}

@Composable
internal fun DuplicateFileRow(
    file: CleanerCandidate,
    selected: Boolean,
    onToggle: () -> Unit,
    onOpenFile: (String) -> Unit = {},
    onOpenContainingFolder: (String) -> Unit = {}
) {
    val formatter = rememberDateFormatter("MMM dd, yyyy  h:mm a")
    val dateString = remember(file.lastModified) {
        try {
            formatter.format(Date(file.lastModified))
        } catch (_: Exception) {
            ""
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = selected, onCheckedChange = { onToggle() })
        Spacer(modifier = Modifier.width(8.dp))
        CleanerFilePreview(file = file)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.absolutePath,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (dateString.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = dateString,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            RiskSummary(file)
            CleanerFileActions(
                file = file,
                onOpenFile = onOpenFile,
                onOpenContainingFolder = onOpenContainingFolder
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DuplicateCompareSheet(
    files: List<CleanerCandidate>,
    selectedFiles: Set<String>,
    onSelectedFilesChange: (Set<String>) -> Unit,
    onRequestClean: (Set<String>) -> Unit,
    onOpenFile: (String) -> Unit,
    onOpenContainingFolder: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = stringResource(R.string.cleaner_compare_title),
                style = MaterialTheme.typography.titleMediumBold
            )
            Spacer(modifier = Modifier.height(12.dp))
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
                    modifier = Modifier.fillMaxWidth(),
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
                            onDelete = {
                                onRequestClean(setOf(file.absolutePath))
                            },
                            onOpenFile = onOpenFile,
                            onOpenContainingFolder = onOpenContainingFolder,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        onRequestClean(selectedComparePaths)
                    },
                    enabled = selectedComparePaths.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
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
    modifier: Modifier = Modifier
) {
    val formatter = rememberDateFormatter("MMM dd, yyyy  h:mm a")
    val dateString = remember(file.lastModified) {
        runCatching { formatter.format(Date(file.lastModified)) }.getOrDefault("")
    }
    val deleteDescription = stringResource(R.string.cleaner_delete_this_file)
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.semantics { contentDescription = deleteDescription }
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            CleanerFilePreview(file = file, size = 96.dp)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyLargeMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatFileSize(file.size),
                style = MaterialTheme.typography.bodyMediumBold
            )
            if (dateString.isNotEmpty()) {
                Text(
                    text = dateString,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = file.absolutePath,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
            RiskSummary(file)
            CleanerFileActions(
                file = file,
                onOpenFile = onOpenFile,
                onOpenContainingFolder = onOpenContainingFolder
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = selected, onCheckedChange = { onToggle() })
                Text(
                    text = stringResource(
                        if (selected) R.string.cleaner_move_file_to_trash else R.string.cleaner_keep_file
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun CleanerFileActions(
    file: CleanerCandidate,
    onOpenFile: (String) -> Unit,
    onOpenContainingFolder: (String) -> Unit
) {
    Spacer(modifier = Modifier.height(4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (!file.isDirectory) {
            TextButton(onClick = { onOpenFile(file.absolutePath) }) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.cleaner_open_file))
            }
        }
        TextButton(onClick = { onOpenContainingFolder(file.absolutePath) }) {
            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringResource(R.string.cleaner_open_folder))
        }
    }
}

@Composable
private fun RiskSummary(file: CleanerCandidate) {
    val reasonLabels = mutableListOf<String>()
    for (reason in file.riskReasons.take(2)) {
        reasonLabels += cleanerRiskReason(reason)
    }
    val reasons = reasonLabels.joinToString(", ")

    Spacer(modifier = Modifier.height(4.dp))
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = cleanerRiskColor(file.riskLevel).copy(alpha = 0.16f)
        ) {
            Text(
                text = cleanerRiskLabel(file.riskLevel),
                style = MaterialTheme.typography.labelSmall,
                color = cleanerRiskColor(file.riskLevel),
                modifier = Modifier
                    .widthIn(min = 52.dp)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        if (reasons.isNotBlank()) {
            Text(
                text = reasons,
                style = MaterialTheme.typography.labelSmall,
                color = cleanerRiskColor(file.riskLevel),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
