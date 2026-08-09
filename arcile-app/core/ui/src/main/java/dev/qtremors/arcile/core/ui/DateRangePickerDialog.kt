package dev.qtremors.arcile.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import dev.qtremors.arcile.core.ui.dialogs.Dialog
import dev.qtremors.arcile.core.ui.theme.ExpressiveShapes

internal object DateRangeDialogTestTags {
    const val Content = "date_range_dialog_content"
    const val Actions = "date_range_dialog_actions"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateRangePickerDialog(
    initialStartMillis: Long?,
    initialEndMillis: Long?,
    onDismiss: () -> Unit,
    onConfirm: (Long?, Long?) -> Unit,
    contentInsets: WindowInsets = WindowInsets.safeDrawing.union(WindowInsets.ime)
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(contentInsets),
            contentAlignment = Alignment.Center
        ) {
            val compactLayout = maxHeight < 480.dp || LocalDensity.current.fontScale >= 2f
            val pickerState = rememberDateRangePickerState(
                initialSelectedStartDateMillis = initialStartMillis,
                initialSelectedEndDateMillis = initialEndMillis,
                initialDisplayMode = if (compactLayout) {
                    DisplayMode.Input
                } else {
                    DisplayMode.Picker
                }
            )
            LaunchedEffect(compactLayout) {
                if (compactLayout) pickerState.displayMode = DisplayMode.Input
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .heightIn(max = minOf(maxHeight, 580.dp)),
                shape = ExpressiveShapes.large,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .testTag(DateRangeDialogTestTags.Content)
                    ) {
                        DateRangePicker(
                            state = pickerState,
                            modifier = Modifier.fillMaxWidth(),
                            showModeToggle = true
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(DateRangeDialogTestTags.Actions),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            shape = ExpressiveShapes.medium
                        ) {
                            Text(stringResource(android.R.string.cancel))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = {
                                onConfirm(
                                    pickerState.selectedStartDateMillis,
                                    pickerState.selectedEndDateMillis
                                )
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
