package dev.qtremors.arcile.core.privilege.android

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.qtremors.arcile.core.privilege.PrivilegeMode
import dev.qtremors.arcile.core.privilege.PrivilegePreferenceState
import dev.qtremors.arcile.core.privilege.PrivilegePreferences
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

private val Context.privilegeDataStore by preferencesDataStore(name = "privilege_prefs")

class AndroidPrivilegePreferences internal constructor(
    private val dataStore: DataStore<Preferences>,
    private val dispatchers: ArcileDispatchers
) : PrivilegePreferences {
    constructor(context: Context, dispatchers: ArcileDispatchers) : this(
        dataStore = context.privilegeDataStore,
        dispatchers = dispatchers
    )

    override val state: Flow<PrivilegePreferenceState> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            PrivilegePreferenceState(
                mode = preferences[MODE_KEY]
                    ?.let { stored -> PrivilegeMode.entries.firstOrNull { it.name == stored } }
                    ?: PrivilegeMode.AUTOMATIC,
                rootPreviouslyAuthorized = preferences[ROOT_AUTHORIZED_KEY] ?: false,
                shizukuPreviouslyAuthorized = preferences[SHIZUKU_AUTHORIZED_KEY] ?: false,
                protectedFilesystemWritesEnabled = preferences[PROTECTED_WRITES_KEY] ?: false
            )
        }
        .flowOn(dispatchers.io)

    override suspend fun setMode(mode: PrivilegeMode) {
        dataStore.edit { it[MODE_KEY] = mode.name }
    }

    override suspend fun setRootPreviouslyAuthorized(authorized: Boolean) {
        dataStore.edit { it[ROOT_AUTHORIZED_KEY] = authorized }
    }

    override suspend fun setShizukuPreviouslyAuthorized(authorized: Boolean) {
        dataStore.edit { it[SHIZUKU_AUTHORIZED_KEY] = authorized }
    }

    override suspend fun setProtectedFilesystemWritesEnabled(enabled: Boolean) {
        dataStore.edit { it[PROTECTED_WRITES_KEY] = enabled }
    }

    private companion object {
        val MODE_KEY = stringPreferencesKey("preferred_mode")
        val ROOT_AUTHORIZED_KEY = booleanPreferencesKey("root_previously_authorized")
        val SHIZUKU_AUTHORIZED_KEY = booleanPreferencesKey("shizuku_previously_authorized")
        val PROTECTED_WRITES_KEY = booleanPreferencesKey("protected_filesystem_writes_enabled")
    }
}
