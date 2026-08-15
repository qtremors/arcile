package dev.qtremors.arcile.presentation.ui

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.browserTabsDataStore by preferencesDataStore(name = "browser_tabs")
private val PINNED_BROWSER_TABS_KEY = stringPreferencesKey("pinned_browser_tabs")
private val BROWSER_TABS_ENABLED_KEY = booleanPreferencesKey("browser_tabs_enabled")

@Serializable
internal data class PersistedBrowserTab(
    val id: Int,
    val entryType: String,
    val path: String? = null,
    val name: String? = null,
    val volumeId: String? = null,
    val entryPrefix: String? = null,
    val isRootStorageScope: Boolean = false,
    val backendId: String? = null,
    val browserPage: Int = BROWSER_PAGE
)

@Singleton
internal class BrowserTabsPreferencesRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val dataStore: DataStore<Preferences> = context.browserTabsDataStore

    private val preferences = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }

    val pinnedTabs: Flow<List<PersistedBrowserTab>> = preferences
        .map { preferences ->
            preferences[PINNED_BROWSER_TABS_KEY]
                ?.let(::decodePersistedBrowserTabs)
                .orEmpty()
        }

    val tabsEnabled: Flow<Boolean> = preferences.map { stored ->
        stored[BROWSER_TABS_ENABLED_KEY] ?: false
    }

    suspend fun setPinnedTabs(browserPage: Int, tabs: List<PersistedBrowserTab>) {
        dataStore.edit { preferences ->
            val existing = preferences[PINNED_BROWSER_TABS_KEY]
                ?.let(::decodePersistedBrowserTabs)
                .orEmpty()
            preferences[PINNED_BROWSER_TABS_KEY] = Json.encodeToString(
                mergePersistedBrowserTabs(existing, browserPage, tabs)
            )
        }
    }

    suspend fun setTabsEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[BROWSER_TABS_ENABLED_KEY] = enabled
        }
    }
}

@HiltViewModel
internal class BrowserTabsViewModel @Inject constructor(
    private val repository: BrowserTabsPreferencesRepository
) : ViewModel() {
    val restoredTabs: StateFlow<List<PersistedBrowserTab>?> = repository.pinnedTabs
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val tabsEnabled: StateFlow<Boolean> = repository.tabsEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val persistenceJobs = mutableMapOf<Int, Job>()

    fun persistPinnedTabs(browserPage: Int, tabs: List<PersistedBrowserTab>) {
        persistenceJobs.remove(browserPage)?.cancel()
        persistenceJobs[browserPage] = viewModelScope.launch {
            repository.setPinnedTabs(browserPage, tabs)
        }
    }

    fun setTabsEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setTabsEnabled(enabled) }
    }
}

internal fun decodePersistedBrowserTabs(encoded: String): List<PersistedBrowserTab> =
    runCatching { Json.decodeFromString<List<PersistedBrowserTab>>(encoded) }
        .getOrDefault(emptyList())

internal fun mergePersistedBrowserTabs(
    existing: List<PersistedBrowserTab>,
    browserPage: Int,
    replacement: List<PersistedBrowserTab>
): List<PersistedBrowserTab> =
    existing.filterNot { it.browserPage == browserPage } + replacement
