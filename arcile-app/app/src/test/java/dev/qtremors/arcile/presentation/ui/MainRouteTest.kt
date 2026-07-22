package dev.qtremors.arcile.presentation.ui

import dev.qtremors.arcile.feature.browser.BrowserEntry
import dev.qtremors.arcile.navigation.AppRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class MainRouteTest {

    @Test
    fun `images and videos use media galleries while other categories use browser`() {
        assertTrue(isGalleryCategory("Images"))
        assertTrue(isGalleryCategory("Videos"))
        assertFalse(isGalleryCategory("Audio"))
    }

    @Test
    fun `fresh main entry starts on home`() {
        assertEquals(
            HOME_PAGE,
            resolveInitialMainPage(
                requestedPage = HOME_PAGE,
                savedPage = null,
                pendingBrowserReturn = false
            )
        )
    }

    @Test
    fun `configuration recreation retains the saved browser page`() {
        assertEquals(
            BROWSER_PAGE,
            resolveInitialMainPage(
                requestedPage = HOME_PAGE,
                savedPage = BROWSER_PAGE,
                pendingBrowserReturn = false
            )
        )
    }

    @Test
    fun `explicit browser entry overrides a saved home page`() {
        assertEquals(
            BROWSER_PAGE,
            resolveInitialMainPage(
                requestedPage = BROWSER_PAGE,
                savedPage = HOME_PAGE,
                pendingBrowserReturn = false
            )
        )
    }

    @Test
    fun `viewer return always restores the browser page`() {
        assertEquals(
            BROWSER_PAGE,
            resolveInitialMainPage(
                requestedPage = HOME_PAGE,
                savedPage = HOME_PAGE,
                pendingBrowserReturn = true
            )
        )
    }

    @Test
    fun `home route has no initial browser entry`() {
        assertNull(AppRoutes.Main().initialBrowserEntry(requestId = 1))
    }

    @Test
    fun `browser root entry preserves persistent restore policy`() {
        val request = AppRoutes.Main(
            initialPage = 1,
            restorePersistentLocation = false
        ).initialBrowserEntry(requestId = 2)

        assertEquals(2L, request?.id)
        assertEquals(BrowserEntry.Root(false), request?.entry)
    }

    @Test
    fun `browser path entry preserves history and focus`() {
        val request = AppRoutes.Main(
            initialPage = 1,
            path = "/storage/Documents",
            focusPath = "/storage/Documents/report.pdf",
            seedInitialPathHistory = false
        ).initialBrowserEntry(requestId = 3)

        assertEquals(
            BrowserEntry.Path("/storage/Documents", seedInitialPathHistory = false),
            request?.entry
        )
        assertEquals("/storage/Documents/report.pdf", request?.focusPath)
    }

    @Test
    fun `browser category entry preserves volume`() {
        val request = AppRoutes.Main(
            initialPage = 1,
            category = "Videos",
            volumeId = "primary"
        ).initialBrowserEntry(requestId = 4)

        assertEquals(BrowserEntry.Category("Videos", "primary"), request?.entry)
    }

    @Test
    fun `browser archive entry takes precedence over path and category`() {
        val request = AppRoutes.Main(
            initialPage = 1,
            path = "/storage",
            archivePath = "/storage/files.zip",
            category = "Documents"
        ).initialBrowserEntry(requestId = 5)

        assertEquals(BrowserEntry.Archive("/storage/files.zip"), request?.entry)
    }
}
