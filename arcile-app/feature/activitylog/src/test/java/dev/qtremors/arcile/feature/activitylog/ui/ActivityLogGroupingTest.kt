package dev.qtremors.arcile.feature.activitylog.ui

import dev.qtremors.arcile.core.storage.domain.ActivityLogEntry
import dev.qtremors.arcile.core.storage.domain.ActivityLogPage
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityLogGroupingTest {
    @Test
    fun `groups chronological activity under calendar dates`() {
        val day = 86_400_000L
        val todayStart = 10 * day
        val entries = listOf(
            page("today", todayStart + 1_000),
            page("yesterday", todayStart - day + 1_000),
            page("older", todayStart - 2 * day + 1_000)
        )

        val groups = groupActivityByCalendarDay(
            entries = entries,
            todayStart = todayStart,
            yesterdayStart = todayStart - day,
            todayLabel = "Today",
            yesterdayLabel = "Yesterday",
            currentYearFormatter = SimpleDateFormat("MMMM d", Locale.US),
            olderYearFormatter = SimpleDateFormat("MMMM d, yyyy", Locale.US),
            timeZone = TimeZone.getTimeZone("UTC")
        )

        assertEquals(listOf("Today", "Yesterday", "January 9"), groups.map { it.label })
        assertEquals(listOf("today", "yesterday", "older"), groups.map { it.entries.single().id })
    }

    private fun page(id: String, timestamp: Long) = ActivityLogEntry.PageVisited(
        id = id,
        timestampMillis = timestamp,
        page = ActivityLogPage.HOME
    )
}
