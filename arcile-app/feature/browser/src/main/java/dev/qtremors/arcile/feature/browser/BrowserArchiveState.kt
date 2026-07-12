package dev.qtremors.arcile.feature.browser

import androidx.compose.runtime.Immutable
import dev.qtremors.arcile.core.storage.domain.ArchiveEntryModel
import dev.qtremors.arcile.core.storage.domain.ArchiveNameEncoding
import dev.qtremors.arcile.core.storage.domain.storageParentPath
import dev.qtremors.arcile.core.storage.domain.storagePathName

internal enum class ArchiveExtractionTarget {
    NAMED_FOLDER,
    SAME_FOLDER,
    CUSTOM_FOLDER
}

@Immutable
internal data class BrowserArchiveContext(
    val archivePath: String,
    val entryPrefix: String? = null,
    val password: String? = null,
    val nameEncoding: ArchiveNameEncoding = ArchiveNameEncoding.UTF_8,
    val entries: List<ArchiveEntryModel> = emptyList(),
    val passwordRequired: Boolean = false,
    val pendingPasswordAction: ArchivePasswordAction = ArchivePasswordAction.OPEN
) {
    val archiveName: String get() = storagePathName(archivePath)
    val parentPath: String get() = storageParentPath(archivePath).orEmpty()
}

internal enum class ArchivePasswordAction {
    OPEN,
    EXTRACT
}

@Immutable
internal data class PendingArchiveExtraction(
    val archivePath: String,
    val destinationPath: String,
    val entryPrefix: String? = null,
    val entryPrefixes: List<String> = emptyList(),
    val password: String? = null,
    val nameEncoding: ArchiveNameEncoding = ArchiveNameEncoding.UTF_8
)
