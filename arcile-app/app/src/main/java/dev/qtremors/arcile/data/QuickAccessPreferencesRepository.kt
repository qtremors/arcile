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

    private fun createExternalHandoffItem(id: String, label: String, relativeDocPath: String) = QuickAccessItem(
        id = id,
        label = label,
        path = buildAndroidTreeUri(relativeDocPath),
        type = QuickAccessType.EXTERNAL_HANDOFF,
        handoffDescription = "Opens in the Android Files app due to platform restrictions.",
        isPinned = false,
        isEnabled = true
    )

    private fun buildAndroidTreeUri(relativeDocPath: String): String {
        val treeUri = android.provider.DocumentsContract.buildTreeDocumentUri(
            "com.android.externalstorage.documents",
            "primary"
        )
        return android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, "primary:$relativeDocPath").toString()
    }

    private fun migrateStoredItem(item: QuickAccessItem): QuickAccessItem {
        if (item.type == QuickAccessType.SAF_TREE && (item.label == "Android/data" || item.label == "Android/obb")) {
            return item.copy(
                type = QuickAccessType.EXTERNAL_HANDOFF,
                handoffDescription = "Opens in the Android Files app due to platform restrictions."
            )
        }
        return item
    }

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
            ),
            createExternalHandoffItem(
                id = "handoff_android_data",
                label = "Android/data",
                relativeDocPath = "Android/data"
            ),
            createExternalHandoffItem(
                id = "handoff_android_obb",
                label = "Android/obb",
                relativeDocPath = "Android/obb"
            )
        )
    }

    val quickAccessItems: Flow<List<QuickAccessItem>> = context.dataStore.data.map { preferences ->
        val serialized = preferences[QUICK_ACCESS_ITEMS_KEY]
        if (serialized.isNullOrEmpty()) {
            defaultItems
        } else {
            try {
                val storedItems = json.decodeFromString<List<QuickAccessItem>>(serialized).map(::migrateStoredItem)
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
                    json.decodeFromString<List<QuickAccessItem>>(currentStr).map(::migrateStoredItem)
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
