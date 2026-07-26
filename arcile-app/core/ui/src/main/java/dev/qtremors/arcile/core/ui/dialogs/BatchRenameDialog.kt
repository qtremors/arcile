package dev.qtremors.arcile.core.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import dev.qtremors.arcile.core.storage.domain.BatchRenameEngine
import dev.qtremors.arcile.core.storage.domain.BatchRenameError
import dev.qtremors.arcile.core.storage.domain.BatchRenameItem
import dev.qtremors.arcile.core.storage.domain.BatchRenameRule
import dev.qtremors.arcile.core.storage.domain.CaseTransform
import dev.qtremors.arcile.core.storage.domain.EnumerationConfig
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.RenameTargetScope
import dev.qtremors.arcile.core.ui.ArcileCardDropdownMenu
import dev.qtremors.arcile.core.ui.ArcileCardDropdownMenuItem
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.theme.ExpressiveShapes
import dev.qtremors.arcile.core.ui.theme.bounceClickable

private data class RegexPreset(val label: String, val pattern: String)
private val REGEX_PRESETS = listOf(
    RegexPreset("Remove [brackets]", """\s*\[.*\]"""),
    RegexPreset("Remove (numbers)", """\s*\(\d+\)"""),
    RegexPreset("Remove 01 - numbers", """^\d+[\.\_\s-]+"""),
    RegexPreset("Normalize spaces", """\s+""")
)

@Composable
fun BatchRenameDialog(
    files: List<FileModel>,
    onDismiss: () -> Unit,
    onConfirm: (List<Pair<FileModel, String>>) -> Unit,
    existingFolderNames: Set<String> = emptySet(),
    searchHistory: List<String> = emptyList(),
    onSaveFindQuery: (String) -> Unit = {},
    onRemoveHistoryItem: (String) -> Unit = {}
) {
    var findQuery by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var useRegex by remember { mutableStateOf(false) }
    var matchCase by remember { mutableStateOf(false) }
    var targetScope by remember { mutableStateOf(RenameTargetScope.NAME_ONLY) }
    var caseTransform by remember { mutableStateOf(CaseTransform.NONE) }
    var useEnumerate by remember { mutableStateOf(false) }
    var showPresetsMenu by remember { mutableStateOf(false) }
    var showHistoryMenu by remember { mutableStateOf(false) }

    val rule = remember(
        findQuery,
        replacement,
        useRegex,
        matchCase,
        targetScope,
        caseTransform,
        useEnumerate
    ) {
        BatchRenameRule(
            findQuery = findQuery,
            replacement = replacement,
            useRegex = useRegex,
            matchCase = matchCase,
            targetScope = targetScope,
            caseTransform = caseTransform,
            enumeration = EnumerationConfig(enabled = useEnumerate)
        )
    }

    val previewItems = remember(files, rule, existingFolderNames) {
        BatchRenameEngine.evaluate(files, rule, existingFolderNames)
    }

    val hasChanges = remember(previewItems) { previewItems.any { it.isChanged } }
    val hasErrors = remember(previewItems) { previewItems.any { !it.isValid } }
    val canApply = hasChanges && !hasErrors
    val presetMenuItems = buildList<@Composable () -> Unit> {
        REGEX_PRESETS.forEach { preset ->
            add {
                ArcileCardDropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = preset.label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = preset.pattern,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    onClick = {
                        findQuery = preset.pattern
                        useRegex = true
                        showPresetsMenu = false
                    }
                )
            }
        }
    }
    val historyMenuItems = buildList<@Composable () -> Unit> {
        searchHistory.take(8).forEach { query ->
            add {
                ArcileCardDropdownMenuItem(
                    text = {
                        Text(
                            text = query,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    onClick = {
                        findQuery = query
                        showHistoryMenu = false
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = { onRemoveHistoryItem(query) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.action_remove_history),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                )
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 24.dp)
                .wrapContentHeight(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header Row with Title on Left, Presets Button on Top Right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.title_batch_rename, files.size),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    // Presets Icon Button & Elevated Dropdown Menu in Top Right
                    Box {
                        IconButton(onClick = { showPresetsMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Regex Presets",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        ArcileCardDropdownMenu(
                            expanded = showPresetsMenu,
                            onDismissRequest = { showPresetsMenu = false },
                            items = presetMenuItems
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Find Input with Trailing History Button & Elevated Dropdown Menu
                OutlinedTextField(
                    value = findQuery,
                    onValueChange = { findQuery = it },
                    label = { Text(stringResource(R.string.label_find)) },
                    singleLine = true,
                    trailingIcon = {
                        Box {
                            IconButton(
                                onClick = { showHistoryMenu = true },
                                enabled = searchHistory.isNotEmpty()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = "Search History",
                                    tint = if (searchHistory.isNotEmpty()) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                    }
                                )
                            }

                            ArcileCardDropdownMenu(
                                expanded = showHistoryMenu,
                                onDismissRequest = { showHistoryMenu = false },
                                items = historyMenuItems
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = ExpressiveShapes.medium
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Replace Input
                OutlinedTextField(
                    value = replacement,
                    onValueChange = { replacement = it },
                    label = { Text(stringResource(R.string.label_replace_with)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = ExpressiveShapes.medium
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Options Chips Row (Horizontal Scrollable)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    FilterChip(
                        selected = useRegex,
                        onClick = { useRegex = !useRegex },
                        label = { Text(stringResource(R.string.option_regex), maxLines = 1, softWrap = false) }
                    )
                    FilterChip(
                        selected = matchCase,
                        onClick = { matchCase = !matchCase },
                        label = { Text(stringResource(R.string.option_match_case), maxLines = 1, softWrap = false) }
                    )
                    FilterChip(
                        selected = useEnumerate,
                        onClick = { useEnumerate = !useEnumerate },
                        label = { Text(stringResource(R.string.option_enumerate), maxLines = 1, softWrap = false) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Target Scope Segmented Buttons
                Text(
                    text = "Target Scope",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = targetScope == RenameTargetScope.NAME_ONLY,
                            onClick = { targetScope = RenameTargetScope.NAME_ONLY },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                        ) {
                            Text(stringResource(R.string.scope_name_only), maxLines = 1, softWrap = false)
                        }
                        SegmentedButton(
                            selected = targetScope == RenameTargetScope.EXTENSION_ONLY,
                            onClick = { targetScope = RenameTargetScope.EXTENSION_ONLY },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                        ) {
                            Text(stringResource(R.string.scope_extension_only), maxLines = 1, softWrap = false)
                        }
                        SegmentedButton(
                            selected = targetScope == RenameTargetScope.BOTH,
                            onClick = { targetScope = RenameTargetScope.BOTH },
                            shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                        ) {
                            Text(stringResource(R.string.scope_both), maxLines = 1, softWrap = false)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Case Transform Segmented Buttons
                Text(
                    text = "Case Transform",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = caseTransform == CaseTransform.NONE,
                            onClick = { caseTransform = CaseTransform.NONE },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 4)
                        ) {
                            Text(stringResource(R.string.case_none), maxLines = 1, softWrap = false)
                        }
                        SegmentedButton(
                            selected = caseTransform == CaseTransform.UPPERCASE,
                            onClick = { caseTransform = CaseTransform.UPPERCASE },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 4)
                        ) {
                            Text(stringResource(R.string.case_uppercase), maxLines = 1, softWrap = false)
                        }
                        SegmentedButton(
                            selected = caseTransform == CaseTransform.LOWERCASE,
                            onClick = { caseTransform = CaseTransform.LOWERCASE },
                            shape = SegmentedButtonDefaults.itemShape(index = 2, count = 4)
                        ) {
                            Text(stringResource(R.string.case_lowercase), maxLines = 1, softWrap = false)
                        }
                        SegmentedButton(
                            selected = caseTransform == CaseTransform.TITLE_CASE,
                            onClick = { caseTransform = CaseTransform.TITLE_CASE },
                            shape = SegmentedButtonDefaults.itemShape(index = 3, count = 4)
                        ) {
                            Text(stringResource(R.string.case_title_case), maxLines = 1, softWrap = false)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Live Preview Header
                Text(
                    text = stringResource(R.string.header_live_preview),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Live Preview Items List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = ExpressiveShapes.medium
                        )
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(previewItems) { item ->
                        BatchRenameItemRow(item)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        shape = ExpressiveShapes.medium,
                        modifier = Modifier.bounceClickable(onClick = onDismiss)
                    ) {
                        Text(stringResource(R.string.cancel))
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    FilledTonalButton(
                        onClick = {
                            if (findQuery.isNotBlank()) {
                                onSaveFindQuery(findQuery)
                            }
                            val changedPairs = previewItems
                                .filter { it.isChanged && it.isValid }
                                .map { it.file to it.proposedName }
                            onConfirm(changedPairs)
                        },
                        enabled = canApply,
                        shape = ExpressiveShapes.medium
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.action_apply_rename))
                    }
                }
            }
        }
    }
}

@Composable
private fun BatchRenameItemRow(item: BatchRenameItem) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = item.file.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            if (item.isChanged) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(horizontal = 6.dp)
                        .size(14.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = item.proposedName,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = if (item.isValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        val error = item.error
        if (error != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = getErrorMessage(error),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun getErrorMessage(error: BatchRenameError): String {
    return when (error) {
        BatchRenameError.EMPTY_NAME -> stringResource(R.string.error_empty_name)
        BatchRenameError.INVALID_CHARACTERS -> stringResource(R.string.error_invalid_chars)
        BatchRenameError.DUPLICATE_IN_BATCH -> stringResource(R.string.error_duplicate_in_batch)
        BatchRenameError.EXISTS_ON_DISK -> stringResource(R.string.error_exists_on_disk)
    }
}
