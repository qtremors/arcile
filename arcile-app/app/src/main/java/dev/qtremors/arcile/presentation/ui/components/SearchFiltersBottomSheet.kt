package dev.qtremors.arcile.presentation.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.R
import dev.qtremors.arcile.domain.FileCategories
import dev.qtremors.arcile.domain.SearchFilters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchFiltersBottomSheet(
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
    var minSizeText by rememberSaveable(currentFilters.minSize) { mutableStateOf(currentFilters.minSize?.toString().orEmpty()) }
    var maxSizeText by rememberSaveable(currentFilters.maxSize) { mutableStateOf(currentFilters.maxSize?.toString().orEmpty()) }
    var minDateText by rememberSaveable(currentFilters.minDateMillis) { mutableStateOf(currentFilters.minDateMillis?.toString().orEmpty()) }
    var maxDateText by rememberSaveable(currentFilters.maxDateMillis) { mutableStateOf(currentFilters.maxDateMillis?.toString().orEmpty()) }
    var presetName by rememberSaveable(currentFilters.savedPresetName) { mutableStateOf(currentFilters.savedPresetName.orEmpty()) }

    fun advancedFilters(): SearchFilters = currentFilters.copy(
        extensions = extensionText.split(',', ' ')
            .map { it.trim().trimStart('.').lowercase() }
            .filter { it.isNotBlank() }
            .toSet(),
        mimeType = mimeText.trim().ifBlank { null },
        folderScopePath = folderScopeText.trim().ifBlank { null },
        storageVolumeId = volumeText.trim().ifBlank { null },
        minSize = minSizeText.toLongOrNull() ?: currentFilters.minSize,
        maxSize = maxSizeText.toLongOrNull() ?: currentFilters.maxSize,
        minDateMillis = minDateText.toLongOrNull() ?: currentFilters.minDateMillis,
        maxDateMillis = maxDateText.toLongOrNull() ?: currentFilters.maxDateMillis,
        savedPresetName = presetName.trim().ifBlank { null }
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.search_filters_title), style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = { onApplyFilters(SearchFilters()) }) {
                    Text(stringResource(R.string.clear_all))
                }
            }

            Text(stringResource(R.string.item_type), style = MaterialTheme.typography.titleMedium)
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                SingleChoiceSegmentedButtonRow {
                    val options = listOf(anyLabel, foldersLabel, filesLabel)
                    options.forEachIndexed { index, label ->
                        val selected = when (label) {
                            anyLabel -> currentFilters.itemType == null || currentFilters.itemType == anyLabel
                            else -> currentFilters.itemType == label
                        }
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                            onClick = { onApplyFilters(currentFilters.copy(itemType = if (label == anyLabel) null else label)) },
                            selected = selected
                        ) {
                            Text(label)
                        }
                    }
                }
            }

            if (showCategoryFilter) {
                Text(stringResource(R.string.file_type), style = MaterialTheme.typography.titleMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    items(categories) { category ->
                        val isSelected = if (category == allLabel) currentFilters.fileType == null else currentFilters.fileType == category
                        FilterChip(
                            selected = isSelected,
                            onClick = { onApplyFilters(currentFilters.copy(fileType = if (category == allLabel) null else category)) },
                            label = { Text(category) }
                        )
                    }
                }
            }

            Text(stringResource(R.string.file_size), style = MaterialTheme.typography.titleMedium)
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                SingleChoiceSegmentedButtonRow {
                    val options = listOf(
                        anyLabel to (null to null),
                        stringResource(R.string.size_under_10_mb) to (null to 10L * 1024 * 1024),
                        stringResource(R.string.size_10_to_100_mb) to (10L * 1024 * 1024 to 100L * 1024 * 1024),
                        stringResource(R.string.size_over_100_mb) to (100L * 1024 * 1024 to null)
                    )
                    options.forEachIndexed { index, (label, sizeRange) ->
                        val selected = currentFilters.minSize == sizeRange.first && currentFilters.maxSize == sizeRange.second
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                            onClick = { onApplyFilters(currentFilters.copy(minSize = sizeRange.first, maxSize = sizeRange.second)) },
                            selected = selected
                        ) {
                            Text(label, maxLines = 1)
                        }
                    }
                }
            }

            Text(stringResource(R.string.date_modified), style = MaterialTheme.typography.titleMedium)
            val dayMillis = 24L * 60 * 60 * 1000
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                val now = System.currentTimeMillis()
                val cal = java.util.Calendar.getInstance()
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                cal.set(java.util.Calendar.MINUTE, 0)
                cal.set(java.util.Calendar.SECOND, 0)
                cal.set(java.util.Calendar.MILLISECOND, 0)
                val todayMidnight = cal.timeInMillis

                SingleChoiceSegmentedButtonRow {
                    val options = listOf(
                        anyLabel to (null to null),
                        stringResource(R.string.today) to (todayMidnight to null),
                        stringResource(R.string.last_7_days) to (now - 7 * dayMillis to null),
                        stringResource(R.string.last_30_days) to (now - 30 * dayMillis to null)
                    )
                    options.forEachIndexed { index, (label, dateRange) ->
                        val selected = currentFilters.minDateMillis == dateRange.first && currentFilters.maxDateMillis == dateRange.second
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                            onClick = { onApplyFilters(currentFilters.copy(minDateMillis = dateRange.first, maxDateMillis = dateRange.second)) },
                            selected = selected
                        ) {
                            Text(label, maxLines = 1)
                        }
                    }
                }
            }

            FilterChip(
                selected = showAdvanced,
                onClick = { showAdvanced = !showAdvanced },
                label = { Text(stringResource(R.string.advanced_filters)) }
            )

            if (showAdvanced) {
                OutlinedTextField(
                    value = extensionText,
                    onValueChange = { extensionText = it },
                    label = { Text(stringResource(R.string.filter_extensions)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.filter_include_hidden), modifier = Modifier.weight(1f))
                    Switch(
                        checked = currentFilters.includeHidden,
                        onCheckedChange = { onApplyFilters(currentFilters.copy(includeHidden = it)) }
                    )
                }
                OutlinedTextField(
                    value = volumeText,
                    onValueChange = { volumeText = it },
                    label = { Text(stringResource(R.string.filter_storage_volume)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = folderScopeText,
                    onValueChange = { folderScopeText = it },
                    label = { Text(stringResource(R.string.filter_folder_scope)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = mimeText,
                    onValueChange = { mimeText = it },
                    label = { Text(stringResource(R.string.filter_mime_type)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = minSizeText,
                        onValueChange = { minSizeText = it },
                        label = { Text(stringResource(R.string.filter_min_size)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = maxSizeText,
                        onValueChange = { maxSizeText = it },
                        label = { Text(stringResource(R.string.filter_max_size)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = minDateText,
                        onValueChange = { minDateText = it },
                        label = { Text(stringResource(R.string.filter_min_date)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = maxDateText,
                        onValueChange = { maxDateText = it },
                        label = { Text(stringResource(R.string.filter_max_date)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = presetName,
                    onValueChange = { presetName = it },
                    label = { Text(stringResource(R.string.filter_saved_preset)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(onClick = { onApplyFilters(advancedFilters()) }, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.apply))
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

private fun SearchFilters.hasActiveAdvancedFilters(): Boolean =
    extensions.isNotEmpty() ||
        includeHidden ||
        storageVolumeId != null ||
        folderScopePath != null ||
        mimeType != null ||
        savedPresetName != null
