package dev.qtremors.arcile.core.storage.data

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

internal const val IMAGE_GALLERY_PRESENTATION_PATH = "image_gallery"
internal val GLOBAL_SORT_KEY = stringPreferencesKey("global_sort_option")
internal val GLOBAL_VIEW_MODE_KEY = stringPreferencesKey("global_view_mode")
internal val GLOBAL_LIST_ZOOM_KEY = floatPreferencesKey("global_list_zoom")
internal val GLOBAL_GRID_MIN_CELL_SIZE_KEY = floatPreferencesKey("global_grid_min_cell_size")
internal val GLOBAL_SHOW_THUMBNAILS_KEY = booleanPreferencesKey("global_show_thumbnails")
internal val RECENT_SORT_KEY = stringPreferencesKey("recent_sort_option")
internal val RECENT_VIEW_MODE_KEY = stringPreferencesKey("recent_view_mode")
internal val RECENT_LIST_ZOOM_KEY = floatPreferencesKey("recent_list_zoom")
internal val RECENT_GRID_MIN_CELL_SIZE_KEY = floatPreferencesKey("recent_grid_min_cell_size")
internal val RECENT_SHOW_THUMBNAILS_KEY = booleanPreferencesKey("recent_show_thumbnails")
internal val HOME_RECENT_CAROUSEL_LIMIT_KEY = intPreferencesKey("home_recent_carousel_limit")
internal val SHOW_HIDDEN_FILES_KEY = booleanPreferencesKey("show_hidden_files")
internal val BROWSER_SCROLLBAR_ENABLED_KEY = booleanPreferencesKey("browser_scrollbar_enabled")
internal val GALLERY_SCROLLBAR_ENABLED_KEY = booleanPreferencesKey("gallery_scrollbar_enabled")
internal val IMAGE_GALLERY_SHOW_FILE_DETAILS_KEY =
    booleanPreferencesKey("image_gallery_show_file_details")
internal val IMAGE_GALLERY_ASPECT_RATIO_KEY = booleanPreferencesKey("image_gallery_aspect_ratio")
internal val IMAGE_GALLERY_SECTIONED_KEY = booleanPreferencesKey("image_gallery_sectioned")
internal val IMAGE_GALLERY_GROUPING_KEY = stringPreferencesKey("image_gallery_grouping")
internal val IMAGE_GALLERY_DEFAULT_TAB_KEY = stringPreferencesKey("image_gallery_default_tab")
internal val ALBUM_VIEW_MODE_KEY = stringPreferencesKey("album_view_mode")
internal val ALBUM_GRID_MIN_CELL_SIZE_KEY = floatPreferencesKey("album_grid_min_cell_size")
internal val ALBUM_SORT_OPTION_KEY = stringPreferencesKey("album_sort_option")
internal val ALBUM_ASPECT_RATIO_KEY = booleanPreferencesKey("album_aspect_ratio")
internal val LAST_OPENED_PATH_KEY = stringPreferencesKey("last_opened_path")
internal val LAST_OPENED_VOLUME_ID_KEY = stringPreferencesKey("last_opened_volume_id")
internal val DEFAULT_SAVE_TO_ARCILE_PATH_KEY = stringPreferencesKey("default_save_to_arcile_path")
internal val FAVORITE_FILES_KEY = stringPreferencesKey("gallery_favorites")
internal val PINNED_ALBUMS_KEY = stringPreferencesKey("gallery_pinned_albums")
internal val ALBUM_COVERS_KEY = stringPreferencesKey("gallery_album_covers")
