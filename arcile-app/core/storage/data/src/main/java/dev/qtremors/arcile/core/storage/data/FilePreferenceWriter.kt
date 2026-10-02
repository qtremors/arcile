package dev.qtremors.arcile.core.storage.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal class FilePreferenceWriter(
    private val dataStore: DataStore<Preferences>
) {
    suspend fun updateDefaultSaveToArcilePath(path: String?) {
        dataStore.edit { prefs ->
            if (path.isNullOrBlank()) {
                prefs.remove(DEFAULT_SAVE_TO_ARCILE_PATH_KEY)
            } else {
                prefs[DEFAULT_SAVE_TO_ARCILE_PATH_KEY] = path
            }
        }
    }

    suspend fun updateFavorite(path: String, isFavorite: Boolean) {
        dataStore.edit { prefs ->
            val favoriteFilesStr = prefs[FAVORITE_FILES_KEY]
            val currentFavorites = if (!favoriteFilesStr.isNullOrEmpty()) {
                runCatchingPreservingCancellation { Json.decodeFromString<Set<String>>(favoriteFilesStr) }.getOrDefault(emptySet())
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

    suspend fun updatePinnedFolder(folderPath: String, isPinned: Boolean) {
        dataStore.edit { prefs ->
            val pinnedStr = prefs[PINNED_ALBUMS_KEY]
            val currentPinned = if (!pinnedStr.isNullOrEmpty()) {
                runCatchingPreservingCancellation { Json.decodeFromString<Set<String>>(pinnedStr) }.getOrDefault(emptySet())
            } else {
                emptySet()
            }
            val newPinned = if (isPinned) {
                currentPinned + folderPath
            } else {
                currentPinned - folderPath
            }
            prefs[PINNED_ALBUMS_KEY] = Json.encodeToString(newPinned)
        }
    }

    suspend fun updateFolderCover(folderPath: String, coverPath: String) {
        dataStore.edit { prefs ->
            val galleryFolderCoversStr = prefs[GALLERY_FOLDER_COVERS_KEY]
            val currentCovers = if (!galleryFolderCoversStr.isNullOrEmpty()) {
                runCatchingPreservingCancellation { Json.decodeFromString<Map<String, String>>(galleryFolderCoversStr) }.getOrDefault(emptyMap())
            } else {
                emptyMap()
            }
            val newCovers = if (coverPath.isEmpty()) {
                currentCovers - folderPath
            } else {
                currentCovers + (folderPath to coverPath)
            }
            prefs[GALLERY_FOLDER_COVERS_KEY] = Json.encodeToString(newCovers)
        }
    }

    internal suspend fun updateCategoryBoolean(
        key: androidx.datastore.preferences.core.Preferences.Key<String>,
        categoryName: String,
        value: Boolean
    ) {
        dataStore.edit { prefs ->
            val current = parseCategoryBooleanMap(prefs[key])
            prefs[key] = Json.encodeToString(current + (categoryName to value))
        }
    }
}
