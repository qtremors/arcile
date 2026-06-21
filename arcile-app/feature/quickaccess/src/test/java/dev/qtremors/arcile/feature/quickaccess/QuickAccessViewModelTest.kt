package dev.qtremors.arcile.feature.quickaccess

import dev.qtremors.arcile.core.storage.domain.QuickAccessItem
import dev.qtremors.arcile.core.storage.domain.QuickAccessPreferencesStore
import dev.qtremors.arcile.core.storage.domain.QuickAccessType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeQuickAccessPreferencesStore : QuickAccessPreferencesStore {
    val itemsFlow = MutableStateFlow<List<QuickAccessItem>>(emptyList())
    override val quickAccessItems: Flow<List<QuickAccessItem>> = itemsFlow

    var updatedItems: List<QuickAccessItem>? = null
    val addedItems = mutableListOf<QuickAccessItem>()
    val removedIds = mutableListOf<String>()

    override suspend fun updateItems(items: List<QuickAccessItem>) {
        updatedItems = items
        itemsFlow.value = items
    }

    override suspend fun addItem(item: QuickAccessItem) {
        addedItems.add(item)
        itemsFlow.value = itemsFlow.value + item
    }

    override suspend fun removeItem(id: String) {
        removedIds.add(id)
        itemsFlow.value = itemsFlow.value.filter { it.id != id }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class QuickAccessViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val fakeStore = FakeQuickAccessPreferencesStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state loads items from store`() = runTest(dispatcher) {
        val initialItems = listOf(
            QuickAccessItem(id = "1", label = "Downloads", path = "/downloads", type = QuickAccessType.STANDARD, isPinned = true, isEnabled = true)
        )
        fakeStore.itemsFlow.value = initialItems

        val viewModel = QuickAccessViewModel(fakeStore)
        advanceUntilIdle()

        assertEquals(initialItems, viewModel.state.value.items)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `togglePin updates item pinning status`() = runTest(dispatcher) {
        val item = QuickAccessItem(id = "1", label = "Downloads", path = "/downloads", type = QuickAccessType.STANDARD, isPinned = true, isEnabled = true)
        fakeStore.itemsFlow.value = listOf(item)

        val viewModel = QuickAccessViewModel(fakeStore)
        advanceUntilIdle()

        viewModel.togglePin(item)
        advanceUntilIdle()

        assertEquals(1, fakeStore.updatedItems?.size)
        assertFalse(fakeStore.updatedItems!![0].isPinned)
    }

    @Test
    fun `removeCustomItem removes non standard items but ignores standard items`() = runTest(dispatcher) {
        val standardItem = QuickAccessItem(id = "1", label = "Downloads", path = "/downloads", type = QuickAccessType.STANDARD, isPinned = true, isEnabled = true)
        val customItem = QuickAccessItem(id = "2", label = "Custom", path = "/custom", type = QuickAccessType.CUSTOM, isPinned = true, isEnabled = true)
        val safItem = QuickAccessItem(id = "3", label = "SAF", path = "content://tree", type = QuickAccessType.SAF_TREE, isPinned = true, isEnabled = true)
        val handoffItem = QuickAccessItem(id = "4", label = "Android/data", path = "content://data", type = QuickAccessType.EXTERNAL_HANDOFF, isPinned = true, isEnabled = true)
        val filesItem = QuickAccessItem(id = "5", label = "Files", path = "content://files", type = QuickAccessType.FILES_APP, isPinned = true, isEnabled = true)
        fakeStore.itemsFlow.value = listOf(standardItem, customItem, safItem, handoffItem, filesItem)

        val viewModel = QuickAccessViewModel(fakeStore)
        advanceUntilIdle()

        viewModel.removeCustomItem(standardItem)
        advanceUntilIdle()
        assertTrue(fakeStore.removedIds.isEmpty())

        viewModel.removeCustomItem(customItem)
        viewModel.removeCustomItem(safItem)
        viewModel.removeCustomItem(handoffItem)
        viewModel.removeCustomItem(filesItem)
        advanceUntilIdle()
        assertEquals(listOf("2", "3", "4", "5"), fakeStore.removedIds)
    }

    @Test
    fun `addCustomFolder adds custom folder item`() = runTest(dispatcher) {
        val viewModel = QuickAccessViewModel(fakeStore)
        advanceUntilIdle()

        viewModel.addCustomFolder("/custom/path", "My Custom")
        advanceUntilIdle()

        assertEquals(1, fakeStore.addedItems.size)
        val added = fakeStore.addedItems[0]
        assertEquals("My Custom", added.label)
        assertEquals("/custom/path", added.path)
        assertEquals(QuickAccessType.CUSTOM, added.type)
        assertTrue(added.id.startsWith("custom_"))
    }

    @Test
    fun `addFilesAppShortcut adds files app handoff item`() = runTest(dispatcher) {
        val viewModel = QuickAccessViewModel(fakeStore)
        advanceUntilIdle()

        viewModel.addFilesAppShortcut("content://files-root")
        advanceUntilIdle()

        val added = fakeStore.addedItems.single()
        assertEquals("Files", added.label)
        assertEquals("handoff_files_app", added.id)
        assertEquals("content://files-root", added.path)
        assertEquals(QuickAccessType.FILES_APP, added.type)
        assertTrue(added.isPinned)
    }

    @Test
    fun `updateItemsOrder reorders items and preserves unpinned items`() = runTest(dispatcher) {
        val item1 = QuickAccessItem(id = "1", label = "Downloads", path = "/downloads", type = QuickAccessType.STANDARD, isPinned = true, isEnabled = true)
        val item2 = QuickAccessItem(id = "2", label = "DCIM", path = "/dcim", type = QuickAccessType.STANDARD, isPinned = true, isEnabled = true)
        val item3 = QuickAccessItem(id = "3", label = "Pictures", path = "/pictures", type = QuickAccessType.STANDARD, isPinned = false, isEnabled = true)
        fakeStore.itemsFlow.value = listOf(item1, item2, item3)

        val viewModel = QuickAccessViewModel(fakeStore)
        advanceUntilIdle()

        viewModel.updateItemsOrder(listOf(item2, item1))
        advanceUntilIdle()

        val updated = fakeStore.updatedItems
        assertEquals(3, updated?.size)
        assertEquals("2", updated!![0].id)
        assertEquals("1", updated[1].id)
        assertEquals("3", updated[2].id)
    }
}
