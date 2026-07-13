package dev.qtremors.arcile.feature.browser

import dev.qtremors.arcile.core.storage.domain.FileConflict
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.FolderStats
import dev.qtremors.arcile.core.storage.domain.SearchFilters
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserOwnedStateReducerTest {
    @Test
    fun `navigation reducer opens category without mutating independently owned state`() {
        val base = BrowserNavigationState().withValues(
            currentPath = "/storage/emulated/0/Download",
            selectedFolderTabPath = "/storage/emulated/0/Download/Sub",
            files = persistentListOf(file("alpha.txt", "/storage/emulated/0/Download/alpha.txt")),
            folderStatsByPath = persistentMapOf("/storage/emulated/0/Download/Sub" to FolderStats(1, 10, 0)),
            folderStatsLoadingPaths = persistentSetOf("/storage/emulated/0/Download/Sub")
        )
        val selection = BrowserSelectionState(
            selectedFiles = persistentSetOf("/storage/emulated/0/Download/alpha.txt"),
            selectedFilesTotalSize = 10L
        )
        val properties = BrowserPropertiesState(isVisible = true, isLoading = true)

        val updated = base.reduce(BrowserNavigationEvent.OpenCategory("Images", "primary"))

        assertEquals("", updated.currentPath)
        assertEquals("primary", updated.currentVolumeId)
        assertTrue(updated.isCategoryScreen)
        assertFalse(updated.isVolumeRootScreen)
        assertEquals("Images", updated.activeCategoryName)
        assertNull(updated.selectedFolderTabPath)
        assertTrue(updated.files.isEmpty())
        assertTrue(updated.folderStatsByPath.isEmpty())
        assertEquals(persistentSetOf("/storage/emulated/0/Download/alpha.txt"), selection.selectedFiles)
        assertEquals(10L, selection.selectedFilesTotalSize)
        assertTrue(properties.isVisible)
        assertTrue(updated.displayState.visibleFiles.isEmpty())
    }

    @Test
    fun `same directory reload preserves listing and selection`() {
        val selected = persistentSetOf("/root/a.jpg")
        val existingFiles = persistentListOf(file("a.jpg", "/root/a.jpg", size = 10))
        val base = BrowserNavigationState().withValues(
            currentPath = "/root",
            currentVolumeId = "primary",
            files = existingFiles,
            folderStatsByPath = persistentMapOf("/root/folder" to FolderStats(1, 10, 0)),
            folderStatsLoadingPaths = persistentSetOf("/root/loading")
        )
        val selection = BrowserSelectionState(selected, 10L)

        val reloaded = base.reduce(BrowserNavigationEvent.OpenDirectory("/root", "primary"))

        assertEquals(existingFiles, reloaded.files)
        assertEquals(base.folderStatsByPath, reloaded.folderStatsByPath)
        assertEquals(base.folderStatsLoadingPaths, reloaded.folderStatsLoadingPaths)
        assertEquals(selected, selection.selectedFiles)
        assertEquals(10L, selection.selectedFilesTotalSize)
    }

    @Test
    fun `different directory reducer leaves selection to coordinator`() {
        val base = BrowserNavigationState().withValues(
            currentPath = "/root",
            currentVolumeId = "primary"
        )
        val selection = BrowserSelectionState(
            selectedFiles = persistentSetOf("/root/a.jpg"),
            selectedFilesTotalSize = 10L
        )

        val navigated = base.reduce(BrowserNavigationEvent.OpenDirectory("/root/child", "primary"))

        assertEquals(persistentSetOf("/root/a.jpg"), selection.selectedFiles)
        assertEquals(10L, selection.selectedFilesTotalSize)
    }

    @Test
    fun `search reducer updates only search fields and preserves navigation and selection`() {
        val selected = persistentSetOf("/root/a.txt")
        val navigation = BrowserNavigationState().withValues(currentPath = "/root")
        val base = BrowserSearchState(
            activeSearchFilters = SearchFilters(minSize = 1L)
        )

        val queried = base.reduce(BrowserSearchEvent.QueryChanged("invoice"))
        val filtered = queried.reduce(BrowserSearchEvent.FiltersChanged(SearchFilters(fileType = "image")))
        val loaded = filtered.reduce(BrowserSearchEvent.ResultsLoaded(listOf(file("invoice.jpg", "/root/invoice.jpg"))))

        assertEquals("/root", navigation.currentPath)
        assertEquals(selected, selected)
        assertEquals("invoice", loaded.browserSearchQuery)
        assertEquals(SearchFilters(fileType = "image"), loaded.activeSearchFilters)
        assertFalse(loaded.isSearching)
        assertEquals(listOf("invoice.jpg"), loaded.searchResults.map { it.name })
    }

    @Test
    fun `dialog reducer does not mutate navigation listing or selection slices`() {
        val conflict = FileConflict(
            sourcePath = "/root/a.txt",
            sourceFile = file("a.txt", "/root/a.txt"),
            existingFile = file("a.txt", "/dest/a.txt")
        )
        val navigation = BrowserNavigationState().withValues(
            currentPath = "/root",
            files = persistentListOf(file("a.txt", "/root/a.txt"))
        )
        val selection = BrowserSelectionState(
            selectedFiles = persistentSetOf("/root/a.txt"),
            selectedFilesTotalSize = 1L
        )
        val base = BrowserDialogState()

        val updated = base.reduce(BrowserDialogEvent.ConflictDialogShown(listOf(conflict)))

        assertEquals("/root", navigation.currentPath)
        assertEquals(1, navigation.files.size)
        assertEquals(persistentSetOf("/root/a.txt"), selection.selectedFiles)
        assertTrue(updated.showConflictDialog)
        assertEquals(listOf(conflict), updated.pasteConflicts)
    }

    private fun file(
        name: String,
        path: String,
        size: Long = 0,
        isDirectory: Boolean = false
    ) = FileModel(
        name = name,
        absolutePath = path,
        size = size,
        lastModified = 0,
        isDirectory = isDirectory,
        extension = path.substringAfterLast('.', "")
    )
}
