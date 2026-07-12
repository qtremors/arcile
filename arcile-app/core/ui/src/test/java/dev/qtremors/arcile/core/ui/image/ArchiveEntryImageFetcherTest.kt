package dev.qtremors.arcile.core.ui.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.test.core.app.ApplicationProvider
import coil.fetch.DrawableResult
import coil.request.Options
import coil.size.Size as CoilSize
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ArchiveEntryImageFetcherTest {

    @Test
    fun `fetch decodes valid archive image sampled to requested size`() = runTest {
        val archive = zipWithEntry("photo.png", validPngBytes(512, 512))
        val options = options(128)
        val data = ArchiveEntryThumbnailData(archive.absolutePath, "photo.png", 1L, 1L)

        val result = ArchiveEntryImageFetcher(data, options).fetch() as DrawableResult

        val bitmap = (result.drawable as BitmapDrawable).bitmap
        assertTrue(result.isSampled)
        assertTrue(bitmap.width <= 256)
        assertTrue(bitmap.height <= 256)
    }

    @Test
    fun `fetch rejects non zip non image traversal missing and directory entries`() = runTest {
        val textFile = File.createTempFile("archive", ".txt").apply { writeText("not zip") }
        val archive = zipWithEntry("note.txt", "hello".toByteArray())
        val directoryArchive = zipWithDirectory("folder/")
        val options = options()

        assertNull(ArchiveEntryImageFetcher(ArchiveEntryThumbnailData(textFile.absolutePath, "photo.png", 1L, 1L), options).fetch())
        assertNull(ArchiveEntryImageFetcher(ArchiveEntryThumbnailData(archive.absolutePath, "note.txt", 1L, 1L), options).fetch())
        assertNull(ArchiveEntryImageFetcher(ArchiveEntryThumbnailData(archive.absolutePath, "../photo.png", 1L, 1L), options).fetch())
        assertNull(ArchiveEntryImageFetcher(ArchiveEntryThumbnailData(archive.absolutePath, "missing.png", 1L, 1L), options).fetch())
        assertNull(ArchiveEntryImageFetcher(ArchiveEntryThumbnailData(directoryArchive.absolutePath, "folder/", 1L, 1L), options).fetch())
    }

    @Test
    fun `fetch rejects oversized metadata and oversized image dimensions`() = runTest {
        val validArchive = zipWithEntry("photo.png", validPngBytes(16, 16))
        val oversizedBoundsArchive = zipWithEntry("huge.png", pngHeaderOnly(width = 7_000, height = 6_000))
        val options = options()

        assertNull(
            ArchiveEntryImageFetcher(
                ArchiveEntryThumbnailData(
                    validArchive.absolutePath,
                    "photo.png",
                    ArchiveEntryImageFetcher.MAX_ARCHIVE_THUMBNAIL_BYTES + 1L,
                    1L
                ),
                options
            ).fetch()
        )
        assertNull(
            ArchiveEntryImageFetcher(
                ArchiveEntryThumbnailData(oversizedBoundsArchive.absolutePath, "huge.png", 1L, 1L),
                options
            ).fetch()
        )
    }

    @Test
    fun `factory creates archive entry fetcher`() {
        val options = options()

        assertNotNull(
            ArchiveEntryImageFetcher.Factory().create(
                ArchiveEntryThumbnailData("/tmp/archive.zip", "photo.png", 1L, 1L),
                options,
                mockk(relaxed = true)
            )
        )
    }

    private fun options(size: Int = 256): Options {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return mockk {
            every { this@mockk.context } returns context
            every { this@mockk.size } returns CoilSize(size, size)
        }
    }

    private fun zipWithEntry(name: String, bytes: ByteArray): File {
        val archive = File.createTempFile("archive-entry", ".zip")
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry(name))
            zip.write(bytes)
            zip.closeEntry()
        }
        return archive
    }

    private fun zipWithDirectory(name: String): File {
        val archive = File.createTempFile("archive-entry-dir", ".zip")
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry(name))
            zip.closeEntry()
        }
        return archive
    }

    private fun validPngBytes(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            output.toByteArray()
        }
    }

    private fun pngHeaderOnly(width: Int, height: Int): ByteArray {
        val output = ByteArrayOutputStream()
        output.write(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10))
        output.writeChunk("IHDR", buildList {
            addAll(width.toBytes())
            addAll(height.toBytes())
            add(8)
            add(6)
            add(0)
            add(0)
            add(0)
        }.map { it.toByte() }.toByteArray())
        output.writeChunk("IEND", byteArrayOf())
        return output.toByteArray()
    }

    private fun Int.toBytes(): List<Int> =
        listOf((this ushr 24) and 0xff, (this ushr 16) and 0xff, (this ushr 8) and 0xff, this and 0xff)

    private fun ByteArrayOutputStream.writeChunk(type: String, data: ByteArray) {
        write(data.size.toBytes().map { it.toByte() }.toByteArray())
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        write(typeBytes)
        write(data)
        val crc = CRC32()
        crc.update(typeBytes)
        crc.update(data)
        write(crc.value.toInt().toBytes().map { it.toByte() }.toByteArray())
    }
}
