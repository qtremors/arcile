package dev.qtremors.arcile.shared.ui.metadata

import android.graphics.Bitmap
import androidx.exifinterface.media.ExifInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ImageMetadataWriterTest {

    @Test
    fun `metadata update and erase round trip preserves orientation`() {
        val context = RuntimeEnvironment.getApplication()
        val image = File(context.cacheDir, "metadata-round-trip.jpg")
        image.outputStream().use { output ->
            Bitmap.createBitmap(16, 9, Bitmap.Config.ARGB_8888)
                .compress(Bitmap.CompressFormat.JPEG, 95, output)
        }
        ExifInterface(image).apply {
            setAttribute(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_ROTATE_90.toString()
            )
            saveAttributes()
        }

        val updateResult = SharedImageMetadataReader.updateFileMetadata(
            filePath = image.absolutePath,
            update = ImageMetadataUpdate(
                description = "Sunset",
                userComment = "Edited in Arcile",
                artist = "Arcile User",
                copyright = "2026",
                cameraMaker = "Google",
                cameraModel = "Pixel",
                dateTaken = "2026:06:28 10:30:00",
                latitude = 12.9716,
                longitude = 77.5946
            ),
            context = context
        )

        assertEquals(ImageMetadataWriteResult.Success, updateResult)
        val updated = SharedImageMetadataReader.readFileMetadata(image.absolutePath, "image/jpeg")
        assertEquals("Sunset", updated.description)
        assertEquals("Edited in Arcile", updated.userComment)
        assertEquals("Arcile User", updated.artist)
        assertEquals("Google", updated.cameraMaker)
        assertEquals(12.9716, updated.latitude ?: 0.0, 0.0001)

        val eraseResult = SharedImageMetadataReader.eraseFileMetadataResult(image.absolutePath, context)
        assertEquals(ImageMetadataWriteResult.Success, eraseResult)
        val erased = SharedImageMetadataReader.readFileMetadata(image.absolutePath, "image/jpeg")
        assertNull(erased.description)
        assertNull(erased.userComment)
        assertNull(erased.artist)
        assertNull(erased.cameraMaker)
        assertNull(erased.latitude)
        assertEquals(
            ExifInterface.ORIENTATION_ROTATE_90,
            ExifInterface(image).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_UNDEFINED
            )
        )
    }

    @Test
    fun `writer rejects unsupported and missing files`() {
        val context = RuntimeEnvironment.getApplication()
        val unsupported = File(context.cacheDir, "metadata.gif").apply {
            writeBytes(byteArrayOf(1, 2, 3))
        }
        val update = ImageMetadataUpdate(
            description = null,
            userComment = null,
            artist = null,
            copyright = null,
            cameraMaker = null,
            cameraModel = null,
            dateTaken = null,
            latitude = null,
            longitude = null
        )

        assertEquals(
            ImageMetadataWriteResult.UnsupportedFormat,
            SharedImageMetadataReader.updateFileMetadata(unsupported.absolutePath, update, context)
        )
        assertEquals(
            ImageMetadataWriteResult.NotWritable,
            SharedImageMetadataReader.updateFileMetadata(
                File(context.cacheDir, "missing.jpg").absolutePath,
                update,
                context
            )
        )
        assertTrue(unsupported.delete())
    }
}
