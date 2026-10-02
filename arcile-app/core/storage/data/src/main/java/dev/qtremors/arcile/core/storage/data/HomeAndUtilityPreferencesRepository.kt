package dev.qtremors.arcile.core.storage.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.qtremors.arcile.core.storage.domain.HomeAndUtilityPreferencesStore
import dev.qtremors.arcile.core.storage.domain.HomeLayoutPreferences
import dev.qtremors.arcile.core.storage.domain.HomeSectionIds
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.utilityDataStore by preferencesDataStore(name = "utility_prefs")

class HomeAndUtilityPreferencesRepository(
    context: Context,
    private val dataStore: DataStore<Preferences> = context.utilityDataStore,
    private val dispatchers: ArcileDispatchers = ArcileDispatchers(
        io = Dispatchers.IO,
        default = Dispatchers.Default,
        main = Dispatchers.Main,
        storage = Dispatchers.IO
    )
) : HomeAndUtilityPreferencesStore {
    private val HOME_UTILITY_IDS_KEY = stringSetPreferencesKey("home_utility_ids")
    private val HOME_UTILITY_ORDER_KEY = stringPreferencesKey("home_utility_order")
    private val HOME_SECTION_ORDER_KEY = stringPreferencesKey("home_section_order")
    private val HOME_SECTION_ENABLED_KEY = stringSetPreferencesKey("home_section_enabled")
    private val defaultHomeUtilityIds = listOf("trash", "cleaner")
    private val allowedHomeUtilityIds = listOf("trash", "cleaner", "activity", "onlyfiles")

    override val homeUtilityIds: Flow<List<String>> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            val ordered = prefs[HOME_UTILITY_ORDER_KEY]?.split(',')
            val legacy = prefs[HOME_UTILITY_IDS_KEY]
            sanitizeHomeUtilityIds(ordered ?: legacy?.let { ids ->
                allowedHomeUtilityIds.filter(ids::contains)
            } ?: defaultHomeUtilityIds)
        }
        .flowOn(dispatchers.io)

    override val homeLayoutPreferences: Flow<HomeLayoutPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            HomeLayoutPreferences(
                orderedSectionIds = sanitizeHomeSectionOrder(
                    prefs[HOME_SECTION_ORDER_KEY]?.split(',').orEmpty()
                ),
                enabledSectionIds = prefs[HOME_SECTION_ENABLED_KEY]
                    ?.filterTo(linkedSetOf()) { it in HomeSectionIds.ALL }
                    ?: HomeSectionIds.ALL.toSet()
            )
        }
        .flowOn(dispatchers.io)

    private val BATCH_RENAME_HISTORY_KEY = stringPreferencesKey("batch_rename_history")

    override val batchRenameHistory: Flow<List<String>> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            val raw = prefs[BATCH_RENAME_HISTORY_KEY] ?: ""
            if (raw.isBlank()) emptyList()
            else raw.split("\n").filter { it.isNotBlank() }
        }
        .flowOn(dispatchers.io)

    override suspend fun setHomeUtilityIds(ids: List<String>) {
        dataStore.edit { prefs ->
            prefs[HOME_UTILITY_ORDER_KEY] = sanitizeHomeUtilityIds(ids).joinToString(",")
            prefs.remove(HOME_UTILITY_IDS_KEY)
        }
    }

    override suspend fun addBatchRenameHistory(query: String) {
        if (query.isBlank()) return
        dataStore.edit { prefs ->
            val current = (prefs[BATCH_RENAME_HISTORY_KEY] ?: "")
                .split("\n")
                .filter { it.isNotBlank() }
                .toMutableList()
            current.remove(query)
            current.add(0, query)
            prefs[BATCH_RENAME_HISTORY_KEY] = current.take(15).joinToString("\n")
        }
    }

    override suspend fun setHomeLayoutPreferences(preferences: HomeLayoutPreferences) {
        dataStore.edit { prefs ->
            prefs[HOME_SECTION_ORDER_KEY] =
                sanitizeHomeSectionOrder(preferences.orderedSectionIds).joinToString(",")
            prefs[HOME_SECTION_ENABLED_KEY] = preferences.enabledSectionIds
                .filterTo(linkedSetOf()) { it in HomeSectionIds.ALL }
        }
    }

    override suspend fun removeBatchRenameHistory(query: String) {
        dataStore.edit { prefs ->
            val remaining = (prefs[BATCH_RENAME_HISTORY_KEY] ?: "")
                .split("\n")
                .filter { it.isNotBlank() && it != query }
            if (remaining.isEmpty()) {
                prefs.remove(BATCH_RENAME_HISTORY_KEY)
            } else {
                prefs[BATCH_RENAME_HISTORY_KEY] = remaining.joinToString("\n")
            }
        }
    }

    override suspend fun clearBatchRenameHistory() {
        dataStore.edit { prefs ->
            prefs.remove(BATCH_RENAME_HISTORY_KEY)
        }
    }

    private fun sanitizeHomeUtilityIds(ids: List<String>): List<String> =
        ids.filter { it in allowedHomeUtilityIds }.distinct()

    private fun sanitizeHomeSectionOrder(ids: List<String>): List<String> {
        val knownIds = ids.filter { it in HomeSectionIds.ALL }.distinct()
        return knownIds + HomeSectionIds.ALL.filterNot(knownIds::contains)
    }
}
