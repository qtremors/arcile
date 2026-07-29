package dev.qtremors.arcile.core.storage.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.core.storage.domain.FileListingPreferences
import dev.qtremors.arcile.core.storage.domain.CategoryLibraryPage
import dev.qtremors.arcile.core.storage.domain.FileSortOption
import dev.qtremors.arcile.core.storage.domain.FileViewMode
import dev.qtremors.arcile.core.storage.domain.CategoryGrouping
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
    private val audioStore by lazy { DefaultAudioLibraryPreferencesStore(dataSource) }
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
        locationStore.updateCategoryGrouping("Documents", CategoryGrouping.WEEK)
        locationStore.updateCategoryGrouping("APKs", CategoryGrouping.DAY)
        locationStore.updateCategoryDefaultPage("Docs", CategoryLibraryPage.FOLDERS)
        locationStore.updateCategoryShowFileDetails("Docs", false)
        locationStore.updateCategoryShowFileDetails("APKs", true)
        locationStore.updatePathPresentation(
            "category_Docs_items",
            FileListingPreferences(sortOption = FileSortOption.NAME_ASC)
        )
        locationStore.updatePathPresentation(
            "category_Docs_folders",
            FileListingPreferences(sortOption = FileSortOption.SIZE_LARGEST)
        )

        val preferences = locationStore.locationPreferencesFlow.first()
        assertEquals(global.normalized(), preferences.globalPresentation)
        assertEquals(
            folder.normalized(),
            preferences.pathPresentationOptions["/Pictures"]
        )
        assertFalse(preferences.showHiddenFiles)
        assertFalse(preferences.scrollbarEnabled)
        assertEquals("/Pictures", preferences.lastOpenedPath)
        assertEquals("primary", preferences.lastOpenedVolumeId)
        assertEquals(
            CategoryGrouping.WEEK,
            preferences.getGroupingForCategory("Documents")
        )
        assertEquals(
            CategoryGrouping.DAY,
            preferences.getGroupingForCategory("APKs")
        )
        assertEquals(
            CategoryGrouping.MONTH,
            preferences.getGroupingForCategory("Other")
        )
        assertEquals(
            CategoryLibraryPage.FOLDERS,
            preferences.getDefaultPageForCategory("Docs")
        )
        assertEquals(
            FileSortOption.NAME_ASC,
            preferences.getPresentationForCategory("Docs", CategoryLibraryPage.ITEMS).sortOption
        )
        assertEquals(
            FileSortOption.SIZE_LARGEST,
            preferences.getPresentationForCategory("Docs", CategoryLibraryPage.FOLDERS).sortOption
        )
        assertFalse(preferences.getShowFileDetailsForCategory("Docs"))
        assertTrue(preferences.getShowFileDetailsForCategory("APKs"))
        assertTrue(preferences.getShowFileDetailsForCategory("Other"))
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

        galleryStore.updateItemPresentation("Images", imagePresentation)
        galleryStore.updateFolderPresentation("Images", albumPresentation)
        galleryStore.updateGalleryScrollbarEnabled(false)
        galleryStore.updateShowFileDetails("Images", false)
        galleryStore.updateAspectRatio("Images", true)
        galleryStore.updateSectioned("Images", true)
        galleryStore.updateGrouping("Images", CategoryGrouping.WEEK)
        galleryStore.updateDefaultPage("Images", CategoryLibraryPage.FOLDERS)
        galleryStore.updateAlbumAspectRatio(true)
        galleryStore.updateFavorite("/Pictures/favorite.jpg", true)
        galleryStore.updatePinnedAlbum("/Pictures/Trips", true)
        galleryStore.updateAlbumCover("/Pictures/Trips", "/Pictures/Trips/cover.jpg")

        val preferences = galleryStore.galleryPreferencesFlow("Images").first()
        assertEquals(imagePresentation.normalized(), preferences.imagePresentation)
        assertEquals(albumPresentation.normalized(), preferences.albumPresentation)
        assertFalse(preferences.scrollbarEnabled)
        assertFalse(preferences.showFileDetails)
        assertTrue(preferences.aspectRatio)
        assertTrue(preferences.sectioned)
        assertEquals(CategoryGrouping.WEEK, preferences.grouping)
        assertEquals(CategoryLibraryPage.FOLDERS, preferences.defaultPage)
        assertTrue(preferences.albumAspectRatio)
        assertEquals(setOf("/Pictures/favorite.jpg"), preferences.favoriteFiles)
        assertEquals(setOf("/Pictures/Trips"), preferences.pinnedAlbums)
        assertEquals("/Pictures/Trips/cover.jpg", preferences.albumCovers["/Pictures/Trips"])
    }

    @Test
    fun `image and video layouts persist independently`() = runBlocking {
        val imagePresentation = FileListingPreferences(
            viewMode = FileViewMode.GRID,
            showThumbnails = true
        )
        val videoPresentation = FileListingPreferences(
            viewMode = FileViewMode.LIST,
            showThumbnails = false
        )

        galleryStore.updateItemPresentation("Images", imagePresentation)
        galleryStore.updateItemPresentation("Videos", videoPresentation)
        galleryStore.updateDefaultPage("Images", CategoryLibraryPage.FOLDERS)
        galleryStore.updateDefaultPage("Videos", CategoryLibraryPage.ITEMS)
        galleryStore.updateShowFileDetails("Images", false)
        galleryStore.updateShowFileDetails("Videos", true)

        val images = galleryStore.galleryPreferencesFlow("Images").first()
        val videos = galleryStore.galleryPreferencesFlow("Videos").first()
        assertEquals(FileViewMode.GRID, images.imagePresentation?.viewMode)
        assertEquals(FileViewMode.LIST, videos.imagePresentation?.viewMode)
        assertTrue(images.imagePresentation?.showThumbnails == true)
        assertFalse(videos.imagePresentation?.showThumbnails ?: true)
        assertEquals(CategoryLibraryPage.FOLDERS, images.defaultPage)
        assertEquals(CategoryLibraryPage.ITEMS, videos.defaultPage)
        assertFalse(images.showFileDetails)
        assertTrue(videos.showFileDetails)
    }

    @Test
    fun `audio store persists category presentation and opening preferences`() = runBlocking {
        val audioPresentation = FileListingPreferences(
            sortOption = FileSortOption.SIZE_LARGEST,
            viewMode = FileViewMode.GRID,
            gridMinCellSize = 188f
        )
        val folderPresentation = FileListingPreferences(
            sortOption = FileSortOption.NAME_DESC,
            viewMode = FileViewMode.LIST,
            listZoom = 1.2f
        )

        audioStore.updateAudioPresentation(audioPresentation)
        audioStore.updateAudioFolderPresentation(folderPresentation)
        audioStore.updateAudioGrouping(CategoryGrouping.WEEK)
        audioStore.updateAudioDefaultPage(CategoryLibraryPage.FOLDERS)
        audioStore.updateAudioShowFileDetails(false)
        audioStore.updateFavorite("/Music/favorite.mp3", true)
        audioStore.updatePinnedFolder("/Music/Album", true)
        audioStore.updateFolderCover("/Music/Album", "/Music/Album/cover.mp3")
        galleryStore.updateGalleryScrollbarEnabled(false)

        val preferences = audioStore.audioLibraryPreferencesFlow.first()
        assertEquals(audioPresentation.normalized(), preferences.audioPresentation)
        assertEquals(folderPresentation.normalized(), preferences.folderPresentation)
        assertEquals(CategoryGrouping.WEEK, preferences.grouping)
        assertEquals(CategoryLibraryPage.FOLDERS, preferences.defaultPage)
        assertFalse(preferences.showFileDetails)
        assertFalse(preferences.scrollbarEnabled)
        assertEquals(setOf("/Music/favorite.mp3"), preferences.favoriteFiles)
        assertEquals(setOf("/Music/Album"), preferences.pinnedFolders)
        assertEquals(
            "/Music/Album/cover.mp3",
            preferences.folderCovers["/Music/Album"]
        )
        assertTrue(galleryStore.galleryPreferencesFlow.first().favoriteFiles.isEmpty())
        assertTrue(galleryStore.galleryPreferencesFlow.first().pinnedAlbums.isEmpty())
        assertTrue(galleryStore.galleryPreferencesFlow.first().albumCovers.isEmpty())
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
