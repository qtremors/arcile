package dev.qtremors.arcile.core.storage.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchFiltersTest {
    private val volume = StorageVolume(
        id = "primary",
        storageKey = "primary",
        name = "Internal",
        path = "/storage/emulated/0",
        totalBytes = 100,
        freeBytes = 50,
        isPrimary = true,
        isRemovable = false
    )

    private val file = FileModel(
        name = "photo.JPG",
        absolutePath = "/storage/emulated/0/DCIM/photo.JPG",
        size = 2048,
        lastModified = 2_000,
        extension = "jpg",
        mimeType = "image/jpeg"
    )

    @Test
    fun `matches extension mime size date scope and volume filters`() {
        val filters = SearchFilters(
            extensions = setOf("jpg"),
            mimeType = "image/*",
            minSize = 1024,
            maxSize = 4096,
            minDateMillis = 1_000,
            maxDateMillis = 3_000,
            folderScopePath = "/storage/emulated/0/DCIM",
            storageVolumeId = "primary"
        )

        assertTrue(file.matchesSearchFilters(filters, listOf(volume)))
    }

    @Test
    fun `hidden files are excluded until include hidden is enabled`() {
        val hidden = file.copy(name = ".secret.jpg", absolutePath = "/storage/emulated/0/.secret.jpg", isHidden = true)

        assertFalse(hidden.matchesSearchFilters(SearchFilters(), listOf(volume)))
        assertTrue(hidden.matchesSearchFilters(SearchFilters(includeHidden = true), listOf(volume)))
    }
}
