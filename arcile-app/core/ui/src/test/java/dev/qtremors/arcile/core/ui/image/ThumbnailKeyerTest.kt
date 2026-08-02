package dev.qtremors.arcile.core.ui.image

import coil.request.Options
import coil.size.Size
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class ThumbnailKeyerTest {
    private val keyer = ThumbnailKeyer()

    @Test
    fun `uses the rendered thumbnail size in the cache key`() {
        val key = thumbnailKey(extension = "mp3")
        val options = mockk<Options> {
            every { size } returns Size(180, 180)
        }

        assertEquals(key.variantKey(180).cacheKey, keyer.key(key, options))
    }

    @Test
    fun `caps expensive generated previews at their rendered limit`() {
        val key = thumbnailKey(extension = "pdf")
        val options = mockk<Options> {
            every { size } returns Size(900, 900)
        }

        assertEquals(
            key.variantKey(ThumbnailTargetSize.MAX_EXPENSIVE_PX).cacheKey,
            keyer.key(key, options)
        )
    }

    private fun thumbnailKey(extension: String) = ThumbnailKey(
        path = "/storage/emulated/0/sample.$extension",
        extension = extension,
        sizeBytes = 1_024L,
        lastModifiedMillis = 2_000L
    )
}
