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

class BrowserPreferencesRepository(private val context: Context) {
    private val GLOBAL_SORT_KEY = stringPreferencesKey("global_sort_option")

    val preferencesFlow: Flow<BrowserPreferences> = context.browserDataStore.data
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
        prefs.asMap().forEach { (key, value) ->
            if (key.name.startsWith("path_sort_") && value is String) {
                val path = key.name.removePrefix("path_sort_")
                val sortOption = FileSortOption.entries.find { it.name == value }
                if (sortOption != null) {
                    pathMap[path] = sortOption
                }
            }
        }
        BrowserPreferences(globalSort, pathMap)
    }

    suspend fun updateGlobalSortOption(sortOption: FileSortOption) {
        context.browserDataStore.edit { prefs ->
            prefs[GLOBAL_SORT_KEY] = sortOption.name
        }
    }

    suspend fun updatePathSortOption(path: String, sortOption: FileSortOption?) {
        val normalizedPath = if (path.length > 1) path.trimEnd('/') else path
        val key = stringPreferencesKey("path_sort_$normalizedPath")
        context.browserDataStore.edit { prefs ->
            if (sortOption == null) {
                prefs.remove(key)
            } else {
                prefs[key] = sortOption.name
            }
        }
    }
}
