package dev.qtremors.arcile.feature.audio

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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

@Composable
internal fun AudioLyricsSheet(
    track: AudioTrack,
    positionMs: Long,
    isPlaying: Boolean,
    editor: AudioTagEditor,
    onSeek: (Long) -> Unit,
    onTogglePlayback: () -> Unit,
    onEdit: () -> Unit,
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
    val lines = remember(lyrics?.syncedLrc) { lyrics?.syncedLrc?.let(::parseLrc).orEmpty() }
    val activeIndex = remember(lines, positionMs) { lines.indexOfLast { it.timeMs <= positionMs } }
    val listState = rememberLazyListState()
    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) listState.animateScrollToItem((activeIndex - 2).coerceAtLeast(0))
    }

    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.audio_collapse_player))
                    }
                    Text(stringResource(R.string.audio_lyrics),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f).padding(start = 8.dp))
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit,
                            contentDescription = stringResource(R.string.audio_edit_music_details))
                    }
                }
                when {
                    error != null -> Box(Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center) {
                        Text(error.orEmpty(), color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(24.dp))
                    }
                    lines.isNotEmpty() -> LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 36.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        itemsIndexed(lines) { index, line ->
                            val color by animateColorAsState(
                                if (index == activeIndex) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                label = "lyric line color"
                            )
                            Text(line.text.ifBlank { "♪" },
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = if (index == activeIndex) FontWeight.Bold else FontWeight.Medium,
                                color = color,
                                modifier = Modifier.fillMaxWidth()
                                    .clickable { onSeek(line.timeMs) }.padding(vertical = 5.dp))
                        }
                    }
                    !lyrics?.plain.isNullOrBlank() -> Text(
                        lyrics?.plain.orEmpty(),
                        style = MaterialTheme.typography.titleLarge,
                        lineHeight = MaterialTheme.typography.headlineSmall.lineHeight,
                        modifier = Modifier.weight(1f).fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 28.dp, vertical = 28.dp)
                    )
                    else -> Box(Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.audio_no_lyrics),
                            modifier = Modifier.padding(28.dp))
                    }
                }
                Surface(shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        AudioArtwork(track, Modifier.size(48.dp), MaterialTheme.shapes.medium)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(track.displayTitle, style = MaterialTheme.typography.titleSmall,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(track.artist ?: stringResource(R.string.audio_unknown_artist),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        FilledIconButton(onClick = onTogglePlayback) {
                            Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = stringResource(
                                    if (isPlaying) R.string.audio_pause else R.string.audio_play))
                        }
                    }
                }
            }
        }
    }
}
