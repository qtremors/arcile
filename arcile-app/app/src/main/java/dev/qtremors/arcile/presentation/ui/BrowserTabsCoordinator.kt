package dev.qtremors.arcile.presentation.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavBackStackEntry
import dev.qtremors.arcile.feature.browser.BrowserEntry
import dev.qtremors.arcile.feature.browser.BrowserEntryRequest
import dev.qtremors.arcile.feature.browser.BrowserRouteStatus

private const val BROWSER_TABS_KEY = "browserTabs"
private const val ACTIVE_BROWSER_TAB_ID_KEY = "activeBrowserTabId"
internal const val MAX_BROWSER_TABS = 8
internal const val PRIMARY_BROWSER_TAB_ID = 1

@Stable
internal data class BrowserTab(
    val id: Int,
    val isPinned: Boolean = false
)

internal data class RestoredBrowserTab(
    val tab: BrowserTab,
    val entry: BrowserEntry
)

@Stable
internal class BrowserTabsCoordinator(
    val browserPage: Int,
    initialTabs: List<BrowserTab>,
    initialBrowserEntryRequests: Map<Int, BrowserEntryRequest>,
    initialActiveTabId: Int?,
    private val savedStateHandle: SavedStateHandle,
    private val restorePersistedTabsIntoSession: Boolean,
    persistentTabsInitiallyReady: Boolean,
    private val onPersistentTabsChanged: (Int, List<PersistedBrowserTab>) -> Unit
) {
    private var requestId by mutableLongStateOf(
        initialBrowserEntryRequests.values.maxOfOrNull(BrowserEntryRequest::id) ?: 0L
    )

    var tabs by mutableStateOf(initialTabs)
        private set
    var activeBrowserTabId by mutableIntStateOf(
        initialActiveTabId?.takeIf { candidate -> initialTabs.any { it.id == candidate } }
            ?: initialTabs.first().id
    )
        private set
    var browserEntryRequests by mutableStateOf(initialBrowserEntryRequests)
        private set
    var browserStatuses by mutableStateOf<Map<Int, BrowserRouteStatus>>(emptyMap())
        private set
    var pendingBrowserTabId: Int? = null
        private set
    private var persistentTabsReady = persistentTabsInitiallyReady

    init {
        persistTabs()
    }

    fun requestBrowser(entry: BrowserEntry, focusPath: String? = null) {
        val targetId = activeBrowserTabId.takeIf(::containsTab) ?: tabs.first().id
        requestId += 1
        browserEntryRequests = browserEntryRequests + (
            targetId to BrowserEntryRequest(requestId, entry, focusPath)
        )
        showTab(targetId)
    }

    fun addTab(entry: BrowserEntry = BrowserEntry.Root(restorePersistentLocation = false)): Boolean {
        if (tabs.size >= MAX_BROWSER_TABS) return false
        val newId = (1..MAX_BROWSER_TABS).first { candidate -> tabs.none { it.id == candidate } }
        val insertionIndex = browserTabInsertionIndex(tabs, activeBrowserTabId)
        requestId += 1
        tabs = tabs.toMutableList().apply { add(insertionIndex, BrowserTab(newId)) }
        browserEntryRequests = browserEntryRequests + (
            newId to BrowserEntryRequest(
                id = requestId,
                entry = entry,
                resetWorkspace = true
            )
        )
        onTabsChanged()
        showTab(newId)
        return true
    }

    fun duplicateTab(tabId: Int): Boolean {
        if (tabs.size >= MAX_BROWSER_TABS || !containsTab(tabId)) return false
        val entry = browserEntryForDuplicate(browserStatuses[tabId], browserEntryRequests[tabId])
        val newId = (1..MAX_BROWSER_TABS).first { candidate -> tabs.none { it.id == candidate } }
        val insertionIndex = browserTabInsertionIndex(tabs, tabId)
        requestId += 1
        tabs = tabs.toMutableList().apply { add(insertionIndex, BrowserTab(newId)) }
        browserEntryRequests = browserEntryRequests + (
            newId to BrowserEntryRequest(requestId, entry, resetWorkspace = true)
        )
        onTabsChanged()
        showTab(newId)
        return true
    }

    fun setTabPinned(tabId: Int, pinned: Boolean) {
        if (tabId == PRIMARY_BROWSER_TAB_ID || !containsTab(tabId)) return
        tabs = pinBrowserTab(tabs, tabId, pinned)
        onTabsChanged()
    }

    fun moveTab(tabId: Int, offset: Int): Boolean {
        val reordered = moveBrowserTab(tabs, tabId, offset)
        if (reordered == tabs) return false
        tabs = reordered
        onTabsChanged()
        return true
    }

    fun closeTab(tabId: Int): Boolean {
        val target = tabs.firstOrNull { it.id == tabId } ?: return false
        if (target.id == PRIMARY_BROWSER_TAB_ID || target.isPinned || tabs.size == 1) return false
        val targetIndex = tabs.indexOf(target)
        val remaining = tabs.filterNot { it.id == tabId }
        val fallback = remaining[targetIndex.coerceAtMost(remaining.lastIndex)]
        tabs = remaining
        browserEntryRequests = browserEntryRequests - tabId
        browserStatuses = browserStatuses - tabId
        onTabsChanged()
        showTab(if (activeBrowserTabId == tabId) fallback.id else activeBrowserTabId)
        return true
    }

    fun closeOtherTabs(tabId: Int): Boolean {
        if (!containsTab(tabId)) return false
        val remaining = browserTabsAfterClosingOthers(tabs, tabId)
        if (remaining == tabs) return false
        val remainingIds = remaining.mapTo(mutableSetOf(), BrowserTab::id)
        tabs = remaining
        browserEntryRequests = browserEntryRequests.filterKeys { it in remainingIds }
        browserStatuses = browserStatuses.filterKeys { it in remainingIds }
        onTabsChanged()
        showTab(activeBrowserTabId.takeIf { it in remainingIds } ?: tabId)
        return true
    }

    fun showTab(tabId: Int) {
        if (!containsTab(tabId)) return
        if (tabId != activeBrowserTabId && tabId !in browserStatuses) {
            pendingBrowserTabId = tabId
            return
        }
        activateTab(tabId)
    }

    private fun activateTab(tabId: Int) {
        pendingBrowserTabId = null
        activeBrowserTabId = tabId
        savedStateHandle[activeTabKey(browserPage)] = tabId
    }

    fun updateBrowserStatus(tabId: Int, status: BrowserRouteStatus) {
        if (containsTab(tabId)) {
            browserStatuses = browserStatuses + (tabId to status)
            if (status.isReady) {
                persistPinnedTabs()
            }
            if (pendingBrowserTabId == tabId) activateTab(tabId)
        }
    }

    fun restorePersistentTabs(persistedTabs: List<PersistedBrowserTab>) {
        if (persistentTabsReady) return
        persistentTabsReady = true
        val restoredRecords = restorePersistedBrowserTabs(persistedTabs, browserPage)
        val existingPinnedIds = tabs.asSequence()
            .filter(BrowserTab::isPinned)
            .mapTo(mutableSetOf(), BrowserTab::id)
        browserEntryRequests = browserEntryRequests + restoredRecords
            .filter { restored ->
                restored.tab.id in existingPinnedIds && restored.tab.id !in browserEntryRequests
            }
            .associate { restored ->
                restored.tab.id to BrowserEntryRequest(
                    id = 0L,
                    entry = restored.entry,
                    preferSavedLocation = true
                )
            }
        if (restorePersistedTabsIntoSession) {
            val restored = restoredRecords
                .filter { candidate -> tabs.none { it.id == candidate.tab.id } }
                .take((MAX_BROWSER_TABS - tabs.size).coerceAtLeast(0))
            if (restored.isNotEmpty()) {
                val primary = tabs.first { it.id == PRIMARY_BROWSER_TAB_ID }
                val existingOthers = tabs.filterNot { it.id == PRIMARY_BROWSER_TAB_ID }
                tabs = listOf(primary) +
                    existingOthers.filter(BrowserTab::isPinned) +
                    restored.map(RestoredBrowserTab::tab) +
                    existingOthers.filterNot(BrowserTab::isPinned)
                browserEntryRequests = browserEntryRequests + restored.associate { restoredTab ->
                    restoredTab.tab.id to BrowserEntryRequest(0L, restoredTab.entry)
                }
                persistTabs()
            }
        }
        persistPinnedTabs()
    }

    private fun onTabsChanged() {
        persistTabs()
        persistPinnedTabs()
    }

    private fun persistTabs() {
        savedStateHandle[tabsKey(browserPage)] = tabs.map { "${it.id}:${it.isPinned}" }.toTypedArray()
        savedStateHandle[activeTabKey(browserPage)] = activeBrowserTabId
    }

    private fun persistPinnedTabs() {
        if (!persistentTabsReady) return
        onPersistentTabsChanged(
            browserPage,
            tabs.asSequence()
                .filter { it.id != PRIMARY_BROWSER_TAB_ID && it.isPinned }
                .map { tab ->
                    persistedBrowserTab(
                        browserPage = browserPage,
                        id = tab.id,
                        entry = browserStatuses[tab.id]?.takeIf { it.isReady }?.entry
                            ?: browserEntryRequests[tab.id]?.entry
                            ?: BrowserEntry.Root(restorePersistentLocation = false)
                    )
                }
                .toList()
        )
    }

    private fun containsTab(tabId: Int): Boolean = tabs.any { it.id == tabId }
}

@Composable
internal fun rememberBrowserTabsCoordinator(
    browserPage: Int,
    backStackEntry: NavBackStackEntry,
    requestedEntry: BrowserEntryRequest?,
    persistedPinnedTabs: List<PersistedBrowserTab>?,
    onPersistentTabsChanged: (Int, List<PersistedBrowserTab>) -> Unit
): BrowserTabsCoordinator {
    val savedStateHandle = backStackEntry.savedStateHandle
    val savedTabs = savedStateHandle.get<Array<String>>(tabsKey(browserPage))
    val restoredPinnedTabs = remember(backStackEntry.id, browserPage) {
        restorePersistedBrowserTabs(persistedPinnedTabs.orEmpty(), browserPage)
    }
    val initialTabs = remember(backStackEntry.id, browserPage) {
        restoreBrowserTabs(savedTabs, restoredPinnedTabs.map(RestoredBrowserTab::tab))
    }
    val initialEntryRequests = remember(backStackEntry.id, browserPage) {
        initialBrowserEntryRequests(
            hasSavedTabs = savedTabs != null,
            initialTabs = initialTabs,
            restoredPinnedTabs = restoredPinnedTabs,
            requestedEntry = requestedEntry
        )
    }
    return remember(backStackEntry.id, browserPage) {
        BrowserTabsCoordinator(
            browserPage = browserPage,
            initialTabs = initialTabs,
            initialBrowserEntryRequests = initialEntryRequests,
            initialActiveTabId = savedStateHandle[activeTabKey(browserPage)],
            savedStateHandle = savedStateHandle,
            restorePersistedTabsIntoSession = savedTabs == null,
            persistentTabsInitiallyReady = persistedPinnedTabs != null,
            onPersistentTabsChanged = onPersistentTabsChanged
        )
    }
}

private fun tabsKey(browserPage: Int): String =
    if (browserPage == BROWSER_PAGE) BROWSER_TABS_KEY else "$BROWSER_TABS_KEY:$browserPage"

private fun activeTabKey(browserPage: Int): String =
    if (browserPage == BROWSER_PAGE) ACTIVE_BROWSER_TAB_ID_KEY else "$ACTIVE_BROWSER_TAB_ID_KEY:$browserPage"

internal fun initialBrowserEntryRequests(
    hasSavedTabs: Boolean,
    initialTabs: List<BrowserTab>,
    restoredPinnedTabs: List<RestoredBrowserTab>,
    requestedEntry: BrowserEntryRequest?
): Map<Int, BrowserEntryRequest> = buildMap {
    put(
        PRIMARY_BROWSER_TAB_ID,
        BrowserEntryRequest(0L, BrowserEntry.PrimaryStorage, preferSavedLocation = hasSavedTabs)
    )
    restoredPinnedTabs.forEach { restored ->
        if (initialTabs.any { tab -> tab.id == restored.tab.id && tab.isPinned }) {
            put(
                restored.tab.id,
                BrowserEntryRequest(0L, restored.entry, preferSavedLocation = hasSavedTabs)
            )
        }
    }
    requestedEntry?.let { request ->
        put(
            PRIMARY_BROWSER_TAB_ID,
            if (request.entry is BrowserEntry.Root) {
                request.copy(entry = BrowserEntry.PrimaryStorage)
            } else {
                request
            }
        )
    }
}

internal fun restoreBrowserTabs(
    saved: Array<String>?,
    restoredPinnedTabs: List<BrowserTab> = emptyList()
): List<BrowserTab> {
    val restored = saved.orEmpty()
        .mapNotNull { value ->
            val id = value.substringBefore(':').toIntOrNull() ?: return@mapNotNull null
            if (id !in 1..MAX_BROWSER_TABS) return@mapNotNull null
            BrowserTab(id, value.substringAfter(':', "false").toBooleanStrictOrNull() ?: false)
        }
        .distinctBy(BrowserTab::id)
        .take(MAX_BROWSER_TABS)
    val source = restored.takeIf(List<BrowserTab>::isNotEmpty)
        ?: (listOf(BrowserTab(PRIMARY_BROWSER_TAB_ID)) + restoredPinnedTabs)
    val primary = BrowserTab(PRIMARY_BROWSER_TAB_ID)
    val others = source.filterNot { it.id == PRIMARY_BROWSER_TAB_ID }
    return (listOf(primary) + others.filter(BrowserTab::isPinned) + others.filterNot(BrowserTab::isPinned))
        .take(MAX_BROWSER_TABS)
}

internal fun restorePersistedBrowserTabs(
    tabs: List<PersistedBrowserTab>,
    browserPage: Int = BROWSER_PAGE
): List<RestoredBrowserTab> = tabs.asSequence()
    .filter { it.browserPage == browserPage }
    .filter { it.id in (PRIMARY_BROWSER_TAB_ID + 1)..MAX_BROWSER_TABS }
    .distinctBy(PersistedBrowserTab::id)
    .mapNotNull { persisted ->
        persisted.browserEntry()?.let { entry ->
            RestoredBrowserTab(BrowserTab(persisted.id, isPinned = true), entry)
        }
    }
    .take(MAX_BROWSER_TABS - 1)
    .toList()

private fun persistedBrowserTab(
    browserPage: Int,
    id: Int,
    entry: BrowserEntry
): PersistedBrowserTab = when (entry) {
    BrowserEntry.PrimaryStorage -> PersistedBrowserTab(id, "primary", browserPage = browserPage)
    is BrowserEntry.Root -> PersistedBrowserTab(id, "root", browserPage = browserPage)
    is BrowserEntry.Path -> PersistedBrowserTab(
        id = id,
        entryType = "path",
        path = entry.path,
        isRootStorageScope = entry.isRootStorageScope,
        browserPage = browserPage
    )
    is BrowserEntry.Category -> PersistedBrowserTab(
        id = id,
        entryType = "category",
        name = entry.name,
        volumeId = entry.volumeId,
        browserPage = browserPage
    )
    is BrowserEntry.Archive -> PersistedBrowserTab(
        id = id,
        entryType = "archive",
        path = entry.path,
        entryPrefix = entry.entryPrefix,
        isRootStorageScope = entry.isRootStorageScope,
        browserPage = browserPage
    )
}

private fun PersistedBrowserTab.browserEntry(): BrowserEntry? = when (entryType) {
    "primary" -> BrowserEntry.PrimaryStorage
    "root" -> BrowserEntry.Root(restorePersistentLocation = false)
    "path" -> path?.takeIf(String::isNotBlank)?.let {
        BrowserEntry.Path(
            path = it,
            seedInitialPathHistory = false,
            isRootStorageScope = isRootStorageScope
        )
    }
    "category" -> name?.takeIf(String::isNotBlank)?.let { BrowserEntry.Category(it, volumeId) }
    "archive" -> path?.takeIf(String::isNotBlank)?.let {
        BrowserEntry.Archive(
            path = it,
            entryPrefix = entryPrefix,
            isRootStorageScope = isRootStorageScope
        )
    }
    else -> null
}

internal fun pinBrowserTab(tabs: List<BrowserTab>, tabId: Int, pinned: Boolean): List<BrowserTab> {
    if (tabId == PRIMARY_BROWSER_TAB_ID) return tabs
    val updated = tabs.map { tab -> if (tab.id == tabId) tab.copy(isPinned = pinned) else tab }
    val primary = updated.firstOrNull { it.id == PRIMARY_BROWSER_TAB_ID }
        ?: BrowserTab(PRIMARY_BROWSER_TAB_ID)
    val others = updated.filterNot { it.id == PRIMARY_BROWSER_TAB_ID }
    return listOf(primary) + others.filter(BrowserTab::isPinned) + others.filterNot(BrowserTab::isPinned)
}

internal fun browserTabInsertionIndex(tabs: List<BrowserTab>, anchorTabId: Int): Int {
    val anchorIndex = tabs.indexOfFirst { it.id == anchorTabId }
    if (anchorIndex > 0 && !tabs[anchorIndex].isPinned) return anchorIndex + 1
    return tabs.indexOfFirst { it.id != PRIMARY_BROWSER_TAB_ID && !it.isPinned }
        .takeIf { it >= 0 }
        ?: tabs.size
}

internal fun moveBrowserTab(
    tabs: List<BrowserTab>,
    tabId: Int,
    offset: Int
): List<BrowserTab> {
    if ((offset != -1 && offset != 1) || tabId == PRIMARY_BROWSER_TAB_ID) return tabs
    val sourceIndex = tabs.indexOfFirst { it.id == tabId }
    val targetIndex = sourceIndex + offset
    if (sourceIndex <= 0 || targetIndex !in tabs.indices) return tabs
    if (tabs[sourceIndex].isPinned != tabs[targetIndex].isPinned) return tabs
    return tabs.toMutableList().apply {
        val moved = removeAt(sourceIndex)
        add(targetIndex, moved)
    }
}

internal fun canMoveBrowserTab(tabs: List<BrowserTab>, tabId: Int, offset: Int): Boolean =
    moveBrowserTab(tabs, tabId, offset) != tabs

internal fun browserTabsAfterClosingOthers(tabs: List<BrowserTab>, tabId: Int): List<BrowserTab> =
    tabs.filter { tab -> tab.id == PRIMARY_BROWSER_TAB_ID || tab.id == tabId || tab.isPinned }

internal fun browserEntryForDuplicate(
    status: BrowserRouteStatus?,
    request: BrowserEntryRequest?
): BrowserEntry = status?.entry
    ?: request?.entry
    ?: BrowserEntry.Root(restorePersistentLocation = false)

internal fun browserViewModelKey(browserPage: Int, tabId: Int): String = when (browserPage) {
    BROWSER_PAGE -> if (tabId == PRIMARY_BROWSER_TAB_ID) {
        "main-browser-primary"
    } else {
        "main-browser-primary-$tabId"
    }
    SECONDARY_BROWSER_PAGE -> if (tabId == PRIMARY_BROWSER_TAB_ID) {
        "main-browser-secondary"
    } else {
        "main-browser-secondary-$tabId"
    }
    else -> "main-browser-$browserPage-$tabId"
}
