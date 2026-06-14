package dev.qtremors.arcile.feature.storagecleaner.ui

import android.graphics.Bitmap
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
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import dev.qtremors.arcile.shared.ui.loadApplicationIconBitmap
import dev.qtremors.arcile.core.storage.domain.CleanerRiskReason
import dev.qtremors.arcile.core.storage.domain.CleanerCandidate
import dev.qtremors.arcile.core.storage.domain.CleanerGroup
import dev.qtremors.arcile.core.storage.domain.CleanerGroupType
import dev.qtremors.arcile.core.storage.domain.CleanerRiskLevel
import dev.qtremors.arcile.core.storage.domain.CleanerSectionRule
import dev.qtremors.arcile.core.storage.domain.StorageCleanerRules
import dev.qtremors.arcile.shared.ui.rememberDateFormatter
import dev.qtremors.arcile.ui.theme.bodyLargeMedium
import dev.qtremors.arcile.ui.theme.bodyMediumBold
import dev.qtremors.arcile.ui.theme.titleMediumBold
import dev.qtremors.arcile.utils.formatFileSize
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    onOpenContainingFolder: (String) -> Unit = {},
    rules: StorageCleanerRules = StorageCleanerRules(),
    onUpdateSectionRule: (CleanerGroupType, CleanerSectionRule) -> Unit = { _, _ -> },
    onResetSectionRule: (CleanerGroupType) -> Unit = {},
    onIgnorePath: (String) -> Unit = {}
) {
    var showRiskInfo by remember { mutableStateOf(false) }
    var showSectionSettings by remember { mutableStateOf(false) }
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
                    IconButton(onClick = { showSectionSettings = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.cleaner_section_settings)
                        )
                    }
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
                    group.candidates.groupBy { it.duplicateGroupKey ?: it.absolutePath }.values
                        .filter { it.size > 1 }
                        .toList()
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
                            },
                            onIgnoreFile = onIgnorePath
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
                            onOpenContainingFolder = onOpenContainingFolder,
                            onIgnoreFile = onIgnorePath
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

    if (showSectionSettings) {
        CleanerSectionSettingsDialog(
            type = group.type,
            rule = rules.section(group.type),
            onSave = { rule ->
                onUpdateSectionRule(group.type, rule)
                showSectionSettings = false
            },
            onReset = {
                onResetSectionRule(group.type)
                showSectionSettings = false
            },
            onDismiss = { showSectionSettings = false }
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
            onIgnoreFile = onIgnorePath,
            onDismiss = { compareFiles = null }
        )
    }
}

@Composable
private fun CleanerSectionSettingsDialog(
    type: CleanerGroupType,
    rule: CleanerSectionRule,
    onSave: (CleanerSectionRule) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    var enabled by remember(type, rule) { mutableStateOf(rule.enabled) }
    var namePatterns by remember(type, rule) { mutableStateOf(rule.ignoredNamePatterns) }
    var pathPatterns by remember(type, rule) { mutableStateOf(rule.ignoredPathPatterns) }
    var namePatternInput by remember(type, rule) { mutableStateOf("") }
    var pathPatternInput by remember(type, rule) { mutableStateOf("") }
    var largeThresholdMb by remember(type, rule) {
        mutableStateOf(rule.largeFileThresholdBytes?.let { (it / (1024L * 1024L)).toString() }.orEmpty())
    }
    var oldDownloadAgeDays by remember(type, rule) {
        mutableStateOf(rule.oldDownloadAgeMs?.let { (it / DAY_MS).toString() }.orEmpty())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cleaner_section_settings)) },
        text = {
            Column(
                modifier = Modifier
                    .height(420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.cleaner_section_enabled))
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                if (type == CleanerGroupType.LargeFiles) {
                    OutlinedTextField(
                        value = largeThresholdMb,
                        onValueChange = { largeThresholdMb = it.filter(Char::isDigit) },
                        label = { Text(stringResource(R.string.cleaner_large_threshold_mb)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (type == CleanerGroupType.OldDownloads) {
                    OutlinedTextField(
                        value = oldDownloadAgeDays,
                        onValueChange = { oldDownloadAgeDays = it.filter(Char::isDigit) },
                        label = { Text(stringResource(R.string.cleaner_old_download_age_days)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                PatternEditor(
                    title = stringResource(R.string.cleaner_ignore_name_patterns),
                    patterns = namePatterns,
                    input = namePatternInput,
                    onInputChange = { namePatternInput = it },
                    onAdd = {
                        val trimmed = namePatternInput.trim()
                        if (trimmed.isNotBlank()) {
                            namePatterns = namePatterns + trimmed
                            namePatternInput = ""
                        }
                    },
                    onRemove = { namePatterns = namePatterns - it }
                )
                PatternEditor(
                    title = stringResource(R.string.cleaner_ignore_path_patterns),
                    patterns = pathPatterns,
                    input = pathPatternInput,
                    onInputChange = { pathPatternInput = it },
                    onAdd = {
                        val trimmed = pathPatternInput.trim()
                        if (trimmed.isNotBlank()) {
                            pathPatterns = pathPatterns + trimmed
                            pathPatternInput = ""
                        }
                    },
                    onRemove = { pathPatterns = pathPatterns - it }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        CleanerSectionRule(
                            enabled = enabled,
                            ignoredNamePatterns = namePatterns,
                            ignoredPathPatterns = pathPatterns,
                            largeFileThresholdBytes = largeThresholdMb.toLongOrNull()?.times(1024L * 1024L),
                            oldDownloadAgeMs = oldDownloadAgeDays.toLongOrNull()?.times(DAY_MS)
                        )
                    )
                }
            ) {
                Text(stringResource(R.string.apply))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onReset) {
                    Text(stringResource(R.string.reset))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    )
}

@Composable
private fun PatternEditor(
    title: String,
    patterns: Set<String>,
    input: String,
    onInputChange: (String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                label = { Text(stringResource(R.string.cleaner_patterns_hint)) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(onClick = onAdd) {
                Text(stringResource(R.string.add))
            }
        }
        patterns.sorted().forEach { pattern ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = pattern,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                TextButton(onClick = { onRemove(pattern) }) {
                    Text(stringResource(R.string.remove))
                }
            }
        }
    }
}

@Composable
internal fun CleanerCandidateRow(
    file: CleanerCandidate,
    selected: Boolean,
    onToggle: () -> Unit,
    onOpenFile: (String) -> Unit = {},
    onOpenContainingFolder: (String) -> Unit = {},
    onIgnoreFile: (String) -> Unit = {}
) {
    val appContext = rememberCleanerAppContext(file)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onToggle() },
                modifier = Modifier.testTag("checkbox_${file.absolutePath}")
            )
            Spacer(modifier = Modifier.width(4.dp))
            CleanerFilePreview(
                file = file,
                badgeBgColor = MaterialTheme.colorScheme.surface,
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
                    style = MaterialTheme.typography.bodyLargeMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = cleanFilePath(file.absolutePath),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .clickable { onOpenContainingFolder(file.absolutePath) }
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = formatFileSize(file.size),
                style = MaterialTheme.typography.bodyMediumBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Row(
            modifier = Modifier.padding(start = 104.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RiskSummary(file = file, appContext = appContext)
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(onClick = { onIgnoreFile(file.absolutePath) }) {
                Text(stringResource(R.string.cleaner_ignore))
            }
        }
    }
}

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
                        text = stringResource(R.string.cleaner_duplicate_count, filesInGroup.size) + " • " + formatFileSize(firstFile.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onCompare) {
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
    onToggle: () -> Unit,
    onOpenFile: (String) -> Unit = {},
    onOpenContainingFolder: (String) -> Unit = {},
    onIgnoreFile: (String) -> Unit = {}
) {
    val formatter = rememberDateFormatter("MMM dd, yyyy  h:mm:ss a")
    val dateString = remember(file.lastModified) {
        try {
            formatter.format(Date(file.lastModified))
        } catch (_: Exception) {
            ""
        }
    }
    val appContext = rememberCleanerAppContext(file)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onToggle() }
            )
            Spacer(modifier = Modifier.width(4.dp))
            CleanerFilePreview(
                file = file,
                badgeBgColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onOpenFile(file.absolutePath) }
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = cleanFilePath(file.absolutePath),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .clickable { onOpenContainingFolder(file.absolutePath) }
                )
                if (dateString.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = dateString,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.testTag("cleaner_duplicate_timestamp_${file.absolutePath}")
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Row(
            modifier = Modifier.padding(start = 104.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RiskSummary(file = file, appContext = appContext)
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(onClick = { onIgnoreFile(file.absolutePath) }) {
                Text(stringResource(R.string.cleaner_ignore))
            }
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
                            onDelete = {
                                onRequestClean(setOf(file.absolutePath))
                            },
                            onOpenFile = onOpenFile,
                            onOpenContainingFolder = onOpenContainingFolder,
                            onIgnoreFile = onIgnoreFile,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        onRequestClean(selectedComparePaths)
                    },
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
            // 1. Header Row (Thumbnail, Name, Info, Badges)
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
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // 2. Path Container (Clickable to open folder)
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

            // 3. Selection Footer (with Checkbox and Delete Button)
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
private fun RiskSummary(
    file: CleanerCandidate,
    appContext: CleanerAppContext? = null
) {
    val reasonLabels = mutableListOf<String>()
    for (reason in file.riskReasons.take(2)) {
        if (reason != CleanerRiskReason.AppLikeFolder || appContext?.icon == null) {
            reasonLabels += cleanerRiskReason(reason)
        }
    }
    val reasons = reasonLabels.joinToString(", ")

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
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

@Composable
private fun rememberCleanerAppContext(file: CleanerCandidate): CleanerAppContext? {
    val context = LocalContext.current
    val packageName = remember(file.absolutePath, file.riskReasons) {
        if (CleanerRiskReason.AppLikeFolder in file.riskReasons) {
            packageNameFromPath(file.absolutePath)
        } else {
            null
        }
    }
    val appContext by produceState<CleanerAppContext?>(initialValue = null, context, packageName) {
        value = packageName?.let { pkg ->
            withContext(Dispatchers.IO) {
                val label = runCatching {
                    val pm = context.packageManager
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                }.getOrNull()
                CleanerAppContext(label = label, icon = context.loadApplicationIconBitmap(pkg))
            }
        }
    }
    return appContext
}

private data class CleanerAppContext(
    val label: String?,
    val icon: Bitmap?
)

private fun cleanFilePath(path: String): String {
    return path.replace("/storage/emulated/0/", "").replace("/storage/emulated/0", "")
}

private const val DAY_MS = 24L * 60L * 60L * 1000L
