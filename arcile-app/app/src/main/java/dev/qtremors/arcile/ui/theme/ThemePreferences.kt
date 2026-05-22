package dev.qtremors.arcile.ui.theme

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "theme_prefs")

class ThemePreferences(private val context: Context) {

    companion object {
        val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        val ACCENT_COLOR_KEY = stringPreferencesKey("accent_color")
        val HARMONIZE_COLORS_KEY = booleanPreferencesKey("harmonize_colors")
    }

    val themeState: Flow<ThemeState> = context.dataStore.data.map { preferences ->
        val themeModeStr = preferences[THEME_MODE_KEY] ?: ThemeMode.SYSTEM.name
        val accentColorStr = preferences[ACCENT_COLOR_KEY] ?: AccentColor.DYNAMIC.name
        val harmonize = preferences[HARMONIZE_COLORS_KEY] ?: true

        ThemeState(
            themeMode = ThemeMode.values().find { it.name == themeModeStr } ?: ThemeMode.SYSTEM,
            accentColor = AccentColor.values().find { it.name == accentColorStr } ?: AccentColor.DYNAMIC,
            harmonizeColors = harmonize
        )
    }

    suspend fun saveThemeState(state: ThemeState) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = state.themeMode.name
            preferences[ACCENT_COLOR_KEY] = state.accentColor.name
            preferences[HARMONIZE_COLORS_KEY] = state.harmonizeColors
        }
    }
}
