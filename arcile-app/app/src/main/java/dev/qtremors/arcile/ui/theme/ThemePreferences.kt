package dev.qtremors.arcile.ui.theme

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "theme_prefs")

class ThemePreferences(private val context: Context) {

    companion object {
        val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        val ACCENT_COLOR_KEY = stringPreferencesKey("accent_color")
    }

    val themeState: Flow<ThemeState> = context.dataStore.data.map { preferences ->
        val themeModeStr = preferences[THEME_MODE_KEY] ?: ThemeMode.SYSTEM.name
        val accentColorStr = preferences[ACCENT_COLOR_KEY] ?: AccentColor.DYNAMIC.name

        ThemeState(
            themeMode = try { ThemeMode.valueOf(themeModeStr) } catch (e: Exception) { ThemeMode.SYSTEM },
            accentColor = try { AccentColor.valueOf(accentColorStr) } catch (e: Exception) { AccentColor.DYNAMIC }
        )
    }

    suspend fun saveThemeState(state: ThemeState) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = state.themeMode.name
            preferences[ACCENT_COLOR_KEY] = state.accentColor.name
        }
    }
}
