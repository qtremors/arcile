package dev.qtremors.arcile.shared.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import dev.qtremors.arcile.shared.ui.ExpressiveFilterChip
import dev.qtremors.arcile.shared.ui.ExpressiveSegmentedRow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.storage.domain.FileListingPreferences
import dev.qtremors.arcile.core.storage.domain.FileViewMode
import dev.qtremors.arcile.core.storage.domain.FileSortOption
import dev.qtremors.arcile.ui.theme.ExpressiveShapes
import dev.qtremors.arcile.ui.theme.bounceClickable
import dev.qtremors.arcile.ui.theme.sheet
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.ui.text.style.TextOverflow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortOptionDialog(
    title: String,
    selectedPreferences: FileListingPreferences,
    showApplyToSubfolders: Boolean,
    onDismiss: () -> Unit,
    onApply: (FileListingPreferences, Boolean) -> Unit,
    minDateMillis: Long? = null,
    maxDateMillis: Long? = null,
    onDateRangeChange: ((Long?, Long?) -> Unit)? = null,
    minSize: Long? = null,
    maxSize: Long? = null,
    onSizeRangeChange: ((Long?, Long?) -> Unit)? = null
) {
    var applyToSubfolders by remember { mutableStateOf(false) }
    var draftPreferences by remember(selectedPreferences) {
        mutableStateOf(selectedPreferences.normalized())
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = ExpressiveShapes.sheet
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            val liveColumnCount = max(
                1,
                floor(((maxWidth.value - 32f) / draftPreferences.gridMinCellSize).toDouble()).toInt()
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Header (Simplified)
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge
                )

                // Layout & Density Section
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = stringResource(R.string.browser_layout_view_mode),
                        style = MaterialTheme.typography.titleMedium
                    )

                    ExpressiveSegmentedRow(
                        options = FileViewMode.entries,
                        selectedOption = draftPreferences.viewMode,
                        onOptionSelected = { mode -> draftPreferences = draftPreferences.copy(viewMode = mode) },
                        modifier = Modifier.fillMaxWidth()
                    ) { mode ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (mode == FileViewMode.LIST) {
                                    Icons.AutoMirrored.Filled.ViewList
                                } else {
                                    Icons.Default.GridView
                                },
                                contentDescription = null
                            )
                            Text(viewModeLabel(mode))
                        }
                    }

                    AnimatedContent(
                        targetState = draftPreferences.viewMode,
                        label = "browser_layout_controls"
                    ) { mode ->
                        if (mode == FileViewMode.LIST) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(R.string.browser_layout_list_zoom),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = stringResource(
                                            R.string.browser_layout_list_zoom_value,
                                            (draftPreferences.listZoom * 100).roundToInt()
                                        ),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Slider(
                                    value = draftPreferences.listZoom,
                                    onValueChange = {
                                        draftPreferences = draftPreferences.copy(listZoom = it)
                                    },
                                    valueRange = FileListingPreferences.MIN_LIST_ZOOM..FileListingPreferences.MAX_LIST_ZOOM,
                                    steps = 7
                                )
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(R.string.browser_layout_grid_size),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = stringResource(
                                            R.string.browser_layout_grid_columns_value,
                                            liveColumnCount
                                        ),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Slider(
                                    value = draftPreferences.gridMinCellSize,
                                    onValueChange = {
                                        draftPreferences = draftPreferences.copy(gridMinCellSize = it)
                                    },
                                    valueRange = FileListingPreferences.MIN_GRID_MIN_CELL_SIZE..FileListingPreferences.MAX_GRID_MIN_CELL_SIZE,
                                    steps = 1
                                )
                            }
                        }
                    }
                }

                // Sort Section (Grid Layout to prevent horizontal scroll)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.action_sort),
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SortOptionChip(FileSortOption.NAME_ASC, draftPreferences, Modifier.weight(1f)) {
                                draftPreferences = draftPreferences.copy(sortOption = it)
                            }
                            SortOptionChip(FileSortOption.NAME_DESC, draftPreferences, Modifier.weight(1f)) {
                                draftPreferences = draftPreferences.copy(sortOption = it)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SortOptionChip(FileSortOption.DATE_NEWEST, draftPreferences, Modifier.weight(1f)) {
                                draftPreferences = draftPreferences.copy(sortOption = it)
                            }
                            SortOptionChip(FileSortOption.DATE_OLDEST, draftPreferences, Modifier.weight(1f)) {
                                draftPreferences = draftPreferences.copy(sortOption = it)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SortOptionChip(FileSortOption.SIZE_LARGEST, draftPreferences, Modifier.weight(1f)) {
                                draftPreferences = draftPreferences.copy(sortOption = it)
                            }
                            SortOptionChip(FileSortOption.SIZE_SMALLEST, draftPreferences, Modifier.weight(1f)) {
                                draftPreferences = draftPreferences.copy(sortOption = it)
                            }
                        }
                    }
                }

                if (onDateRangeChange != null) {
                    var minDateState by remember(minDateMillis) { mutableStateOf(minDateMillis) }
                    var maxDateState by remember(maxDateMillis) { mutableStateOf(maxDateMillis) }
                    var showDateRangePickerInSort by remember { mutableStateOf(false) }

                    val anyLabel = stringResource(R.string.all)
                    val presets = remember(anyLabel) { getPresetRanges(anyLabel) }
                    val selectedPreset = presets.find { (_, dateRange) ->
                        minDateState == dateRange.first && maxDateState == dateRange.second
                    } ?: presets.first()

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = stringResource(R.string.date_modified),
                            style = MaterialTheme.typography.titleMedium
                        )

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(presets) { preset ->
                                ExpressiveFilterChip(
                                    selected = selectedPreset.first == preset.first,
                                    onClick = {
                                        minDateState = preset.second.first
                                        maxDateState = preset.second.second
                                        onDateRangeChange(preset.second.first, preset.second.second)
                                    },
                                    label = { Text(preset.first) }
                                )
                            }
                        }

                        val dateFormatter = rememberDateOnlyFormatter()
                        val customRangeActive = minDateState != null || maxDateState != null

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { showDateRangePickerInSort = true },
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
                                        val startStr = minDateState?.let { dateFormatter.format(java.util.Date(it)) } ?: "Any"
                                        val endStr = maxDateState?.let { dateFormatter.format(java.util.Date(it)) } ?: "Any"
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
                                        minDateState = null
                                        maxDateState = null
                                        onDateRangeChange(null, null)
                                    },
                                    modifier = Modifier.clip(CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear date filter",
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }

                    if (showDateRangePickerInSort) {
                        val dateRangePickerState = rememberDateRangePickerState(
                            initialSelectedStartDateMillis = minDateState,
                            initialSelectedEndDateMillis = maxDateState
                        )
                        Dialog(
                            onDismissRequest = { showDateRangePickerInSort = false },
                            properties = DialogProperties(usePlatformDefaultWidth = false)
                        ) {
                            androidx.compose.material3.Surface(
                                modifier = Modifier
                                    .fillMaxWidth(0.9f)
                                    .height(580.dp),
                                shape = ExpressiveShapes.large,
                                color = MaterialTheme.colorScheme.surface,
                                tonalElevation = 6.dp
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        DateRangePicker(
                                            state = dateRangePickerState,
                                            modifier = Modifier.fillMaxSize(),
                                            showModeToggle = false
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(
                                            onClick = { showDateRangePickerInSort = false },
                                            shape = ExpressiveShapes.medium
                                        ) {
                                            Text(stringResource(android.R.string.cancel))
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        TextButton(
                                            onClick = {
                                                minDateState = dateRangePickerState.selectedStartDateMillis
                                                maxDateState = dateRangePickerState.selectedEndDateMillis
                                                onDateRangeChange(
                                                    dateRangePickerState.selectedStartDateMillis,
                                                    dateRangePickerState.selectedEndDateMillis
                                                )
                                                showDateRangePickerInSort = false
                                            },
                                            shape = ExpressiveShapes.medium
                                        ) {
                                            Text(stringResource(android.R.string.ok))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (onSizeRangeChange != null) {
                    var minSizeTextState by remember(minSize) {
                        mutableStateOf(minSize?.let { (it / (1024f * 1024f)).toString() }.orEmpty())
                    }
                    var maxSizeTextState by remember(maxSize) {
                        mutableStateOf(maxSize?.let { (it / (1024f * 1024f)).toString() }.orEmpty())
                    }

                    val anyLabel = stringResource(R.string.all)
                    val sizePresets = remember(anyLabel) { getPresetSizes(anyLabel) }

                    val parsedMinSize = minSizeTextState.trim().toFloatOrNull()?.let { (it * 1024 * 1024).toLong() }
                    val parsedMaxSize = maxSizeTextState.trim().toFloatOrNull()?.let { (it * 1024 * 1024).toLong() }

                    val selectedSizePreset = sizePresets.find { (_, sizeRange) ->
                        parsedMinSize == sizeRange.first && parsedMaxSize == sizeRange.second
                    } ?: sizePresets.first()

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = stringResource(R.string.file_size),
                            style = MaterialTheme.typography.titleMedium
                        )

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(sizePresets) { preset ->
                                ExpressiveFilterChip(
                                    selected = selectedSizePreset.first == preset.first,
                                    onClick = {
                                        minSizeTextState = preset.second.first?.let { (it / (1024f * 1024f)).toString() }.orEmpty()
                                        maxSizeTextState = preset.second.second?.let { (it / (1024f * 1024f)).toString() }.orEmpty()
                                        onSizeRangeChange(preset.second.first, preset.second.second)
                                    },
                                    label = { Text(preset.first) }
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = minSizeTextState,
                                onValueChange = {
                                    minSizeTextState = it
                                    val parsedMin = it.trim().toFloatOrNull()?.let { (it * 1024 * 1024).toLong() }
                                    onSizeRangeChange(parsedMin, maxSizeTextState.trim().toFloatOrNull()?.let { (it * 1024 * 1024).toLong() })
                                },
                                label = { Text(stringResource(R.string.filter_min_size)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                shape = ExpressiveShapes.medium,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = maxSizeTextState,
                                onValueChange = {
                                    maxSizeTextState = it
                                    val parsedMax = it.trim().toFloatOrNull()?.let { (it * 1024 * 1024).toLong() }
                                    onSizeRangeChange(minSizeTextState.trim().toFloatOrNull()?.let { (it * 1024 * 1024).toLong() }, parsedMax)
                                },
                                label = { Text(stringResource(R.string.filter_max_size)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                shape = ExpressiveShapes.medium,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Scope Section
                if (showApplyToSubfolders) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.large)
                            .bounceClickable { applyToSubfolders = !applyToSubfolders }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.apply_to_folder_and_subfolders),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = stringResource(R.string.browser_layout_persistence_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Switch(
                            checked = applyToSubfolders,
                            onCheckedChange = null // Handled by Row click
                        )
                    }
                }

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        shape = ExpressiveShapes.medium
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = {
                            onApply(draftPreferences.normalized(), applyToSubfolders)
                            onDismiss()
                        },
                        shape = ExpressiveShapes.medium
                    ) {
                        Text(stringResource(R.string.apply))
                    }
                }
            }
        }
    }
}

@Composable
private fun SortOptionChip(
    option: FileSortOption,
    preferences: FileListingPreferences,
    modifier: Modifier = Modifier,
    onSelect: (FileSortOption) -> Unit
) {
    ExpressiveFilterChip(
        selected = preferences.sortOption == option,
        onClick = { onSelect(option) },
        label = { 
            Text(
                text = sortLabel(option),
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            ) 
        },
        modifier = modifier
    )
}

@Composable
private fun sortLabel(option: FileSortOption): String {
    val labelRes = when (option) {
        FileSortOption.NAME_ASC -> R.string.sort_name_asc
        FileSortOption.NAME_DESC -> R.string.sort_name_desc
        FileSortOption.DATE_NEWEST -> R.string.sort_date_newest
        FileSortOption.DATE_OLDEST -> R.string.sort_date_oldest
        FileSortOption.SIZE_LARGEST -> R.string.sort_size_largest
        FileSortOption.SIZE_SMALLEST -> R.string.sort_size_smallest
    }
    return stringResource(labelRes)
}

@Composable
private fun viewModeLabel(mode: FileViewMode): String {
    return stringResource(
        if (mode == FileViewMode.LIST) R.string.list_view else R.string.grid_view
    )
}
