package dev.qtremors.arcile.testutil

import dev.qtremors.arcile.core.storage.data.BrowserPreferencesStore
import dev.qtremors.arcile.core.storage.domain.BrowserPreferences
import dev.qtremors.arcile.core.storage.domain.BrowserPresentationPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeBrowserPreferencesStore(
    initialPreferences: BrowserPreferences = BrowserPreferences()
) : BrowserPreferencesStore {
    private val preferences = MutableStateFlow(initialPreferences)
    override val preferencesFlow: Flow<BrowserPreferences> = preferences

    var lastUpdatedGlobalPresentation: BrowserPresentationPreferences? = null
    var lastUpdatedRecentPresentation: BrowserPresentationPreferences? = null
    var lastUpdatedPath: String? = null
    var lastUpdatedPathPresentation: BrowserPresentationPreferences? = null

    override suspend fun updateGlobalPresentation(presentation: BrowserPresentationPreferences) {
        lastUpdatedGlobalPresentation = presentation
        preferences.value = preferences.value.copy(globalPresentation = presentation)
    }

    override suspend fun updateRecentPresentation(presentation: BrowserPresentationPreferences) {
        lastUpdatedRecentPresentation = presentation
        preferences.value = preferences.value.copy(recentPresentation = presentation)
    }

    override suspend fun updatePathPresentation(
        path: String,
        presentation: BrowserPresentationPreferences?,
        applyToSubfolders: Boolean
    ) {
        lastUpdatedPath = path
        lastUpdatedPathPresentation = presentation
        val updatedMap = if (applyToSubfolders) {
            preferences.value.pathPresentationOptions.toMutableMap().apply {
                if (presentation == null) remove(path) else put(path, presentation)
            }
        } else {
            preferences.value.exactPathPresentationOptions.toMutableMap().apply {
                if (presentation == null) remove(path) else put(path, presentation)
            }
        }
        preferences.value = if (applyToSubfolders) {
            preferences.value.copy(pathPresentationOptions = updatedMap)
        } else {
            preferences.value.copy(exactPathPresentationOptions = updatedMap)
        }
    }

    override suspend fun updateLastOpenedLocation(path: String, volumeId: String?) {
        lastUpdatedPath = path
    }
}
