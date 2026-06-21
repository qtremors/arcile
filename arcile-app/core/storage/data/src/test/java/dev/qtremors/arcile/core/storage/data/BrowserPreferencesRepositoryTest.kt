package dev.qtremors.arcile.core.storage.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.core.storage.domain.ActivityLogEntry
import dev.qtremors.arcile.core.storage.domain.BrowserPreferences
import dev.qtremors.arcile.core.storage.domain.BrowserPresentationPreferences
import dev.qtremors.arcile.core.storage.domain.BrowserViewMode
import dev.qtremors.arcile.core.storage.domain.FileSortOption
import dev.qtremors.arcile.core.storage.domain.ImageGalleryDefaultTab
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BrowserPreferencesRepositoryTest {

    private lateinit var context: Context
    private lateinit var dataStoreFile: File
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dataStoreFile = File(
            context.filesDir,
            "datastore/browser-prefs-test-${UUID.randomUUID()}.preferences_pb"
        )
        dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { dataStoreFile }
        )
    }

    @After
    fun tearDown() {
        dataStoreScope.cancel()
        dataStoreFile.delete()
    }

    @Test
    fun `defaults to name ascending with empty maps`() = runBlocking {
        val repository = BrowserPreferencesRepository(context, dataStore)

        val preferences = repository.preferencesFlow.first()

        assertEquals(FileSortOption.NAME_ASC, preferences.globalPresentation.sortOption)
        assertEquals(BrowserViewMode.LIST, preferences.globalPresentation.viewMode)
        assertEquals(BrowserViewMode.GRID, preferences.albumPresentation.viewMode)
        assertEquals(BrowserPresentationPreferences.DEFAULT_LIST_ZOOM, preferences.globalPresentation.listZoom)
        assertEquals(BrowserPresentationPreferences.DEFAULT_GRID_MIN_CELL_SIZE, preferences.globalPresentation.gridMinCellSize)
        assertEquals(BrowserPreferences.DEFAULT_HOME_RECENT_CAROUSEL_LIMIT, preferences.homeRecentCarouselLimit)
        assertEquals(ImageGalleryDefaultTab.PHOTOS, preferences.imageGalleryDefaultTab)
        assertEquals(true, preferences.showHiddenFiles)
        assertEquals(emptyMap<String, BrowserPresentationPreferences>(), preferences.pathPresentationOptions)
        assertEquals(emptyMap<String, BrowserPresentationPreferences>(), preferences.exactPathPresentationOptions)
    }

    @Test
    fun `updatePathPresentation stores exact and recursive keys with normalized path`() = runBlocking {
        val repository = BrowserPreferencesRepository(context, dataStore)

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
        val repository = BrowserPreferencesRepository(context, dataStore)

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
    fun `updateLastOpenedLocation records opened folder activity when activity log is attached`() = runBlocking {
        val activityDataStoreFile = File(
            context.filesDir,
            "datastore/activity-log-browser-prefs-test-${UUID.randomUUID()}.preferences_pb"
        )
        val activityDataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { activityDataStoreFile }
        )
        val activityLog = ActivityLogRepository(context, activityDataStore)
        val repository = BrowserPreferencesRepository(
            context = context,
            dataStore = dataStore,
            activityLogRepository = activityLog
        )

        repository.updateLastOpenedLocation("/storage/emulated/0/Download", "primary")

        val entry = activityLog.entries.first().single() as ActivityLogEntry.FolderOpened
        assertEquals("/storage/emulated/0/Download", entry.path)
        assertEquals("primary", entry.volumeId)
        activityDataStoreFile.delete()
        Unit
    }

    @Test
    fun `invalid stored sort and view options fall back to defaults`() = runBlocking {
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey("global_sort_option")] = "NOT_REAL"
            prefs[stringPreferencesKey("global_view_mode")] = "NOPE"
            prefs[floatPreferencesKey("global_list_zoom")] = 9f
        }

        val preferences = BrowserPreferencesRepository(context, dataStore).preferencesFlow.first()

        assertEquals(FileSortOption.NAME_ASC, preferences.globalPresentation.sortOption)
        assertEquals(BrowserViewMode.LIST, preferences.globalPresentation.viewMode)
        assertEquals(BrowserPresentationPreferences.MAX_LIST_ZOOM, preferences.globalPresentation.listZoom)
    }

    @Test
    fun `invalid stored album view option falls back to album default`() = runBlocking {
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey("album_view_mode")] = "NOPE"
        }

        val preferences = BrowserPreferencesRepository(context, dataStore).preferencesFlow.first()

        assertEquals(BrowserViewMode.GRID, preferences.albumPresentation.viewMode)
    }

    @Test
    fun `home recent carousel limit is clamped when stored or updated`() = runBlocking {
        dataStore.edit { prefs ->
            prefs[intPreferencesKey("home_recent_carousel_limit")] = 200
        }
        val repository = BrowserPreferencesRepository(context, dataStore)

        assertEquals(48, repository.preferencesFlow.first().homeRecentCarouselLimit)

        repository.updateHomeRecentCarouselLimit(-5)

        assertEquals(0, repository.preferencesFlow.first().homeRecentCarouselLimit)
    }

    @Test
    fun `show hidden files preference is persisted`() = runBlocking {
        val repository = BrowserPreferencesRepository(context, dataStore)

        repository.updateShowHiddenFiles(true)

        assertEquals(true, repository.preferencesFlow.first().showHiddenFiles)
    }

    @Test
    fun `default save to arcile path is persisted and cleared`() = runBlocking {
        val repository = BrowserPreferencesRepository(context, dataStore)

        repository.updateDefaultSaveToArcilePath("/storage/emulated/0/Download")

        assertEquals("/storage/emulated/0/Download", repository.preferencesFlow.first().defaultSaveToArcilePath)

        repository.updateDefaultSaveToArcilePath(null)

        assertEquals(null, repository.preferencesFlow.first().defaultSaveToArcilePath)
    }

    @Test
    fun `image gallery default tab preference is persisted`() = runBlocking {
        val repository = BrowserPreferencesRepository(context, dataStore)

        repository.updateImageGalleryDefaultTab(ImageGalleryDefaultTab.ALBUMS)

        assertEquals(ImageGalleryDefaultTab.ALBUMS, repository.preferencesFlow.first().imageGalleryDefaultTab)
    }

}
