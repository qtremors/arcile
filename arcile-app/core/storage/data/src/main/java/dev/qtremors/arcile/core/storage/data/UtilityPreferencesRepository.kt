package dev.qtremors.arcile.core.storage.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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
    private val HOME_UTILITY_ORDER_KEY = stringPreferencesKey("home_utility_order")
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

    override suspend fun setHomeUtilityIds(ids: List<String>) {
        dataStore.edit { prefs ->
            prefs[HOME_UTILITY_ORDER_KEY] = sanitizeHomeUtilityIds(ids).joinToString(",")
            prefs.remove(HOME_UTILITY_IDS_KEY)
        }
    }

    private fun sanitizeHomeUtilityIds(ids: List<String>): List<String> =
        ids.filter { it in allowedHomeUtilityIds }.distinct()
}
