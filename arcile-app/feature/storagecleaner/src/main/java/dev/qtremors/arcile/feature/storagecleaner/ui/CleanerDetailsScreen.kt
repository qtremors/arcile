package dev.qtremors.arcile.feature.storagecleaner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Tune
import dev.qtremors.arcile.core.ui.dialogs.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import dev.qtremors.arcile.core.ui.theme.ExpressiveShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SelectAll
import dev.qtremors.arcile.core.presentation.formatFileSize
import dev.qtremors.arcile.core.storage.domain.CleanerCandidate
import dev.qtremors.arcile.core.storage.domain.CleanerGroup
import dev.qtremors.arcile.core.storage.domain.CleanerGroupType
import dev.qtremors.arcile.core.storage.domain.CleanerRiskLevel
import dev.qtremors.arcile.core.storage.domain.CleanerSectionRule
import dev.qtremors.arcile.core.storage.domain.StorageCleanerRules
import dev.qtremors.arcile.core.storage.domain.StorageCleanerScanProgress
import dev.qtremors.arcile.core.ui.ArcilePullRefreshIndicator
import dev.qtremors.arcile.core.ui.R

private val cleanerPathListSaver = Saver<List<String>, ArrayList<String>>(
    save = { ArrayList(it) },
    restore = { it.toList() }
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CleanerDetailsScreen(
    group: CleanerGroup,
    isCleaning: Boolean,
    isLoaded: Boolean = true,
    isScanning: Boolean = false,
    isPullToRefreshing: Boolean = false,
    scanProgress: StorageCleanerScanProgress? = null,
    onRefresh: () -> Unit = {},
    onNavigateBack: () -> Unit,
    selectedFiles: Set<String>,
    onSelectedFilesChange: (Set<String>) -> Unit,
    onRequestClean: (Set<String>) -> Unit,
    onOpenFile: (String) -> Unit = {},
    onOpenContainingFolder: (String) -> Unit = {},
    rules: StorageCleanerRules = StorageCleanerRules(),
    onUpdateSectionRule: (CleanerGroupType, CleanerSectionRule) -> Unit = { _, _ -> },
    onResetSectionRule: (CleanerGroupType) -> Unit = {},
    onIgnorePath: (String) -> Unit = {}
) {
    var showRiskInfo by rememberSaveable(group.type.name) { mutableStateOf(false) }
    var showSectionSettings by rememberSaveable(group.type.name) { mutableStateOf(false) }
    var comparePaths by rememberSaveable(group.type.name, stateSaver = cleanerPathListSaver) {
        mutableStateOf<List<String>>(emptyList())
    }
    val compareFiles = remember(comparePaths, group.candidates) {
        val candidatesByPath = group.candidates.associateBy(CleanerCandidate::absolutePath)
        comparePaths.mapNotNull(candidatesByPath::get).takeIf { it.size >= 2 }
    }

    val isInSelectionMode = selectedFiles.isNotEmpty()
    val selectableCandidates = remember(group.candidates) {
        group.candidates.filterNot { it.riskLevel == CleanerRiskLevel.High }
    }
    val allSelectableSelected = selectableCandidates.isNotEmpty() &&
        selectableCandidates.all { it.absolutePath in selectedFiles }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isInSelectionMode) {
                        Text(pluralStringResource(R.plurals.selected_count, selectedFiles.size, selectedFiles.size))
                    } else {
                        Text(cleanerTitle(group.type))
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isInSelectionMode) {
                                onSelectedFilesChange(emptySet())
                            } else {
                                onNavigateBack()
                            }
                        },
                        modifier = Modifier.clip(CircleShape)
                    ) {
                        Icon(
                            if (isInSelectionMode) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(
                                if (isInSelectionMode) R.string.clear_selection else R.string.back
                            )
                        )
                    }
                },
                actions = {
                    if (isInSelectionMode) {
                        IconButton(
                            onClick = { onRequestClean(selectedFiles) },
                            enabled = isLoaded && !isCleaning && !isScanning,
                            modifier = Modifier.clip(CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.cleaner_delete_selected),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            val selectablePaths = selectableCandidates.map { it.absolutePath }.toSet()
                            if (allSelectableSelected) {
                                onSelectedFilesChange(selectedFiles - selectablePaths)
                            } else {
                                onSelectedFilesChange(selectedFiles + selectablePaths)
                            }
                        },
                        modifier = Modifier.clip(CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SelectAll,
                            contentDescription = stringResource(
                                if (allSelectableSelected) R.string.clear_selection else R.string.cleaner_select_all
                            ),
                            tint = if (allSelectableSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (!isInSelectionMode) {
                        IconButton(
                            onClick = { showSectionSettings = true },
                            modifier = Modifier.clip(CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = stringResource(
                                    R.string.cleaner_section_settings_title,
                                    cleanerTitle(group.type)
                                )
                            )
                        }
                        IconButton(
                            onClick = { showRiskInfo = true },
                            modifier = Modifier.clip(CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = stringResource(R.string.cleaner_risk_info_title)
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        val pullRefreshState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = isPullToRefreshing,
            onRefresh = {
                if (!isScanning && !isCleaning) onRefresh()
            },
            state = pullRefreshState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            indicator = {
                ArcilePullRefreshIndicator(
                    isRefreshing = isPullToRefreshing,
                    state = pullRefreshState
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
            if (isScanning) {
                CleanerScanStatus(
                    progress = scanProgress,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
            CleanerDetailStorageMap(group.candidates, selectedFiles)

            if (group.candidates.isEmpty() && !isScanning) {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    item {
                        Box(
                            modifier = Modifier.fillParentMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.cleaner_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
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
                                comparePaths = filesInGroup.map(CleanerCandidate::absolutePath)
                            },
                            onIgnoreFile = { path ->
                                onSelectedFilesChange(selectedFiles - path)
                                onIgnorePath(path)
                            }
                        )
                    }
                }
            } else if (group.type == CleanerGroupType.FilenameVersions) {
                val versionFamilies = remember(group.candidates) {
                    group.candidates.groupBy { it.filenameVersionMetadata?.familyKey ?: it.absolutePath }.values
                        .filter { it.size > 1 }
                        .toList()
                }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(versionFamilies, key = { it.first().filenameVersionMetadata?.familyKey ?: it.first().absolutePath }) { filesInFamily ->
                        FilenameVersionFamilyCard(
                            filesInFamily = filesInFamily,
                            selectedFiles = selectedFiles,
                            onSelectedFilesChange = onSelectedFilesChange,
                            onOpenFile = onOpenFile,
                            onOpenContainingFolder = onOpenContainingFolder,
                            onIgnoreFile = { path ->
                                onSelectedFilesChange(selectedFiles - path)
                                onIgnorePath(path)
                            }
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
                ) {
                    items(
                        count = group.candidates.size,
                        key = { group.candidates[it].absolutePath }
                    ) { index ->
                        val file = group.candidates[index]
                        CleanerCandidateRow(
                            file = file,
                            selected = file.absolutePath in selectedFiles,
                            index = index,
                            count = group.candidates.size,
                            isInSelectionMode = isInSelectionMode,
                            onToggle = {
                                onSelectedFilesChange(if (file.absolutePath in selectedFiles) {
                                    selectedFiles - file.absolutePath
                                } else {
                                    selectedFiles + file.absolutePath
                                })
                            },
                            onOpenFile = onOpenFile,
                            onOpenContainingFolder = onOpenContainingFolder,
                            onIgnoreFile = { path ->
                                onSelectedFilesChange(selectedFiles - path)
                                onIgnorePath(path)
                            }
                        )
                    }
                }
            }

            }
        }
    }

    if (showRiskInfo) {
        AlertDialog(
            onDismissRequest = { showRiskInfo = false },
            title = { Text(stringResource(R.string.cleaner_risk_info_title)) },
            text = { Text(stringResource(R.string.cleaner_risk_info_description)) },
            confirmButton = {
                val dismissClick = { showRiskInfo = false }
                TextButton(
                    onClick = dismissClick,
                    shape = ExpressiveShapes.medium
                ) {
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
                comparePaths = emptyList()
                onRequestClean(paths)
            },
            onOpenFile = onOpenFile,
            onOpenContainingFolder = onOpenContainingFolder,
            onIgnoreFile = { path ->
                onSelectedFilesChange(selectedFiles - path)
                onIgnorePath(path)
            },
            onDismiss = { comparePaths = emptyList() }
        )
    }
}

@Composable
private fun CleanerDetailStorageMap(
    candidates: List<CleanerCandidate>,
    selectedFiles: Set<String>
) {
    val total = candidates.sumOf(CleanerCandidate::size).coerceAtLeast(0L)
    val selected = candidates
        .filter { it.absolutePath in selectedFiles }
        .sumOf(CleanerCandidate::size)
        .coerceIn(0L, total)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.cleaner_detail_storage_map),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f)
            )
            val context = androidx.compose.ui.platform.LocalContext.current
            Text(
                stringResource(
                    R.string.cleaner_detail_storage_selected,
                    formatFileSize(context, selected),
                    formatFileSize(context, total)
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(CircleShape),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (selected > 0L && total > 0L) {
                Box(
                    Modifier
                        .weight(selected.toFloat() / total.toFloat())
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.error)
                )
            }
            Box(
                Modifier
                    .weight(
                        if (total > 0L) {
                            ((total - selected).toFloat() / total.toFloat()).coerceAtLeast(0.01f)
                        } else {
                            1f
                        }
                    )
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            )
        }
    }
}
