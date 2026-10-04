package dev.qtremors.arcile.core.ui.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ImageMetadataTest {
    @Test
    fun `detail rows include content uri image metadata`() {
        val metadata = VisualMediaMetadata(
            path = "content://example/photo",
            size = 4096L,
            mimeType = "image/jpeg",
            width = 4000,
            height = 3000,
            megapixel = 12.0,
            cameraMaker = "Google",
            cameraModel = "Pixel",
            lensModel = null,
            iso = null,
            exposureTime = null,
            fNumber = null,
            focalLength = null,
            whiteBalance = null,
            flash = null,
            dateTaken = "2026:06:21 10:00:00",
            latitude = null,
            longitude = null,
            altitude = null
        )

        val rows = buildMediaMetadataDetailRows(
            title = "photo.jpg",
            reference = "content://example/photo",
            size = 4096L,
            lastModifiedText = null,
            mimeType = "image/jpeg",
            extension = "jpg",
            metadata = metadata,
            labels = labels,
            isUriReference = true,
            context = RuntimeEnvironment.getApplication()
        )

        assertEquals("photo.jpg", rows.valueFor("Title"))
        assertEquals("2026:06:21 10:00:00", rows.valueFor("Date taken"))
        assertEquals("4000 x 3000", rows.valueFor("Resolution"))
        assertEquals("4:3", rows.valueFor("Aspect ratio"))
        assertEquals("4.1 kB", rows.valueFor("Size"))
        assertEquals("content://example/photo", rows.valueFor("URI"))
        assertEquals("image/jpeg", rows.valueFor("MIME type"))
        assertEquals("JPG", rows.valueFor("Extension"))
        assertNull(rows.valueFor("Path"))
    }

    @Test
    fun `detail rows omit resolution when metadata is missing`() {
        val rows = buildMediaMetadataDetailRows(
            title = "photo",
            reference = "/storage/emulated/0/photo",
            size = 0L,
            lastModifiedText = null,
            mimeType = null,
            extension = null,
            metadata = null,
            labels = labels,
            context = RuntimeEnvironment.getApplication()
        )

        assertEquals("0 B", rows.valueFor("Size"))
        assertEquals("/storage/emulated/0/photo", rows.valueFor("Path"))
        assertNull(rows.valueFor("Resolution"))
        assertNull(rows.valueFor("Aspect ratio"))
    }

    @Test
    fun `aspect ratio is reduced using exact dimensions`() {
        assertEquals("16:9", formatMediaAspectRatio(3840, 2160))
        assertEquals("9:16", formatMediaAspectRatio(1080, 1920))
        assertEquals("1053:317", formatMediaAspectRatio(1053, 317))
        assertNull(formatMediaAspectRatio(0, 1080))
    }
}

private val labels = MediaMetadataDetailLabels(
    title = "Title",
    date = "Date",
    dateTaken = "Date taken",
    resolution = "Resolution",
    size = "Size",
    uri = "URI",
    path = "Path",
    mimeType = "MIME type",
    extension = "Extension",
    aspectRatio = "Aspect ratio"
)

private fun List<MediaMetadataDetailRow>.valueFor(label: String): String? =
    firstOrNull { it.label == label }?.value
