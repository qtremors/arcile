package dev.qtremors.arcile.core.storage.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.qtremors.arcile.core.storage.domain.BrowserPreferences
import dev.qtremors.arcile.core.storage.domain.FileListingPreferences
import dev.qtremors.arcile.core.storage.domain.BrowserLocationPreferencesStore
import dev.qtremors.arcile.core.storage.domain.GalleryPreferencesStore
import dev.qtremors.arcile.core.storage.domain.RecentFilesPreferencesStore
import dev.qtremors.arcile.core.storage.domain.SaveDestinationPreferencesStore
import dev.qtremors.arcile.core.storage.domain.FileViewMode
import dev.qtremors.arcile.core.storage.domain.ImageGalleryDefaultTab
import dev.qtremors.arcile.core.storage.domain.ImageGalleryGrouping
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import dev.qtremors.arcile.core.storage.domain.FileSortOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.IOException

val Context.browserDataStore by preferencesDataStore(name = "browser_prefs")

class BrowserPreferencesRepository(
    context: Context,
    private val dataStore: DataStore<Preferences> = context.browserDataStore,
    private val activityLogRepository: ActivityLogRepository? = null,
    private val dispatchers: ArcileDispatchers = ArcileDispatchers(
        io = Dispatchers.IO,
        default = Dispatchers.Default,
        main = Dispatchers.Main,
        storage = Dispatchers.IO
    )
) : BrowserLocationPreferencesStore,
    RecentFilesPreferencesStore,
    GalleryPreferencesStore,
    SaveDestinationPreferencesStore {
    internal val preferencesFlow: Flow<BrowserPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            val globalPresentation = FileListingPreferences(
                sortOption = parseSortOption(
                    prefs[GLOBAL_SORT_KEY],
                    FileListingPreferences.DEFAULT_SORT_OPTION
                ),
                viewMode = parseViewMode(
                    prefs[GLOBAL_VIEW_MODE_KEY],
                    FileListingPreferences.DEFAULT_VIEW_MODE
                ),
                listZoom = prefs[GLOBAL_LIST_ZOOM_KEY] ?: FileListingPreferences.DEFAULT_LIST_ZOOM,
                gridMinCellSize = prefs[GLOBAL_GRID_MIN_CELL_SIZE_KEY]
                    ?: FileListingPreferences.DEFAULT_GRID_MIN_CELL_SIZE,
                showThumbnails = prefs[GLOBAL_SHOW_THUMBNAILS_KEY] ?: FileListingPreferences.DEFAULT_SHOW_THUMBNAILS
            ).normalized()

            val recentPresentation = FileListingPreferences(
                sortOption = parseSortOption(
                    prefs[RECENT_SORT_KEY],
                    FileListingPreferences.DEFAULT_CATEGORY_SORT_OPTION
                ),
                viewMode = parseViewMode(
                    prefs[RECENT_VIEW_MODE_KEY],
                    FileListingPreferences.DEFAULT_VIEW_MODE
                ),
                listZoom = prefs[RECENT_LIST_ZOOM_KEY] ?: FileListingPreferences.DEFAULT_LIST_ZOOM,
                gridMinCellSize = prefs[RECENT_GRID_MIN_CELL_SIZE_KEY]
                    ?: FileListingPreferences.DEFAULT_GRID_MIN_CELL_SIZE,
                showThumbnails = prefs[RECENT_SHOW_THUMBNAILS_KEY]
                    ?: prefs[GLOBAL_SHOW_THUMBNAILS_KEY]
                    ?: FileListingPreferences.DEFAULT_SHOW_THUMBNAILS
            ).normalized()

            val pathMap = mutableMapOf<String, FileListingPreferences>()
            val exactPathMap = mutableMapOf<String, FileListingPreferences>()
            prefs.asMap().forEach { (key, value) ->
                when {
                    key.name.startsWith("path_sort_") && value is String -> {
                        val path = key.name.removePrefix("path_sort_")
                        pathMap[path] = currentPresentation(
                            pathMap[path],
                            globalPresentation
                        ).copy(sortOption = parseSortOption(value, globalPresentation.sortOption))
                    }

                    key.name.startsWith("exact_path_sort_") && value is String -> {
                        val path = key.name.removePrefix("exact_path_sort_")
                        exactPathMap[path] = currentPresentation(
                            exactPathMap[path],
                            globalPresentation
                        ).copy(sortOption = parseSortOption(value, globalPresentation.sortOption))
                    }

                    key.name.startsWith("path_view_mode_") && value is String -> {
                        val path = key.name.removePrefix("path_view_mode_")
                        pathMap[path] = currentPresentation(
                            pathMap[path],
                            globalPresentation
                        ).copy(viewMode = parseViewMode(value, FileListingPreferences.DEFAULT_VIEW_MODE))
                    }

                    key.name.startsWith("exact_path_view_mode_") && value is String -> {
                        val path = key.name.removePrefix("exact_path_view_mode_")
                        exactPathMap[path] = currentPresentation(
                            exactPathMap[path],
                            globalPresentation
                        ).copy(viewMode = parseViewMode(value, FileListingPreferences.DEFAULT_VIEW_MODE))
                    }

                    key.name.startsWith("path_list_zoom_") && value is Float -> {
                        val path = key.name.removePrefix("path_list_zoom_")
                        pathMap[path] = currentPresentation(
                            pathMap[path],
                            globalPresentation
                        ).copy(listZoom = value)
                    }

                    key.name.startsWith("exact_path_list_zoom_") && value is Float -> {
                        val path = key.name.removePrefix("exact_path_list_zoom_")
                        exactPathMap[path] = currentPresentation(
                            exactPathMap[path],
                            globalPresentation
                        ).copy(listZoom = value)
                    }

                    key.name.startsWith("path_grid_min_cell_size_") && value is Float -> {
                        val path = key.name.removePrefix("path_grid_min_cell_size_")
                        pathMap[path] = currentPresentation(
                            pathMap[path],
                            globalPresentation
                        ).copy(gridMinCellSize = value)
                    }

                    key.name.startsWith("exact_path_grid_min_cell_size_") && value is Float -> {
                        val path = key.name.removePrefix("exact_path_grid_min_cell_size_")
                        exactPathMap[path] = currentPresentation(
                            exactPathMap[path],
                            globalPresentation
                        ).copy(gridMinCellSize = value)
                    }
                }
            }

            val groupingStr = prefs[IMAGE_GALLERY_GROUPING_KEY]
            val grouping = ImageGalleryGrouping.entries.find { it.name == groupingStr }
                ?: BrowserPreferences().imageGalleryGrouping
            val defaultTabStr = prefs[IMAGE_GALLERY_DEFAULT_TAB_KEY]
            val defaultTab = ImageGalleryDefaultTab.entries.find { it.name == defaultTabStr }
                ?: BrowserPreferences().imageGalleryDefaultTab

            val albumPresentation = FileListingPreferences(
                sortOption = parseSortOption(
                    prefs[ALBUM_SORT_OPTION_KEY],
                    BrowserPreferences().albumPresentation.sortOption
                ),
                viewMode = parseViewMode(
                    prefs[ALBUM_VIEW_MODE_KEY],
                    BrowserPreferences().albumPresentation.viewMode
                ),
                listZoom = FileListingPreferences.DEFAULT_LIST_ZOOM,
                gridMinCellSize = prefs[ALBUM_GRID_MIN_CELL_SIZE_KEY]
                    ?: BrowserPreferences().albumPresentation.gridMinCellSize,
                showThumbnails = true
            ).normalized()

            val albumAspectRatio = prefs[ALBUM_ASPECT_RATIO_KEY]
                ?: BrowserPreferences().albumAspectRatio

            val favoriteFilesStr = prefs[FAVORITE_FILES_KEY]
            val favoriteFiles: Set<String> = if (!favoriteFilesStr.isNullOrEmpty()) {
                runCatching { Json.decodeFromString<Set<String>>(favoriteFilesStr) }.getOrDefault(emptySet())
            } else {
                emptySet()
            }

            val pinnedAlbumsStr = prefs[PINNED_ALBUMS_KEY]
            val pinnedAlbums: Set<String> = if (!pinnedAlbumsStr.isNullOrEmpty()) {
                runCatching { Json.decodeFromString<Set<String>>(pinnedAlbumsStr) }.getOrDefault(emptySet())
            } else {
                emptySet()
            }

            val albumCoversStr = prefs[ALBUM_COVERS_KEY]
            val albumCovers: Map<String, String> = if (!albumCoversStr.isNullOrEmpty()) {
                runCatching { Json.decodeFromString<Map<String, String>>(albumCoversStr) }.getOrDefault(emptyMap())
            } else {
                emptyMap()
            }

            BrowserPreferences(
                globalPresentation = globalPresentation,
                recentPresentation = recentPresentation,
                pathPresentationOptions = pathMap.mapValues { it.value.normalized() },
                exactPathPresentationOptions = exactPathMap.mapValues { it.value.normalized() },
                homeRecentCarouselLimit = BrowserPreferences.normalizeHomeRecentCarouselLimit(
                    prefs[HOME_RECENT_CAROUSEL_LIMIT_KEY] ?: BrowserPreferences.DEFAULT_HOME_RECENT_CAROUSEL_LIMIT
                ),
                showHiddenFiles = prefs[SHOW_HIDDEN_FILES_KEY] ?: BrowserPreferences().showHiddenFiles,
                imageGalleryShowFileDetails = prefs[IMAGE_GALLERY_SHOW_FILE_DETAILS_KEY]
                    ?: BrowserPreferences().imageGalleryShowFileDetails,
                imageGalleryAspectRatio = prefs[IMAGE_GALLERY_ASPECT_RATIO_KEY]
                    ?: BrowserPreferences().imageGalleryAspectRatio,
                imageGallerySectioned = prefs[IMAGE_GALLERY_SECTIONED_KEY]
                    ?: BrowserPreferences().imageGallerySectioned,
                imageGalleryGrouping = grouping,
                imageGalleryDefaultTab = defaultTab,
                albumPresentation = albumPresentation,
                albumAspectRatio = albumAspectRatio,
                favoriteFiles = favoriteFiles,
                pinnedAlbums = pinnedAlbums,
                albumCovers = albumCovers,
                lastOpenedPath = prefs[LAST_OPENED_PATH_KEY],
                lastOpenedVolumeId = prefs[LAST_OPENED_VOLUME_ID_KEY],
                defaultSaveToArcilePath = prefs[DEFAULT_SAVE_TO_ARCILE_PATH_KEY],
                browserScrollbarEnabled = prefs[BROWSER_SCROLLBAR_ENABLED_KEY]
                    ?: BrowserPreferences().browserScrollbarEnabled,
                galleryScrollbarEnabled = prefs[GALLERY_SCROLLBAR_ENABLED_KEY]
                    ?: BrowserPreferences().galleryScrollbarEnabled
            )
        }
        .flowOn(dispatchers.io)

    override val locationPreferencesFlow = preferencesFlow.asLocationPreferences()
    override val recentFilesPreferencesFlow = preferencesFlow.asRecentFilesPreferences()
    override val galleryPreferencesFlow = preferencesFlow.asGalleryPreferences()
    override val saveDestinationPreferencesFlow = preferencesFlow.asSaveDestinationPreferences()

    override suspend fun updateGlobalPresentation(presentation: FileListingPreferences) {
        val normalized = presentation.normalized()
        dataStore.edit { prefs ->
            prefs[GLOBAL_SORT_KEY] = normalized.sortOption.name
            prefs[GLOBAL_VIEW_MODE_KEY] = normalized.viewMode.name
            prefs[GLOBAL_LIST_ZOOM_KEY] = normalized.listZoom
            prefs[GLOBAL_GRID_MIN_CELL_SIZE_KEY] = normalized.gridMinCellSize
            prefs[GLOBAL_SHOW_THUMBNAILS_KEY] = normalized.showThumbnails
        }
    }

    override suspend fun updateRecentPresentation(presentation: FileListingPreferences) {
        val normalized = presentation.normalized()
        dataStore.edit { prefs ->
            prefs[RECENT_SORT_KEY] = normalized.sortOption.name
            prefs[RECENT_VIEW_MODE_KEY] = normalized.viewMode.name
            prefs[RECENT_LIST_ZOOM_KEY] = normalized.listZoom
            prefs[RECENT_GRID_MIN_CELL_SIZE_KEY] = normalized.gridMinCellSize
            prefs[RECENT_SHOW_THUMBNAILS_KEY] = normalized.showThumbnails
        }
    }

    override suspend fun updateHomeRecentCarouselLimit(limit: Int) {
        dataStore.edit { prefs ->
            prefs[HOME_RECENT_CAROUSEL_LIMIT_KEY] = BrowserPreferences.normalizeHomeRecentCarouselLimit(limit)
        }
    }

    override suspend fun updateShowHiddenFiles(show: Boolean) {
        dataStore.edit { prefs ->
            prefs[SHOW_HIDDEN_FILES_KEY] = show
        }
    }

    override suspend fun updateBrowserScrollbarEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[BROWSER_SCROLLBAR_ENABLED_KEY] = enabled
        }
    }

    override suspend fun updateGalleryScrollbarEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[GALLERY_SCROLLBAR_ENABLED_KEY] = enabled
        }
    }

    override suspend fun updateImageGalleryShowFileDetails(show: Boolean) {
        dataStore.edit { prefs ->
            prefs[IMAGE_GALLERY_SHOW_FILE_DETAILS_KEY] = show
        }
    }

    override suspend fun updateImageGalleryAspectRatio(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[IMAGE_GALLERY_ASPECT_RATIO_KEY] = enabled
        }
    }

    override suspend fun updateImageGallerySectioned(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[IMAGE_GALLERY_SECTIONED_KEY] = enabled
        }
    }

    override suspend fun updateImageGalleryGrouping(grouping: ImageGalleryGrouping) {
        dataStore.edit { prefs ->
            prefs[IMAGE_GALLERY_GROUPING_KEY] = grouping.name
        }
    }

    override suspend fun updateImageGalleryDefaultTab(tab: ImageGalleryDefaultTab) {
        dataStore.edit { prefs ->
            prefs[IMAGE_GALLERY_DEFAULT_TAB_KEY] = tab.name
        }
    }

    override suspend fun updateAlbumPresentation(presentation: FileListingPreferences) {
        val normalized = presentation.normalized()
        dataStore.edit { prefs ->
            prefs[ALBUM_SORT_OPTION_KEY] = normalized.sortOption.name
            prefs[ALBUM_VIEW_MODE_KEY] = normalized.viewMode.name
            prefs[ALBUM_GRID_MIN_CELL_SIZE_KEY] = normalized.gridMinCellSize
        }
    }

    override suspend fun updateImageGalleryPresentation(presentation: FileListingPreferences) {
        updatePathPresentation(
            path = IMAGE_GALLERY_PRESENTATION_PATH,
            presentation = presentation,
            applyToSubfolders = false
        )
    }

    override suspend fun updateAlbumAspectRatio(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[ALBUM_ASPECT_RATIO_KEY] = enabled
        }
    }

    override suspend fun updatePathPresentation(
        path: String,
        presentation: FileListingPreferences?,
        applyToSubfolders: Boolean
    ) {
        val normalizedPath = if (path.length > 1) path.trimEnd('/') else path
        val keys = presentationKeys(normalizedPath, applyToSubfolders)
        val keysToClear = presentationKeys(normalizedPath, true).all() + presentationKeys(normalizedPath, false).all()

        dataStore.edit { prefs ->
            keysToClear.forEach { prefs.remove(it) }
            if (presentation != null) {
                val normalized = presentation.normalized()
                prefs[keys.sort] = normalized.sortOption.name
                prefs[keys.viewMode] = normalized.viewMode.name
                prefs[keys.listZoom] = normalized.listZoom
                prefs[keys.gridMinCellSize] = normalized.gridMinCellSize
            }
        }
    }

    override suspend fun updateLastOpenedLocation(path: String, volumeId: String?) {
        dataStore.edit { prefs ->
            prefs[LAST_OPENED_PATH_KEY] = path
            if (volumeId != null) {
                prefs[LAST_OPENED_VOLUME_ID_KEY] = volumeId
            } else {
                prefs.remove(LAST_OPENED_VOLUME_ID_KEY)
            }
        }
        activityLogRepository?.recordFolderOpened(path, volumeId)
    }

    override suspend fun updateDefaultSaveToArcilePath(path: String?) {
        dataStore.edit { prefs ->
            if (path.isNullOrBlank()) {
                prefs.remove(DEFAULT_SAVE_TO_ARCILE_PATH_KEY)
            } else {
                prefs[DEFAULT_SAVE_TO_ARCILE_PATH_KEY] = path
            }
        }
    }

    override suspend fun updateFavorite(path: String, isFavorite: Boolean) {
        dataStore.edit { prefs ->
            val favoriteFilesStr = prefs[FAVORITE_FILES_KEY]
            val currentFavorites = if (!favoriteFilesStr.isNullOrEmpty()) {
                runCatching { Json.decodeFromString<Set<String>>(favoriteFilesStr) }.getOrDefault(emptySet())
            } else {
                emptySet()
            }
            val newFavorites = if (isFavorite) {
                currentFavorites + path
            } else {
                currentFavorites - path
            }
            prefs[FAVORITE_FILES_KEY] = Json.encodeToString(newFavorites)
        }
    }

    override suspend fun updatePinnedAlbum(albumPath: String, isPinned: Boolean) {
        dataStore.edit { prefs ->
            val pinnedStr = prefs[PINNED_ALBUMS_KEY]
            val currentPinned = if (!pinnedStr.isNullOrEmpty()) {
                runCatching { Json.decodeFromString<Set<String>>(pinnedStr) }.getOrDefault(emptySet())
            } else {
                emptySet()
            }
            val newPinned = if (isPinned) {
                currentPinned + albumPath
            } else {
                currentPinned - albumPath
            }
            prefs[PINNED_ALBUMS_KEY] = Json.encodeToString(newPinned)
        }
    }

    override suspend fun updateAlbumCover(albumPath: String, coverPath: String) {
        dataStore.edit { prefs ->
            val albumCoversStr = prefs[ALBUM_COVERS_KEY]
            val currentCovers = if (!albumCoversStr.isNullOrEmpty()) {
                runCatching { Json.decodeFromString<Map<String, String>>(albumCoversStr) }.getOrDefault(emptyMap())
            } else {
                emptyMap()
            }
            val newCovers = if (coverPath.isEmpty()) {
                currentCovers - albumPath
            } else {
                currentCovers + (albumPath to coverPath)
            }
            prefs[ALBUM_COVERS_KEY] = Json.encodeToString(newCovers)
        }
    }

    private fun parseSortOption(value: String?, fallback: FileSortOption): FileSortOption {
        return FileSortOption.entries.find { it.name == value } ?: fallback
    }

    private fun parseViewMode(value: String?, fallback: FileViewMode): FileViewMode {
        return FileViewMode.entries.find { it.name == value } ?: fallback
    }

    private fun currentPresentation(
        existing: FileListingPreferences?,
        globalPresentation: FileListingPreferences
    ): FileListingPreferences {
        return existing ?: globalPresentation
    }

    private fun presentationKeys(path: String, recursive: Boolean): PresentationKeys {
        val prefix = if (recursive) "path" else "exact_path"
        return PresentationKeys(
            sort = stringPreferencesKey("${prefix}_sort_$path"),
            viewMode = stringPreferencesKey("${prefix}_view_mode_$path"),
            listZoom = floatPreferencesKey("${prefix}_list_zoom_$path"),
            gridMinCellSize = floatPreferencesKey("${prefix}_grid_min_cell_size_$path")
        )
    }

    private data class PresentationKeys(
        val sort: androidx.datastore.preferences.core.Preferences.Key<String>,
        val viewMode: androidx.datastore.preferences.core.Preferences.Key<String>,
        val listZoom: androidx.datastore.preferences.core.Preferences.Key<Float>,
        val gridMinCellSize: androidx.datastore.preferences.core.Preferences.Key<Float>
    ) {
        fun all(): List<androidx.datastore.preferences.core.Preferences.Key<*>> =
            listOf(sort, viewMode, listZoom, gridMinCellSize)
    }
}
