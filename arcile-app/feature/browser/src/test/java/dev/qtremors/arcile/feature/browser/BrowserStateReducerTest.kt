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

class BrowserStateReducerTest {
    @Test
    fun `navigation reducer opens category and clears incompatible listing selection and properties`() {
        val base = BrowserState(
            currentPath = "/storage/emulated/0/Download",
            selectedFolderTabPath = "/storage/emulated/0/Download/Sub",
            files = persistentListOf(file("alpha.txt", "/storage/emulated/0/Download/alpha.txt")),
            folderStatsByPath = persistentMapOf("/storage/emulated/0/Download/Sub" to FolderStats(1, 10, 0)),
            folderStatsLoadingPaths = persistentSetOf("/storage/emulated/0/Download/Sub"),
            selectedFiles = persistentSetOf("/storage/emulated/0/Download/alpha.txt"),
            selectedFilesTotalSize = 10L,
            isPropertiesVisible = true,
            isPropertiesLoading = true
        ).withUpdatedDisplayState()

        val updated = base.reduce(BrowserNavigationEvent.OpenCategory("Images", "primary"))

        assertEquals("", updated.currentPath)
        assertEquals("primary", updated.currentVolumeId)
        assertTrue(updated.isCategoryScreen)
        assertFalse(updated.isVolumeRootScreen)
        assertEquals("Images", updated.activeCategoryName)
        assertNull(updated.selectedFolderTabPath)
        assertTrue(updated.files.isEmpty())
        assertTrue(updated.folderStatsByPath.isEmpty())
        assertTrue(updated.selectedFiles.isEmpty())
        assertEquals(0L, updated.selectedFilesTotalSize)
        assertFalse(updated.isPropertiesVisible)
        assertTrue(updated.displayState.visibleFiles.isEmpty())
    }

    @Test
    fun `selection reducer toggles select all invert and clear with directory sizes`() {
        val directory = file("Folder", "/root/Folder", isDirectory = true)
        val child = file("child.txt", "/root/child.txt", size = 7)
        val base = BrowserState(
            files = persistentListOf(directory, child),
            folderStatsByPath = persistentMapOf(directory.absolutePath to FolderStats(4, 42, 0))
        )

        val toggled = base.reduce(BrowserSelectionEvent.Toggle(directory.absolutePath, base.files, base.folderStatsByPath))
        assertEquals(setOf(directory.absolutePath), toggled.selectedFiles)
        assertEquals(42L, toggled.selectedFilesTotalSize)

        val selectedAll = toggled.reduce(BrowserSelectionEvent.SelectAll(listOf(directory.absolutePath, child.absolutePath), base.files, base.folderStatsByPath))
        assertEquals(49L, selectedAll.selectedFilesTotalSize)

        val inverted = selectedAll.reduce(BrowserSelectionEvent.Invert(listOf(directory.absolutePath, child.absolutePath), base.files, base.folderStatsByPath))
        assertTrue(inverted.selectedFiles.isEmpty())
        assertEquals(0L, inverted.selectedFilesTotalSize)

        val cleared = selectedAll.reduce(BrowserSelectionEvent.Clear)
        assertTrue(cleared.selectedFiles.isEmpty())
        assertEquals(0L, cleared.selectedFilesTotalSize)
    }

    @Test
    fun `same directory reload preserves listing and selection`() {
        val selected = persistentSetOf("/root/a.jpg")
        val existingFiles = persistentListOf(file("a.jpg", "/root/a.jpg", size = 10))
        val base = BrowserState(
            currentPath = "/root",
            currentVolumeId = "primary",
            files = existingFiles,
            folderStatsByPath = persistentMapOf("/root/folder" to FolderStats(1, 10, 0)),
            folderStatsLoadingPaths = persistentSetOf("/root/loading"),
            selectedFiles = selected,
            selectedFilesTotalSize = 10L
        ).withUpdatedDisplayState()

        val reloaded = base.reduce(BrowserNavigationEvent.OpenDirectory("/root", "primary"))

        assertEquals(existingFiles, reloaded.files)
        assertEquals(base.folderStatsByPath, reloaded.folderStatsByPath)
        assertEquals(base.folderStatsLoadingPaths, reloaded.folderStatsLoadingPaths)
        assertEquals(selected, reloaded.selectedFiles)
        assertEquals(10L, reloaded.selectedFilesTotalSize)
    }

    @Test
    fun `different directory navigation clears selection`() {
        val base = BrowserState(
            currentPath = "/root",
            currentVolumeId = "primary",
            selectedFiles = persistentSetOf("/root/a.jpg"),
            selectedFilesTotalSize = 10L
        ).withUpdatedDisplayState()

        val navigated = base.reduce(BrowserNavigationEvent.OpenDirectory("/root/child", "primary"))

        assertTrue(navigated.selectedFiles.isEmpty())
        assertEquals(0L, navigated.selectedFilesTotalSize)
    }

    @Test
    fun `search reducer updates only search fields and preserves navigation and selection`() {
        val selected = persistentSetOf("/root/a.txt")
        val base = BrowserState(
            currentPath = "/root",
            selectedFiles = selected,
            activeSearchFilters = SearchFilters(minSize = 1L)
        )

        val queried = base.reduce(BrowserSearchEvent.QueryChanged("invoice"))
        val filtered = queried.reduce(BrowserSearchEvent.FiltersChanged(SearchFilters(fileType = "image")))
        val loaded = filtered.reduce(BrowserSearchEvent.ResultsLoaded(listOf(file("invoice.jpg", "/root/invoice.jpg"))))

        assertEquals("/root", loaded.currentPath)
        assertEquals(selected, loaded.selectedFiles)
        assertEquals("invoice", loaded.browserSearchQuery)
        assertEquals(SearchFilters(fileType = "image"), loaded.activeSearchFilters)
        assertFalse(loaded.isSearching)
        assertEquals(listOf("invoice.jpg"), loaded.searchResults.map { it.name })
    }

    @Test
    fun `dialog and operation reducers do not mutate navigation listing or selection slices`() {
        val conflict = FileConflict(
            sourcePath = "/root/a.txt",
            sourceFile = file("a.txt", "/root/a.txt"),
            existingFile = file("a.txt", "/dest/a.txt")
        )
        val base = BrowserState(
            currentPath = "/root",
            files = persistentListOf(file("a.txt", "/root/a.txt")),
            selectedFiles = persistentSetOf("/root/a.txt"),
            selectedFilesTotalSize = 1L
        ).withUpdatedDisplayState()
        val navigation = base.locationState()
        val listing = base.listingState()
        val selection = base.selectionState()

        val updated = base
            .reduce(BrowserDialogEvent.ConflictDialogShown(listOf(conflict)))
            .reduce(BrowserOperationEvent.StatusMessageChanged(dev.qtremors.arcile.core.presentation.UiText.Dynamic("Done")))

        assertEquals(navigation, updated.locationState())
        assertEquals(listing, updated.listingState())
        assertEquals(selection, updated.selectionState())
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
