package dev.qtremors.arcile.feature.imagegallery

import dev.qtremors.arcile.core.storage.domain.BrowserPresentationPreferences
import dev.qtremors.arcile.core.storage.domain.BrowserViewMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageGalleryStateTest {
    @Test
    fun `default state is ready for image gallery first load`() {
        val state = ImageGalleryState(volumeId = "primary")

        assertEquals("primary", state.volumeId)
        assertEquals(BrowserPresentationPreferences.DEFAULT_CATEGORY_SORT_OPTION, state.presentation.sortOption)
        assertEquals(BrowserViewMode.GRID, state.presentation.viewMode)
        assertTrue(state.isLoading)
        assertTrue(state.files.isEmpty())
        assertTrue(state.selectedFiles.isEmpty())
    }
}
