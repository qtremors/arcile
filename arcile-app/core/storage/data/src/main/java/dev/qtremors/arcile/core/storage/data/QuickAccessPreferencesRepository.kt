package dev.qtremors.arcile.core.storage.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.qtremors.arcile.core.storage.domain.QuickAccessItem
import dev.qtremors.arcile.core.storage.domain.QuickAccessPreferencesStore
import dev.qtremors.arcile.core.storage.domain.QuickAccessType
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
    private val context: Context,
    private val dataStore: DataStore<Preferences> = context.dataStore
) : QuickAccessPreferencesStore {
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
        if (item.type == QuickAccessType.FILES_APP || item.id == "handoff_files_app") {
            return item.copy(
                path = buildAndroidTreeUri(""),
                type = QuickAccessType.FILES_APP,
                handoffDescription = "Open the Android Files app."
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
                id = "handoff_files_app",
                label = "Files",
                path = buildAndroidTreeUri(""),
                type = QuickAccessType.FILES_APP,
                handoffDescription = "Open the Android Files app.",
                isPinned = true,
                isEnabled = true
            ),
            QuickAccessItem(
                id = "standard_whatsapp_media",
                label = "WhatsApp",
                path = "/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media",
                type = QuickAccessType.STANDARD,
                isPinned = true,
                isEnabled = true
            ),
            QuickAccessItem(
                id = "internal_all_files",
                label = "Arcile",
                path = "",
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

    private fun decodeStoredItems(serialized: String?): List<QuickAccessItem> {
        if (serialized.isNullOrEmpty()) return defaultItems
        return runCatching {
            json.decodeFromString<List<QuickAccessItem>>(serialized).map(::migrateStoredItem)
        }.getOrDefault(defaultItems)
    }

    private fun mergeDefaultAndStored(storedItems: List<QuickAccessItem>): List<QuickAccessItem> {
        val storedIds = storedItems.map { it.id }.toSet()
        val missingDefaultItems = defaultItems.filter { it.id !in storedIds }
        return (storedItems + missingDefaultItems)
            .distinctBy { it.id }
            .filter { it.isEnabled }
    }

    override val quickAccessItems: Flow<List<QuickAccessItem>> = dataStore.data.map { preferences ->
        val serialized = preferences[QUICK_ACCESS_ITEMS_KEY]
        if (serialized.isNullOrEmpty()) {
            defaultItems
        } else {
            mergeDefaultAndStored(decodeStoredItems(serialized))
        }
    }

    override suspend fun updateItems(items: List<QuickAccessItem>) {
        dataStore.edit { preferences ->
            val storedTombstones = decodeStoredItems(preferences[QUICK_ACCESS_ITEMS_KEY])
                .filter { !it.isEnabled && defaultItems.any { defaultItem -> defaultItem.id == it.id } }
            preferences[QUICK_ACCESS_ITEMS_KEY] = json.encodeToString((items + storedTombstones).distinctBy { it.id })
        }
    }

    override suspend fun addItem(item: QuickAccessItem) {
        dataStore.edit { preferences ->
            val currentList = decodeStoredItems(preferences[QUICK_ACCESS_ITEMS_KEY])
            val existing = currentList.firstOrNull { it.id == item.id || it.path == item.path }
            if (existing == null) {
                preferences[QUICK_ACCESS_ITEMS_KEY] = json.encodeToString(currentList + item)
            } else if (!existing.isEnabled) {
                preferences[QUICK_ACCESS_ITEMS_KEY] = json.encodeToString(
                    (currentList.filter { it.id != existing.id } + item).distinctBy { it.id }
                )
            }
        }
    }

    override suspend fun removeItem(id: String) {
         dataStore.edit { preferences ->
            val currentList = decodeStoredItems(preferences[QUICK_ACCESS_ITEMS_KEY])
            val defaultItem = defaultItems.firstOrNull { it.id == id }
            val tombstone = defaultItem
                ?.takeIf { it.type != QuickAccessType.STANDARD }
                ?.copy(isEnabled = false, isPinned = false)
            val newList = (currentList.filter { it.id != id } + listOfNotNull(tombstone))
                .distinctBy { it.id }
            preferences[QUICK_ACCESS_ITEMS_KEY] = json.encodeToString(newList)
        }
    }
}
