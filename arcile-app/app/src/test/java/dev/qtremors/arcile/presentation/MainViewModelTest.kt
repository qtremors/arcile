package dev.qtremors.arcile.presentation

import dev.qtremors.arcile.core.storage.domain.AppStartPage
import dev.qtremors.arcile.core.storage.domain.FileOpenBehavior
import dev.qtremors.arcile.testutil.FakeFilePreferencesStore
import dev.qtremors.arcile.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `initial permission state is false`() = runTest {
        val store = FakeFilePreferencesStore()
        val viewModel = MainViewModel(store)

        assertFalse(viewModel.hasPermission.value)
    }

    @Test
    fun `updatePermission updates permission state`() = runTest {
        val store = FakeFilePreferencesStore()
        val viewModel = MainViewModel(store)

        viewModel.updatePermission(true)
        assertTrue(viewModel.hasPermission.value)

        viewModel.updatePermission(false)
        assertFalse(viewModel.hasPermission.value)
    }

    @Test
    fun `file open behaviors are loaded from preferences store`() = runTest {
        val store = FakeFilePreferencesStore()
        store.updateFileOpenBehavior("pdf", FileOpenBehavior.EXTERNAL)
        val viewModel = MainViewModel(store)

        val behaviors = viewModel.fileOpenBehaviors.first { it["pdf"] == FileOpenBehavior.EXTERNAL }

        assertEquals(FileOpenBehavior.EXTERNAL, behaviors["pdf"])
    }

    @Test
    fun `updateAppStartPage persists start page selection`() = runTest {
        val store = FakeFilePreferencesStore()
        val viewModel = MainViewModel(store)

        viewModel.updateAppStartPage(AppStartPage.BROWSER)

        val preferences = store.locationPreferencesFlow.first {
            it.appStartPage == AppStartPage.BROWSER
        }

        assertEquals(AppStartPage.BROWSER, preferences.appStartPage)
    }
}
