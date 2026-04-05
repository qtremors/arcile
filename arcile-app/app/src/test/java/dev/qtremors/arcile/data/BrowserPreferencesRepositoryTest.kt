package dev.qtremors.arcile.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.domain.BrowserPresentationPreferences
import dev.qtremors.arcile.domain.BrowserViewMode
import dev.qtremors.arcile.presentation.FileSortOption
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BrowserPreferencesRepositoryTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        runBlocking {
            context.browserDataStore.edit { it.clear() }
        }
    }

    @Test
    fun `defaults to name ascending with empty maps`() = runBlocking {
        val repository = BrowserPreferencesRepository(context)

        val preferences = repository.preferencesFlow.first()

        assertEquals(FileSortOption.NAME_ASC, preferences.globalPresentation.sortOption)
        assertEquals(BrowserViewMode.LIST, preferences.globalPresentation.viewMode)
        assertEquals(BrowserPresentationPreferences.DEFAULT_LIST_ZOOM, preferences.globalPresentation.listZoom)
        assertEquals(BrowserPresentationPreferences.DEFAULT_GRID_MIN_CELL_SIZE, preferences.globalPresentation.gridMinCellSize)
        assertEquals(emptyMap<String, BrowserPresentationPreferences>(), preferences.pathPresentationOptions)
        assertEquals(emptyMap<String, BrowserPresentationPreferences>(), preferences.exactPathPresentationOptions)
    }

    @Test
    fun `updatePathPresentation stores exact and recursive keys with normalized path`() = runBlocking {
        val repository = BrowserPreferencesRepository(context)

        repository.updatePathPresentation(
            "/storage/emulated/0/Download/",
            BrowserPresentationPreferences(
                sortOption = FileSortOption.SIZE_LARGEST,
                viewMode = BrowserViewMode.GRID,
                listZoom = 1.1f,
                gridMinCellSize = 148f
            ),
            applyToSubfolders = false
        )
        repository.updatePathPresentation(
            "/storage/emulated/0/Pictures/",
            BrowserPresentationPreferences(
                sortOption = FileSortOption.DATE_NEWEST,
                viewMode = BrowserViewMode.LIST,
                listZoom = 0.95f,
                gridMinCellSize = 124f
            ),
            applyToSubfolders = true
        )

        val preferences = repository.preferencesFlow.first()

        assertEquals(FileSortOption.SIZE_LARGEST, preferences.exactPathPresentationOptions["/storage/emulated/0/Download"]?.sortOption)
        assertEquals(BrowserViewMode.GRID, preferences.exactPathPresentationOptions["/storage/emulated/0/Download"]?.viewMode)
        assertEquals(FileSortOption.DATE_NEWEST, preferences.pathPresentationOptions["/storage/emulated/0/Pictures"]?.sortOption)
        assertEquals(124f, preferences.pathPresentationOptions["/storage/emulated/0/Pictures"]?.gridMinCellSize)
    }

    @Test
    fun `clearing path presentation removes both exact and recursive values`() = runBlocking {
        val repository = BrowserPreferencesRepository(context)

        repository.updatePathPresentation(
            "/storage/emulated/0/Download",
            BrowserPresentationPreferences(sortOption = FileSortOption.NAME_DESC, viewMode = BrowserViewMode.GRID),
            applyToSubfolders = true
        )
        repository.updatePathPresentation("/storage/emulated/0/Download", null, applyToSubfolders = false)

        val preferences = repository.preferencesFlow.first()

        assertEquals(null, preferences.pathPresentationOptions["/storage/emulated/0/Download"])
        assertEquals(null, preferences.exactPathPresentationOptions["/storage/emulated/0/Download"])
    }

    @Test
    fun `invalid stored sort and view options fall back to defaults`() = runBlocking {
        context.browserDataStore.edit { prefs ->
            prefs[stringPreferencesKey("global_sort_option")] = "NOT_REAL"
            prefs[stringPreferencesKey("global_view_mode")] = "NOPE"
            prefs[floatPreferencesKey("global_list_zoom")] = 9f
        }

        val preferences = BrowserPreferencesRepository(context).preferencesFlow.first()

        assertEquals(FileSortOption.NAME_ASC, preferences.globalPresentation.sortOption)
        assertEquals(BrowserViewMode.LIST, preferences.globalPresentation.viewMode)
        assertEquals(BrowserPresentationPreferences.MAX_LIST_ZOOM, preferences.globalPresentation.listZoom)
    }
}
