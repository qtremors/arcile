package dev.qtremors.arcile.core.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import dev.qtremors.arcile.core.ui.ExpressiveFilterChip
import dev.qtremors.arcile.core.ui.ExpressiveSegmentedRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.text.style.TextOverflow
import dev.qtremors.arcile.core.presentation.formatFileSize
import dev.qtremors.arcile.core.ui.theme.ExpressiveShapes
import dev.qtremors.arcile.core.ui.theme.sheet
import kotlin.math.roundToInt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.storage.domain.FileCategories
import dev.qtremors.arcile.core.storage.domain.SearchFilters
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.font.FontWeight
import dev.qtremors.arcile.core.ui.theme.bounceClickable
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.CalendarViewWeek
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Check



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchFiltersSheet(
    currentFilters: SearchFilters,
    onApplyFilters: (SearchFilters) -> Unit,
    onDismiss: () -> Unit,
    showCategoryFilter: Boolean = true
) {
    val allLabel = stringResource(R.string.all)
    val anyLabel = stringResource(R.string.item_type_any)
    val foldersLabel = stringResource(R.string.folders)
    val filesLabel = stringResource(R.string.item_type_files)
    val categories = listOf(allLabel) + FileCategories.all.map { it.name }
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showAdvanced by rememberSaveable { mutableStateOf(currentFilters.hasActiveAdvancedFilters()) }
    var extensionText by rememberSaveable(currentFilters.extensions) {
        mutableStateOf(currentFilters.extensions.joinToString(", "))
    }
    var mimeText by rememberSaveable(currentFilters.mimeType) { mutableStateOf(currentFilters.mimeType.orEmpty()) }
    var folderScopeText by rememberSaveable(currentFilters.folderScopePath) { mutableStateOf(currentFilters.folderScopePath.orEmpty()) }
    var volumeText by rememberSaveable(currentFilters.storageVolumeId) { mutableStateOf(currentFilters.storageVolumeId.orEmpty()) }
    var minSizeText by rememberSaveable(currentFilters.minSize) {
        mutableStateOf(currentFilters.minSize?.let { (it / (1024f * 1024f)).toString() }.orEmpty())
    }
    var maxSizeText by rememberSaveable(currentFilters.maxSize) {
        mutableStateOf(currentFilters.maxSize?.let { (it / (1024f * 1024f)).toString() }.orEmpty())
    }
    var minDateMillis by rememberSaveable(currentFilters.minDateMillis) { mutableStateOf(currentFilters.minDateMillis) }
    var maxDateMillis by rememberSaveable(currentFilters.maxDateMillis) { mutableStateOf(currentFilters.maxDateMillis) }

    var showDateRangePicker by remember { mutableStateOf(false) }
    var presetName by rememberSaveable(currentFilters.savedPresetName) { mutableStateOf(currentFilters.savedPresetName.orEmpty()) }

    fun advancedFilters(): SearchFilters = currentFilters.copy(
        extensions = extensionText.split(',', ' ')
            .map { it.trim().trimStart('.').lowercase() }
            .filter { it.isNotBlank() }
            .toSet(),
        mimeType = mimeText.trim().ifBlank { null },
        folderScopePath = folderScopeText.trim().ifBlank { null },
        storageVolumeId = volumeText.trim().ifBlank { null },
        minSize = minSizeText.trim().toFloatOrNull()?.let { (it * 1024 * 1024).toLong() },
        maxSize = maxSizeText.trim().toFloatOrNull()?.let { (it * 1024 * 1024).toLong() },
        minDateMillis = minDateMillis,
        maxDateMillis = maxDateMillis,
        savedPresetName = presetName.trim().ifBlank { null }
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = ExpressiveShapes.sheet
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.search_filters_title), style = MaterialTheme.typography.titleLarge)
                TextButton(
                    onClick = { onApplyFilters(SearchFilters()) },
                    shape = ExpressiveShapes.medium
                ) {
                    Text(stringResource(R.string.clear_all))
                }
            }

            Text(stringResource(R.string.item_type), style = MaterialTheme.typography.titleMedium)
            val itemTypeOptions = listOf(anyLabel, foldersLabel, filesLabel)
            val currentItemTypeSelected = when {
                currentFilters.itemType == foldersLabel -> foldersLabel
                currentFilters.itemType == filesLabel -> filesLabel
                else -> anyLabel
            }
            ExpressiveSegmentedRow(
                options = itemTypeOptions,
                selectedOption = currentItemTypeSelected,
                onOptionSelected = { label ->
                    onApplyFilters(currentFilters.copy(itemType = if (label == anyLabel) null else label))
                },
                modifier = Modifier.fillMaxWidth()
            ) { label ->
                Text(label)
            }

            if (showCategoryFilter) {
                Text(stringResource(R.string.file_type), style = MaterialTheme.typography.titleMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    items(categories) { category ->
                        val isSelected = if (category == allLabel) currentFilters.fileType == null else currentFilters.fileType == category
                        ExpressiveFilterChip(
                            selected = isSelected,
                            onClick = { onApplyFilters(currentFilters.copy(fileType = if (category == allLabel) null else category)) },
                            label = { Text(category) }
                        )
                    }
                }
            }

            Text(stringResource(R.string.file_size), style = MaterialTheme.typography.titleMedium)

            val sizePresets = remember(allLabel) { getPresetSizes(allLabel) }
            val parsedMinSize = minSizeText.trim().toFloatOrNull()?.let { (it * 1024 * 1024).toLong() }
            val parsedMaxSize = maxSizeText.trim().toFloatOrNull()?.let { (it * 1024 * 1024).toLong() }

            val selectedSizePreset = sizePresets.find { (_, sizeRange) ->
                parsedMinSize == sizeRange.first && parsedMaxSize == sizeRange.second
            } ?: sizePresets.first()

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(sizePresets) { preset ->
                    ExpressiveFilterChip(
                        selected = selectedSizePreset.first == preset.first,
                        onClick = {
                            minSizeText = preset.second.first?.let { (it / (1024f * 1024f)).toString() }.orEmpty()
                            maxSizeText = preset.second.second?.let { (it / (1024f * 1024f)).toString() }.orEmpty()
                            onApplyFilters(
                                currentFilters.copy(
                                    minSize = preset.second.first,
                                    maxSize = preset.second.second
                                )
                            )
                        },
                        label = { Text(preset.first) }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = minSizeText,
                    onValueChange = { minSizeText = it },
                    label = { Text(stringResource(R.string.filter_min_size)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = ExpressiveShapes.medium,
                    modifier = Modifier.weight(1f).keyboardInputField()
                )
                OutlinedTextField(
                    value = maxSizeText,
                    onValueChange = { maxSizeText = it },
                    label = { Text(stringResource(R.string.filter_max_size)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = ExpressiveShapes.medium,
                    modifier = Modifier.weight(1f).keyboardInputField()
                )
            }

            Text(stringResource(R.string.date_modified), style = MaterialTheme.typography.titleMedium)
            val presets = remember(anyLabel) { getPresetRanges(anyLabel) }
            val selectedPreset = presets.find { (_, dateRange) ->
                currentFilters.minDateMillis == dateRange.first && currentFilters.maxDateMillis == dateRange.second
            } ?: presets.first()

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(presets) { preset ->
                    ExpressiveFilterChip(
                        selected = selectedPreset.first == preset.first,
                        onClick = {
                            onApplyFilters(
                                currentFilters.copy(
                                    minDateMillis = preset.second.first,
                                    maxDateMillis = preset.second.second
                                )
                            )
                        },
                        label = { Text(preset.first) }
                    )
                }
            }

            val dateFormatter = rememberDateOnlyFormatter()
            val customRangeActive = currentFilters.minDateMillis != null || currentFilters.maxDateMillis != null

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { showDateRangePicker = true },
                    shape = ExpressiveShapes.medium,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (customRangeActive) {
                            val startStr = currentFilters.minDateMillis?.let { dateFormatter.format(java.util.Date(it)) } ?: "Any"
                            val endStr = currentFilters.maxDateMillis?.let { dateFormatter.format(java.util.Date(it)) } ?: "Any"
                            "$startStr - $endStr"
                        } else {
                            "Select custom range"
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (customRangeActive) {
                    IconButton(
                        onClick = {
                            onApplyFilters(
                                currentFilters.copy(
                                    minDateMillis = null,
                                    maxDateMillis = null
                                )
                            )
                        },
                        modifier = Modifier.clip(CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.clear_date_filter),
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }


            ExpressiveFilterChip(
                selected = showAdvanced,
                onClick = { showAdvanced = !showAdvanced },
                label = { Text(stringResource(R.string.advanced_filters)) }
            )

            if (showAdvanced) {
                OutlinedTextField(
                    value = extensionText,
                    onValueChange = { extensionText = it },
                    label = { Text(stringResource(R.string.filter_extensions)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Extension,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    singleLine = true,
                    shape = ExpressiveShapes.medium,
                    modifier = Modifier.fillMaxWidth().keyboardInputField()
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.filter_include_hidden)) },
                    leadingContent = {
                        Icon(
                            imageVector = if (currentFilters.includeHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = if (currentFilters.includeHidden) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = currentFilters.includeHidden,
                            onCheckedChange = { onApplyFilters(currentFilters.copy(includeHidden = it)) },
                            thumbContent = {
                                Icon(
                                    imageVector = if (currentFilters.includeHidden) Icons.Default.Check else Icons.Default.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ExpressiveShapes.medium)
                        .bounceClickable { onApplyFilters(currentFilters.copy(includeHidden = !currentFilters.includeHidden)) }
                )
                OutlinedTextField(
                    value = volumeText,
                    onValueChange = { volumeText = it },
                    label = { Text(stringResource(R.string.filter_storage_volume)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.SdCard,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    singleLine = true,
                    shape = ExpressiveShapes.medium,
                    modifier = Modifier.fillMaxWidth().keyboardInputField()
                )
                OutlinedTextField(
                    value = folderScopeText,
                    onValueChange = { folderScopeText = it },
                    label = { Text(stringResource(R.string.filter_folder_scope)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    singleLine = true,
                    shape = ExpressiveShapes.medium,
                    modifier = Modifier.fillMaxWidth().keyboardInputField()
                )
                OutlinedTextField(
                    value = mimeText,
                    onValueChange = { mimeText = it },
                    label = { Text(stringResource(R.string.filter_mime_type)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Label,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    singleLine = true,
                    shape = ExpressiveShapes.medium,
                    modifier = Modifier.fillMaxWidth().keyboardInputField()
                )

                OutlinedTextField(
                    value = presetName,
                    onValueChange = { presetName = it },
                    label = { Text(stringResource(R.string.filter_saved_preset)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Bookmark,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    singleLine = true,
                    shape = ExpressiveShapes.medium,
                    modifier = Modifier.fillMaxWidth().keyboardInputField()
                )
            }

            FilledTonalButton(
                onClick = { onApplyFilters(advancedFilters()) },
                modifier = Modifier.align(Alignment.End),
                shape = ExpressiveShapes.medium
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.apply))
            }


            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showDateRangePicker) {
        SearchDateRangeDialog(
            initialStartMillis = minDateMillis,
            initialEndMillis = maxDateMillis,
            onDismiss = { showDateRangePicker = false },
            onConfirm = { start, end ->
                minDateMillis = start
                maxDateMillis = end
                showDateRangePicker = false
            }
        )
    }
}
