package dev.qtremors.arcile.core.storage.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface UtilityPreferencesStore {
    val homeUtilityIds: Flow<List<String>>
    val homeLayoutPreferences: Flow<HomeLayoutPreferences>
        get() = flowOf(HomeLayoutPreferences())
    val batchRenameHistory: Flow<List<String>> get() = flowOf(emptyList())

    suspend fun setHomeUtilityIds(ids: List<String>)
    suspend fun setHomeLayoutPreferences(preferences: HomeLayoutPreferences) {}
    suspend fun addBatchRenameHistory(query: String) {}
    suspend fun removeBatchRenameHistory(query: String) {}
    suspend fun clearBatchRenameHistory() {}
}

object NoOpUtilityPreferencesStore : UtilityPreferencesStore {
    override val homeUtilityIds: Flow<List<String>> = flowOf(listOf("trash", "cleaner"))
    override val homeLayoutPreferences: Flow<HomeLayoutPreferences> = flowOf(HomeLayoutPreferences())
    override val batchRenameHistory: Flow<List<String>> = flowOf(emptyList())

    override suspend fun setHomeUtilityIds(ids: List<String>) = Unit
    override suspend fun setHomeLayoutPreferences(preferences: HomeLayoutPreferences) = Unit
    override suspend fun addBatchRenameHistory(query: String) = Unit
    override suspend fun removeBatchRenameHistory(query: String) = Unit
    override suspend fun clearBatchRenameHistory() = Unit
}

data class HomeLayoutPreferences(
    val orderedSectionIds: List<String> = HomeSectionIds.ALL,
    val enabledSectionIds: Set<String> = HomeSectionIds.ALL.toSet()
)

object HomeSectionIds {
    const val STORAGE = "storage"
    const val CATEGORIES = "categories"
    const val QUICK_ACCESS = "quick_access"
    const val UTILITIES = "utilities"
    const val RECENT_FILES = "recent_files"

    val ALL = listOf(STORAGE, CATEGORIES, QUICK_ACCESS, UTILITIES, RECENT_FILES)
}
