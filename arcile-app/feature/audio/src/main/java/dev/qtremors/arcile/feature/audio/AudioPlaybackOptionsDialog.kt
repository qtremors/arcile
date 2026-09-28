package dev.qtremors.arcile.feature.audio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
internal fun AudioPlaybackSettingsDialog(
    speed: Float,
    pitch: Float,
    onSave: (Float, Float) -> Unit,
    onDismiss: () -> Unit
) {
    var draftSpeed by remember(speed) { mutableFloatStateOf(speed) }
    var draftPitch by remember(pitch) { mutableFloatStateOf(pitch) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.audio_playback_settings)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.audio_playback_speed_value,
                    String.format(Locale.ROOT, "%.2f", draftSpeed)))
                Slider(
                    value = draftSpeed,
                    onValueChange = { draftSpeed = it },
                    valueRange = 0.5f..2f,
                    steps = 5
                )
                Text(stringResource(R.string.audio_playback_pitch_value,
                    String.format(Locale.ROOT, "%.2f", draftPitch)))
                Slider(
                    value = draftPitch,
                    onValueChange = { draftPitch = it },
                    valueRange = 0.5f..2f,
                    steps = 5
                )
                TextButton(onClick = {
                    draftSpeed = 1f
                    draftPitch = 1f
                }) {
                    Text(stringResource(R.string.audio_reset_playback))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(draftSpeed, draftPitch); onDismiss() }) {
                Text(stringResource(R.string.audio_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(dev.qtremors.arcile.core.ui.R.string.cancel))
            }
        }
    )
}

@Composable
internal fun AudioSleepTimerDialog(
    active: Boolean,
    onSet: (Int?) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedMinutes by remember { mutableIntStateOf(30) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.audio_sleep_timer)) },
        text = {
            Column {
                listOf(5, 15, 30, 45, 60).forEach { minutes ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable { selectedMinutes = minutes }
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedMinutes == minutes,
                            onClick = { selectedMinutes = minutes }
                        )
                        Text(stringResource(R.string.audio_sleep_minutes, minutes))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSet(selectedMinutes); onDismiss() }) {
                Text(stringResource(R.string.audio_set_timer))
            }
        },
        dismissButton = {
            TextButton(onClick = { if (active) onSet(null); onDismiss() }) {
                Text(stringResource(if (active) R.string.audio_cancel_timer
                    else dev.qtremors.arcile.core.ui.R.string.cancel))
            }
        }
    )
}
