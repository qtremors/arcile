package dev.qtremors.arcile.feature.home.ui.components

import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.ui.image.ThumbnailKey
import org.junit.Assert.assertEquals
import org.junit.Test

class RecentFilesCarouselTest {
    @Test
    fun `home recent thumbnail cache key uses bucketed carousel variant`() {
        val file = FileModel(
            name = "shot.png",
            absolutePath = "/storage/emulated/0/Pictures/Screenshots/shot.png",
            size = 42_000L,
            lastModified = 1_234L,
            isDirectory = false,
            extension = "png",
            mimeType = "image/png"
        )

        val expected = ThumbnailKey.from(file)
            .variantKey(HomeRecentFilesCarouselThumbnailSizePx)
            .cacheKey

        assertEquals(expected, homeThumbnailCacheKey(file))
    }
}
