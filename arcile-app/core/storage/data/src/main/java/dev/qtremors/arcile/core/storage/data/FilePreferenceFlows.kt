package dev.qtremors.arcile.core.storage.data

import dev.qtremors.arcile.core.storage.domain.BrowserLocationPreferences
import dev.qtremors.arcile.core.storage.domain.SharedFilePreferences
import dev.qtremors.arcile.core.storage.domain.GalleryPreferences
import dev.qtremors.arcile.core.storage.domain.RecentFilesPreferences
import dev.qtremors.arcile.core.storage.domain.SaveDestinationPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal fun Flow<SharedFilePreferences>.asLocationPreferences() =
    map(BrowserLocationPreferences::from)

internal fun Flow<SharedFilePreferences>.asRecentFilesPreferences() =
    map(RecentFilesPreferences::from)

internal fun Flow<SharedFilePreferences>.asGalleryPreferences() =
    map(GalleryPreferences::from)

internal fun Flow<SharedFilePreferences>.asGalleryPreferences(categoryName: String) =
    map { GalleryPreferences.from(it, categoryName) }

internal fun Flow<SharedFilePreferences>.asSaveDestinationPreferences() =
    map(SaveDestinationPreferences::from)
