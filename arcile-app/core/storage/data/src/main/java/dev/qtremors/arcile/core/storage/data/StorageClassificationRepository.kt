package dev.qtremors.arcile.core.storage.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.qtremors.arcile.core.storage.domain.StorageClassification
import dev.qtremors.arcile.core.storage.domain.StorageClassificationStore
import dev.qtremors.arcile.core.storage.domain.StorageKind
import dev.qtremors.arcile.utils.AppLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.classificationDataStore by preferencesDataStore(name = "storage_classifications_prefs")

class StorageClassificationRepository(
    context: Context,
    private val dataStore: DataStore<Preferences> = context.classificationDataStore
) : StorageClassificationStore {

    private val jsonFormat = Json { ignoreUnknownKeys = true }

    override fun observeClassifications(): Flow<Map<String, StorageClassification>> {
        return dataStore.data.map { prefs ->
            val result = mutableMapOf<String, StorageClassification>()
            prefs.asMap().forEach { (key, value) ->
                if (value is String) {
                    try {
                        val classification = jsonFormat.decodeFromString<StorageClassification>(value)
                        result[key.name] = classification
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        AppLogger.e("StorageClassification", "Failed to parse stored classification", e)
                    }
                }
            }
            result
        }
    }

    override suspend fun getClassification(storageKey: String): StorageClassification? {
        val key = stringPreferencesKey(storageKey)
        val prefs = dataStore.data.first()
        val jsonString = prefs[key] ?: return null
        return try {
            jsonFormat.decodeFromString<StorageClassification>(jsonString)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            AppLogger.e("StorageClassification", "Failed to parse stored classification", e)
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

        dataStore.edit { prefs ->
            prefs[key] = jsonString
        }
    }

    override suspend fun resetClassification(storageKey: String) {
        val key = stringPreferencesKey(storageKey)
        dataStore.edit { prefs ->
            prefs.remove(key)
        }
    }
}


