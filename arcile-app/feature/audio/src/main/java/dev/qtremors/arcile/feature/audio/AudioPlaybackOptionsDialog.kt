package dev.qtremors.arcile.feature.audio

import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AudioPlaybackSettingsDialog(
    speed: Float,
    pitch: Float,
    onSave: (Float, Float) -> Unit,
    onDismiss: () -> Unit
) {
    var draftSpeed by remember(speed) { mutableFloatStateOf(speed) }
    var draftPitch by remember(pitch) { mutableFloatStateOf(pitch) }
    val context = LocalContext.current
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val minVolume = audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC)
    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    var volume by remember { mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.audio_playback_settings), style = MaterialTheme.typography.headlineSmall)
            Surface(shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text(stringResource(R.string.audio_volume), style = MaterialTheme.typography.titleMedium)
                    Slider(value = volume,
                        onValueChange = { volume = it },
                        onValueChangeFinished = {
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume.roundToInt(), 0)
                            volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                        },
                        valueRange = minVolume.toFloat()..maxVolume.coerceAtLeast(minVolume + 1).toFloat(),
                        enabled = !audioManager.isVolumeFixed && maxVolume > minVolume)
                }
            }
            Surface(shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.audio_speed_and_pitch), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.audio_playback_speed_value,
                        String.format(Locale.ROOT, "%.2f", draftSpeed)))
                    Slider(
                        value = draftSpeed,
                        onValueChange = { draftSpeed = it },
                        valueRange = 0.5f..2f,
                        steps = 29
                    )
                    Text(stringResource(R.string.audio_playback_pitch_value,
                        String.format(Locale.ROOT, "%.2f", draftPitch)))
                    Slider(
                        value = draftPitch,
                        onValueChange = { draftPitch = it },
                        valueRange = 0.5f..2f,
                        steps = 29
                    )
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0.5f, 1f, 1.5f, 2f).forEach { preset ->
                            FilterChip(selected = draftSpeed == preset,
                                onClick = { draftSpeed = preset },
                                label = { Text(stringResource(R.string.audio_speed_preset,
                                    String.format(Locale.getDefault(), "%.1f", preset))) })
                        }
                    }
                    TextButton(onClick = {
                        draftSpeed = 1f
                        draftPitch = 1f
                    }) {
                        Text(stringResource(R.string.audio_reset_playback))
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(dev.qtremors.arcile.core.ui.R.string.cancel))
                }
                FilledTonalButton(onClick = { onSave(draftSpeed, draftPitch); onDismiss() }) {
                    Text(stringResource(R.string.audio_save))
                }
            }
        }
    }
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
