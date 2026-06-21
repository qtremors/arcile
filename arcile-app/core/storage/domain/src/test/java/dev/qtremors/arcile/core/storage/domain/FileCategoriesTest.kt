package dev.qtremors.arcile.core.storage.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileCategoriesTest {
    @Test
    fun `images category includes common image extensions`() {
        val expected = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "heic", "heif", "tiff", "tif")

        assertTrue(FileCategories.Images.extensions.containsAll(expected))
    }

    @Test
    fun `png is categorized as image by extension or mime type`() {
        assertEquals(FileCategories.Images, FileCategories.getCategoryForFile("png", null))
        assertEquals(FileCategories.Images, FileCategories.getCategoryForFile("", "image/png"))
    }

    @Test
    fun `videos category includes common video extensions`() {
        val expected = setOf("mp4", "mkv", "mov", "webm", "3gp", "3g2", "mts", "m2ts", "mpeg", "mpg", "ogv")

        assertTrue(FileCategories.Videos.extensions.containsAll(expected))
        assertEquals(FileCategories.Videos, FileCategories.getCategoryForFile("m2ts", null))
        assertEquals(FileCategories.Videos, FileCategories.getCategoryForFile("", "video/mp4"))
    }
}
