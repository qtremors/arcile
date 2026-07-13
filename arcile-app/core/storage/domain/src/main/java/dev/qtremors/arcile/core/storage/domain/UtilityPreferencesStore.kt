package dev.qtremors.arcile.core.storage.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface UtilityPreferencesStore {
    val homeUtilityIds: Flow<Set<String>>

    suspend fun setHomeUtilityIds(ids: Set<String>)
}

object NoOpUtilityPreferencesStore : UtilityPreferencesStore {
    override val homeUtilityIds: Flow<Set<String>> = flowOf(setOf("trash", "cleaner"))

    override suspend fun setHomeUtilityIds(ids: Set<String>) = Unit
}
