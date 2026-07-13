package dev.qtremors.arcile.core.storage.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.qtremors.arcile.core.storage.domain.CleanerGroupType
import dev.qtremors.arcile.core.storage.domain.CleanerSectionRule
import dev.qtremors.arcile.core.storage.domain.StorageCleanerPreferencesStore
import dev.qtremors.arcile.core.storage.domain.StorageCleanerRules
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException

val Context.storageCleanerDataStore by preferencesDataStore(name = "storage_cleaner_prefs")

class StorageCleanerPreferencesRepository(
    context: Context,
    private val dataStore: DataStore<Preferences> = context.storageCleanerDataStore,
    private val dispatchers: ArcileDispatchers = ArcileDispatchers(
        io = Dispatchers.IO,
        default = Dispatchers.Default,
        main = Dispatchers.Main,
        storage = Dispatchers.IO
    )
) : StorageCleanerPreferencesStore {
    private val rulesKey = stringPreferencesKey("rules")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override val rulesFlow: Flow<StorageCleanerRules> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            prefs[rulesKey]
                ?.let { encoded -> runCatchingPreservingCancellation { json.decodeFromString<StorageCleanerRules>(encoded) }.getOrNull() }
                ?.normalized()
                ?: StorageCleanerRules()
        }
        .flowOn(dispatchers.io)

    override suspend fun updateRules(rules: StorageCleanerRules) {
        dataStore.edit { prefs ->
            prefs[rulesKey] = json.encodeToString(rules.normalized())
        }
    }

    override suspend fun updateSectionRule(type: CleanerGroupType, rule: CleanerSectionRule) {
        mutateRules { it.withSection(type, rule) }
    }

    override suspend fun ignorePath(path: String) {
        mutateRules { it.withIgnoredPath(path) }
    }

    override suspend fun unignorePath(path: String) {
        mutateRules { it.withoutIgnoredPath(path) }
    }

    override suspend fun resetSection(type: CleanerGroupType) {
        mutateRules { it.withSection(type, CleanerSectionRule()) }
    }

    private suspend fun mutateRules(transform: (StorageCleanerRules) -> StorageCleanerRules) {
        dataStore.edit { prefs ->
            val current = prefs[rulesKey]
                ?.let { encoded -> runCatchingPreservingCancellation { json.decodeFromString<StorageCleanerRules>(encoded) }.getOrNull() }
                ?.normalized()
                ?: StorageCleanerRules()
            prefs[rulesKey] = json.encodeToString(transform(current).normalized())
        }
    }
}
