package dev.qtremors.arcile.feature.activitylog.ui

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
import dev.qtremors.arcile.core.storage.domain.ActivityLogEntry
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

@Composable
internal fun ActivityDateHeader(dateHeader: String) {
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

internal data class ActivityCalendarGroup(
    val dayStartMillis: Long,
    val label: String,
    val entries: List<ActivityLogEntry>
)

internal fun groupActivityByCalendarDay(
    entries: List<ActivityLogEntry>,
    todayStart: Long,
    yesterdayStart: Long,
    todayLabel: String,
    yesterdayLabel: String,
    currentYearFormatter: DateFormat,
    olderYearFormatter: DateFormat,
    timeZone: TimeZone = TimeZone.getDefault()
): List<ActivityCalendarGroup> {
    val currentYear = Calendar.getInstance(timeZone).apply {
        timeInMillis = todayStart
    }.get(Calendar.YEAR)
    val entriesByDay = linkedMapOf<Long, MutableList<ActivityLogEntry>>()
    entries.forEach { entry ->
        val dayStart = Calendar.getInstance(timeZone).apply {
            timeInMillis = entry.timestampMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        entriesByDay.getOrPut(dayStart) { mutableListOf() }.add(entry)
    }
    return entriesByDay.map { (dayStart, dayEntries) ->
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
        ActivityCalendarGroup(dayStart, label, dayEntries)
    }
}
