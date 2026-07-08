package dev.qtremors.arcile.core.ui

import dev.qtremors.arcile.core.storage.domain.SearchFilters

internal fun SearchFilters.hasActiveAdvancedFilters(): Boolean =
    extensions.isNotEmpty() ||
        includeHidden ||
        storageVolumeId != null ||
        folderScopePath != null ||
        mimeType != null ||
        minSize != null ||
        maxSize != null ||
        minDateMillis != null ||
        maxDateMillis != null ||
        savedPresetName != null
