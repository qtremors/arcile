package dev.qtremors.arcile.feature.audio

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.storage.domain.AudioTrack

internal data class TimedLyricLine(val timeMs: Long, val text: String)

internal fun parseLrc(text: String): List<TimedLyricLine> {
    val time = Regex("\\[(\\d{1,2}):(\\d{2})(?:\\.(\\d{1,3}))?]")
    return text.lineSequence().flatMap { line ->
        val matches = time.findAll(line).toList()
        val content = time.replace(line, "").trim()
        matches.asSequence().mapNotNull { match ->
            val minutes = match.groupValues[1].toLongOrNull() ?: return@mapNotNull null
            val seconds = match.groupValues[2].toLongOrNull() ?: return@mapNotNull null
            if (seconds >= 60L) return@mapNotNull null
            val fraction = match.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0L
            TimedLyricLine(minutes * 60_000L + seconds * 1000L + fraction, content)
        }
    }.sortedBy(TimedLyricLine::timeMs).toList()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AudioLyricsSheet(
    track: AudioTrack,
    positionMs: Long,
    editor: AudioTagEditor,
    onDismiss: () -> Unit
) {
    val trackKey = track.file.absolutePath
    var lyrics by remember(trackKey) { mutableStateOf<AudioLyrics?>(null) }
    var error by remember(trackKey) { mutableStateOf<String?>(null) }
    LaunchedEffect(trackKey) {
        runCatching { editor.lyrics(track) }
            .onSuccess { lyrics = it }
            .onFailure { error = it.message ?: "Lyrics could not be loaded" }
    }
    val lines = remember(lyrics?.syncedLrc) {
        lyrics?.syncedLrc?.let(::parseLrc).orEmpty()
    }
    val activeIndex = remember(lines, positionMs) {
        lines.indexOfLast { it.timeMs <= positionMs }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) listState.animateScrollToItem((activeIndex - 2).coerceAtLeast(0))
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Text(track.displayTitle, style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.audio_lyrics), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            when {
                error != null -> Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                lines.isNotEmpty() -> LazyColumn(state = listState) {
                    itemsIndexed(lines) { index, line ->
                        Text(
                            line.text.ifBlank { "♪" },
                            style = if (index == activeIndex) {
                                MaterialTheme.typography.titleLarge
                            } else {
                                MaterialTheme.typography.bodyLarge
                            },
                            fontWeight = if (index == activeIndex) FontWeight.Bold else FontWeight.Normal,
                            color = if (index == activeIndex) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                        )
                    }
                }
                !lyrics?.plain.isNullOrBlank() -> Text(
                    lyrics?.plain.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = 20.dp)
                )
                else -> Text(
                    stringResource(R.string.audio_no_lyrics),
                    modifier = Modifier.padding(vertical = 20.dp)
                )
            }
        }
    }
}
