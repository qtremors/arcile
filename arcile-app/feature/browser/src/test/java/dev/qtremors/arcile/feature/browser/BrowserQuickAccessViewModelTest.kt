package dev.qtremors.arcile.feature.browser

import dev.qtremors.arcile.core.storage.domain.QuickAccessItem
import dev.qtremors.arcile.core.storage.domain.QuickAccessPreferencesStore
import dev.qtremors.arcile.core.storage.domain.QuickAccessType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BrowserQuickAccessViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `pinning browser folder adds enabled custom item`() = runTest(dispatcher) {
        val store = RecordingQuickAccessStore()
        val viewModel = BrowserQuickAccessViewModel(store)

        viewModel.addCustomFolder("/storage/Documents", "Documents")
        advanceUntilIdle()

        val item = store.added.single()
        assertTrue(item.id.startsWith("custom_"))
        assertEquals("Documents", item.label)
        assertEquals("/storage/Documents", item.path)
        assertEquals(QuickAccessType.CUSTOM, item.type)
        assertTrue(item.isPinned)
        assertTrue(item.isEnabled)
    }
}

private class RecordingQuickAccessStore : QuickAccessPreferencesStore {
    override val quickAccessItems: Flow<List<QuickAccessItem>> = MutableStateFlow(emptyList())
    val added = mutableListOf<QuickAccessItem>()

    override suspend fun updateItems(items: List<QuickAccessItem>) = Unit

    override suspend fun addItem(item: QuickAccessItem) {
        added += item
    }

    override suspend fun removeItem(id: String) = Unit
}
