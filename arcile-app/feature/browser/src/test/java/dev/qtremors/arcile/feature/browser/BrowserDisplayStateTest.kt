package dev.qtremors.arcile.feature.browser

import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.FolderStats
import dev.qtremors.arcile.core.storage.domain.FolderStatsStatus
import dev.qtremors.arcile.core.storage.domain.StorageKind
import dev.qtremors.arcile.core.storage.domain.StorageVolume
import dev.qtremors.arcile.core.storage.domain.FileSortOption
import kotlinx.collections.immutable.toPersistentList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class BrowserDisplayStateTest {
    @Test
    fun `folder size updates reuse ordering and only replace the affected active row`() {
        val folder = file("folder", "/folder", isDirectory = true)
        val plain = file("one.txt", "/one.txt")
        val initial = BrowserNavigationState().withValues(files = listOf(folder, plain).toPersistentList(),
            browserViewMode = dev.qtremors.arcile.core.storage.domain.FileViewMode.LIST).withUpdatedDisplayState()
        val updated = initial.withValues(folderStatsByPath = kotlinx.collections.immutable.persistentMapOf(
            folder.reference to FolderStats(2, 20, 2, FolderStatsStatus.Ready)
        )).withUpdatedDisplayState(setOf(folder.reference))
        assertSame(initial.displayState.visibleFiles, updated.displayState.visibleFiles)
        assertSame(initial.displayState.visiblePaths, updated.displayState.visiblePaths)
        assertSame(initial.displayState.visibleListRows[1], updated.displayState.visibleListRows[1])
        org.junit.Assert.assertNotSame(initial.displayState.visibleListRows[0], updated.displayState.visibleListRows[0])
        org.junit.Assert.assertTrue(updated.displayState.visibleGridRows.isEmpty())
        val grid = updated.withValues(browserViewMode = dev.qtremors.arcile.core.storage.domain.FileViewMode.GRID).withUpdatedDisplayState()
        assertEquals(2, grid.displayState.visibleGridRows.size)
        assertEquals(0, grid.displayState.visibleListRows.size)
    }

    @Test
    fun `file count updates deliberately reorder folder rows`() {
        val a = file("a", "/a", isDirectory = true)
        val b = file("b", "/b", isDirectory = true)
        val initial = BrowserNavigationState().withValues(files = listOf(a, b).toPersistentList(),
            browserSortOption = FileSortOption.FILE_COUNT_HIGHEST,
            folderStatsByPath = kotlinx.collections.immutable.persistentMapOf(
                "/a" to FolderStats(2, 20, 2, FolderStatsStatus.Ready),
                "/b" to FolderStats(1, 10, 1, FolderStatsStatus.Ready)
            )).withUpdatedDisplayState()
        val updated = initial.withValues(folderStatsByPath = initial.folderStatsByPath.put(
            "/b", FolderStats(3, 30, 3, FolderStatsStatus.Ready)
        )).withUpdatedDisplayState(setOf("/b"))
        assertEquals(listOf("a", "b"), initial.displayState.visibleFiles.map { it.name })
        assertEquals(listOf("b", "a"), updated.displayState.visibleFiles.map { it.name })
    }

    @Test
    fun `display state sorts visible files and exposes visible paths`() {
        val files = listOf(
            file("zeta.txt", "/storage/emulated/0/zeta.txt", size = 20),
            file("alpha", "/storage/emulated/0/alpha", isDirectory = true),
            file("beta.txt", "/storage/emulated/0/beta.txt", size = 10)
        )

        val display = buildBrowserDisplayState(
            files = files,
            sortOption = FileSortOption.NAME_ASC,
            selectedFolderTabPath = null,
            isCategoryScreen = false,
            currentVolumeId = "primary",
            storageVolumes = listOf(volume("primary", "/storage/emulated/0")),
            showHiddenFiles = true,
            allFilesLabel = "All files"
        )

        assertEquals(listOf("alpha", "beta.txt", "zeta.txt"), display.visibleFiles.map { it.name })
        assertEquals(display.visibleFiles.map { it.reference }, display.visiblePaths)
        assertEquals(setOf("alpha", "beta.txt", "zeta.txt"), display.existingNames)
        assertEquals("primary", display.currentVolume?.id)
    }

    @Test
    fun `display state honors disabled folders first for date sorting`() {
        val files = listOf(
            file("folder", "/storage/emulated/0/folder", isDirectory = true, size = 1),
            file("latest.txt", "/storage/emulated/0/latest.txt", size = 2)
        )

        val display = buildBrowserDisplayState(
            files = files,
            sortOption = FileSortOption.DATE_NEWEST,
            foldersFirst = false,
            selectedFolderTabPath = null,
            isCategoryScreen = false,
            currentVolumeId = null,
            storageVolumes = emptyList(),
            showHiddenFiles = true,
            allFilesLabel = "All files"
        )

        assertEquals(listOf("latest.txt", "folder"), display.visibleFiles.map { it.name })
    }

    @Test
    fun `category display state builds folder tabs and selected index`() {
        val files = listOf(
            file("one.jpg", "/storage/emulated/0/DCIM/one.jpg"),
            file("two.jpg", "/storage/emulated/0/Pictures/two.jpg")
        )

        val display = buildBrowserDisplayState(
            files = files,
            sortOption = FileSortOption.NAME_ASC,
            selectedFolderTabPath = "/storage/emulated/0/Pictures",
            isCategoryScreen = true,
            currentVolumeId = null,
            storageVolumes = emptyList(),
            showHiddenFiles = true,
            allFilesLabel = "All files"
        )

        assertEquals(listOf("All files", "DCIM", "Pictures"), display.categoryFolderTabs.map { it.label })
        assertEquals(2, display.selectedCategoryFolderTabIndex)
        assertEquals(listOf("two.jpg"), display.visibleFiles.map { it.name })
    }

    @Test
    fun `transient browser updates keep display state reference`() {
        val base = BrowserNavigationState(
            listing = BrowserListingState(
                files = listOf(file("one.txt", "/storage/emulated/0/one.txt")).toPersistentList()
            )
        ).withUpdatedDisplayState()

        val updated = base.copy(listing = base.listing.copy(isLoading = true))

        assertSame(base.displayState, updated.displayState)
    }

    @Test
    fun `display state reuses row models when inputs are unchanged`() {
        val files = listOf(
            file("folder", "/storage/emulated/0/folder", isDirectory = true),
            file("one.txt", "/storage/emulated/0/one.txt")
        )
        val initial = buildBrowserDisplayState(
            files = files,
            sortOption = FileSortOption.NAME_ASC,
            selectedFolderTabPath = null,
            isCategoryScreen = false,
            currentVolumeId = null,
            storageVolumes = emptyList(),
            showHiddenFiles = true,
            allFilesLabel = "All files",
            folderStatsByPath = mapOf(files.first().reference to FolderStats(1, 10, 1, FolderStatsStatus.Ready))
        )

        val updated = buildBrowserDisplayState(
            files = files,
            sortOption = FileSortOption.NAME_ASC,
            selectedFolderTabPath = null,
            isCategoryScreen = false,
            currentVolumeId = null,
            storageVolumes = emptyList(),
            showHiddenFiles = true,
            allFilesLabel = "All files",
            folderStatsByPath = mapOf(files.first().reference to FolderStats(1, 10, 1, FolderStatsStatus.Ready)),
            previousDisplayState = initial
        )

        assertSame(initial.visibleListRows[0], updated.visibleListRows[0])
        assertSame(initial.visibleListRows[1], updated.visibleListRows[1])
        assertSame(initial.visibleGridRows[0], updated.visibleGridRows[0])
    }

    @Test
    fun `display state rebuilds only folder stat affected rows`() {
        val folder = file("folder", "/storage/emulated/0/folder", isDirectory = true)
        val plain = file("one.txt", "/storage/emulated/0/one.txt")
        val files = listOf(folder, plain)
        val initial = buildBrowserDisplayState(
            files = files,
            sortOption = FileSortOption.NAME_ASC,
            selectedFolderTabPath = null,
            isCategoryScreen = false,
            currentVolumeId = null,
            storageVolumes = emptyList(),
            showHiddenFiles = true,
            allFilesLabel = "All files",
            folderStatsByPath = mapOf(folder.reference to FolderStats(1, 10, 1, FolderStatsStatus.Ready))
        )

        val updated = buildBrowserDisplayState(
            files = files,
            sortOption = FileSortOption.NAME_ASC,
            selectedFolderTabPath = null,
            isCategoryScreen = false,
            currentVolumeId = null,
            storageVolumes = emptyList(),
            showHiddenFiles = true,
            allFilesLabel = "All files",
            folderStatsByPath = mapOf(folder.reference to FolderStats(2, 20, 2, FolderStatsStatus.Ready)),
            previousDisplayState = initial
        )

        assertEquals(folder.reference, updated.visibleListRows[0].absolutePath)
        assertSame(initial.visibleListRows[1], updated.visibleListRows[1])
        assertSame(initial.visibleGridRows[1], updated.visibleGridRows[1])
        org.junit.Assert.assertNotSame(initial.visibleListRows[0], updated.visibleListRows[0])
    }

    @Test
    fun `display state hides hidden files unless enabled`() {
        val files = listOf(
            file("visible.txt", "/storage/emulated/0/visible.txt"),
            file(".secret", "/storage/emulated/0/.secret", isHidden = true)
        )

        val hiddenDisabled = buildBrowserDisplayState(
            files = files,
            sortOption = FileSortOption.NAME_ASC,
            selectedFolderTabPath = null,
            isCategoryScreen = false,
            currentVolumeId = null,
            storageVolumes = emptyList(),
            showHiddenFiles = false,
            allFilesLabel = "All files"
        )
        val hiddenEnabled = buildBrowserDisplayState(
            files = files,
            sortOption = FileSortOption.NAME_ASC,
            selectedFolderTabPath = null,
            isCategoryScreen = false,
            currentVolumeId = null,
            storageVolumes = emptyList(),
            showHiddenFiles = true,
            allFilesLabel = "All files"
        )

        assertEquals(listOf("visible.txt"), hiddenDisabled.visibleFiles.map { it.name })
        assertEquals(listOf(".secret", "visible.txt"), hiddenEnabled.visibleFiles.map { it.name })
        assertEquals(setOf("visible.txt", ".secret"), hiddenDisabled.existingNames)
    }

    private fun file(
        name: String,
        path: String,
        isDirectory: Boolean = false,
        size: Long = 1L,
        isHidden: Boolean = false
    ) = FileModel(
        name = name,
        reference = path,
        size = size,
        lastModified = size,
        isDirectory = isDirectory,
        extension = name.substringAfterLast('.', ""),
        isHidden = isHidden
    )

    private fun volume(id: String, path: String) = StorageVolume(
        id = id,
        storageKey = id,
        name = id,
        path = path,
        totalBytes = 100,
        freeBytes = 50,
        isPrimary = id == "primary",
        isRemovable = false,
        kind = StorageKind.INTERNAL
    )
}
