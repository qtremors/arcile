package dev.qtremors.arcile.core.storage.data

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.qtremors.arcile.core.storage.domain.CategoryLibraryPage
import dev.qtremors.arcile.core.storage.domain.FileListingPreferences
import dev.qtremors.arcile.core.storage.domain.FileSortOption
import dev.qtremors.arcile.core.storage.domain.FileViewMode
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

internal fun parseSortOption(value: String?, fallback: FileSortOption): FileSortOption {
    return FileSortOption.entries.find { it.name == value } ?: fallback
}

internal fun parseViewMode(value: String?, fallback: FileViewMode): FileViewMode {
    return FileViewMode.entries.find { it.name == value } ?: fallback
}

internal fun parseCategoryLibraryPage(value: String?): CategoryLibraryPage? = when (value) {
    CategoryLibraryPage.ITEMS.name,
    "PHOTOS",
    "AUDIO" -> CategoryLibraryPage.ITEMS
    CategoryLibraryPage.FOLDERS.name,
    "ALBUMS" -> CategoryLibraryPage.FOLDERS
    else -> null
}

internal fun parseCategoryBooleanMap(value: String?): Map<String, Boolean> =
    value?.let { encoded ->
        runCatchingPreservingCancellation {
            Json.decodeFromString<Map<String, Boolean>>(encoded)
        }.getOrDefault(emptyMap())
    }.orEmpty()


internal fun currentPresentation(
    existing: FileListingPreferences?,
    globalPresentation: FileListingPreferences
): FileListingPreferences {
    return existing ?: globalPresentation
}

internal fun presentationKeys(path: String, recursive: Boolean): PresentationKeys {
    val prefix = if (recursive) "path" else "exact_path"
    return PresentationKeys(
        sort = stringPreferencesKey("${prefix}_sort_$path"),
        viewMode = stringPreferencesKey("${prefix}_view_mode_$path"),
        listZoom = floatPreferencesKey("${prefix}_list_zoom_$path"),
        gridMinCellSize = floatPreferencesKey("${prefix}_grid_min_cell_size_$path"),
        showThumbnails = booleanPreferencesKey("${prefix}_show_thumbnails_$path")
    )
}

internal data class PresentationKeys(
    val sort: androidx.datastore.preferences.core.Preferences.Key<String>,
    val viewMode: androidx.datastore.preferences.core.Preferences.Key<String>,
    val listZoom: androidx.datastore.preferences.core.Preferences.Key<Float>,
    val gridMinCellSize: androidx.datastore.preferences.core.Preferences.Key<Float>,
    val showThumbnails: androidx.datastore.preferences.core.Preferences.Key<Boolean>
) {
    fun all(): List<androidx.datastore.preferences.core.Preferences.Key<*>> =
        listOf(sort, viewMode, listZoom, gridMinCellSize, showThumbnails)
}
