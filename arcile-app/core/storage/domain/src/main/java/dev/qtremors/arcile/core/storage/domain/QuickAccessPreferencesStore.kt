package dev.qtremors.arcile.core.storage.domain

import kotlinx.coroutines.flow.Flow

interface QuickAccessPreferencesStore {
    val quickAccessItems: Flow<List<QuickAccessItem>>

    suspend fun updateItems(items: List<QuickAccessItem>)
    suspend fun addItem(item: QuickAccessItem)
    suspend fun removeItem(id: String)
}
