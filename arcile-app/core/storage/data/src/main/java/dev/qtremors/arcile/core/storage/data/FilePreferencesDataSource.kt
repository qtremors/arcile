package dev.qtremors.arcile.core.storage.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.qtremors.arcile.core.storage.domain.SharedFilePreferences
import dev.qtremors.arcile.core.storage.domain.AppStartPage
import dev.qtremors.arcile.core.storage.domain.AudioLibraryPreferences
import dev.qtremors.arcile.core.storage.domain.CategoryLibraryPage
import dev.qtremors.arcile.core.storage.domain.FileListingPreferences
import dev.qtremors.arcile.core.storage.domain.FileOpenBehavior
import dev.qtremors.arcile.core.storage.domain.FileViewMode
import dev.qtremors.arcile.core.storage.domain.CategoryGrouping
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

val Context.filePreferencesDataStore by preferencesDataStore(name = "browser_prefs")

class FilePreferencesDataSource(
    context: Context,
    private val dataStore: DataStore<Preferences> = context.filePreferencesDataStore,
    private val activityLogRepository: ActivityLogRepository? = null,
    private val dispatchers: ArcileDispatchers = ArcileDispatchers(
        io = Dispatchers.IO,
        default = Dispatchers.Default,
        main = Dispatchers.Main,
        storage = Dispatchers.IO
    )
) {
    private val writer = FilePreferenceWriter(dataStore)
    internal val preferencesFlow: Flow<SharedFilePreferences> = dataStore.data
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
                showThumbnails = prefs[GLOBAL_SHOW_THUMBNAILS_KEY]
                    ?: FileListingPreferences.DEFAULT_SHOW_THUMBNAILS,
                foldersFirst = prefs[GLOBAL_FOLDERS_FIRST_KEY]
                    ?: FileListingPreferences.DEFAULT_FOLDERS_FIRST
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
                    ?: FileListingPreferences.DEFAULT_SHOW_THUMBNAILS,
                foldersFirst = prefs[RECENT_FOLDERS_FIRST_KEY]
                    ?: SharedFilePreferences().recentPresentation.foldersFirst
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

                    key.name.startsWith("path_show_thumbnails_") && value is Boolean -> {
                        val path = key.name.removePrefix("path_show_thumbnails_")
                        pathMap[path] = currentPresentation(
                            pathMap[path],
                            globalPresentation
                        ).copy(showThumbnails = value)
                    }

                    key.name.startsWith("exact_path_show_thumbnails_") && value is Boolean -> {
                        val path = key.name.removePrefix("exact_path_show_thumbnails_")
                        exactPathMap[path] = currentPresentation(
                            exactPathMap[path],
                            globalPresentation
                        ).copy(showThumbnails = value)
                    }

                    key.name.startsWith("path_folders_first_") && value is Boolean -> {
                        val path = key.name.removePrefix("path_folders_first_")
                        pathMap[path] = currentPresentation(
                            pathMap[path],
                            globalPresentation
                        ).copy(foldersFirst = value)
                    }

                    key.name.startsWith("exact_path_folders_first_") && value is Boolean -> {
                        val path = key.name.removePrefix("exact_path_folders_first_")
                        exactPathMap[path] = currentPresentation(
                            exactPathMap[path],
                            globalPresentation
                        ).copy(foldersFirst = value)
                    }
                }
            }

            val groupingStr = prefs[IMAGE_GALLERY_GROUPING_KEY]
            val grouping = CategoryGrouping.entries.find { it.name == groupingStr }
                ?: SharedFilePreferences().imageGalleryGrouping
            val defaultPageString = prefs[IMAGE_GALLERY_DEFAULT_TAB_KEY]
            val defaultPage = parseCategoryLibraryPage(defaultPageString)
                ?: SharedFilePreferences().imageGalleryDefaultPage

            val folderPresentation = FileListingPreferences(
                sortOption = parseSortOption(
                    prefs[GALLERY_FOLDER_SORT_OPTION_KEY],
                    SharedFilePreferences().folderPresentation.sortOption
                ),
                viewMode = parseViewMode(
                    prefs[GALLERY_FOLDER_VIEW_MODE_KEY],
                    SharedFilePreferences().folderPresentation.viewMode
                ),
                listZoom = FileListingPreferences.DEFAULT_LIST_ZOOM,
                gridMinCellSize = prefs[GALLERY_FOLDER_GRID_MIN_CELL_SIZE_KEY]
                    ?: SharedFilePreferences().folderPresentation.gridMinCellSize,
                showThumbnails = true,
                foldersFirst = prefs[GALLERY_FOLDER_FOLDERS_FIRST_KEY]
                    ?: FileListingPreferences.DEFAULT_FOLDERS_FIRST
            ).normalized()

            val defaults = SharedFilePreferences()
            val audioPresentation = FileListingPreferences(
                sortOption = parseSortOption(
                    prefs[AUDIO_SORT_OPTION_KEY],
                    defaults.audioPresentation.sortOption
                ),
                viewMode = parseViewMode(
                    prefs[AUDIO_VIEW_MODE_KEY],
                    defaults.audioPresentation.viewMode
                ),
                listZoom = prefs[AUDIO_LIST_ZOOM_KEY] ?: defaults.audioPresentation.listZoom,
                gridMinCellSize = prefs[AUDIO_GRID_MIN_CELL_SIZE_KEY]
                    ?: defaults.audioPresentation.gridMinCellSize,
                showThumbnails = true,
                foldersFirst = prefs[AUDIO_FOLDERS_FIRST_KEY]
                    ?: FileListingPreferences.DEFAULT_FOLDERS_FIRST
            ).normalized()
            val audioCollectionPresentation = FileListingPreferences(
                sortOption = parseSortOption(
                    prefs[AUDIO_COLLECTION_SORT_OPTION_KEY],
                    defaults.audioCollectionPresentation.sortOption
                ),
                viewMode = parseViewMode(
                    prefs[AUDIO_COLLECTION_VIEW_MODE_KEY],
                    defaults.audioCollectionPresentation.viewMode
                ),
                listZoom = prefs[AUDIO_COLLECTION_LIST_ZOOM_KEY]
                    ?: defaults.audioCollectionPresentation.listZoom,
                gridMinCellSize = prefs[AUDIO_COLLECTION_GRID_MIN_CELL_SIZE_KEY]
                    ?: defaults.audioCollectionPresentation.gridMinCellSize,
                showThumbnails = true,
                foldersFirst = prefs[AUDIO_COLLECTION_FOLDERS_FIRST_KEY]
                    ?: FileListingPreferences.DEFAULT_FOLDERS_FIRST
            ).normalized()
            val audioGrouping = CategoryGrouping.entries.firstOrNull {
                it.name == prefs[AUDIO_GROUPING_KEY]
            } ?: defaults.audioGrouping
            val audioDefaultPage = parseCategoryLibraryPage(prefs[AUDIO_DEFAULT_TAB_KEY])
                ?: defaults.audioDefaultPage
            val audioFavoriteFiles = prefs[AUDIO_FAVORITE_FILES_KEY]
                ?.let { encoded ->
                    runCatchingPreservingCancellation {
                        Json.decodeFromString<Set<String>>(encoded)
                    }.getOrDefault(emptySet())
                }
                .orEmpty()
            val audioPinnedFolders = prefs[AUDIO_PINNED_FOLDERS_KEY]
                ?.let { encoded ->
                    runCatchingPreservingCancellation {
                        Json.decodeFromString<Set<String>>(encoded)
                    }.getOrDefault(emptySet())
                }
                .orEmpty()
            val audioFolderCovers = prefs[AUDIO_FOLDER_COVERS_KEY]
                ?.let { encoded ->
                    runCatchingPreservingCancellation {
                        Json.decodeFromString<Map<String, String>>(encoded)
                    }.getOrDefault(emptyMap())
                }
                .orEmpty()
            val categoryGroupings = prefs[CATEGORY_GROUPINGS_KEY]
                ?.let { encoded ->
                    runCatchingPreservingCancellation {
                        Json.decodeFromString<Map<String, String>>(encoded)
                    }.getOrDefault(emptyMap())
                }
                .orEmpty()
                .mapNotNull { (category, grouping) ->
                    CategoryGrouping.entries.firstOrNull { it.name == grouping }
                        ?.let { category to it }
                }
                .toMap()
            val categoryDefaultPages = prefs[CATEGORY_DEFAULT_PAGES_KEY]
                ?.let { encoded ->
                    runCatchingPreservingCancellation {
                        Json.decodeFromString<Map<String, String>>(encoded)
                    }.getOrDefault(emptyMap())
                }
                .orEmpty()
                .mapNotNull { (category, page) ->
                    parseCategoryLibraryPage(page)?.let { category to it }
                }
                .toMap()
            val categoryShowFileDetails =
                parseCategoryBooleanMap(prefs[CATEGORY_SHOW_FILE_DETAILS_KEY])
            val categoryAspectRatios =
                parseCategoryBooleanMap(prefs[CATEGORY_ASPECT_RATIOS_KEY])
            val categorySectioned =
                parseCategoryBooleanMap(prefs[CATEGORY_SECTIONED_KEY])

            val folderAspectRatio = prefs[GALLERY_FOLDER_ASPECT_RATIO_KEY]
                ?: SharedFilePreferences().folderAspectRatio

            val favoriteFilesStr = prefs[FAVORITE_FILES_KEY]
            val favoriteFiles: Set<String> = if (!favoriteFilesStr.isNullOrEmpty()) {
                runCatchingPreservingCancellation { Json.decodeFromString<Set<String>>(favoriteFilesStr) }.getOrDefault(emptySet())
            } else {
                emptySet()
            }

            val pinnedGalleryFoldersStr = prefs[PINNED_ALBUMS_KEY]
            val pinnedFolders: Set<String> = if (!pinnedGalleryFoldersStr.isNullOrEmpty()) {
                runCatchingPreservingCancellation { Json.decodeFromString<Set<String>>(pinnedGalleryFoldersStr) }.getOrDefault(emptySet())
            } else {
                emptySet()
            }

            val galleryFolderCoversStr = prefs[GALLERY_FOLDER_COVERS_KEY]
            val folderCovers: Map<String, String> = if (!galleryFolderCoversStr.isNullOrEmpty()) {
                runCatchingPreservingCancellation { Json.decodeFromString<Map<String, String>>(galleryFolderCoversStr) }.getOrDefault(emptyMap())
            } else {
                emptyMap()
            }
            val fileOpenBehaviors = prefs[FILE_OPEN_BEHAVIORS_KEY]
                ?.let { encoded ->
                    runCatchingPreservingCancellation {
                        Json.decodeFromString<Map<String, String>>(encoded)
                    }.getOrDefault(emptyMap())
                }
                .orEmpty()
                .mapNotNull { (category, behavior) ->
                    FileOpenBehavior.entries.firstOrNull { it.name == behavior }
                        ?.let { category to it }
                }
                .toMap()

            SharedFilePreferences(
                appStartPage = AppStartPage.entries.firstOrNull {
                    it.name == prefs[APP_START_PAGE_KEY]
                } ?: AppStartPage.HOME,
                globalPresentation = globalPresentation,
                recentPresentation = recentPresentation,
                pathPresentationOptions = pathMap.mapValues { it.value.normalized() },
                exactPathPresentationOptions = exactPathMap.mapValues { it.value.normalized() },
                homeRecentCarouselLimit = SharedFilePreferences.normalizeHomeRecentCarouselLimit(
                    prefs[HOME_RECENT_CAROUSEL_LIMIT_KEY] ?: SharedFilePreferences.DEFAULT_HOME_RECENT_CAROUSEL_LIMIT
                ),
                showHiddenFiles = prefs[SHOW_HIDDEN_FILES_KEY] ?: SharedFilePreferences().showHiddenFiles,
                imageGalleryShowFileDetails = prefs[IMAGE_GALLERY_SHOW_FILE_DETAILS_KEY]
                    ?: SharedFilePreferences().imageGalleryShowFileDetails,
                imageGalleryAspectRatio = prefs[IMAGE_GALLERY_ASPECT_RATIO_KEY]
                    ?: SharedFilePreferences().imageGalleryAspectRatio,
                imageGallerySectioned = prefs[IMAGE_GALLERY_SECTIONED_KEY]
                    ?: SharedFilePreferences().imageGallerySectioned,
                imageGalleryGrouping = grouping,
                imageGalleryDefaultPage = defaultPage,
                audioPresentation = audioPresentation,
                audioCollectionPresentation = audioCollectionPresentation,
                audioGrouping = audioGrouping,
                audioDefaultPage = audioDefaultPage,
                audioShowFileDetails = prefs[AUDIO_SHOW_FILE_DETAILS_KEY]
                    ?: defaults.audioShowFileDetails,
                audioFavoriteFiles = audioFavoriteFiles,
                audioPinnedFolders = audioPinnedFolders,
                audioFolderCovers = audioFolderCovers,
                categoryGroupings = categoryGroupings,
                categoryDefaultPages = categoryDefaultPages,
                categoryShowFileDetails = categoryShowFileDetails,
                categoryAspectRatios = categoryAspectRatios,
                categorySectioned = categorySectioned,
                folderPresentation = folderPresentation,
                folderAspectRatio = folderAspectRatio,
                favoriteFiles = favoriteFiles,
                pinnedFolders = pinnedFolders,
                folderCovers = folderCovers,
                lastOpenedPath = prefs[LAST_OPENED_PATH_KEY],
                lastOpenedVolumeId = prefs[LAST_OPENED_VOLUME_ID_KEY],
                fileOpenBehaviors = fileOpenBehaviors,
                defaultSaveToArcilePath = prefs[DEFAULT_SAVE_TO_ARCILE_PATH_KEY],
                browserScrollbarEnabled = prefs[BROWSER_SCROLLBAR_ENABLED_KEY]
                    ?: SharedFilePreferences().browserScrollbarEnabled,
                galleryScrollbarEnabled = prefs[GALLERY_SCROLLBAR_ENABLED_KEY]
                    ?: SharedFilePreferences().galleryScrollbarEnabled,
                browserTabsEnabled = prefs[BROWSER_TABS_ENABLED_KEY]
                    ?: SharedFilePreferences().browserTabsEnabled,
                rememberLastFolder = prefs[REMEMBER_LAST_FOLDER_KEY]
                    ?: SharedFilePreferences().rememberLastFolder,
                expandableBrowserAppBar = prefs[EXPANDABLE_BROWSER_APP_BAR_KEY]
                    ?: SharedFilePreferences().expandableBrowserAppBar
            )
        }
        .flowOn(dispatchers.io)

    val locationPreferencesFlow = preferencesFlow.asLocationPreferences()
    val recentFilesPreferencesFlow = preferencesFlow.asRecentFilesPreferences()
    val galleryPreferencesFlow = preferencesFlow.asGalleryPreferences()

    fun galleryPreferencesFlow(categoryName: String) =
        preferencesFlow.asGalleryPreferences(categoryName)
    val audioLibraryPreferencesFlow = preferencesFlow.map(AudioLibraryPreferences::from)
    val saveDestinationPreferencesFlow = preferencesFlow.asSaveDestinationPreferences()

    suspend fun updateAppStartPage(page: AppStartPage) {
        dataStore.edit { prefs ->
            prefs[APP_START_PAGE_KEY] = page.name
        }
    }

    suspend fun updateGlobalPresentation(presentation: FileListingPreferences) {
        val normalized = presentation.normalized()
        dataStore.edit { prefs ->
            prefs[GLOBAL_SORT_KEY] = normalized.sortOption.name
            prefs[GLOBAL_VIEW_MODE_KEY] = normalized.viewMode.name
            prefs[GLOBAL_LIST_ZOOM_KEY] = normalized.listZoom
            prefs[GLOBAL_GRID_MIN_CELL_SIZE_KEY] = normalized.gridMinCellSize
            prefs[GLOBAL_SHOW_THUMBNAILS_KEY] = normalized.showThumbnails
            prefs[GLOBAL_FOLDERS_FIRST_KEY] = normalized.foldersFirst
        }
    }

    suspend fun updateRecentPresentation(presentation: FileListingPreferences) {
        val normalized = presentation.normalized()
        dataStore.edit { prefs ->
            prefs[RECENT_SORT_KEY] = normalized.sortOption.name
            prefs[RECENT_VIEW_MODE_KEY] = normalized.viewMode.name
            prefs[RECENT_LIST_ZOOM_KEY] = normalized.listZoom
            prefs[RECENT_GRID_MIN_CELL_SIZE_KEY] = normalized.gridMinCellSize
            prefs[RECENT_SHOW_THUMBNAILS_KEY] = normalized.showThumbnails
            prefs[RECENT_FOLDERS_FIRST_KEY] = normalized.foldersFirst
        }
    }

    suspend fun updateHomeRecentCarouselLimit(limit: Int) {
        dataStore.edit { prefs ->
            prefs[HOME_RECENT_CAROUSEL_LIMIT_KEY] = SharedFilePreferences.normalizeHomeRecentCarouselLimit(limit)
        }
    }

    suspend fun updateShowHiddenFiles(show: Boolean) {
        dataStore.edit { prefs ->
            prefs[SHOW_HIDDEN_FILES_KEY] = show
        }
    }

    suspend fun updateBrowserScrollbarEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[BROWSER_SCROLLBAR_ENABLED_KEY] = enabled
        }
    }

    suspend fun updateBrowserTabsEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[BROWSER_TABS_ENABLED_KEY] = enabled
        }
    }

    suspend fun updateRememberLastFolder(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[REMEMBER_LAST_FOLDER_KEY] = enabled
        }
    }

    suspend fun updateExpandableAppBar(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[EXPANDABLE_BROWSER_APP_BAR_KEY] = enabled
        }
    }

    suspend fun updateGalleryScrollbarEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[GALLERY_SCROLLBAR_ENABLED_KEY] = enabled
        }
    }

    suspend fun updateMediaGalleryShowFileDetails(show: Boolean) {
        dataStore.edit { prefs ->
            prefs[IMAGE_GALLERY_SHOW_FILE_DETAILS_KEY] = show
        }
    }

    suspend fun updateMediaGalleryAspectRatio(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[IMAGE_GALLERY_ASPECT_RATIO_KEY] = enabled
        }
    }

    suspend fun updateMediaGallerySectioned(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[IMAGE_GALLERY_SECTIONED_KEY] = enabled
        }
    }

    suspend fun updateGalleryGrouping(grouping: CategoryGrouping) {
        dataStore.edit { prefs ->
            prefs[IMAGE_GALLERY_GROUPING_KEY] = grouping.name
        }
    }

    suspend fun updateGalleryDefaultPage(tab: CategoryLibraryPage) {
        dataStore.edit { prefs ->
            prefs[IMAGE_GALLERY_DEFAULT_TAB_KEY] = tab.name
        }
    }

    suspend fun updateFolderPresentation(presentation: FileListingPreferences) {
        val normalized = presentation.normalized()
        dataStore.edit { prefs ->
            prefs[GALLERY_FOLDER_SORT_OPTION_KEY] = normalized.sortOption.name
            prefs[GALLERY_FOLDER_VIEW_MODE_KEY] = normalized.viewMode.name
            prefs[GALLERY_FOLDER_GRID_MIN_CELL_SIZE_KEY] = normalized.gridMinCellSize
            prefs[GALLERY_FOLDER_FOLDERS_FIRST_KEY] = normalized.foldersFirst
        }
    }

    suspend fun updateMediaGalleryPresentation(presentation: FileListingPreferences) {
        updatePathPresentation(
            path = IMAGE_GALLERY_PRESENTATION_PATH,
            presentation = presentation,
            applyToSubfolders = false
        )
    }

    suspend fun updateFolderAspectRatio(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[GALLERY_FOLDER_ASPECT_RATIO_KEY] = enabled
        }
    }

    suspend fun updatePathPresentation(
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
                prefs[keys.showThumbnails] = normalized.showThumbnails
                prefs[keys.foldersFirst] = normalized.foldersFirst
            }
        }
    }

    suspend fun updateLastOpenedLocation(path: String, volumeId: String?) {
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

    suspend fun updateAudioPresentation(presentation: FileListingPreferences) {
        val normalized = presentation.normalized()
        dataStore.edit { prefs ->
            prefs[AUDIO_SORT_OPTION_KEY] = normalized.sortOption.name
            prefs[AUDIO_VIEW_MODE_KEY] = normalized.viewMode.name
            prefs[AUDIO_LIST_ZOOM_KEY] = normalized.listZoom
            prefs[AUDIO_GRID_MIN_CELL_SIZE_KEY] = normalized.gridMinCellSize
            prefs[AUDIO_FOLDERS_FIRST_KEY] = normalized.foldersFirst
        }
    }

    suspend fun updateAudioCollectionPresentation(presentation: FileListingPreferences) {
        val normalized = presentation.normalized()
        dataStore.edit { prefs ->
            prefs[AUDIO_COLLECTION_SORT_OPTION_KEY] = normalized.sortOption.name
            prefs[AUDIO_COLLECTION_VIEW_MODE_KEY] = normalized.viewMode.name
            prefs[AUDIO_COLLECTION_LIST_ZOOM_KEY] = normalized.listZoom
            prefs[AUDIO_COLLECTION_GRID_MIN_CELL_SIZE_KEY] = normalized.gridMinCellSize
            prefs[AUDIO_COLLECTION_FOLDERS_FIRST_KEY] = normalized.foldersFirst
        }
    }

    suspend fun updateAudioGrouping(grouping: CategoryGrouping) {
        dataStore.edit { prefs -> prefs[AUDIO_GROUPING_KEY] = grouping.name }
    }

    suspend fun updateAudioDefaultPage(tab: CategoryLibraryPage) {
        dataStore.edit { prefs -> prefs[AUDIO_DEFAULT_TAB_KEY] = tab.name }
    }

    suspend fun updateAudioShowFileDetails(show: Boolean) {
        dataStore.edit { prefs -> prefs[AUDIO_SHOW_FILE_DETAILS_KEY] = show }
    }

    suspend fun updateAudioFavorite(path: String, isFavorite: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[AUDIO_FAVORITE_FILES_KEY]
                ?.let { encoded ->
                    runCatchingPreservingCancellation {
                        Json.decodeFromString<Set<String>>(encoded)
                    }.getOrDefault(emptySet())
                }
                .orEmpty()
            prefs[AUDIO_FAVORITE_FILES_KEY] = Json.encodeToString(
                if (isFavorite) current + path else current - path
            )
        }
    }

    suspend fun clearMigratedAudioFavorites() {
        dataStore.edit { prefs -> prefs.remove(AUDIO_FAVORITE_FILES_KEY) }
    }

    suspend fun updateAudioPinnedFolder(path: String, isPinned: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[AUDIO_PINNED_FOLDERS_KEY]
                ?.let { encoded ->
                    runCatchingPreservingCancellation {
                        Json.decodeFromString<Set<String>>(encoded)
                    }.getOrDefault(emptySet())
                }
                .orEmpty()
            prefs[AUDIO_PINNED_FOLDERS_KEY] = Json.encodeToString(
                if (isPinned) current + path else current - path
            )
        }
    }

    suspend fun updateAudioFolderCover(folderPath: String, coverPath: String) {
        dataStore.edit { prefs ->
            val current = prefs[AUDIO_FOLDER_COVERS_KEY]
                ?.let { encoded ->
                    runCatchingPreservingCancellation {
                        Json.decodeFromString<Map<String, String>>(encoded)
                    }.getOrDefault(emptyMap())
                }
                .orEmpty()
            prefs[AUDIO_FOLDER_COVERS_KEY] = Json.encodeToString(
                if (coverPath.isBlank()) {
                    current - folderPath
                } else {
                    current + (folderPath to coverPath)
                }
            )
        }
    }

    suspend fun updateCategoryGrouping(
        categoryName: String,
        grouping: CategoryGrouping
    ) {
        dataStore.edit { prefs ->
            val current = prefs[CATEGORY_GROUPINGS_KEY]
                ?.let { encoded ->
                    runCatchingPreservingCancellation {
                        Json.decodeFromString<Map<String, String>>(encoded)
                    }.getOrDefault(emptyMap())
                }
                .orEmpty()
            prefs[CATEGORY_GROUPINGS_KEY] = Json.encodeToString(
                current + (categoryName to grouping.name)
            )
        }
    }

    suspend fun updateCategoryDefaultPage(
        categoryName: String,
        page: CategoryLibraryPage
    ) {
        dataStore.edit { prefs ->
            val current = prefs[CATEGORY_DEFAULT_PAGES_KEY]
                ?.let { encoded ->
                    runCatchingPreservingCancellation {
                        Json.decodeFromString<Map<String, String>>(encoded)
                    }.getOrDefault(emptyMap())
                }
                .orEmpty()
            prefs[CATEGORY_DEFAULT_PAGES_KEY] = Json.encodeToString(
                current + (categoryName to page.name)
            )
        }
    }

    suspend fun updateCategoryShowFileDetails(categoryName: String, show: Boolean) =
        writer.updateCategoryBoolean(CATEGORY_SHOW_FILE_DETAILS_KEY, categoryName, show)

    suspend fun updateCategoryAspectRatio(categoryName: String, enabled: Boolean) =
        writer.updateCategoryBoolean(CATEGORY_ASPECT_RATIOS_KEY, categoryName, enabled)

    suspend fun updateCategorySectioned(categoryName: String, enabled: Boolean) =
        writer.updateCategoryBoolean(CATEGORY_SECTIONED_KEY, categoryName, enabled)

    suspend fun updateFileOpenBehavior(categoryName: String, behavior: FileOpenBehavior) {
        dataStore.edit { prefs ->
            val current = prefs[FILE_OPEN_BEHAVIORS_KEY]
                ?.let { encoded ->
                    runCatchingPreservingCancellation {
                        Json.decodeFromString<Map<String, String>>(encoded)
                    }.getOrDefault(emptyMap())
                }
                .orEmpty()
            prefs[FILE_OPEN_BEHAVIORS_KEY] = Json.encodeToString(
                current + (categoryName to behavior.name)
            )
        }
    }

    suspend fun removeFileOpenBehavior(key: String) {
        dataStore.edit { prefs ->
            val current = prefs[FILE_OPEN_BEHAVIORS_KEY]
                ?.let { encoded ->
                    runCatchingPreservingCancellation {
                        Json.decodeFromString<Map<String, String>>(encoded)
                    }.getOrDefault(emptyMap())
                }
                .orEmpty()
            val updated = current - key
            if (updated.isEmpty()) {
                prefs.remove(FILE_OPEN_BEHAVIORS_KEY)
            } else {
                prefs[FILE_OPEN_BEHAVIORS_KEY] = Json.encodeToString(updated)
            }
        }
    }

    suspend fun updateDefaultSaveToArcilePath(path: String?) =
        writer.updateDefaultSaveToArcilePath(path)

    suspend fun updateFavorite(path: String, isFavorite: Boolean) =
        writer.updateFavorite(path, isFavorite)

    suspend fun updatePinnedFolder(folderPath: String, isPinned: Boolean) =
        writer.updatePinnedFolder(folderPath, isPinned)

    suspend fun updateFolderCover(folderPath: String, coverPath: String) =
        writer.updateFolderCover(folderPath, coverPath)

}
