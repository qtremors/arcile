package dev.qtremors.arcile.core.storage.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface UtilityPreferencesStore {
    val homeUtilityIds: Flow<List<String>>

    suspend fun setHomeUtilityIds(ids: List<String>)
}

object NoOpUtilityPreferencesStore : UtilityPreferencesStore {
    override val homeUtilityIds: Flow<List<String>> = flowOf(listOf("trash", "cleaner"))

    override suspend fun setHomeUtilityIds(ids: List<String>) = Unit
}
