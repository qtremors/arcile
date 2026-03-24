package dev.qtremors.arcile.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.qtremors.arcile.domain.StorageKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.classificationDataStore by preferencesDataStore(name = "storage_classifications_prefs")

@Serializable
data class StorageClassification(
    val assignedKind: StorageKind,
    val lastSeenName: String? = null,
    val lastSeenPath: String? = null,
    val updatedAt: Long = 0L
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

    private val jsonFormat = Json { ignoreUnknownKeys = true }

    override fun observeClassifications(): Flow<Map<String, StorageClassification>> {
        return context.classificationDataStore.data.map { prefs ->
            val result = mutableMapOf<String, StorageClassification>()
            prefs.asMap().forEach { (key, value) ->
                if (value is String) {
                    try {
                        val classification = jsonFormat.decodeFromString<StorageClassification>(value)
                        result[key.name] = classification
                    } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
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
            jsonFormat.decodeFromString<StorageClassification>(jsonString)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
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
        val classification = StorageClassification(
            assignedKind = kind,
            lastSeenName = lastSeenName,
            lastSeenPath = lastSeenPath,
            updatedAt = System.currentTimeMillis()
        )
        val jsonString = jsonFormat.encodeToString(classification)

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
