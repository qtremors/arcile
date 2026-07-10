package dev.qtremors.arcile.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.qtremors.arcile.core.storage.domain.BrowserPreferences
import dev.qtremors.arcile.core.storage.domain.RecentFilesPreferencesStore
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
internal class HomePreferencesViewModel @Inject constructor(
    preferencesStore: RecentFilesPreferencesStore
) : ViewModel() {
    val recentCarouselLimit = preferencesStore.recentFilesPreferencesFlow
        .map { preferences -> preferences.homeCarouselLimit }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            BrowserPreferences.DEFAULT_HOME_RECENT_CAROUSEL_LIMIT
        )
}
