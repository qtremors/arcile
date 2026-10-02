package dev.qtremors.arcile.core.storage.data

import dev.qtremors.arcile.core.storage.domain.SaveDestinationPreferencesStore

class DefaultSaveDestinationPreferencesStore(
    private val dataSource: FilePreferencesDataSource
) : SaveDestinationPreferencesStore {
    override val saveDestinationPreferencesFlow = dataSource.saveDestinationPreferencesFlow

    override suspend fun updateDefaultSaveToArcilePath(path: String?) =
        dataSource.updateDefaultSaveToArcilePath(path)
}
