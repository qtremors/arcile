package dev.qtremors.arcile.feature.recentfiles.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.storage.domain.FileListingPreferences
import dev.qtremors.arcile.core.storage.domain.FileSortOption
import dev.qtremors.arcile.core.storage.domain.FileModel
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

@Composable
internal fun RecentDateHeaderPill(dateHeader: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.primary,
            tonalElevation = 3.dp,
            shadowElevation = 2.dp
        ) {
            Text(
                text = dateHeader,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
            )
        }
    }
}

internal fun shouldGroupRecentFiles(
    showSearchBar: Boolean,
    presentation: FileListingPreferences
): Boolean = !showSearchBar &&
    presentation.sortOption in setOf(FileSortOption.DATE_NEWEST, FileSortOption.DATE_OLDEST)

internal data class RecentCalendarGroup(
    val dayStartMillis: Long,
    val label: String,
    val files: List<FileModel>
)

internal fun groupRecentFilesByCalendarDay(
    files: List<FileModel>,
    todayStart: Long,
    yesterdayStart: Long,
    todayLabel: String,
    yesterdayLabel: String,
    currentYearFormatter: DateFormat,
    olderYearFormatter: DateFormat,
    timeZone: TimeZone = TimeZone.getDefault()
): List<RecentCalendarGroup> {
    val currentYear = Calendar.getInstance(timeZone).apply {
        timeInMillis = todayStart
    }.get(Calendar.YEAR)
    val filesByDay = linkedMapOf<Long, MutableList<FileModel>>()
    files.forEach { file ->
        val dayStart = Calendar.getInstance(timeZone).apply {
            timeInMillis = file.lastModified
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        filesByDay.getOrPut(dayStart) { mutableListOf() }.add(file)
    }
    return filesByDay.map { (dayStart, dayFiles) ->
        val label = when {
            dayStart >= todayStart -> todayLabel
            dayStart >= yesterdayStart -> yesterdayLabel
            else -> {
                val year = Calendar.getInstance(timeZone).apply {
                    timeInMillis = dayStart
                }.get(Calendar.YEAR)
                val formatter = if (year == currentYear) currentYearFormatter else olderYearFormatter
                formatter.format(Date(dayStart))
            }
        }
        RecentCalendarGroup(dayStart, label, dayFiles)
    }
}
