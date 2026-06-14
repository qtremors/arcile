package dev.qtremors.arcile.shared.ui.lists

import android.net.Uri
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import dev.qtremors.arcile.image.ThumbnailKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
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

    @Test
    fun `supports thumbnails for pdf apk and audio rows`() {
        val rows = listOf(
            FileModel(name = "report.pdf", absolutePath = "/storage/emulated/0/report.pdf", extension = "pdf"),
            FileModel(name = "app.apk", absolutePath = "/storage/emulated/0/app.apk", extension = "apk"),
            FileModel(name = "song.mp3", absolutePath = "/storage/emulated/0/song.mp3", extension = "mp3")
        ).map { it.toFileRowUiModel(formatter) }

        assertTrue(rows.all { it.canShowThumbnail })
    }

    @Test
    fun `image rows prefer content uri request data when present`() {
        val row = FileModel(
            name = "photo.png",
            absolutePath = "/storage/emulated/0/DCIM/photo.png",
            extension = "png",
            nodeRef = StorageNodeRef.mediaStore(
                id = 42L,
                volumeName = "external",
                contentUri = "content://media/external/images/media/42",
                displayPath = "/storage/emulated/0/DCIM/photo.png"
            )
        ).toFileRowUiModel(formatter)

        assertEquals(Uri.parse("content://media/external/images/media/42"), row.thumbnailRequestData())
    }

    @Test
    fun `image rows fall back to file request data without content uri`() {
        val row = FileModel(
            name = "photo.png",
            absolutePath = "/storage/emulated/0/DCIM/photo.png",
            extension = "png"
        ).toFileRowUiModel(formatter)

        assertTrue(row.thumbnailRequestData() is File)
    }

    @Test
    fun `audio rows use thumbnail key request data for content uri fetchers`() {
        val row = FileModel(
            name = "song.mp3",
            absolutePath = "/storage/emulated/0/Music/song.mp3",
            extension = "mp3",
            nodeRef = StorageNodeRef.mediaStore(
                id = 42L,
                volumeName = "external",
                contentUri = "content://media/external/audio/media/42",
                displayPath = "/storage/emulated/0/Music/song.mp3"
            )
        ).toFileRowUiModel(formatter)

        assertTrue(row.thumbnailRequestData() is ThumbnailKey)
    }
}
