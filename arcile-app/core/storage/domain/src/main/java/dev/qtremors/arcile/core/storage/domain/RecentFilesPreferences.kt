package dev.qtremors.arcile.core.storage.domain

import kotlinx.coroutines.flow.Flow

data class RecentFilesPreferences(
    val presentation: FileListingPreferences = FileListingPreferences(
        sortOption = FileListingPreferences.DEFAULT_CATEGORY_SORT_OPTION
    ),
    val homeCarouselLimit: Int = BrowserPreferences.DEFAULT_HOME_RECENT_CAROUSEL_LIMIT
) {
    companion object {
        fun from(preferences: BrowserPreferences) = RecentFilesPreferences(
            presentation = preferences.recentPresentation,
            homeCarouselLimit = preferences.homeRecentCarouselLimit
        )
    }
}

interface RecentFilesPreferencesStore {
    val recentFilesPreferencesFlow: Flow<RecentFilesPreferences>
    suspend fun updateRecentPresentation(presentation: FileListingPreferences)
    suspend fun updateHomeRecentCarouselLimit(limit: Int)
}
