package dev.qtremors.arcile.core.storage.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface StorageCleanerPreferencesStore {
    val rulesFlow: Flow<StorageCleanerRules>

    suspend fun updateRules(rules: StorageCleanerRules)
    suspend fun updateSectionRule(type: CleanerGroupType, rule: CleanerSectionRule)
    suspend fun ignorePath(path: String)
    suspend fun unignorePath(path: String)
    suspend fun resetSection(type: CleanerGroupType)
}

object NoOpStorageCleanerPreferencesStore : StorageCleanerPreferencesStore {
    override val rulesFlow: Flow<StorageCleanerRules> = flowOf(StorageCleanerRules())

    override suspend fun updateRules(rules: StorageCleanerRules) = Unit
    override suspend fun updateSectionRule(type: CleanerGroupType, rule: CleanerSectionRule) = Unit
    override suspend fun ignorePath(path: String) = Unit
    override suspend fun unignorePath(path: String) = Unit
    override suspend fun resetSection(type: CleanerGroupType) = Unit
}
