package dev.qtremors.arcile.core.vault.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.qtremors.arcile.core.runtime.di.ApplicationScope
import dev.qtremors.arcile.core.vault.domain.VaultSecurityPreferences
import dev.qtremors.arcile.core.vault.domain.VaultSecuritySettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private val Context.vaultSecurityDataStore: DataStore<Preferences> by preferencesDataStore("onlyfiles_security")

@Singleton
class DefaultVaultSecurityPreferences @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope scope: CoroutineScope
) : VaultSecurityPreferences {
    private val store = context.vaultSecurityDataStore
    override val settings = store.data.map { preferences ->
        VaultSecuritySettings(
            screenshotProtectionEnabled = preferences[SCREENSHOT_PROTECTION] ?: true
        )
    }.stateIn(scope, SharingStarted.Eagerly, VaultSecuritySettings())

    override suspend fun setScreenshotProtectionEnabled(enabled: Boolean) {
        store.edit { it[SCREENSHOT_PROTECTION] = enabled }
    }

    private companion object {
        val SCREENSHOT_PROTECTION = booleanPreferencesKey("screenshot_protection_enabled")
    }
}
