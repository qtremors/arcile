package dev.qtremors.arcile.feature.browser

import dev.qtremors.arcile.feature.browser.delegate.BrowserArchiveController
import dev.qtremors.arcile.feature.browser.delegate.BrowserConflictController
import dev.qtremors.arcile.feature.browser.delegate.BrowserNavigationController
import dev.qtremors.arcile.feature.browser.delegate.BrowserOperationController
import dev.qtremors.arcile.feature.browser.delegate.SearchController
import dev.qtremors.arcile.feature.browser.delegate.SelectionController
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BrowserCoordinatorTest {
    private lateinit var navigation: BrowserNavigationController
    private lateinit var search: SearchController
    private lateinit var selection: SelectionController
    private lateinit var archive: BrowserArchiveController
    private lateinit var conflicts: BrowserConflictController
    private lateinit var operation: BrowserOperationController
    private lateinit var coordinator: BrowserCoordinator
    private lateinit var searchState: MutableStateFlow<BrowserSearchState>
    private lateinit var selectionState: MutableStateFlow<BrowserSelectionState>

    @Before
    fun setUp() {
        navigation = mockk(relaxed = true)
        search = mockk(relaxed = true)
        selection = mockk(relaxed = true)
        archive = mockk(relaxed = true)
        conflicts = mockk(relaxed = true)
        operation = mockk(relaxed = true)
        searchState = MutableStateFlow(BrowserSearchState())
        selectionState = MutableStateFlow(BrowserSelectionState())
        every { search.state } returns searchState
        every { selection.state } returns selectionState
        coordinator = BrowserCoordinator(
            navigation,
            search,
            selection,
            archive,
            conflicts,
            operation
        )
    }

    @Test
    fun `back clears search before selection or navigation`() {
        searchState.value = BrowserSearchState(browserSearchQuery = "photo")
        selectionState.value = BrowserSelectionState(selectedFiles = persistentSetOf("/photo.jpg"))

        assertTrue(coordinator.navigateBack(allowVolumeRootFallback = true))

        verify(exactly = 1) { search.updateQuery("") }
        verify(exactly = 0) { selection.clear() }
        verify(exactly = 0) { navigation.navigateBack(any()) }
    }

    @Test
    fun `back clears selection before navigation`() {
        selectionState.value = BrowserSelectionState(selectedFiles = persistentSetOf("/photo.jpg"))

        assertTrue(coordinator.navigateBack(allowVolumeRootFallback = true))

        verify(exactly = 1) { selection.clear() }
        verify(exactly = 0) { navigation.navigateBack(any()) }
    }

    @Test
    fun `location changes clear transient state owned by other workflows`() {
        coordinator.onLocationChanged()

        verify(exactly = 1) { selection.clear() }
        verify(exactly = 1) { archive.dismissWorkflow() }
        verify(exactly = 1) { conflicts.dismiss() }
    }

    @Test
    fun `folder tab transition clears selection before navigation`() {
        coordinator.selectFolderTab("/Pictures")

        verify(exactly = 1) { selection.clear() }
        verify(exactly = 1) { navigation.selectFolderTab("/Pictures") }
    }

    @Test
    fun `mutation completion refreshes navigation`() {
        coordinator.refreshAfterMutation()

        verify(exactly = 1) { navigation.refresh() }
    }
}
