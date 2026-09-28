package dev.qtremors.arcile.feature.settings.ui

import dev.qtremors.arcile.core.storage.domain.StorageKind

internal data class SettingsStorageActions(
    val clearExternalCache: () -> Unit,
    val setVolumeClassification: (storageKey: String, kind: StorageKind) -> Unit = { _, _ -> },
    val resetVolumeClassification: (storageKey: String) -> Unit = {}
)
