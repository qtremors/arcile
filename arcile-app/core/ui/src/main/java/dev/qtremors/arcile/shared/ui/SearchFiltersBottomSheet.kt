package dev.qtremors.arcile.shared.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import dev.qtremors.arcile.shared.ui.ExpressiveFilterChip
import dev.qtremors.arcile.shared.ui.ExpressiveSegmentedRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.text.style.TextOverflow
import dev.qtremors.arcile.utils.formatFileSize
import dev.qtremors.arcile.ui.theme.ExpressiveShapes
import dev.qtremors.arcile.ui.theme.sheet
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
import dev.qtremors.arcile.ui.theme.bounceClickable
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.CalendarMonth
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
    var minSize by rememberSaveable(currentFilters.minSize) { mutableStateOf(currentFilters.minSize) }
    var maxSize by rememberSaveable(currentFilters.maxSize) { mutableStateOf(currentFilters.maxSize) }
    var minDateMillis by rememberSaveable(currentFilters.minDateMillis) { mutableStateOf(currentFilters.minDateMillis) }
    var maxDateMillis by rememberSaveable(currentFilters.maxDateMillis) { mutableStateOf(currentFilters.maxDateMillis) }

    var activeDatePicker by remember { mutableStateOf<DatePickerTarget?>(null) }

    val initialMinIndex = minSize?.let { size ->
        sizeSteps.indexOfFirst { it >= size }.takeIf { it >= 0 } ?: 0
    } ?: 0
    val initialMaxIndex = maxSize?.let { size ->
        sizeSteps.indexOfLast { it <= size }.takeIf { it >= 0 } ?: 10
    } ?: 10

    var sizeRange by remember(minSize, maxSize) {
        mutableStateOf(initialMinIndex.toFloat()..initialMaxIndex.toFloat())
    }
    var presetName by rememberSaveable(currentFilters.savedPresetName) { mutableStateOf(currentFilters.savedPresetName.orEmpty()) }

    fun advancedFilters(): SearchFilters = currentFilters.copy(
        extensions = extensionText.split(',', ' ')
            .map { it.trim().trimStart('.').lowercase() }
            .filter { it.isNotBlank() }
            .toSet(),
        mimeType = mimeText.trim().ifBlank { null },
        folderScopePath = folderScopeText.trim().ifBlank { null },
        storageVolumeId = volumeText.trim().ifBlank { null },
        minSize = minSize,
        maxSize = maxSize,
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
            val sizeOptions = listOf(
                anyLabel to (null to null),
                stringResource(R.string.size_under_10_mb) to (null to 10L * 1024 * 1024),
                stringResource(R.string.size_10_to_100_mb) to (10L * 1024 * 1024 to 100L * 1024 * 1024),
                stringResource(R.string.size_over_100_mb) to (100L * 1024 * 1024 to null)
            )
            val selectedSizeOption = sizeOptions.find { (_, sizeRange) ->
                currentFilters.minSize == sizeRange.first && currentFilters.maxSize == sizeRange.second
            } ?: sizeOptions.first()

            val sizeIcons = listOf(
                Icons.Default.AllInclusive,
                Icons.Default.Description,
                Icons.Default.FolderOpen,
                Icons.Default.Storage
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ExpressiveSelectorCard(
                        selected = selectedSizeOption == sizeOptions[0],
                        onClick = { onApplyFilters(currentFilters.copy(minSize = sizeOptions[0].second.first, maxSize = sizeOptions[0].second.second)) },
                        icon = sizeIcons[0],
                        title = sizeOptions[0].first,
                        modifier = Modifier.weight(1f)
                    )
                    ExpressiveSelectorCard(
                        selected = selectedSizeOption == sizeOptions[1],
                        onClick = { onApplyFilters(currentFilters.copy(minSize = sizeOptions[1].second.first, maxSize = sizeOptions[1].second.second)) },
                        icon = sizeIcons[1],
                        title = sizeOptions[1].first,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ExpressiveSelectorCard(
                        selected = selectedSizeOption == sizeOptions[2],
                        onClick = { onApplyFilters(currentFilters.copy(minSize = sizeOptions[2].second.first, maxSize = sizeOptions[2].second.second)) },
                        icon = sizeIcons[2],
                        title = sizeOptions[2].first,
                        modifier = Modifier.weight(1f)
                    )
                    ExpressiveSelectorCard(
                        selected = selectedSizeOption == sizeOptions[3],
                        onClick = { onApplyFilters(currentFilters.copy(minSize = sizeOptions[3].second.first, maxSize = sizeOptions[3].second.second)) },
                        icon = sizeIcons[3],
                        title = sizeOptions[3].first,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Text(stringResource(R.string.date_modified), style = MaterialTheme.typography.titleMedium)
            val dayMillis = 24L * 60 * 60 * 1000
            val now = System.currentTimeMillis()
            val cal = java.util.Calendar.getInstance()
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            val todayMidnight = cal.timeInMillis

            val dateOptions = listOf(
                anyLabel to (null to null),
                stringResource(R.string.today) to (todayMidnight to null),
                stringResource(R.string.last_7_days) to (now - 7 * dayMillis to null),
                stringResource(R.string.last_30_days) to (now - 30 * dayMillis to null)
            )
            val selectedDateOption = dateOptions.find { (_, dateRange) ->
                currentFilters.minDateMillis == dateRange.first && currentFilters.maxDateMillis == dateRange.second
            } ?: dateOptions.first()

            val dateIcons = listOf(
                Icons.Default.CalendarMonth,
                Icons.Default.Today,
                Icons.Default.CalendarViewWeek,
                Icons.Default.History
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ExpressiveSelectorCard(
                        selected = selectedDateOption == dateOptions[0],
                        onClick = { onApplyFilters(currentFilters.copy(minDateMillis = dateOptions[0].second.first, maxDateMillis = dateOptions[0].second.second)) },
                        icon = dateIcons[0],
                        title = dateOptions[0].first,
                        modifier = Modifier.weight(1f)
                    )
                    ExpressiveSelectorCard(
                        selected = selectedDateOption == dateOptions[1],
                        onClick = { onApplyFilters(currentFilters.copy(minDateMillis = dateOptions[1].second.first, maxDateMillis = dateOptions[1].second.second)) },
                        icon = dateIcons[1],
                        title = dateOptions[1].first,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ExpressiveSelectorCard(
                        selected = selectedDateOption == dateOptions[2],
                        onClick = { onApplyFilters(currentFilters.copy(minDateMillis = dateOptions[2].second.first, maxDateMillis = dateOptions[2].second.second)) },
                        icon = dateIcons[2],
                        title = dateOptions[2].first,
                        modifier = Modifier.weight(1f)
                    )
                    ExpressiveSelectorCard(
                        selected = selectedDateOption == dateOptions[3],
                        onClick = { onApplyFilters(currentFilters.copy(minDateMillis = dateOptions[3].second.first, maxDateMillis = dateOptions[3].second.second)) },
                        icon = dateIcons[3],
                        title = dateOptions[3].first,
                        modifier = Modifier.weight(1f)
                    )
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

                Text(stringResource(R.string.file_size), style = MaterialTheme.typography.titleMedium)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val minLabel = minSize?.let { formatFileSize(it) } ?: stringResource(R.string.item_type_any)
                    val maxLabel = maxSize?.let { formatFileSize(it) } ?: stringResource(R.string.item_type_any)
                    Text(
                        text = "$minLabel - $maxLabel",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    RangeSlider(
                        value = sizeRange,
                        onValueChange = { range ->
                            sizeRange = range
                            minSize = if (range.start == 0f) null else sizeSteps[range.start.roundToInt()]
                            maxSize = if (range.endInclusive == 10f) null else sizeSteps[range.endInclusive.roundToInt()]
                        },
                        valueRange = 0f..10f,
                        steps = 9,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Text(stringResource(R.string.date_modified), style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val dateFormatter = rememberDateOnlyFormatter()

                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { activeDatePicker = DatePickerTarget.MIN_DATE },
                            modifier = Modifier.fillMaxWidth(),
                            shape = ExpressiveShapes.medium
                        ) {
                            Text(
                                text = minDateMillis?.let { dateFormatter.format(java.util.Date(it)) }
                                    ?: stringResource(R.string.filter_start_date),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (minDateMillis != null) {
                            IconButton(
                                onClick = { minDateMillis = null },
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 4.dp)
                                    .clip(CircleShape)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.filter_clear_start_date),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { activeDatePicker = DatePickerTarget.MAX_DATE },
                            modifier = Modifier.fillMaxWidth(),
                            shape = ExpressiveShapes.medium
                        ) {
                            Text(
                                text = maxDateMillis?.let { dateFormatter.format(java.util.Date(it)) }
                                    ?: stringResource(R.string.filter_end_date),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (maxDateMillis != null) {
                            IconButton(
                                onClick = { maxDateMillis = null },
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 4.dp)
                                    .clip(CircleShape)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.filter_clear_end_date),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
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

            }

            if (activeDatePicker != null) {
                val initialDate = when (activeDatePicker) {
                    DatePickerTarget.MIN_DATE -> minDateMillis
                    DatePickerTarget.MAX_DATE -> maxDateMillis
                    else -> null
                }
                val datePickerState = rememberDatePickerState(
                    initialSelectedDateMillis = initialDate
                )
                DatePickerDialog(
                    onDismissRequest = { activeDatePicker = null },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                if (activeDatePicker == DatePickerTarget.MIN_DATE) {
                                    minDateMillis = datePickerState.selectedDateMillis
                                } else {
                                    maxDateMillis = datePickerState.selectedDateMillis
                                }
                                activeDatePicker = null
                            },
                            shape = ExpressiveShapes.medium
                        ) {
                            Text(stringResource(android.R.string.ok))
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { activeDatePicker = null },
                            shape = ExpressiveShapes.medium
                        ) {
                            Text(stringResource(android.R.string.cancel))
                        }
                    }
                ) {
                    DatePicker(state = datePickerState)
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
private enum class DatePickerTarget {
    MIN_DATE,
    MAX_DATE
}
private val sizeSteps = listOf(
    0L,                      // Any / 0
    100L * 1024,             // 100 KB
    500L * 1024,             // 500 KB
    1L * 1024 * 1024,        // 1 MB
    5L * 1024 * 1024,        // 5 MB
    10L * 1024 * 1024,       // 10 MB
    50L * 1024 * 1024,       // 50 MB
    100L * 1024 * 1024,      // 100 MB
    500L * 1024 * 1024,      // 500 MB
    1024L * 1024 * 1024,     // 1 GB
    10L * 1024 * 1024 * 1024 // 10 GB / Any Max / 10
)

private fun SearchFilters.hasActiveAdvancedFilters(): Boolean =
    extensions.isNotEmpty() ||
        includeHidden ||
        storageVolumeId != null ||
        folderScopePath != null ||
        mimeType != null ||
        minSize != null ||
        maxSize != null ||
        minDateMillis != null ||
        maxDateMillis != null ||
        savedPresetName != null

@Composable
private fun ExpressiveSelectorCard(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
        },
        label = "cardBackground"
    )

    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "cardContent"
    )

    val borderStroke = if (selected) {
        null
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }

    Surface(
        modifier = modifier.bounceClickable(onClick = onClick),
        shape = ExpressiveShapes.medium,
        color = backgroundColor,
        contentColor = contentColor,
        border = borderStroke
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = contentColor
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
