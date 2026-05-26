package dev.qtremors.arcile.feature.browser

import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.StorageKind
import dev.qtremors.arcile.core.storage.domain.StorageVolume
import dev.qtremors.arcile.presentation.FileSortOption
import kotlinx.collections.immutable.toPersistentList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class BrowserDisplayStateTest {

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
            allFilesLabel = "All files"
        )

        assertEquals(listOf("alpha", "beta.txt", "zeta.txt"), display.visibleFiles.map { it.name })
        assertEquals(display.visibleFiles.map { it.absolutePath }, display.visiblePaths)
        assertEquals(setOf("alpha", "beta.txt", "zeta.txt"), display.existingNames)
        assertEquals("primary", display.currentVolume?.id)
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
            allFilesLabel = "All files"
        )

        assertEquals(listOf("All files", "DCIM", "Pictures"), display.categoryFolderTabs.map { it.label })
        assertEquals(2, display.selectedCategoryFolderTabIndex)
        assertEquals(listOf("two.jpg"), display.visibleFiles.map { it.name })
    }

    @Test
    fun `transient browser updates keep display state reference`() {
        val base = BrowserState(
            files = listOf(file("one.txt", "/storage/emulated/0/one.txt")).toPersistentList()
        ).withUpdatedDisplayState()

        val updated = base.copy(isPropertiesVisible = true, isLoading = true)

        assertSame(base.displayState, updated.displayState)
    }

    private fun file(
        name: String,
        path: String,
        isDirectory: Boolean = false,
        size: Long = 1L
    ) = FileModel(
        name = name,
        absolutePath = path,
        size = size,
        lastModified = size,
        isDirectory = isDirectory,
        extension = name.substringAfterLast('.', ""),
        isHidden = false
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
