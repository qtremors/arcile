package dev.qtremors.arcile.core.storage.domain

import kotlinx.coroutines.flow.Flow

data class SaveDestinationPreferences(
    val defaultPath: String? = null
) {
    companion object {
        fun from(preferences: BrowserPreferences) =
            SaveDestinationPreferences(defaultPath = preferences.defaultSaveToArcilePath)
    }
}

interface SaveDestinationPreferencesStore {
    val saveDestinationPreferencesFlow: Flow<SaveDestinationPreferences>
    suspend fun updateDefaultSaveToArcilePath(path: String?)
}
