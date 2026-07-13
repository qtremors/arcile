package dev.qtremors.arcile.core.storage.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.core.storage.domain.FileListingPreferences
import dev.qtremors.arcile.core.storage.domain.FileSortOption
import dev.qtremors.arcile.core.storage.domain.FileViewMode
import dev.qtremors.arcile.core.storage.domain.ImageGalleryDefaultTab
import dev.qtremors.arcile.core.storage.domain.ImageGalleryGrouping
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FocusedBrowserPreferencesStoresTest {
    private lateinit var context: Context
    private lateinit var dataStoreFile: File
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var dataSource: BrowserPreferencesDataSource

    private val locationStore by lazy { DefaultBrowserLocationPreferencesStore(dataSource) }
    private val recentStore by lazy { DefaultRecentFilesPreferencesStore(dataSource) }
    private val galleryStore by lazy { DefaultGalleryPreferencesStore(dataSource) }
    private val saveStore by lazy { DefaultSaveDestinationPreferencesStore(dataSource) }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dataStoreFile = File(
            context.filesDir,
            "datastore/focused-browser-prefs-${UUID.randomUUID()}.preferences_pb"
        )
        dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { dataStoreFile }
        )
        dataSource = BrowserPreferencesDataSource(context, dataStore)
    }

    @After
    fun tearDown() {
        dataStoreScope.cancel()
        dataStoreFile.delete()
    }

    @Test
    fun `location store exposes only location projection updates`() = runBlocking {
        val global = FileListingPreferences(
            sortOption = FileSortOption.SIZE_LARGEST,
            viewMode = FileViewMode.GRID,
            showThumbnails = false
        )
        val folder = FileListingPreferences(sortOption = FileSortOption.DATE_NEWEST)

        locationStore.updateGlobalPresentation(global)
        locationStore.updatePathPresentation("/Pictures/", folder, applyToSubfolders = true)
        locationStore.updateShowHiddenFiles(false)
        locationStore.updateBrowserScrollbarEnabled(false)
        locationStore.updateLastOpenedLocation("/Pictures", "primary")

        val preferences = locationStore.locationPreferencesFlow.first()
        assertEquals(global.normalized(), preferences.globalPresentation)
        assertEquals(
            folder.copy(showThumbnails = global.showThumbnails).normalized(),
            preferences.pathPresentationOptions["/Pictures"]
        )
        assertFalse(preferences.showHiddenFiles)
        assertFalse(preferences.scrollbarEnabled)
        assertEquals("/Pictures", preferences.lastOpenedPath)
        assertEquals("primary", preferences.lastOpenedVolumeId)
    }

    @Test
    fun `recent store changes do not overwrite browser presentation`() = runBlocking {
        val browserPresentation = FileListingPreferences(sortOption = FileSortOption.NAME_DESC)
        val recentPresentation = FileListingPreferences(
            sortOption = FileSortOption.DATE_OLDEST,
            viewMode = FileViewMode.GRID
        )
        locationStore.updateGlobalPresentation(browserPresentation)

        recentStore.updateRecentPresentation(recentPresentation)
        recentStore.updateHomeRecentCarouselLimit(17)

        val recent = recentStore.recentFilesPreferencesFlow.first()
        val location = locationStore.locationPreferencesFlow.first()
        assertEquals(recentPresentation.normalized(), recent.presentation)
        assertEquals(17, recent.homeCarouselLimit)
        assertEquals(browserPresentation.normalized(), location.globalPresentation)
    }

    @Test
    fun `gallery store persists independent view album and collection preferences`() = runBlocking {
        val imagePresentation = FileListingPreferences(
            sortOption = FileSortOption.DATE_NEWEST,
            viewMode = FileViewMode.GRID,
            gridMinCellSize = 188f
        )
        val albumPresentation = FileListingPreferences(
            sortOption = FileSortOption.NAME_DESC,
            viewMode = FileViewMode.GRID,
            gridMinCellSize = 172f
        )

        galleryStore.updateImageGalleryPresentation(imagePresentation)
        galleryStore.updateAlbumPresentation(albumPresentation)
        galleryStore.updateGalleryScrollbarEnabled(false)
        galleryStore.updateImageGalleryShowFileDetails(false)
        galleryStore.updateImageGalleryAspectRatio(true)
        galleryStore.updateImageGallerySectioned(true)
        galleryStore.updateImageGalleryGrouping(ImageGalleryGrouping.WEEK)
        galleryStore.updateImageGalleryDefaultTab(ImageGalleryDefaultTab.ALBUMS)
        galleryStore.updateAlbumAspectRatio(true)
        galleryStore.updateFavorite("/Pictures/favorite.jpg", true)
        galleryStore.updatePinnedAlbum("/Pictures/Trips", true)
        galleryStore.updateAlbumCover("/Pictures/Trips", "/Pictures/Trips/cover.jpg")

        val preferences = galleryStore.galleryPreferencesFlow.first()
        assertEquals(imagePresentation.normalized(), preferences.imagePresentation)
        assertEquals(albumPresentation.normalized(), preferences.albumPresentation)
        assertFalse(preferences.scrollbarEnabled)
        assertFalse(preferences.showFileDetails)
        assertTrue(preferences.aspectRatio)
        assertTrue(preferences.sectioned)
        assertEquals(ImageGalleryGrouping.WEEK, preferences.grouping)
        assertEquals(ImageGalleryDefaultTab.ALBUMS, preferences.defaultTab)
        assertTrue(preferences.albumAspectRatio)
        assertEquals(setOf("/Pictures/favorite.jpg"), preferences.favoriteFiles)
        assertEquals(setOf("/Pictures/Trips"), preferences.pinnedAlbums)
        assertEquals("/Pictures/Trips/cover.jpg", preferences.albumCovers["/Pictures/Trips"])
    }

    @Test
    fun `gallery collection removals retain unrelated entries`() = runBlocking {
        galleryStore.updateFavorite("one", true)
        galleryStore.updateFavorite("two", true)
        galleryStore.updatePinnedAlbum("album-one", true)
        galleryStore.updatePinnedAlbum("album-two", true)
        galleryStore.updateAlbumCover("album-one", "cover-one")
        galleryStore.updateAlbumCover("album-two", "cover-two")

        galleryStore.updateFavorite("one", false)
        galleryStore.updatePinnedAlbum("album-one", false)
        galleryStore.updateAlbumCover("album-one", "")

        val preferences = galleryStore.galleryPreferencesFlow.first()
        assertEquals(setOf("two"), preferences.favoriteFiles)
        assertEquals(setOf("album-two"), preferences.pinnedAlbums)
        assertEquals(mapOf("album-two" to "cover-two"), preferences.albumCovers)
    }

    @Test
    fun `save destination store persists and clears without changing other projections`() = runBlocking {
        recentStore.updateHomeRecentCarouselLimit(23)

        saveStore.updateDefaultSaveToArcilePath("/Download/Arcile")
        assertEquals(
            "/Download/Arcile",
            saveStore.saveDestinationPreferencesFlow.first().defaultPath
        )

        saveStore.updateDefaultSaveToArcilePath(" ")
        assertNull(saveStore.saveDestinationPreferencesFlow.first().defaultPath)
        assertEquals(23, recentStore.recentFilesPreferencesFlow.first().homeCarouselLimit)
    }
}
