package dev.qtremors.arcile.feature.imagegallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.metadata.ImageMetadataUpdate
import dev.qtremors.arcile.core.ui.theme.ExpressiveShapes
import dev.qtremors.arcile.core.ui.theme.bounceClickable
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
internal fun MetadataEditor(
    metadata: GalleryFileMetadata?,
    isSaving: Boolean,
    onCancel: () -> Unit,
    onSave: (ImageMetadataUpdate) -> Unit,
    modifier: Modifier = Modifier
) {
    var description by remember(metadata) { mutableStateOf(metadata?.description.orEmpty()) }
    var comment by remember(metadata) { mutableStateOf(metadata?.userComment.orEmpty()) }
    var artist by remember(metadata) { mutableStateOf(metadata?.artist.orEmpty()) }
    var copyright by remember(metadata) { mutableStateOf(metadata?.copyright.orEmpty()) }
    var cameraMake by remember(metadata) { mutableStateOf(metadata?.cameraMaker.orEmpty()) }
    var cameraModel by remember(metadata) { mutableStateOf(metadata?.cameraModel.orEmpty()) }

    var datePart by remember(metadata) {
        val dt = metadata?.dateTaken.orEmpty().trim()
        val parts = dt.split(' ')
        mutableStateOf(parts.firstOrNull() ?: "")
    }
    var timePart by remember(metadata) {
        val dt = metadata?.dateTaken.orEmpty().trim()
        val parts = dt.split(' ')
        mutableStateOf(parts.getOrNull(1) ?: "")
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val dateDisplay = if (datePart.isBlank()) {
        stringResource(R.string.image_gallery_metadata_set_date_only)
    } else {
        datePart.replace(':', '-')
    }
    val timeDisplay = if (timePart.isBlank()) {
        stringResource(R.string.image_gallery_metadata_set_time)
    } else {
        timePart
    }

    var latitude by remember(metadata) { mutableStateOf(metadata?.latitude?.toString().orEmpty()) }
    var longitude by remember(metadata) { mutableStateOf(metadata?.longitude?.toString().orEmpty()) }

    val parsedLatitude = latitude.trim().takeIf(String::isNotEmpty)?.toDoubleOrNull()
    val parsedLongitude = longitude.trim().takeIf(String::isNotEmpty)?.toDoubleOrNull()
    val coordinatesBlank = latitude.isBlank() && longitude.isBlank()
    val coordinatesValid = coordinatesBlank ||
        (parsedLatitude != null && parsedLatitude in -90.0..90.0 &&
            parsedLongitude != null && parsedLongitude in -180.0..180.0)

    LazyColumn(
        modifier = modifier.padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.image_gallery_metadata_description)) },
                shape = ExpressiveShapes.medium,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving
            )
        }
        item {
            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it },
                label = { Text(stringResource(R.string.image_gallery_metadata_comment)) },
                shape = ExpressiveShapes.medium,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving
            )
        }
        item {
            OutlinedTextField(
                value = artist,
                onValueChange = { artist = it },
                label = { Text(stringResource(R.string.image_gallery_metadata_artist)) },
                shape = ExpressiveShapes.medium,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving
            )
        }
        item {
            OutlinedTextField(
                value = copyright,
                onValueChange = { copyright = it },
                label = { Text(stringResource(R.string.image_gallery_metadata_copyright)) },
                shape = ExpressiveShapes.medium,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = cameraMake,
                    onValueChange = { cameraMake = it },
                    label = { Text(stringResource(R.string.image_gallery_metadata_camera_make)) },
                    shape = ExpressiveShapes.medium,
                    modifier = Modifier.weight(1f),
                    enabled = !isSaving
                )
                OutlinedTextField(
                    value = cameraModel,
                    onValueChange = { cameraModel = it },
                    label = { Text(stringResource(R.string.image_gallery_metadata_camera_model)) },
                    shape = ExpressiveShapes.medium,
                    modifier = Modifier.weight(1f),
                    enabled = !isSaving
                )
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.image_gallery_metadata_date_taken),
                    style = MaterialTheme.typography.labelLarge
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        enabled = !isSaving,
                        shape = ExpressiveShapes.medium,
                        modifier = Modifier
                            .weight(1f)
                            .bounceClickable(enabled = !isSaving, onClick = { showDatePicker = true })
                    ) {
                        Text(
                            text = dateDisplay,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    OutlinedButton(
                        onClick = { showTimePicker = true },
                        enabled = !isSaving,
                        shape = ExpressiveShapes.medium,
                        modifier = Modifier
                            .weight(1f)
                            .bounceClickable(enabled = !isSaving, onClick = { showTimePicker = true })
                    ) {
                        Text(
                            text = timeDisplay,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (datePart.isNotBlank() || timePart.isNotBlank()) {
                        val onClearDateClick = {
                            datePart = ""
                            timePart = ""
                        }
                        IconButton(
                            onClick = onClearDateClick,
                            enabled = !isSaving,
                            modifier = Modifier.bounceClickable(enabled = !isSaving, onClick = onClearDateClick)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.image_gallery_metadata_clear_date),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = latitude,
                    onValueChange = { latitude = it },
                    label = { Text(stringResource(R.string.image_gallery_metadata_latitude)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = !coordinatesValid,
                    shape = ExpressiveShapes.medium,
                    modifier = Modifier.weight(1f),
                    enabled = !isSaving
                )
                OutlinedTextField(
                    value = longitude,
                    onValueChange = { longitude = it },
                    label = { Text(stringResource(R.string.image_gallery_metadata_longitude)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = !coordinatesValid,
                    shape = ExpressiveShapes.medium,
                    modifier = Modifier.weight(1f),
                    enabled = !isSaving
                )
            }
        }
        if (!coordinatesValid) {
            item {
                Text(
                    text = stringResource(R.string.image_gallery_metadata_coordinates_error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    enabled = !isSaving,
                    shape = ExpressiveShapes.medium,
                    modifier = Modifier.bounceClickable(enabled = !isSaving, onClick = onCancel)
                ) {
                    Text(stringResource(R.string.cancel))
                }
                Spacer(modifier = Modifier.width(8.dp))
                val onSaveClick = {
                    val combinedDateTaken = if (datePart.isNotBlank()) {
                        val time = timePart.ifBlank { "00:00:00" }
                        "$datePart $time"
                    } else {
                        ""
                    }
                    onSave(
                        ImageMetadataUpdate(
                            description = description,
                            userComment = comment,
                            artist = artist,
                            copyright = copyright,
                            cameraMaker = cameraMake,
                            cameraModel = cameraModel,
                            dateTaken = combinedDateTaken,
                            latitude = parsedLatitude,
                            longitude = parsedLongitude
                        )
                    )
                }
                Button(
                    onClick = onSaveClick,
                    enabled = coordinatesValid && !isSaving,
                    shape = ExpressiveShapes.medium,
                    modifier = Modifier.bounceClickable(
                        enabled = coordinatesValid && !isSaving,
                        onClick = onSaveClick
                    )
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.image_gallery_metadata_save))
                }
            }
        }
    }

    if (showDatePicker) {
        val initialMillis = remember(datePart) {
            if (datePart.isNotBlank()) {
                try {
                    val sdf = SimpleDateFormat("yyyy:MM:dd", Locale.US)
                    sdf.parse(datePart)?.time
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }
        }
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialMillis
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val cal = Calendar.getInstance().apply { timeInMillis = millis }
                            val sdf = SimpleDateFormat("yyyy:MM:dd", Locale.US)
                            datePart = sdf.format(cal.time)
                        }
                        showDatePicker = false
                    },
                    shape = ExpressiveShapes.medium
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDatePicker = false },
                    shape = ExpressiveShapes.medium
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val calendar = Calendar.getInstance()
        if (timePart.isNotBlank()) {
            try {
                val sdf = SimpleDateFormat("HH:mm:ss", Locale.US)
                sdf.parse(timePart)?.let { calendar.time = it }
            } catch (e: Exception) {
                // ignore
            }
        }
        val timePickerState = rememberTimePickerState(
            initialHour = calendar.get(Calendar.HOUR_OF_DAY),
            initialMinute = calendar.get(Calendar.MINUTE),
            is24Hour = true
        )
        TimePickerDialog(
            title = stringResource(R.string.image_gallery_metadata_set_time),
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val formattedTime = String.format(
                            Locale.US,
                            "%02d:%02d:00",
                            timePickerState.hour,
                            timePickerState.minute
                        )
                        timePart = formattedTime
                        showTimePicker = false
                    },
                    shape = ExpressiveShapes.medium
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showTimePicker = false },
                    shape = ExpressiveShapes.medium
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        ) {
            TimePicker(state = timePickerState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    title: String,
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismissRequest,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            modifier = Modifier
                .width(androidx.compose.foundation.layout.IntrinsicSize.Min)
                .height(androidx.compose.foundation.layout.IntrinsicSize.Min)
                .background(
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.extraLarge
                ),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    text = title,
                    style = MaterialTheme.typography.labelMedium
                )
                content()
                Row(
                    modifier = Modifier
                        .height(40.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    dismissButton()
                    Spacer(modifier = Modifier.width(8.dp))
                    confirmButton()
                }
            }
        }
    }
}
