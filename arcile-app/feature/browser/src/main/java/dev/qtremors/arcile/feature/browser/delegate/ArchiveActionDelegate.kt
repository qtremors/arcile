package dev.qtremors.arcile.feature.browser.delegate

import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.operation.BulkFileOperationType
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.ui.UiText
import dev.qtremors.arcile.feature.browser.BrowserState
import dev.qtremors.arcile.core.operation.BulkFileOperationCoordinator
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class ArchiveActionDelegate(
    private val state: MutableStateFlow<BrowserState>,
    private val bulkFileOperationCoordinator: BulkFileOperationCoordinator,
    private val clearSelection: () -> Unit
) {
    fun extractSelectedArchiveHere(password: String? = null) {
        val archivePath = state.value.selectedFiles.singleOrNull() ?: return
        if (!ArchiveFormat.isSupported(archivePath)) {
            state.update { it.copy(error = UiText.StringResource(R.string.error_unsupported_archive)) }
            return
        }
        bulkFileOperationCoordinator.startOperation(
            type = BulkFileOperationType.EXTRACT_ARCHIVE,
            sourcePaths = listOf(archivePath),
            destinationPath = state.value.currentPath,
            resolutions = emptyMap<String, ConflictResolution>(),
            archivePassword = password
        )
        clearSelection()
    }

    fun extractSelectedArchiveToFolder(password: String? = null) {
        val archivePath = state.value.selectedFiles.singleOrNull() ?: return
        val currentPath = state.value.currentPath
        if (!ArchiveFormat.isSupported(archivePath) || currentPath.isEmpty()) {
            state.update { it.copy(error = UiText.StringResource(R.string.error_unsupported_archive)) }
            return
        }
        val archive = File(archivePath)
        val destination = File(currentPath, archive.nameWithoutExtension).absolutePath
        bulkFileOperationCoordinator.startOperation(
            type = BulkFileOperationType.EXTRACT_ARCHIVE,
            sourcePaths = listOf(archivePath),
            destinationPath = destination,
            resolutions = emptyMap<String, ConflictResolution>(),
            archivePassword = password
        )
        clearSelection()
    }

    fun createArchiveFromSelection(
        archiveName: String,
        format: ArchiveFormat,
        password: String? = null
    ) {
        val selected = state.value.selectedFiles.toList()
        val currentPath = state.value.currentPath
        if (selected.isEmpty() || currentPath.isEmpty()) return
        val archivePath = nextArchivePath(currentPath, selected, archiveName, format)
        bulkFileOperationCoordinator.startOperation(
            type = BulkFileOperationType.CREATE_ARCHIVE,
            sourcePaths = selected,
            destinationPath = archivePath,
            resolutions = emptyMap<String, ConflictResolution>(),
            archiveFormat = format,
            archivePassword = password
        )
        clearSelection()
    }

    fun createZipFromSelection() {
        val selected = state.value.selectedFiles.toList()
        val defaultName = if (selected.size == 1) {
            File(selected.first()).nameWithoutExtension.ifBlank { DEFAULT_ARCHIVE_NAME }
        } else {
            DEFAULT_ARCHIVE_NAME
        }
        createArchiveFromSelection(defaultName, ArchiveFormat.ZIP)
    }

    private fun nextArchivePath(
        currentPath: String,
        selected: List<String>,
        requestedName: String? = null,
        format: ArchiveFormat = ArchiveFormat.ZIP
    ): String {
        val defaultBaseName = if (selected.size == 1) {
            File(selected.first()).nameWithoutExtension.ifBlank { DEFAULT_ARCHIVE_NAME }
        } else {
            DEFAULT_ARCHIVE_NAME
        }
        val extension = format.extension
        val cleanedName = requestedName
            ?.substringBeforeLast(".$extension", requestedName)
            ?.replace('/', '_')
            ?.replace('\\', '_')
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: defaultBaseName
        var candidate = File(currentPath, "$cleanedName.$extension")
        var index = 1
        while (candidate.exists()) {
            candidate = File(currentPath, "$cleanedName ($index).$extension")
            index += 1
        }
        return candidate.absolutePath
    }

    private companion object {
        const val DEFAULT_ARCHIVE_NAME = "Archive"
    }
}
