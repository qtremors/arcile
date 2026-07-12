package dev.qtremors.arcile.feature.browser.delegate

import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.FolderStats
import dev.qtremors.arcile.feature.browser.BrowserSelectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionControllerTest {
    private val directory = file("Folder", "/root/Folder", isDirectory = true)
    private val child = file("child.txt", "/root/child.txt", size = 7)
    private var context = BrowserSelectionContext(
        isVolumeRootScreen = false,
        files = listOf(directory, child),
        folderStats = mapOf(directory.absolutePath to FolderStats(4, 42, 0))
    )
    private var changedCount = 0
    private val controller = SelectionController(
        initialState = BrowserSelectionState(),
        contextProvider = { context },
        onSelectionChanged = { changedCount += 1 }
    )

    @Test
    fun `toggle select all invert and clear calculate directory sizes`() {
        controller.toggle(directory.absolutePath)
        assertEquals(setOf(directory.absolutePath), controller.state.value.selectedFiles)
        assertEquals(42L, controller.state.value.selectedFilesTotalSize)

        controller.selectAll(listOf(directory.absolutePath, child.absolutePath))
        assertEquals(49L, controller.state.value.selectedFilesTotalSize)

        controller.invert(listOf(directory.absolutePath, child.absolutePath))
        assertTrue(controller.state.value.selectedFiles.isEmpty())

        controller.selectMultiple(listOf(directory.absolutePath, child.absolutePath))
        controller.clear()
        assertTrue(controller.state.value.selectedFiles.isEmpty())
        assertEquals(0L, controller.state.value.selectedFilesTotalSize)
        assertEquals(5, changedCount)
    }

    @Test
    fun `volume roots reject selection commands`() {
        context = context.copy(isVolumeRootScreen = true)

        controller.toggle(directory.absolutePath)
        controller.selectAll(listOf(directory.absolutePath))
        controller.invert(listOf(directory.absolutePath))
        controller.selectMultiple(listOf(directory.absolutePath))

        assertTrue(controller.state.value.selectedFiles.isEmpty())
        assertEquals(0, changedCount)
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
