package dev.qtremors.arcile.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.qtremors.arcile.domain.BrowserPreferences
import dev.qtremors.arcile.presentation.FileSortOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.browserDataStore by preferencesDataStore(name = "browser_prefs")

interface BrowserPreferencesStore {
    val preferencesFlow: Flow<BrowserPreferences>

    suspend fun updateGlobalSortOption(sortOption: FileSortOption)

    suspend fun updatePathSortOption(path: String, sortOption: FileSortOption?, applyToSubfolders: Boolean = false)
}

class BrowserPreferencesRepository(private val context: Context) : BrowserPreferencesStore {
    private val GLOBAL_SORT_KEY = stringPreferencesKey("global_sort_option")

    override val preferencesFlow: Flow<BrowserPreferences> = context.browserDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            val globalSortStr = prefs[GLOBAL_SORT_KEY] ?: FileSortOption.NAME_ASC.name
            val globalSort = FileSortOption.entries.find { it.name == globalSortStr } ?: FileSortOption.NAME_ASC

            val pathMap = mutableMapOf<String, FileSortOption>()
            val exactPathMap = mutableMapOf<String, FileSortOption>()
            prefs.asMap().forEach { (key, value) ->
                if (key.name.startsWith("path_sort_") && value is String) {
                    val path = key.name.removePrefix("path_sort_")
                    val sortOption = FileSortOption.entries.find { it.name == value }
                    if (sortOption != null) {
                        pathMap[path] = sortOption
                    }
                } else if (key.name.startsWith("exact_path_sort_") && value is String) {
                    val path = key.name.removePrefix("exact_path_sort_")
                    val sortOption = FileSortOption.entries.find { it.name == value }
                    if (sortOption != null) {
                        exactPathMap[path] = sortOption
                    }
                }
            }
            BrowserPreferences(globalSort, pathMap, exactPathMap)
        }

    override suspend fun updateGlobalSortOption(sortOption: FileSortOption) {
        context.browserDataStore.edit { prefs ->
            prefs[GLOBAL_SORT_KEY] = sortOption.name
        }
    }

    override suspend fun updatePathSortOption(path: String, sortOption: FileSortOption?, applyToSubfolders: Boolean) {
        val normalizedPath = if (path.length > 1) path.trimEnd('/') else path
        val recursiveKey = stringPreferencesKey("path_sort_$normalizedPath")
        val exactKey = stringPreferencesKey("exact_path_sort_$normalizedPath")
        
        context.browserDataStore.edit { prefs ->
            if (sortOption == null) {
                prefs.remove(recursiveKey)
                prefs.remove(exactKey)
            } else {
                if (applyToSubfolders) {
                    prefs[recursiveKey] = sortOption.name
                    prefs.remove(exactKey)
                } else {
                    prefs[exactKey] = sortOption.name
                    prefs.remove(recursiveKey)
                }
            }
        }
    }
}
