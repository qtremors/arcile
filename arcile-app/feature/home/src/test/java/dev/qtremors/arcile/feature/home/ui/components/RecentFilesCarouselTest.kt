package dev.qtremors.arcile.feature.home.ui.components

import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.ui.image.ThumbnailKey
import dev.qtremors.arcile.core.ui.image.ThumbnailType
import org.junit.Assert.assertEquals
import org.junit.Test

class RecentFilesCarouselTest {
    @Test
    fun `home recent thumbnail cache key uses rendered image variant`() {
        val file = FileModel(
            name = "shot.png",
            absolutePath = "/storage/emulated/0/Pictures/Screenshots/shot.png",
            size = 42_000L,
            lastModified = 1_234L,
            isDirectory = false,
            extension = "png",
            mimeType = "image/png"
        )
        val renderedSizePx = homeCarouselRenderedThumbnailSizePx(
            screenWidthDp = 360,
            density = 3f
        )
        val requestSizePx = homeCarouselThumbnailSizePx(renderedSizePx, ThumbnailType.Image)

        val expected = ThumbnailKey.from(file)
            .variantKey(requestSizePx)
            .cacheKey

        assertEquals(expected, homeThumbnailCacheKey(file, requestSizePx))
    }

    @Test
    fun `home recent image thumbnail follows rendered carousel bounds`() {
        val renderedSizePx = homeCarouselRenderedThumbnailSizePx(
            screenWidthDp = 360,
            density = 3f
        )

        assertEquals(675, renderedSizePx)
        assertEquals(675, homeCarouselThumbnailSizePx(renderedSizePx, ThumbnailType.Image))
    }

    @Test
    fun `home recent expensive previews retain gallery safety cap`() {
        val renderedSizePx = homeCarouselRenderedThumbnailSizePx(
            screenWidthDp = 412,
            density = 3.5f
        )

        assertEquals(901, renderedSizePx)
        assertEquals(512, homeCarouselThumbnailSizePx(renderedSizePx, ThumbnailType.Pdf))
    }
}
