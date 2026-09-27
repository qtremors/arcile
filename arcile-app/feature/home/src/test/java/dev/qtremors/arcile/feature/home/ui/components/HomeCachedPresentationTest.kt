package dev.qtremors.arcile.feature.home.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import dev.qtremors.arcile.core.storage.domain.CategoryStorage
import dev.qtremors.arcile.core.storage.domain.StorageInfo
import dev.qtremors.arcile.feature.home.HomeState
import dev.qtremors.arcile.testutil.testVolume
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeCachedPresentationTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `capacity without breakdown keeps the multicolor animation visible`() {
        val categories = mutableStateOf<List<CategoryStorage>>(emptyList())
        compose.setContent {
            MaterialTheme { MultiColorStorageBar(1000L, 500L, categories.value) }
        }
        compose.onNodeWithTag("storage_bar_multicolor_loading", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("storage_bar_segments", useUnmergedTree = true).assertDoesNotExist()
        compose.runOnIdle { categories.value = listOf(CategoryStorage("Images", 200L, emptySet())) }
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithTag("storage_bar_segments", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("storage_bar_multicolor_loading", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `missing breakdown uses multicolor loading until saved categories arrive`() {
        val categories = mutableStateOf<List<CategoryStorage>>(emptyList())
        compose.setContent {
            MaterialTheme { MultiColorStorageBar(1000L, 500L, categories.value) }
        }
        compose.onNodeWithTag("storage_bar_multicolor_loading", useUnmergedTree = true).assertExists()
        compose.runOnIdle { categories.value = listOf(CategoryStorage("Images", 200L, emptySet())) }
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithTag("storage_bar_segments", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("storage_bar_multicolor_loading", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `single volume uses its saved breakdown when global totals are unavailable`() {
        val volume = testVolume("primary", "/storage/emulated/0", totalBytes = 1000L, freeBytes = 500L)
        val saved = persistentListOf(CategoryStorage("Images", 200L, emptySet()))
        val state = HomeState(
            storageInfo = StorageInfo(listOf(volume)),
            allStorageVolumes = persistentListOf(volume),
            categoryStoragesByVolume = persistentMapOf(volume.id to saved)
        )
        compose.setContent { MaterialTheme { StorageSummaryCard(state, {}, {}, {}) } }
        compose.onNodeWithTag("storage_bar_segments", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("storage_bar_multicolor_loading", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `category shortcuts follow size rank and move only when rank changes`() {
        val categories = mutableStateOf<List<CategoryStorage>>(listOf(
            CategoryStorage("Images", 900L, emptySet()),
            CategoryStorage("Videos", 800L, emptySet())
        ))
        compose.setContent { MaterialTheme { CategoryGrid(categories.value) {} } }
        val images = compose.onNodeWithText("Images").fetchSemanticsNode().boundsInRoot
        val videos = compose.onNodeWithText("Videos").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {
            categories.value = listOf(CategoryStorage("Videos", 700L, emptySet()), CategoryStorage("Images", 1000L, emptySet()))
        }
        compose.mainClock.advanceTimeBy(1000)
        assertEquals(images, compose.onNodeWithText("Images").fetchSemanticsNode().boundsInRoot)
        assertEquals(videos, compose.onNodeWithText("Videos").fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle {
            categories.value = listOf(CategoryStorage("Images", 10L, emptySet()), CategoryStorage("Videos", 1100L, emptySet()))
        }
        compose.mainClock.advanceTimeBy(2000)
        assertNotEquals(images, compose.onNodeWithText("Images").fetchSemanticsNode().boundsInRoot)
        assertNotEquals(videos, compose.onNodeWithText("Videos").fetchSemanticsNode().boundsInRoot)
        assertEquals(images.left, compose.onNodeWithText("Videos").fetchSemanticsNode().boundsInRoot.left, 1f)
    }

    @Test
    fun `known storage stays visible when refresh starts`() {
        val volume = testVolume("primary", "/storage/emulated/0", totalBytes = 1024L * 1024L, freeBytes = 512L * 1024L)
        val state = mutableStateOf(HomeState(
            storageInfo = StorageInfo(listOf(volume)),
            allStorageVolumes = persistentListOf(volume),
            categoryStorages = persistentListOf(CategoryStorage("Images", 1024L, emptySet())),
            isLoading = false
        ))
        compose.setContent {
            MaterialTheme {
                StorageSummaryCard(state.value, {}, {}, {})
            }
        }
        compose.onNodeWithTag("storage_bar", useUnmergedTree = true).assertExists()
        compose.runOnIdle { state.value = state.value.copy(isLoading = true, isCalculatingStorage = true) }
        compose.onNodeWithTag("storage_bar", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("storage_bar_loading", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("storage_bar_multicolor_loading", useUnmergedTree = true).assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(isPullToRefreshing = true) }
        compose.onNodeWithTag("storage_bar_multicolor_loading", useUnmergedTree = true).assertExists()
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithTag("storage_bar_segments", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("used", substring = true).assertExists()
        compose.onNodeWithText("free", substring = true).assertExists()
        compose.runOnIdle { state.value = state.value.copy(isPullToRefreshing = false) }
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithTag("storage_bar_multicolor_loading", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("storage_bar_segments", useUnmergedTree = true).assertExists()
    }
}
