package dev.qtremors.arcile.feature.recentfiles.ui

import dev.qtremors.arcile.core.storage.domain.FileListingPreferences
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.FileSortOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class RecentFilesGroupingTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val parser = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
        timeZone = utc
    }

    @Test
    fun `calendar groups use stable days and year aware labels`() {
        val todayStart = parser.parse("2026-01-02 00:00:00")!!.time
        val yesterdayStart = parser.parse("2026-01-01 00:00:00")!!.time
        val files = listOf(
            file("today-a", "2026-01-02 08:00:00"),
            file("today-b", "2026-01-02 01:00:00"),
            file("yesterday", "2026-01-01 20:00:00"),
            file("older", "2025-12-31 20:00:00")
        )
        val groups = groupRecentFilesByCalendarDay(
            files = files,
            todayStart = todayStart,
            yesterdayStart = yesterdayStart,
            todayLabel = "Today",
            yesterdayLabel = "Yesterday",
            currentYearFormatter = formatter("EEEE, MMM d"),
            olderYearFormatter = formatter("EEEE, MMM d, yyyy"),
            timeZone = utc
        )

        assertEquals(listOf("Today", "Yesterday", "Wednesday, Dec 31, 2025"), groups.map { it.label })
        assertEquals(listOf("today-a", "today-b"), groups.first().files.map { it.name })
        assertEquals(3, groups.map { it.dayStartMillis }.distinct().size)
    }

    @Test
    fun `calendar grouping is limited to date sorts outside search`() {
        val newest = FileListingPreferences(sortOption = FileSortOption.DATE_NEWEST)
        val oldest = FileListingPreferences(sortOption = FileSortOption.DATE_OLDEST)
        val named = FileListingPreferences(sortOption = FileSortOption.NAME_ASC)

        assertTrue(shouldGroupRecentFiles(showSearchBar = false, newest))
        assertTrue(shouldGroupRecentFiles(showSearchBar = false, oldest))
        assertFalse(shouldGroupRecentFiles(showSearchBar = false, named))
        assertFalse(shouldGroupRecentFiles(showSearchBar = true, newest))
    }

    private fun file(name: String, timestamp: String) = FileModel(
        name = name,
        absolutePath = "/$name",
        lastModified = parser.parse(timestamp)!!.time,
        isDirectory = false
    )

    private fun formatter(pattern: String) = SimpleDateFormat(pattern, Locale.US).apply {
        timeZone = utc
    }
}
