package dev.qtremors.arcile.core.storage.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.qtremors.arcile.core.storage.domain.ActivityLogEntry
import dev.qtremors.arcile.core.storage.domain.ActivityLogStore
import dev.qtremors.arcile.di.ArcileDispatchers
import dev.qtremors.arcile.utils.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import java.util.UUID

val Context.activityLogDataStore by preferencesDataStore(name = "activity_log")

class ActivityLogRepository(
    context: Context,
    private val dataStore: DataStore<Preferences> = context.activityLogDataStore,
    private val dispatchers: ArcileDispatchers = ArcileDispatchers(
        io = Dispatchers.IO,
        default = Dispatchers.Default,
        main = Dispatchers.Main,
        storage = Dispatchers.IO
    )
) : ActivityLogStore {
    private val entriesKey = stringPreferencesKey("entries")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "kind"
    }

    override val entries: Flow<List<ActivityLogEntry>> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs -> decodeEntries(prefs[entriesKey]) }
        .flowOn(dispatchers.io)

    override suspend fun recordFolderOpened(path: String, volumeId: String?) {
        append(
            ActivityLogEntry.FolderOpened(
                id = UUID.randomUUID().toString(),
                timestampMillis = System.currentTimeMillis(),
                path = path,
                volumeId = volumeId
            )
        )
    }

    override suspend fun upsertFileOperation(entry: ActivityLogEntry.FileOperation) {
        withContext(dispatchers.io) {
            dataStore.edit { prefs ->
                val existing = decodeEntries(prefs[entriesKey])
                    .filterNot { it is ActivityLogEntry.FileOperation && it.operationId == entry.operationId }
                prefs[entriesKey] = json.encodeToString(trim(listOf(entry) + existing))
            }
        }
    }

    override suspend fun clear() {
        withContext(dispatchers.io) {
            dataStore.edit { prefs -> prefs.remove(entriesKey) }
        }
    }

    private suspend fun append(entry: ActivityLogEntry) {
        withContext(dispatchers.io) {
            dataStore.edit { prefs ->
                prefs[entriesKey] = json.encodeToString(trim(listOf(entry) + decodeEntries(prefs[entriesKey])))
            }
        }
    }

    private fun decodeEntries(encoded: String?): List<ActivityLogEntry> {
        if (encoded.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString<List<ActivityLogEntry>>(encoded)
        } catch (error: SerializationException) {
            AppLogger.w("ActivityLog", "Dropping unreadable activity log", error)
            emptyList()
        } catch (error: IllegalArgumentException) {
            AppLogger.w("ActivityLog", "Dropping unreadable activity log", error)
            emptyList()
        }
    }

    private fun trim(entries: List<ActivityLogEntry>): List<ActivityLogEntry> =
        entries.sortedByDescending { it.timestampMillis }.take(MAX_ENTRIES)

    private companion object {
        const val MAX_ENTRIES = 1_000
    }
}
