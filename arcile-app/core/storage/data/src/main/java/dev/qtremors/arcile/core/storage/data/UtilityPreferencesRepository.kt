package dev.qtremors.arcile.core.storage.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.qtremors.arcile.core.storage.domain.UtilityPreferencesStore
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.utilityDataStore by preferencesDataStore(name = "utility_prefs")

class UtilityPreferencesRepository(
    context: Context,
    private val dataStore: DataStore<Preferences> = context.utilityDataStore,
    private val dispatchers: ArcileDispatchers = ArcileDispatchers(
        io = Dispatchers.IO,
        default = Dispatchers.Default,
        main = Dispatchers.Main,
        storage = Dispatchers.IO
    )
) : UtilityPreferencesStore {
    private val HOME_UTILITY_IDS_KEY = stringSetPreferencesKey("home_utility_ids")
    private val defaultHomeUtilityIds = setOf("trash", "cleaner")
    private val allowedHomeUtilityIds = defaultHomeUtilityIds + "activity"

    override val homeUtilityIds: Flow<Set<String>> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs -> sanitizeHomeUtilityIds(prefs[HOME_UTILITY_IDS_KEY] ?: defaultHomeUtilityIds) }
        .flowOn(dispatchers.io)

    override suspend fun setHomeUtilityIds(ids: Set<String>) {
        dataStore.edit { prefs ->
            prefs[HOME_UTILITY_IDS_KEY] = sanitizeHomeUtilityIds(ids)
        }
    }

    private fun sanitizeHomeUtilityIds(ids: Set<String>): Set<String> =
        ids.filterTo(linkedSetOf()) { it in allowedHomeUtilityIds }
}
