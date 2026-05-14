package dev.qtremors.arcile.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.qtremors.arcile.domain.OnboardingPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.onboardingDataStore by preferencesDataStore(name = "onboarding_prefs")

interface OnboardingPreferencesStore {
    val preferencesFlow: Flow<OnboardingPreferences>

    suspend fun markCompleted(completedVersion: Int, notificationPermissionHandled: Boolean)

    suspend fun markNotificationPermissionHandled()
}

class OnboardingPreferencesRepository(
    context: Context,
    private val dataStore: DataStore<Preferences> = context.onboardingDataStore
) : OnboardingPreferencesStore {
    private val IS_COMPLETED_KEY = booleanPreferencesKey("is_completed")
    private val COMPLETED_VERSION_KEY = intPreferencesKey("completed_version")
    private val NOTIFICATION_PERMISSION_HANDLED_KEY = booleanPreferencesKey("notification_permission_handled")

    override val preferencesFlow: Flow<OnboardingPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            OnboardingPreferences(
                isCompleted = preferences[IS_COMPLETED_KEY] ?: false,
                completedVersion = preferences[COMPLETED_VERSION_KEY] ?: 0,
                notificationPermissionHandled = preferences[NOTIFICATION_PERMISSION_HANDLED_KEY] ?: false
            )
        }
        .flowOn(Dispatchers.IO)

    override suspend fun markCompleted(completedVersion: Int, notificationPermissionHandled: Boolean) {
        dataStore.edit { preferences ->
            preferences[IS_COMPLETED_KEY] = true
            preferences[COMPLETED_VERSION_KEY] = completedVersion
            preferences[NOTIFICATION_PERMISSION_HANDLED_KEY] = notificationPermissionHandled
        }
    }

    override suspend fun markNotificationPermissionHandled() {
        dataStore.edit { preferences ->
            preferences[NOTIFICATION_PERMISSION_HANDLED_KEY] = true
        }
    }
}
