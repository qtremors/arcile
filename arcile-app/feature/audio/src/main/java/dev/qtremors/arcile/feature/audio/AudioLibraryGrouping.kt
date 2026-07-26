package dev.qtremors.arcile.feature.audio

import dev.qtremors.arcile.core.storage.domain.AudioTrack
import dev.qtremors.arcile.core.storage.domain.ImageGalleryGrouping
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

internal data class AudioGroupKey(
    val label: String,
    val timestamp: Long
) : Comparable<AudioGroupKey> {
    override fun compareTo(other: AudioGroupKey): Int =
        other.timestamp.compareTo(timestamp)
}

internal fun groupAudioTracks(
    tracks: List<AudioTrack>,
    grouping: ImageGalleryGrouping
): Map<AudioGroupKey, List<AudioTrack>> {
    if (grouping == ImageGalleryGrouping.NONE) return emptyMap()
    val dayFormatter = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
    val monthFormatter = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    return tracks.groupBy { track ->
        val calendar = Calendar.getInstance().apply { timeInMillis = track.file.lastModified }
        val label = when (grouping) {
            ImageGalleryGrouping.DAY -> dayFormatter.format(Date(track.file.lastModified))
            ImageGalleryGrouping.WEEK -> audioWeekLabel(track.file.lastModified)
            ImageGalleryGrouping.MONTH -> monthFormatter.format(Date(track.file.lastModified))
            ImageGalleryGrouping.NONE -> ""
        }
        when (grouping) {
            ImageGalleryGrouping.DAY -> Unit
            ImageGalleryGrouping.WEEK -> {
                while (calendar.get(Calendar.DAY_OF_WEEK) != calendar.firstDayOfWeek) {
                    calendar.add(Calendar.DAY_OF_MONTH, -1)
                }
            }
            ImageGalleryGrouping.MONTH -> calendar.set(Calendar.DAY_OF_MONTH, 1)
            ImageGalleryGrouping.NONE -> Unit
        }
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        AudioGroupKey(label, calendar.timeInMillis)
    }.toSortedMap()
}

private fun audioWeekLabel(timestamp: Long): String {
    val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
    while (calendar.get(Calendar.DAY_OF_WEEK) != calendar.firstDayOfWeek) {
        calendar.add(Calendar.DAY_OF_MONTH, -1)
    }
    val formatter = SimpleDateFormat("MMM d", Locale.getDefault())
    val start = formatter.format(calendar.time)
    calendar.add(Calendar.DAY_OF_MONTH, 6)
    return "$start – ${formatter.format(calendar.time)}, ${calendar.get(Calendar.YEAR)}"
}
