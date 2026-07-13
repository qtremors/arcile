package dev.qtremors.arcile.core.storage.data

import dev.qtremors.arcile.core.storage.domain.BrowserLocationPreferences
import dev.qtremors.arcile.core.storage.domain.BrowserPreferences
import dev.qtremors.arcile.core.storage.domain.GalleryPreferences
import dev.qtremors.arcile.core.storage.domain.RecentFilesPreferences
import dev.qtremors.arcile.core.storage.domain.SaveDestinationPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal fun Flow<BrowserPreferences>.asLocationPreferences() =
    map(BrowserLocationPreferences::from)

internal fun Flow<BrowserPreferences>.asRecentFilesPreferences() =
    map(RecentFilesPreferences::from)

internal fun Flow<BrowserPreferences>.asGalleryPreferences() =
    map(GalleryPreferences::from)

internal fun Flow<BrowserPreferences>.asSaveDestinationPreferences() =
    map(SaveDestinationPreferences::from)
