package dev.qtremors.arcile.feature.archive

import dev.qtremors.arcile.core.storage.domain.ArchiveEntryModel

internal typealias ArchiveBrowserItem = dev.qtremors.arcile.core.ui.archive.ArchiveBrowserItem

internal fun buildArchiveBrowserItems(entries: List<ArchiveEntryModel>, prefix: String?, searchQuery: String) =
    dev.qtremors.arcile.core.ui.archive.buildArchiveBrowserItems(entries, prefix, searchQuery)
