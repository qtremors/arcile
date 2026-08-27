package dev.qtremors.arcile.feature.browser

import dev.qtremors.arcile.core.storage.domain.BrowserLocationPreferences
import dev.qtremors.arcile.core.storage.domain.FileListingPreferences
import dev.qtremors.arcile.core.storage.domain.FileSortOption
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserNavigationPreferencesTest {
    private val path = "/storage/emulated/0/Pictures"
    private val presentation = FileListingPreferences(
        sortOption = FileSortOption.DATE_NEWEST,
        foldersFirst = false
    )

    @Test
    fun `direct recursive preference restores folder-tree scope`() {
        val state = directoryState(path).applyNavigationPreferences(
            BrowserLocationPreferences(
                pathPresentationOptions = mapOf(path to presentation)
            )
        )

        assertTrue(state.browserPresentationAppliesToSubfolders)
        assertFalse(state.browserFoldersFirst)
    }

    @Test
    fun `exact and inherited preferences restore current-folder scope`() {
        val exact = directoryState(path).applyNavigationPreferences(
            BrowserLocationPreferences(
                exactPathPresentationOptions = mapOf(path to presentation)
            )
        )
        val inherited = directoryState("$path/Trips").applyNavigationPreferences(
            BrowserLocationPreferences(
                pathPresentationOptions = mapOf(path to presentation)
            )
        )

        assertFalse(exact.browserPresentationAppliesToSubfolders)
        assertFalse(inherited.browserPresentationAppliesToSubfolders)
    }

    private fun directoryState(currentPath: String) = BrowserNavigationState(
        location = BrowserLocationState(currentPath = currentPath)
    )
}
