package dev.qtremors.arcile.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.theme.ExpressiveShapes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SortDateRangeSection(
    minDateMillis: Long?,
    maxDateMillis: Long?,
    onDateRangeChange: (Long?, Long?) -> Unit
) {
    var minDateState by remember(minDateMillis) { mutableStateOf(minDateMillis) }
    var maxDateState by remember(maxDateMillis) { mutableStateOf(maxDateMillis) }
    var showPicker by remember { mutableStateOf(false) }
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
                onClick = { showPicker = true },
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
                        val start = minDateState?.let { dateFormatter.format(java.util.Date(it)) }
                            ?: stringResource(R.string.all)
                        val end = maxDateState?.let { dateFormatter.format(java.util.Date(it)) }
                            ?: stringResource(R.string.all)
                        "$start - $end"
                    } else {
                        stringResource(R.string.select_range)
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
                        contentDescription = stringResource(R.string.action_clear),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }

    if (showPicker) {
        val pickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = minDateState,
            initialSelectedEndDateMillis = maxDateState
        )
        Dialog(
            onDismissRequest = { showPicker = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
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
                            state = pickerState,
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
                            onClick = { showPicker = false },
                            shape = ExpressiveShapes.medium
                        ) {
                            Text(stringResource(android.R.string.cancel))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = {
                                minDateState = pickerState.selectedStartDateMillis
                                maxDateState = pickerState.selectedEndDateMillis
                                onDateRangeChange(
                                    pickerState.selectedStartDateMillis,
                                    pickerState.selectedEndDateMillis
                                )
                                showPicker = false
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
