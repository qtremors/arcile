package dev.qtremors.arcile.feature.imagegallery

import dev.qtremors.arcile.core.storage.domain.BrowserPresentationPreferences
import dev.qtremors.arcile.core.storage.domain.BrowserViewMode
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.ClipboardState
import dev.qtremors.arcile.core.storage.domain.ClipboardOperation
import kotlinx.collections.immutable.persistentMapOf
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

    @Test
    fun `state aspect ratios and grouping preferences are correct`() {
        val state = ImageGalleryState(
            isAspectRatio = true,
            isSectioned = true,
            aspectRatios = persistentMapOf("path/to/img" to 1.5f)
        )
        assertTrue(state.isAspectRatio)
        assertTrue(state.isSectioned)
        assertEquals(1.5f, state.aspectRatios["path/to/img"])
    }

    @Test
    fun `state clipboard syncing works`() {
        val dummyFiles = listOf(
            FileModel("file1", "path1", size = 100, lastModified = 0, isDirectory = false)
        )
        val clipboard = ClipboardState(ClipboardOperation.COPY, dummyFiles)
        val state = ImageGalleryState(clipboardState = clipboard)
        assertEquals(clipboard, state.clipboardState)
    }

    @Test
    fun `time section helper categorizes dates correctly`() {
        val now = System.currentTimeMillis()
        val oneDay = 24 * 60 * 60 * 1000L
        val oneWeek = 7 * oneDay
        val oneMonth = 30 * oneDay

        assertEquals(TimeSection.TODAY, getTimeSection(now - 1000L, now))
        assertEquals(TimeSection.WEEK, getTimeSection(now - 2 * oneDay, now))
        assertEquals(TimeSection.MONTH, getTimeSection(now - 10 * oneDay, now))
        assertEquals(TimeSection.OLDER, getTimeSection(now - 45 * oneDay, now))
    }

    @Test
    fun `invert selection logic accurately swaps selected and unselected paths`() {
        val displayed = listOf(
            FileModel("file1", "path1", size = 100, lastModified = 0, isDirectory = false),
            FileModel("file2", "path2", size = 100, lastModified = 0, isDirectory = false),
            FileModel("file3", "path3", size = 100, lastModified = 0, isDirectory = false)
        )
        val selected = setOf("path1")
        
        val allPaths = displayed.map(FileModel::absolutePath).toSet()
        val inverted = allPaths - selected
        
        assertEquals(2, inverted.size)
        assertTrue(inverted.contains("path2"))
        assertTrue(inverted.contains("path3"))
        assertTrue(!inverted.contains("path1"))
    }
}
