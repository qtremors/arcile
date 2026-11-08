package dev.qtremors.arcile.core.ui.archive

import dev.qtremors.arcile.core.presentation.OperationUiState
import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.core.storage.domain.ArchiveEntryModel
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.core.storage.domain.ArchiveNameEncoding
import dev.qtremors.arcile.core.storage.domain.ArchiveSummary
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.FileConflict

enum class ArchiveOperationStatusMessage {
    ExtractionComplete,
    ExtractionCancelled
}

data class ArchiveViewerState(
    val archivePath: String = "",
    val extractionDestination: String? = null,
    val currentPrefix: String? = null,
    val searchQuery: String = "",
    val summary: ArchiveSummary? = null,
    val entries: List<ArchiveEntryModel> = emptyList(),
    val visibleItems: List<ArchiveBrowserItem> = emptyList(),
    val isLoading: Boolean = true,
    val error: UiText? = null,
    val passwordRequired: Boolean = false,
    val archivePassword: String? = null,
    val nameEncoding: ArchiveNameEncoding = ArchiveNameEncoding.UTF_8,
    val pendingConflicts: List<FileConflict> = emptyList(),
    val conflictResolutions: Map<String, ConflictResolution> = emptyMap(),
    val pendingExtractionPrefix: String? = null,
    val pendingExtractionPassword: String? = null,
    val activeOperation: OperationUiState? = null,
    val operationStatusMessage: ArchiveOperationStatusMessage? = null,
    val selectedItems: Set<String> = emptySet(),
    val pendingExtractionPrefixes: List<String> = emptyList(),
    val displayName: String? = null,
    val privateSession: Boolean = false,
    val extractionEnabled: Boolean = true,
    val notice: String? = null
) {
    val archiveFormat: ArchiveFormat? get() = ArchiveFormat.fromPath(displayName ?: archivePath)

    val breadcrumbSegments: List<Pair<String, String?>>
        get() {
            val prefix = currentPrefix
                ?.trimEnd('/')
                ?.takeIf { it.isNotBlank() }
                ?: return listOf("Root" to null)
            val segments = prefix.split('/').filter { it.isNotBlank() }
            return listOf("Root" to null) + segments.mapIndexed { index, segment ->
                segment to segments.take(index + 1).joinToString("/")
            }
        }
}
