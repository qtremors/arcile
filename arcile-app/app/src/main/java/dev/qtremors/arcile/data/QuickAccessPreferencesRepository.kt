package dev.qtremors.arcile.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.qtremors.arcile.domain.QuickAccessItem
import dev.qtremors.arcile.domain.QuickAccessType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import android.os.Environment
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "quick_access_prefs")

@Singleton
class QuickAccessPreferencesRepository @Inject constructor(
    private val context: Context
) {
    private val QUICK_ACCESS_ITEMS_KEY = stringPreferencesKey("quick_access_items")

    private val json = Json { ignoreUnknownKeys = true }

    private val defaultItems: List<QuickAccessItem> by lazy {
        val root = Environment.getExternalStorageDirectory()
        listOf(
            QuickAccessItem(
                id = "standard_downloads",
                label = "Downloads",
                path = File(root, Environment.DIRECTORY_DOWNLOADS).absolutePath,
                type = QuickAccessType.STANDARD,
                isPinned = true,
                isEnabled = true
            ),
            QuickAccessItem(
                id = "standard_dcim",
                label = "DCIM",
                path = File(root, Environment.DIRECTORY_DCIM).absolutePath,
                type = QuickAccessType.STANDARD,
                isPinned = true,
                isEnabled = true
            ),
            QuickAccessItem(
                id = "standard_documents",
                label = "Documents",
                path = File(root, Environment.DIRECTORY_DOCUMENTS).absolutePath,
                type = QuickAccessType.STANDARD,
                isPinned = true,
                isEnabled = true
            ),
            QuickAccessItem(
                id = "standard_pictures",
                label = "Pictures",
                path = File(root, Environment.DIRECTORY_PICTURES).absolutePath,
                type = QuickAccessType.STANDARD,
                isPinned = false,
                isEnabled = true
            ),
            QuickAccessItem(
                id = "standard_music",
                label = "Music",
                path = File(root, Environment.DIRECTORY_MUSIC).absolutePath,
                type = QuickAccessType.STANDARD,
                isPinned = false,
                isEnabled = true
            ),
            QuickAccessItem(
                id = "standard_movies",
                label = "Movies",
                path = File(root, Environment.DIRECTORY_MOVIES).absolutePath,
                type = QuickAccessType.STANDARD,
                isPinned = false,
                isEnabled = true
            )
        )
    }

    val quickAccessItems: Flow<List<QuickAccessItem>> = context.dataStore.data.map { preferences ->
        val serialized = preferences[QUICK_ACCESS_ITEMS_KEY]
        if (serialized.isNullOrEmpty()) {
            defaultItems
        } else {
            try {
                val storedItems = json.decodeFromString<List<QuickAccessItem>>(serialized)
                val merged = defaultItems.map { defaultItem ->
                    storedItems.find { it.id == defaultItem.id } ?: defaultItem
                } + storedItems.filter { it.type != QuickAccessType.STANDARD }
                merged
            } catch (e: Exception) {
                defaultItems
            }
        }
    }

    suspend fun updateItems(items: List<QuickAccessItem>) {
        context.dataStore.edit { preferences ->
            preferences[QUICK_ACCESS_ITEMS_KEY] = json.encodeToString(items)
        }
    }

    suspend fun addItem(item: QuickAccessItem) {
        context.dataStore.edit { preferences ->
            val currentStr = preferences[QUICK_ACCESS_ITEMS_KEY]
            val currentList = if (currentStr.isNullOrEmpty()) {
                defaultItems
            } else {
                try {
                    json.decodeFromString<List<QuickAccessItem>>(currentStr)
                } catch (e: Exception) {
                    defaultItems
                }
            }
            if (currentList.none { it.path == item.path }) {
                val newList = currentList + item
                preferences[QUICK_ACCESS_ITEMS_KEY] = json.encodeToString(newList)
            }
        }
    }

    suspend fun removeItem(id: String) {
         context.dataStore.edit { preferences ->
            val currentStr = preferences[QUICK_ACCESS_ITEMS_KEY]
            val currentList = if (currentStr.isNullOrEmpty()) defaultItems else json.decodeFromString<List<QuickAccessItem>>(currentStr)
            val newList = currentList.filter { it.id != id }
            preferences[QUICK_ACCESS_ITEMS_KEY] = json.encodeToString(newList)
        }
    }
}
