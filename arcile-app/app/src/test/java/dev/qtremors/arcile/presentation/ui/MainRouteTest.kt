package dev.qtremors.arcile.presentation.ui

import androidx.lifecycle.SavedStateHandle
import dev.qtremors.arcile.AppLaunchMode
import dev.qtremors.arcile.core.storage.domain.AppStartPage
import dev.qtremors.arcile.feature.browser.BrowserEntry
import dev.qtremors.arcile.feature.browser.BrowserEntryRequest
import dev.qtremors.arcile.feature.browser.BrowserRouteStatus
import dev.qtremors.arcile.navigation.AppRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class MainRouteTest {

    @Test
    fun `cold launch applies the stored browser start page once`() {
        assertEquals(
            BROWSER_PAGE,
            resolveColdLaunchPage(
                mode = AppLaunchMode.ColdLauncher,
                isColdLaunchResetting = true,
                appStartPage = AppStartPage.BROWSER
            )
        )
        assertNull(
            resolveColdLaunchPage(
                mode = AppLaunchMode.ColdLauncher,
                isColdLaunchResetting = false,
                appStartPage = AppStartPage.HOME
            )
        )
    }

    @Test
    fun `images and videos use media galleries while other categories use browser`() {
        assertTrue(isGalleryCategory("Images"))
        assertTrue(isGalleryCategory("Videos"))
        assertFalse(isGalleryCategory("Docs"))
        assertFalse(isGalleryCategory("APKs"))
        assertFalse(isGalleryCategory("Audio"))
        assertTrue(isDocumentCategory("Docs"))
        assertTrue(isApkCategory("APKs"))
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
    fun `viewer return restores the secondary browser page`() {
        assertEquals(
            SECONDARY_BROWSER_PAGE,
            resolveInitialMainPage(
                requestedPage = HOME_PAGE,
                savedPage = SECONDARY_BROWSER_PAGE,
                pendingBrowserReturn = true
            )
        )
    }

    @Test
    fun `configuration recreation retains the secondary browser page`() {
        assertEquals(
            SECONDARY_BROWSER_PAGE,
            resolveInitialMainPage(
                requestedPage = HOME_PAGE,
                savedPage = SECONDARY_BROWSER_PAGE,
                pendingBrowserReturn = false
            )
        )
    }

    @Test
    fun `landscape dual pane combines both browser pages into one pager destination`() {
        assertEquals(3, mainPageCount(dualPaneEnabled = false))
        assertEquals(2, mainPageCount(dualPaneEnabled = true))
        assertEquals(
            BROWSER_PAGE,
            resolveInitialMainPage(
                requestedPage = HOME_PAGE,
                savedPage = SECONDARY_BROWSER_PAGE,
                pendingBrowserReturn = false,
                dualPaneEnabled = true
            )
        )
    }

    @Test
    fun `restored tab metadata keeps valid unique workspaces and pins`() {
        assertEquals(
            listOf(BrowserTab(1), BrowserTab(2, isPinned = true)),
            restoreBrowserTabs(arrayOf("2:true", "1:false", "2:false", "99:true", "broken"))
        )
    }

    @Test
    fun `missing tab metadata starts with internal storage only`() {
        assertEquals(
            listOf(BrowserTab(1)),
            restoreBrowserTabs(null)
        )
    }

    @Test
    fun `cold launch restores pinned tabs after internal storage`() {
        assertEquals(
            listOf(BrowserTab(1), BrowserTab(3, isPinned = true)),
            restoreBrowserTabs(
                saved = null,
                restoredPinnedTabs = listOf(BrowserTab(3, isPinned = true))
            )
        )
    }

    @Test
    fun `pinning moves tabs into the protected leading group`() {
        assertEquals(
            listOf(BrowserTab(1), BrowserTab(2, true), BrowserTab(3, true)),
            pinBrowserTab(
                tabs = listOf(BrowserTab(2, true), BrowserTab(1), BrowserTab(3)),
                tabId = 3,
                pinned = true
            )
        )
    }

    @Test
    fun `close others preserves every pinned tab`() {
        assertEquals(
            listOf(BrowserTab(1), BrowserTab(2, true), BrowserTab(3)),
            browserTabsAfterClosingOthers(
                tabs = listOf(BrowserTab(1), BrowserTab(2, true), BrowserTab(3), BrowserTab(4)),
                tabId = 3
            )
        )
    }

    @Test
    fun `internal storage cannot be reordered or pinned`() {
        val tabs = listOf(BrowserTab(1), BrowserTab(2))
        assertEquals(tabs, pinBrowserTab(tabs, PRIMARY_BROWSER_TAB_ID, pinned = true))
    }

    @Test
    fun `new tabs stay after pinned tabs and beside an unpinned source`() {
        val tabs = listOf(BrowserTab(1), BrowserTab(3, isPinned = true), BrowserTab(2))
        assertEquals(2, browserTabInsertionIndex(tabs, PRIMARY_BROWSER_TAB_ID))
        assertEquals(2, browserTabInsertionIndex(tabs, 3))
        assertEquals(3, browserTabInsertionIndex(tabs, 2))
    }

    @Test
    fun `tab ordering moves within the matching pinned group`() {
        val tabs = listOf(
            BrowserTab(1),
            BrowserTab(2, isPinned = true),
            BrowserTab(3, isPinned = true),
            BrowserTab(4),
            BrowserTab(5)
        )

        assertEquals(
            listOf(
                BrowserTab(1),
                BrowserTab(3, isPinned = true),
                BrowserTab(2, isPinned = true),
                BrowserTab(4),
                BrowserTab(5)
            ),
            moveBrowserTab(tabs, tabId = 3, offset = -1)
        )
        assertEquals(
            listOf(
                BrowserTab(1),
                BrowserTab(2, isPinned = true),
                BrowserTab(3, isPinned = true),
                BrowserTab(5),
                BrowserTab(4)
            ),
            moveBrowserTab(tabs, tabId = 4, offset = 1)
        )
    }

    @Test
    fun `tab ordering protects primary and pinned group boundaries`() {
        val tabs = listOf(BrowserTab(1), BrowserTab(2, true), BrowserTab(3), BrowserTab(4))

        assertEquals(tabs, moveBrowserTab(tabs, PRIMARY_BROWSER_TAB_ID, offset = 1))
        assertEquals(tabs, moveBrowserTab(tabs, tabId = 2, offset = -1))
        assertEquals(tabs, moveBrowserTab(tabs, tabId = 3, offset = -1))
        assertFalse(canMoveBrowserTab(tabs, tabId = 2, offset = -1))
        assertTrue(canMoveBrowserTab(tabs, tabId = 3, offset = 1))
    }

    @Test
    fun `persisted pinned paths restore their location and reject invalid records`() {
        assertEquals(
            listOf(
                RestoredBrowserTab(
                    tab = BrowserTab(3, isPinned = true),
                    entry = BrowserEntry.Path("/storage/emulated/0/Documents", seedInitialPathHistory = false)
                )
            ),
            restorePersistedBrowserTabs(
                listOf(
                    PersistedBrowserTab(3, entryType = "path", path = "/storage/emulated/0/Documents"),
                    PersistedBrowserTab(1, entryType = "path", path = "/ignored"),
                    PersistedBrowserTab(4, entryType = "unknown")
                )
            )
        )
        assertTrue(decodePersistedBrowserTabs("not-json").isEmpty())
    }

    @Test
    fun `pinned tabs restore only into their owning browser page`() {
        val primary = PersistedBrowserTab(
            id = 2,
            entryType = "path",
            path = "/primary",
            browserPage = BROWSER_PAGE
        )
        val secondary = PersistedBrowserTab(
            id = 2,
            entryType = "path",
            path = "/secondary",
            browserPage = SECONDARY_BROWSER_PAGE
        )

        assertEquals(
            BrowserEntry.Path("/primary", seedInitialPathHistory = false),
            restorePersistedBrowserTabs(listOf(primary, secondary), BROWSER_PAGE).single().entry
        )
        assertEquals(
            BrowserEntry.Path("/secondary", seedInitialPathHistory = false),
            restorePersistedBrowserTabs(listOf(primary, secondary), SECONDARY_BROWSER_PAGE).single().entry
        )
    }

    @Test
    fun `persisting one browser page preserves the other page records`() {
        val secondary = PersistedBrowserTab(
            2,
            "path",
            path = "/secondary",
            browserPage = SECONDARY_BROWSER_PAGE
        )
        val primaryReplacement = PersistedBrowserTab(
            3,
            "path",
            path = "/primary",
            browserPage = BROWSER_PAGE
        )

        assertEquals(
            listOf(secondary, primaryReplacement),
            mergePersistedBrowserTabs(
                existing = listOf(PersistedBrowserTab(2, "root"), secondary),
                browserPage = BROWSER_PAGE,
                replacement = listOf(primaryReplacement)
            )
        )
    }

    @Test
    fun `legacy pinned tab records default to the primary browser page`() {
        val restored = decodePersistedBrowserTabs("""[{"id":2,"entryType":"root"}]""")

        assertEquals(BROWSER_PAGE, restored.single().browserPage)
        assertTrue(restorePersistedBrowserTabs(restored, SECONDARY_BROWSER_PAGE).isEmpty())
    }

    @Test
    fun `browser pages own independent tab collections and active tabs`() {
        val savedStateHandle = SavedStateHandle()
        val primary = testTabsCoordinator(BROWSER_PAGE, savedStateHandle)
        val secondary = testTabsCoordinator(SECONDARY_BROWSER_PAGE, savedStateHandle)

        assertTrue(primary.addTab())

        assertEquals(listOf(BrowserTab(1), BrowserTab(2)), primary.tabs)
        assertEquals(1, primary.activeBrowserTabId)
        assertEquals(2, primary.pendingBrowserTabId)
        primary.updateBrowserStatus(2, BrowserRouteStatus())
        assertEquals(2, primary.activeBrowserTabId)
        assertNull(primary.pendingBrowserTabId)
        assertEquals(listOf(BrowserTab(1)), secondary.tabs)
        assertEquals(1, secondary.activeBrowserTabId)
    }

    @Test
    fun `workspace swipes move through tabs and report pane boundaries`() {
        val coordinator = testTabsCoordinator(BROWSER_PAGE)
        coordinator.updateBrowserStatus(1, BrowserRouteStatus())
        assertTrue(coordinator.addTab())
        coordinator.updateBrowserStatus(2, BrowserRouteStatus())

        assertEquals(2, coordinator.activeBrowserTabId)
        assertTrue(coordinator.showAdjacentTab(direction = -1))
        assertEquals(1, coordinator.activeBrowserTabId)
        assertFalse(coordinator.showAdjacentTab(direction = -1))
        assertTrue(coordinator.showAdjacentTab(direction = 1))
        assertEquals(2, coordinator.activeBrowserTabId)
        assertFalse(coordinator.showAdjacentTab(direction = 1))
    }

    @Test
    fun `addTab with explicit entry targets that location`() {
        val coordinator = testTabsCoordinator(BROWSER_PAGE)
        val rootEntry = BrowserEntry.Path("/", isRootStorageScope = true)
        assertTrue(coordinator.addTab(entry = rootEntry))

        assertEquals(listOf(BrowserTab(1), BrowserTab(2)), coordinator.tabs)
        assertEquals(rootEntry, coordinator.browserEntryRequests[2]?.entry)
    }

    @Test
    fun `failed new tab activates its retry surface without becoming a ready location`() {
        val coordinator = testTabsCoordinator(BROWSER_PAGE)
        coordinator.updateBrowserStatus(1, BrowserRouteStatus())
        assertTrue(coordinator.addTab())

        coordinator.updateBrowserStatus(2, BrowserRouteStatus(isReady = false))

        assertEquals(2, coordinator.activeBrowserTabId)
        assertFalse(coordinator.browserStatuses.getValue(2).isReady)
        coordinator.showTab(1)
        coordinator.showTab(2)
        assertEquals(2, coordinator.activeBrowserTabId)
        assertNull(coordinator.pendingBrowserTabId)
    }

    @Test
    fun `returning to the current tab cancels pending automatic activation`() {
        val coordinator = testTabsCoordinator(BROWSER_PAGE)
        assertTrue(coordinator.addTab())

        coordinator.showTab(1)
        coordinator.updateBrowserStatus(2, BrowserRouteStatus())

        assertEquals(1, coordinator.activeBrowserTabId)
        assertNull(coordinator.pendingBrowserTabId)
    }

    @Test
    fun `process recreation keeps saved-state-first fallbacks for primary and pinned tabs`() {
        val pinnedEntry = BrowserEntry.Path(
            "/storage/emulated/0/Documents",
            seedInitialPathHistory = false
        )
        val requests = initialBrowserEntryRequests(
            hasSavedTabs = true,
            initialTabs = listOf(BrowserTab(1), BrowserTab(3, isPinned = true), BrowserTab(2)),
            restoredPinnedTabs = listOf(
                RestoredBrowserTab(BrowserTab(3, isPinned = true), pinnedEntry)
            ),
            requestedEntry = null
        )

        assertEquals(BrowserEntry.Root(true), requests[1]?.entry)
        assertTrue(requests[1]?.preferSavedLocation == true)
        assertEquals(pinnedEntry, requests[3]?.entry)
        assertTrue(requests[3]?.preferSavedLocation == true)
        assertFalse(requests.containsKey(2))
    }

    @Test
    fun `duplicate falls back to a restored entry before root`() {
        val restoredEntry = BrowserEntry.Path("/storage/emulated/0/Pictures")

        assertEquals(
            restoredEntry,
            browserEntryForDuplicate(
                status = null,
                request = BrowserEntryRequest(id = 0L, entry = restoredEntry)
            )
        )
        assertEquals(
            BrowserEntry.Category("Images", "primary"),
            browserEntryForDuplicate(
                status = BrowserRouteStatus(entry = BrowserEntry.Category("Images", "primary")),
                request = BrowserEntryRequest(id = 0L, entry = restoredEntry)
            )
        )
    }

    @Test
    fun `tab titles use their original location names instead of numbers`() {
        assertEquals(
            "Documents",
            browserEntryTabTitle(
                BrowserEntry.Path("/storage/emulated/0/Documents"),
                fallback = "Browse"
            )
        )
        assertEquals(
            "Images",
            browserEntryTabTitle(BrowserEntry.Category("Images", "primary"), fallback = "Browse")
        )
        assertEquals(
            "backup.zip",
            browserEntryTabTitle(
                BrowserEntry.Archive("/storage/emulated/0/Download/backup.zip"),
                fallback = "Browse"
            )
        )
        assertEquals(
            "Browse",
            browserEntryTabTitle(
                BrowserEntry.Root(restorePersistentLocation = false),
                fallback = "Browse"
            )
        )
        assertEquals(
            "Root Storage",
            browserEntryTabTitle(
                BrowserEntry.Path(
                    path = "/",
                    seedInitialPathHistory = false,
                    isRootStorageScope = true
                ),
                fallback = "Browse",
                rootStorageTitle = "Root Storage"
            )
        )
    }

    @Test
    fun `pinned root storage tab restores its scope and title`() {
        val restored = restorePersistedBrowserTabs(
            tabs = listOf(
                PersistedBrowserTab(
                    id = 3,
                    entryType = "path",
                    path = "/",
                    isRootStorageScope = true
                )
            ),
            browserPage = BROWSER_PAGE
        ).single()

        assertEquals(
            BrowserEntry.Path(
                path = "/",
                seedInitialPathHistory = false,
                isRootStorageScope = true
            ),
            restored.entry
        )
        assertEquals(
            "Root Storage",
            browserEntryTabTitle(restored.entry, "Browse", "Root Storage")
        )
    }

    @Test
    fun `pinned root archive tab restores scope`() {
        val restored = restorePersistedBrowserTabs(
            tabs = listOf(
                PersistedBrowserTab(
                    id = 4,
                    entryType = "archive",
                    path = "/data/local/archive.zip",
                    isRootStorageScope = true
                )
            ),
            browserPage = BROWSER_PAGE
        ).single()

        assertEquals(
            BrowserEntry.Archive(
                path = "/data/local/archive.zip",
                isRootStorageScope = true
            ),
            restored.entry
        )
    }

    @Test
    fun `invalid saved page falls back to the primary browser on viewer return`() {
        assertEquals(
            BROWSER_PAGE,
            resolveInitialMainPage(
                requestedPage = HOME_PAGE,
                savedPage = 8,
                pendingBrowserReturn = true
            )
        )
    }

    @Test
    fun `workspace keys isolate tabs with matching ids across browser pages`() {
        assertEquals("main-browser-primary", browserViewModelKey(BROWSER_PAGE, 1))
        assertEquals("main-browser-primary-2", browserViewModelKey(BROWSER_PAGE, 2))
        assertEquals("main-browser-secondary", browserViewModelKey(SECONDARY_BROWSER_PAGE, 1))
        assertEquals("main-browser-secondary-2", browserViewModelKey(SECONDARY_BROWSER_PAGE, 2))
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

    @Test
    fun `fresh swipe workspace restores last location while explicit roots retain their policy`() {
        fun initial(entry: BrowserEntryRequest? = null) = initialBrowserEntryRequests(
            hasSavedTabs = false,
            initialTabs = listOf(BrowserTab(1)),
            restoredPinnedTabs = emptyList(),
            requestedEntry = entry
        )[1]?.entry

        assertEquals(BrowserEntry.Root(true), initial())
        assertEquals(BrowserEntry.Root(true), initial(BrowserEntryRequest(0L, BrowserEntry.Root(true))))
        assertEquals(BrowserEntry.Root(false), initial(BrowserEntryRequest(0L, BrowserEntry.Root(false))))
        val path = BrowserEntry.Path("/storage/emulated/0", seedInitialPathHistory = false)
        assertEquals(path, initial(BrowserEntryRequest(0L, path)))
    }

    private fun testTabsCoordinator(
        browserPage: Int,
        savedStateHandle: SavedStateHandle = SavedStateHandle()
    ) = BrowserTabsCoordinator(
        browserPage = browserPage,
        initialTabs = listOf(BrowserTab(1)),
        initialBrowserEntryRequests = initialBrowserEntryRequests(
            hasSavedTabs = false,
            initialTabs = listOf(BrowserTab(1)),
            restoredPinnedTabs = emptyList(),
            requestedEntry = null
        ),
        initialActiveTabId = null,
        savedStateHandle = savedStateHandle,
        restorePersistedTabsIntoSession = true,
        persistentTabsInitiallyReady = true,
        onPersistentTabsChanged = { _, _ -> }
    )
}
