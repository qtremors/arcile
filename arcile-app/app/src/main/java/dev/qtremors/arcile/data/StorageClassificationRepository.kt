package dev.qtremors.arcile.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.qtremors.arcile.domain.StorageKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

private val Context.classificationDataStore by preferencesDataStore(name = "storage_classifications_prefs")

data class StorageClassification(
    val assignedKind: StorageKind,
    val lastSeenName: String?,
    val lastSeenPath: String?,
    val updatedAt: Long
)

interface StorageClassificationStore {
    fun observeClassifications(): Flow<Map<String, StorageClassification>>
    suspend fun getClassification(storageKey: String): StorageClassification?
    suspend fun setClassification(
        storageKey: String,
        kind: StorageKind,
        lastSeenName: String? = null,
        lastSeenPath: String? = null
    )
    suspend fun resetClassification(storageKey: String)
}

class StorageClassificationRepository(private val context: Context) : StorageClassificationStore {

    private fun JSONObject.optNullableString(name: String): String? =
        if (has(name) && !isNull(name)) getString(name) else null

    override fun observeClassifications(): Flow<Map<String, StorageClassification>> {
        return context.classificationDataStore.data.map { prefs ->
            val result = mutableMapOf<String, StorageClassification>()
            prefs.asMap().forEach { (key, value) ->
                if (value is String) {
                    try {
                        val json = JSONObject(value)
                        val kindName = json.getString("assignedKind")
                        val kind = StorageKind.entries.find { it.name == kindName }
                        if (kind != null) {
                            result[key.name] = StorageClassification(
                                assignedKind = kind,
                                lastSeenName = json.optNullableString("lastSeenName"),
                                lastSeenPath = json.optNullableString("lastSeenPath"),
                                updatedAt = json.optLong("updatedAt", 0L)
                            )
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("StorageClassification", "Failed to parse classification for key: ${key.name}", e)
                    }
                }
            }
            result
        }
    }

    override suspend fun getClassification(storageKey: String): StorageClassification? {
        val key = stringPreferencesKey(storageKey)
        val prefs = context.classificationDataStore.data.first()
        val jsonString = prefs[key] ?: return null
        return try {
            val json = JSONObject(jsonString)
            val kindName = json.getString("assignedKind")
            val kind = StorageKind.entries.find { it.name == kindName }
            if (kind != null) {
                StorageClassification(
                    assignedKind = kind,
                    lastSeenName = json.optNullableString("lastSeenName"),
                    lastSeenPath = json.optNullableString("lastSeenPath"),
                    updatedAt = json.optLong("updatedAt", 0L)
                )
            } else null
        } catch (e: Exception) {
            android.util.Log.e("StorageClassification", "Failed to parse classification for key: $storageKey", e)
            resetClassification(storageKey)
            null
        }
    }

    override suspend fun setClassification(
        storageKey: String,
        kind: StorageKind,
        lastSeenName: String?,
        lastSeenPath: String?
    ) {
        val key = stringPreferencesKey(storageKey)
        val jsonString = JSONObject().apply {
            put("assignedKind", kind.name)
            if (lastSeenName != null) put("lastSeenName", lastSeenName)
            if (lastSeenPath != null) put("lastSeenPath", lastSeenPath)
            put("updatedAt", System.currentTimeMillis())
        }.toString()

        context.classificationDataStore.edit { prefs ->
            prefs[key] = jsonString
        }
    }

    override suspend fun resetClassification(storageKey: String) {
        val key = stringPreferencesKey(storageKey)
        context.classificationDataStore.edit { prefs ->
            prefs.remove(key)
        }
    }
}
