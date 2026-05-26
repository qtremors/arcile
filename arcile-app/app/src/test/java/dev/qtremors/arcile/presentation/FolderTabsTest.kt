package dev.qtremors.arcile.presentation

import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.FileSortOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderTabsTest {

    @Test
    fun `buildFolderTabs puts all first and groups by parent path`() {
        val files = listOf(
            file("three.jpg", "/storage/emulated/0/Download/three.jpg"),
            file("one.jpg", "/storage/emulated/0/DCIM/one.jpg"),
            file("two.jpg", "/storage/emulated/0/DCIM/two.jpg")
        )

        val tabs = buildFolderTabs(files, "All")

        assertEquals(listOf(null, "/storage/emulated/0/Download", "/storage/emulated/0/DCIM"), tabs.map { it.path })
        assertEquals(listOf("All", "Download", "DCIM"), tabs.map { it.label })
        assertEquals(listOf(3, 1, 2), tabs.map { it.count })
        assertEquals(listOf(3L, 1L, 2L), tabs.map { it.totalSizeBytes })
    }

    @Test
    fun `buildFolderTabs keeps duplicate folder labels separate by path`() {
        val files = listOf(
            file("one.jpg", "/storage/emulated/0/DCIM/Camera/one.jpg"),
            file("two.jpg", "/storage/1234-5678/DCIM/Camera/two.jpg")
        )

        val tabs = buildFolderTabs(files, "All")

        assertEquals(3, tabs.size)
        assertEquals(listOf("Camera", "Camera"), tabs.drop(1).map { it.label })
        assertEquals(listOf("/storage/emulated/0/DCIM/Camera", "/storage/1234-5678/DCIM/Camera"), tabs.drop(1).map { it.path })
    }

    @Test
    fun `buildFolderTabs follows the order of the provided files`() {
        val files = listOf(
            file("newest.jpg", "/storage/emulated/0/Camera/newest.jpg", lastModified = 300L),
            file("middle.jpg", "/storage/emulated/0/Screenshots/middle.jpg", lastModified = 200L),
            file("older.jpg", "/storage/emulated/0/Camera/older.jpg", lastModified = 100L)
        )
        val sortedFiles = filterAndSortFiles(files, query = "", sortOption = FileSortOption.DATE_NEWEST)

        val tabs = buildFolderTabs(sortedFiles, "All")

        assertEquals(listOf(null, "/storage/emulated/0/Camera", "/storage/emulated/0/Screenshots"), tabs.map { it.path })
    }

    @Test
    fun `filterFilesByFolderTab returns all files for null and matching parent files for folder path`() {
        val files = listOf(
            file("one.jpg", "/storage/emulated/0/DCIM/one.jpg"),
            file("two.jpg", "/storage/emulated/0/Download/two.jpg")
        )

        assertEquals(files, filterFilesByFolderTab(files, null))
        assertEquals(listOf("one.jpg"), filterFilesByFolderTab(files, "/storage/emulated/0/DCIM").map { it.name })
    }

    @Test
    fun `hasFolderTabPath detects whether selected path is still represented`() {
        val files = listOf(file("one.jpg", "/storage/emulated/0/DCIM/one.jpg"))

        assertTrue(hasFolderTabPath(files, null))
        assertTrue(hasFolderTabPath(files, "/storage/emulated/0/DCIM"))
        assertFalse(hasFolderTabPath(files, "/storage/emulated/0/Download"))
    }
}

private fun file(name: String, path: String, lastModified: Long = 1L) = FileModel(
    name = name,
    absolutePath = path,
    size = 1L,
    lastModified = lastModified,
    isDirectory = false,
    extension = name.substringAfterLast('.', ""),
    isHidden = false
)
