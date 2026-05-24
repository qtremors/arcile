package dev.qtremors.arcile.presentation.ui.components.lists

import dev.qtremors.arcile.domain.FileModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
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
}
