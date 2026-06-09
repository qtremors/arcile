package dev.qtremors.arcile.core.storage.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.qtremors.arcile.core.storage.domain.BrowserPreferences
import dev.qtremors.arcile.core.storage.domain.BrowserPresentationPreferences
import dev.qtremors.arcile.core.storage.domain.BrowserPreferencesStore
import dev.qtremors.arcile.core.storage.domain.BrowserViewMode
import dev.qtremors.arcile.di.ArcileDispatchers
import dev.qtremors.arcile.core.storage.domain.FileSortOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.browserDataStore by preferencesDataStore(name = "browser_prefs")

class BrowserPreferencesRepository(
    context: Context,
    private val dataStore: DataStore<Preferences> = context.browserDataStore,
    private val dispatchers: ArcileDispatchers = ArcileDispatchers(
        io = Dispatchers.IO,
        default = Dispatchers.Default,
        main = Dispatchers.Main,
        storage = Dispatchers.IO
    )
) : BrowserPreferencesStore {
    private val GLOBAL_SORT_KEY = stringPreferencesKey("global_sort_option")
    private val GLOBAL_VIEW_MODE_KEY = stringPreferencesKey("global_view_mode")
    private val GLOBAL_LIST_ZOOM_KEY = floatPreferencesKey("global_list_zoom")
    private val GLOBAL_GRID_MIN_CELL_SIZE_KEY = floatPreferencesKey("global_grid_min_cell_size")
    private val GLOBAL_SHOW_THUMBNAILS_KEY = booleanPreferencesKey("global_show_thumbnails")
    private val RECENT_SORT_KEY = stringPreferencesKey("recent_sort_option")
    private val RECENT_VIEW_MODE_KEY = stringPreferencesKey("recent_view_mode")
    private val RECENT_LIST_ZOOM_KEY = floatPreferencesKey("recent_list_zoom")
    private val RECENT_GRID_MIN_CELL_SIZE_KEY = floatPreferencesKey("recent_grid_min_cell_size")
    private val RECENT_SHOW_THUMBNAILS_KEY = booleanPreferencesKey("recent_show_thumbnails")
    private val HOME_RECENT_CAROUSEL_LIMIT_KEY = intPreferencesKey("home_recent_carousel_limit")
    private val SHOW_HIDDEN_FILES_KEY = booleanPreferencesKey("show_hidden_files")
    private val IMAGE_GALLERY_SHOW_FILE_DETAILS_KEY = booleanPreferencesKey("image_gallery_show_file_details")
    private val IMAGE_GALLERY_ASPECT_RATIO_KEY = booleanPreferencesKey("image_gallery_aspect_ratio")
    private val IMAGE_GALLERY_SECTIONED_KEY = booleanPreferencesKey("image_gallery_sectioned")
    private val LAST_OPENED_PATH_KEY = stringPreferencesKey("last_opened_path")
    private val LAST_OPENED_VOLUME_ID_KEY = stringPreferencesKey("last_opened_volume_id")

    override val preferencesFlow: Flow<BrowserPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            val globalPresentation = BrowserPresentationPreferences(
                sortOption = parseSortOption(
                    prefs[GLOBAL_SORT_KEY],
                    BrowserPresentationPreferences.DEFAULT_SORT_OPTION
                ),
                viewMode = parseViewMode(prefs[GLOBAL_VIEW_MODE_KEY]),
                listZoom = prefs[GLOBAL_LIST_ZOOM_KEY] ?: BrowserPresentationPreferences.DEFAULT_LIST_ZOOM,
                gridMinCellSize = prefs[GLOBAL_GRID_MIN_CELL_SIZE_KEY]
                    ?: BrowserPresentationPreferences.DEFAULT_GRID_MIN_CELL_SIZE,
                showThumbnails = prefs[GLOBAL_SHOW_THUMBNAILS_KEY] ?: BrowserPresentationPreferences.DEFAULT_SHOW_THUMBNAILS
            ).normalized()

            val recentPresentation = BrowserPresentationPreferences(
                sortOption = parseSortOption(
                    prefs[RECENT_SORT_KEY],
                    BrowserPresentationPreferences.DEFAULT_CATEGORY_SORT_OPTION
                ),
                viewMode = parseViewMode(prefs[RECENT_VIEW_MODE_KEY]),
                listZoom = prefs[RECENT_LIST_ZOOM_KEY] ?: BrowserPresentationPreferences.DEFAULT_LIST_ZOOM,
                gridMinCellSize = prefs[RECENT_GRID_MIN_CELL_SIZE_KEY]
                    ?: BrowserPresentationPreferences.DEFAULT_GRID_MIN_CELL_SIZE,
                showThumbnails = prefs[RECENT_SHOW_THUMBNAILS_KEY]
                    ?: prefs[GLOBAL_SHOW_THUMBNAILS_KEY]
                    ?: BrowserPresentationPreferences.DEFAULT_SHOW_THUMBNAILS
            ).normalized()

            val pathMap = mutableMapOf<String, BrowserPresentationPreferences>()
            val exactPathMap = mutableMapOf<String, BrowserPresentationPreferences>()
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
                        ).copy(viewMode = parseViewMode(value))
                    }

                    key.name.startsWith("exact_path_view_mode_") && value is String -> {
                        val path = key.name.removePrefix("exact_path_view_mode_")
                        exactPathMap[path] = currentPresentation(
                            exactPathMap[path],
                            globalPresentation
                        ).copy(viewMode = parseViewMode(value))
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
                lastOpenedPath = prefs[LAST_OPENED_PATH_KEY],
                lastOpenedVolumeId = prefs[LAST_OPENED_VOLUME_ID_KEY]
            )
        }
        .flowOn(dispatchers.io)

    override suspend fun updateGlobalPresentation(presentation: BrowserPresentationPreferences) {
        val normalized = presentation.normalized()
        dataStore.edit { prefs ->
            prefs[GLOBAL_SORT_KEY] = normalized.sortOption.name
            prefs[GLOBAL_VIEW_MODE_KEY] = normalized.viewMode.name
            prefs[GLOBAL_LIST_ZOOM_KEY] = normalized.listZoom
            prefs[GLOBAL_GRID_MIN_CELL_SIZE_KEY] = normalized.gridMinCellSize
            prefs[GLOBAL_SHOW_THUMBNAILS_KEY] = normalized.showThumbnails
        }
    }

    override suspend fun updateRecentPresentation(presentation: BrowserPresentationPreferences) {
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

    override suspend fun updatePathPresentation(
        path: String,
        presentation: BrowserPresentationPreferences?,
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
    }

    private fun parseSortOption(value: String?, fallback: FileSortOption): FileSortOption {
        return FileSortOption.entries.find { it.name == value } ?: fallback
    }

    private fun parseViewMode(value: String?): BrowserViewMode {
        return BrowserViewMode.entries.find { it.name == value } ?: BrowserPresentationPreferences.DEFAULT_VIEW_MODE
    }

    private fun currentPresentation(
        existing: BrowserPresentationPreferences?,
        globalPresentation: BrowserPresentationPreferences
    ): BrowserPresentationPreferences {
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
