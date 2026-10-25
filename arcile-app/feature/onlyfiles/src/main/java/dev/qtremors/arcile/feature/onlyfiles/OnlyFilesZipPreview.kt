package dev.qtremors.arcile.feature.onlyfiles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import dev.qtremors.arcile.core.archive.ArchivePasswordRequired
import dev.qtremors.arcile.core.operation.BulkFileOperationType
import dev.qtremors.arcile.core.presentation.OperationUiState
import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.core.storage.domain.ArchiveEntryModel
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.core.storage.domain.ArchiveNameEncoding
import dev.qtremors.arcile.core.storage.domain.ArchiveSummary
import dev.qtremors.arcile.core.ui.archive.ArchiveConflictActions
import dev.qtremors.arcile.core.ui.archive.ArchiveExtractionActions
import dev.qtremors.arcile.core.ui.archive.ArchiveNavigationActions
import dev.qtremors.arcile.core.ui.archive.ArchiveSelectionActions
import dev.qtremors.arcile.core.ui.archive.ArchiveViewerScreen
import dev.qtremors.arcile.core.ui.archive.ArchiveViewerState
import dev.qtremors.arcile.core.ui.archive.buildArchiveBrowserItems
import dev.qtremors.arcile.core.ui.viewer.ViewerFileAction
import dev.qtremors.arcile.core.vault.domain.VAULT_ARCHIVE_MAX_ENTRIES
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import dev.qtremors.arcile.core.vault.domain.VaultNodeRef
import dev.qtremors.arcile.core.vault.domain.VaultSeekableReader
import kotlinx.coroutines.CancellationException

internal data class VaultArchiveListing(
    val entries: List<ArchiveEntryModel>,
    val encryptedPaths: Set<List<String>>,
    val truncated: Boolean
) {
    fun selection(folder: List<String>, selected: Set<String>, encoding: ArchiveNameEncoding): VaultZipSelection {
        val paths = selected.map { requireNotNull(safeZipSegments(it)) { "Invalid archive selection" } }.toSet()
        val selection = VaultZipSelection(folder, paths, encoding = encoding)
        return selection.copy(requiresPassword = encryptedPaths.any { selection.relativePath(it) != null })
    }
}

/** Adapts private readers and extraction requests to the same screen used by Browser. */
@Composable
internal fun VaultArchivePreview(
    node: VaultNodeMetadata,
    openReader: (VaultNodeRef) -> Result<VaultSeekableReader>,
    folder: List<String>,
    operationActive: Boolean,
    onOpenFolder: (List<String>) -> Unit,
    onExtract: (VaultZipSelection) -> Unit,
    onBack: () -> Unit,
    allowedActions: Set<ViewerFileAction>,
    onAction: (ViewerFileAction) -> Unit,
    progress: OnlyFilesArchiveProgress?,
    onCancel: () -> Unit,
    destination: String
) {
    var encoding by remember(node.ref, node.revision) { mutableStateOf(ArchiveNameEncoding.UTF_8) }
    var password by remember(node.ref, node.revision) { mutableStateOf<CharArray?>(null) }
    val secret = password
    DisposableEffect(secret) { onDispose { secret?.fill('\u0000') } }
    val contents by produceState<Result<VaultArchiveListing>?>(null, node.ref, node.revision, encoding, secret) {
        value = null
        value = try {
            Result.success(readVaultArchive(node, openReader, secret, encoding, preview = true) { archive ->
                val accepted = archive.entries.take(VAULT_ARCHIVE_MAX_ENTRIES)
                VaultArchiveListing(
                    accepted.filter { it.canRead }.mapNotNull { it.toModel() },
                    accepted.filter { it.encrypted }.mapNotNull { it.segments }.toSet(),
                    archive.entries.size > VAULT_ARCHIVE_MAX_ENTRIES
                )
            })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }
    val listing = contents?.getOrNull()
    val failure = contents?.exceptionOrNull()
    val passwordRequired = failure is ArchivePasswordRequired || failure != null && secret != null
    var errorDismissed by remember(contents) { mutableStateOf(false) }
    var query by remember(node.ref, node.revision) { mutableStateOf("") }
    var selected by remember(node.ref, node.revision, folder, query, encoding) { mutableStateOf(emptySet<String>()) }
    val prefix = folder.joinToString("/").ifEmpty { null }
    val entries = listing?.entries.orEmpty()
    val visible = remember(entries, prefix, query) { buildArchiveBrowserItems(entries, prefix, query) }
    val format = ArchiveFormat.fromPath(node.name)
    val summary = remember(listing, node) {
        format?.let {
            ArchiveSummary(
                node.toSharedFileModel().reference, it, node.sizeBytes,
                entries.filterNot { entry -> entry.isDirectory }.sumOf { entry -> entry.size },
                entries.count { entry -> !entry.isDirectory }, entries.count { entry -> entry.isDirectory },
                entries.mapNotNull { entry -> entry.lastModified }.maxOrNull(),
                entries.mapNotNull { entry -> entry.lastModified }.minOrNull(), false
            )
        }
    }
    val truncated = listing?.truncated == true
    val canExtract = listing != null && !truncated && !operationActive
    fun extract(currentFolder: List<String>, paths: Set<String> = emptySet()) {
        if (!canExtract) return
        listing?.selection(currentFolder, paths, encoding)?.let {
            selected = emptySet()
            onExtract(it.copy(requiresPassword = it.requiresPassword || secret != null))
        }
    }
    val activeOperation = progress?.takeIf { it.action == VaultArchiveAction.EXTRACT }?.let {
        OperationUiState(
            type = BulkFileOperationType.EXTRACT_ARCHIVE,
            totalItems = it.progress?.total ?: 0,
            completedItems = it.progress?.completed ?: 0,
            currentPath = it.progress?.currentName
        )
    }
    ArchiveViewerScreen(
        state = ArchiveViewerState(
            archivePath = node.toSharedFileModel().reference,
            displayName = node.name, privateSession = true,
            extractionDestination = destination, currentPrefix = prefix, searchQuery = query,
            entries = entries, visibleItems = visible, summary = summary.takeIf { listing != null },
            isLoading = contents == null, passwordRequired = passwordRequired,
            nameEncoding = encoding, selectedItems = selected, activeOperation = activeOperation,
            error = if (failure != null && !passwordRequired && !errorDismissed) {
                UiText.StringResource(R.string.onlyfiles_zip_unavailable)
            } else null,
            extractionEnabled = canExtract,
            notice = if (truncated) stringResource(R.string.onlyfiles_zip_truncated, VAULT_ARCHIVE_MAX_ENTRIES) else null
        ),
        navigationActions = ArchiveNavigationActions(
            navigateBack = onBack,
            navigateUpInArchive = {
                if (folder.isEmpty()) false else { onOpenFolder(folder.dropLast(1)); true }
            },
            openFolder = { path ->
                if (!operationActive) {
                    if (path.isBlank()) onOpenFolder(emptyList())
                    else safeZipSegments(path)?.let(onOpenFolder)
                }
            },
            searchQueryChange = { if (!operationActive) query = it }
        ),
        extractionActions = ArchiveExtractionActions(
            extractAll = { extract(emptyList()) },
            extractCurrentFolder = { extract(folder) },
            submitPassword = { password = it.toCharArray() },
            selectNameEncoding = { encoding = it; onOpenFolder(emptyList()) },
            cancelExtraction = onCancel, clearError = { errorDismissed = true },
            clearOperationStatusMessage = {}, clearActiveOperation = {}
        ),
        conflictActions = ArchiveConflictActions({ _, _ -> }, {}, {}, {}),
        selectionActions = ArchiveSelectionActions(
            toggleItem = { path ->
                if (!operationActive) selected = if (path in selected) selected - path else selected + path
            },
            clear = { selected = emptySet() },
            extractSelected = { extract(folder, selected) },
            selectAll = { if (!operationActive) selected = visible.mapTo(linkedSetOf()) { it.path } }
        ),
        archiveFileModel = node.toSharedFileModel(), viewerActions = allowedActions, onViewerAction = onAction
    )
}
