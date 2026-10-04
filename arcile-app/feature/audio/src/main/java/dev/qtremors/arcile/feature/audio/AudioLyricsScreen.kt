@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

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
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.qtremors.arcile.core.storage.domain.AudioTrack
import dev.qtremors.arcile.core.ui.SplitButtonGroup
import dev.qtremors.arcile.core.ui.ToolbarAction
import dev.qtremors.arcile.core.ui.theme.LocalReducedMotionEnabled
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

internal data class TimedLyricLine(val timeMs: Long, val text: String)

internal fun parseLrc(text: String): List<TimedLyricLine> {
    val time = Regex("\\[(\\d{1,3}):(\\d{2})(?:\\.(\\d{1,3}))?]")
    val offset = Regex("\\[offset:([+-]?\\d+)]", RegexOption.IGNORE_CASE)
        .find(text)?.groupValues?.get(1)?.toLongOrNull()?.coerceIn(-86_400_000L, 86_400_000L) ?: 0L
    return text.lineSequence().flatMap { line ->
        val matches = time.findAll(line).toList()
        val content = time.replace(line, "").trim()
        matches.asSequence().mapNotNull { match ->
            val minutes = match.groupValues[1].toLongOrNull() ?: return@mapNotNull null
            val seconds = match.groupValues[2].toLongOrNull() ?: return@mapNotNull null
            if (seconds >= 60L) return@mapNotNull null
            val fraction = match.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0L
            TimedLyricLine((minutes * 60_000L + seconds * 1000L + fraction + offset)
                .coerceAtLeast(0L), content)
        }
    }.sortedBy(TimedLyricLine::timeMs).toList()
}

internal fun activeLyricIndex(lines: List<TimedLyricLine>, positionMs: Long): Int {
    var low = 0
    var high = lines.lastIndex
    while (low <= high) {
        val middle = (low + high) ushr 1
        if (lines[middle].timeMs <= positionMs) low = middle + 1 else high = middle - 1
    }
    return high
}

@Composable
internal fun AudioPlayerLyrics(
    track: AudioTrack,
    positionMs: Long,
    editor: AudioTagEditor,
    onSeek: (Long) -> Unit,
    onShowArtwork: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier, shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onShowArtwork) {
                    Icon(Icons.Default.Album, stringResource(R.string.audio_show_artwork))
                }
                Text(stringResource(R.string.audio_lyrics), Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onExpand) {
                    Icon(Icons.Default.Fullscreen, stringResource(R.string.audio_expand_lyrics))
                }
            }
            AudioLyricsContent(track, positionMs, editor, onSeek,
                Modifier.weight(1f).fillMaxWidth(), compact = true)
        }
    }
}

@Composable
internal fun AudioLyricsScreen(
    track: AudioTrack,
    playback: AudioPlaybackState,
    editor: AudioTagEditor,
    onSeek: (Long) -> Unit,
    onTogglePlayback: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit
) {
    var sliderPosition by remember(track.file.reference) {
        mutableFloatStateOf(playback.positionMs.toFloat())
    }
    var seeking by remember(track.file.reference) { mutableStateOf(false) }
    LaunchedEffect(playback.positionMs, seeking) {
        if (!seeking) sliderPosition = playback.positionMs.toFloat()
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack,
                            stringResource(R.string.audio_back_to_player))
                    }
                    Text(stringResource(R.string.audio_lyrics), Modifier.weight(1f).padding(start = 8.dp),
                        style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, stringResource(R.string.audio_edit_music_details))
                    }
                }
                AudioLyricsContent(track, playback.positionMs, editor, onSeek,
                    Modifier.weight(1f).fillMaxWidth())
                Surface(shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AudioArtwork(track, Modifier.size(40.dp), MaterialTheme.shapes.medium)
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(track.displayTitle, style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(track.artist ?: stringResource(R.string.audio_unknown_artist),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        AudioPlaybackProgress(sliderPosition,
                            playback.durationMs.takeIf { it > 0L } ?: track.durationMs,
                            onPositionChange = { seeking = true; sliderPosition = it },
                            onPositionChangeFinished = {
                                onSeek(sliderPosition.toLong())
                                seeking = false
                            })
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            SplitButtonGroup(actions = listOf(
                                ToolbarAction(Icons.Default.SkipPrevious,
                                    stringResource(R.string.audio_previous), onClick = onPrevious),
                                ToolbarAction(if (playback.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    stringResource(if (playback.isPlaying) R.string.audio_pause else R.string.audio_play),
                                    onClick = onTogglePlayback),
                                ToolbarAction(Icons.Default.SkipNext,
                                    stringResource(R.string.audio_next), onClick = onNext)
                            ))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioLyricsContent(
    track: AudioTrack,
    positionMs: Long,
    editor: AudioTagEditor,
    onSeek: (Long) -> Unit,
    modifier: Modifier,
    compact: Boolean = false
) = key(track) {
    var lyrics by remember { mutableStateOf<AudioLyrics?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(track) {
        try {
            lyrics = editor.lyrics(track)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed = true
        }
    }
    val lines = remember(lyrics) { lyrics?.syncedLrc?.let(::parseLrc).orEmpty() }
    val activeIndex = activeLyricIndex(lines, positionMs)
    val listState = rememberLazyListState()
    var following by remember { mutableStateOf(true) }
    val reducedMotion = LocalReducedMotionEnabled.current
    val scrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) following = false
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(activeIndex, following) {
        if (following && activeIndex >= 0) {
            val viewport = snapshotFlow { listState.layoutInfo.viewportEndOffset }
                .first { it > 0 }
            val offset = -(viewport / 3)
            if (reducedMotion) listState.scrollToItem(activeIndex, offset)
            else listState.animateScrollToItem(activeIndex, offset)
        }
    }
    Box(modifier) {
        when {
            failed -> LyricsMessage(stringResource(R.string.audio_lyrics_load_failed))
            lyrics == null -> LoadingIndicator(Modifier.align(Alignment.Center))
            lines.isNotEmpty() -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().nestedScroll(scrollConnection),
                contentPadding = PaddingValues(start = if (compact) 20.dp else 28.dp,
                    end = if (compact) 20.dp else 28.dp, top = 24.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 20.dp)
            ) {
                itemsIndexed(lines) { index, line ->
                    val active = activeIndex >= 0 && line.timeMs == lines[activeIndex].timeMs
                    val color by animateColorAsState(
                        if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "lyric line color")
                    Text(line.text.ifBlank { "♪" },
                        style = if (compact) MaterialTheme.typography.titleLarge
                            else MaterialTheme.typography.headlineSmall,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        color = color,
                        modifier = Modifier.fillMaxWidth().clickable {
                            onSeek(line.timeMs)
                            following = true
                        }.padding(vertical = 8.dp))
                }
            }
            !lyrics?.plain.isNullOrBlank() -> Text(lyrics?.plain.orEmpty(),
                style = if (compact) MaterialTheme.typography.titleLarge
                    else MaterialTheme.typography.headlineSmall,
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = if (compact) 20.dp else 28.dp, vertical = 24.dp))
            else -> LyricsMessage(stringResource(R.string.audio_no_lyrics))
        }
        if (!following && activeIndex >= 0) {
            FilledTonalButton(onClick = { following = true },
                modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp)) {
                Icon(Icons.Default.MyLocation, null, Modifier.size(18.dp))
                Text(stringResource(R.string.audio_follow_lyrics), Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun LyricsMessage(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, modifier = Modifier.padding(24.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
