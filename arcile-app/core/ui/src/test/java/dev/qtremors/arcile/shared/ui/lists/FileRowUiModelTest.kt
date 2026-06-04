package dev.qtremors.arcile.shared.ui.lists

import dev.qtremors.arcile.core.storage.domain.FileModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class FileRowUiModelTest {
    private val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    @Test
    fun `preformats file metadata for lazy rows`() {
        val row = FileModel(
            name = "clip.mp4",
            absolutePath = "/storage/emulated/0/Movies/clip.mp4",
            size = 1024,
            lastModified = 0,
            extension = "mp4",
            mimeType = "video/mp4"
        ).toFileRowUiModel(formatter, thumbnailSizePx = 192)

        assertEquals("1970-01-01", row.formattedDate)
        assertEquals(FileIconType.Video, row.iconType)
        assertEquals(192, row.thumbnailSizePx)
        assertTrue(row.canShowThumbnail)
    }

    @Test
    fun `accepts locale aware DateFormat instances`() {
        val localeFormatter = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.FRANCE).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        val row = FileModel(
            name = "report.pdf",
            absolutePath = "/storage/emulated/0/Documents/report.pdf",
            size = 512,
            lastModified = 0,
            extension = "pdf",
            mimeType = "application/pdf"
        ).toFileRowUiModel(localeFormatter)

        assertEquals("1 janv. 1970", row.formattedDate)
    }
}
