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
    private var latestState = BrowserSelectionState()
    private var changedCount = 0
    private val controller = SelectionController(
        initialState = latestState,
        contextProvider = { context },
        onStateChange = { latestState = it },
        onSelectionChanged = { changedCount += 1 }
    )

    @Test
    fun `toggle select all invert and clear calculate directory sizes`() {
        controller.toggle(directory.absolutePath)
        assertEquals(setOf(directory.absolutePath), latestState.selectedFiles)
        assertEquals(42L, latestState.selectedFilesTotalSize)

        controller.selectAll(listOf(directory.absolutePath, child.absolutePath))
        assertEquals(49L, latestState.selectedFilesTotalSize)

        controller.invert(listOf(directory.absolutePath, child.absolutePath))
        assertTrue(latestState.selectedFiles.isEmpty())

        controller.selectMultiple(listOf(directory.absolutePath, child.absolutePath))
        controller.clear()
        assertTrue(latestState.selectedFiles.isEmpty())
        assertEquals(0L, latestState.selectedFilesTotalSize)
        assertEquals(5, changedCount)
    }

    @Test
    fun `volume roots reject selection commands`() {
        context = context.copy(isVolumeRootScreen = true)

        controller.toggle(directory.absolutePath)
        controller.selectAll(listOf(directory.absolutePath))
        controller.invert(listOf(directory.absolutePath))
        controller.selectMultiple(listOf(directory.absolutePath))

        assertTrue(latestState.selectedFiles.isEmpty())
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
