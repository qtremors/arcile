package dev.qtremors.arcile.feature.onlyfiles

import dev.qtremors.arcile.core.runtime.runCatchingCancellable
import dev.qtremors.arcile.core.storage.domain.FileOpenAsType

import androidx.compose.ui.platform.LocalContext

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import dev.qtremors.arcile.core.ui.dialogs.AlertDialog
import dev.qtremors.arcile.core.ui.dialogs.DeleteConfirmationDialog
import dev.qtremors.arcile.core.storage.domain.DeleteDecision
import dev.qtremors.arcile.core.storage.domain.DeleteDestination
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.core.net.toUri
import dev.qtremors.arcile.core.vault.domain.VaultExternalGrant
import dev.qtremors.arcile.core.vault.domain.VaultHealthReport
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import dev.qtremors.arcile.core.vault.domain.VaultSeekableReader
import dev.qtremors.arcile.core.ui.metadata.VisualMediaMetadata
import dev.qtremors.arcile.core.vault.domain.VaultSortDirection
import dev.qtremors.arcile.core.vault.domain.VaultSortField
import dev.qtremors.arcile.core.ui.FileViewOptionsSheet
import dev.qtremors.arcile.core.storage.domain.FileListingPreferences
import dev.qtremors.arcile.core.storage.domain.FileSortOption
import dev.qtremors.arcile.core.storage.domain.FileViewMode
import dev.qtremors.arcile.core.vault.domain.VaultViewOptions
import dev.qtremors.arcile.core.ui.asString
import dev.qtremors.arcile.core.ui.theme.bounceClickable
import dev.qtremors.arcile.core.ui.theme.ExpressiveShapes
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.pluralStringResource
import dev.qtremors.arcile.core.ui.dialogs.PropertiesDialog
import dev.qtremors.arcile.core.ui.R as UiR
import dev.qtremors.arcile.core.presentation.PropertiesUiModel
import dev.qtremors.arcile.core.storage.domain.PropertiesAccessStatus
import dev.qtremors.arcile.core.vault.domain.VaultNodeRef
import dev.qtremors.arcile.core.vault.domain.VaultFolderTotals

@Composable
internal fun VaultDeleteConfirmation(
    nodes: List<VaultNodeMetadata>,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val folderCount = nodes.count(VaultNodeMetadata::isDirectory)
    DeleteConfirmationDialog(
        selectedCount = nodes.size,
        isPermanentDeleteChecked = true,
        isPermanentDeleteToggleEnabled = false,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        onTogglePermanentDelete = {},
        decision = DeleteDecision(
            destination = DeleteDestination.Permanent,
            selectedCount = nodes.size,
            totalBytes = nodes.sumOf(VaultNodeMetadata::sizeBytes),
            fileCount = nodes.size - folderCount,
            folderCount = folderCount,
            irreversible = true
        ),
        showPermanentDeleteToggle = false
    )
}

@Composable
internal fun VaultViewOptionsSheet(
    options: VaultViewOptions,
    applyToSubfolders: Boolean,
    gridContentWidth: Dp?,
    onDismiss: () -> Unit,
    onApply: (VaultViewOptions, Boolean) -> Unit
) {
    val ascending = options.sortDirection == VaultSortDirection.ASCENDING
    val sortOption = when (options.sortField) {
        VaultSortField.NAME, VaultSortField.TYPE -> if (ascending) FileSortOption.NAME_ASC else FileSortOption.NAME_DESC
        VaultSortField.MODIFIED -> if (ascending) FileSortOption.DATE_OLDEST else FileSortOption.DATE_NEWEST
        VaultSortField.SIZE -> if (ascending) FileSortOption.SIZE_SMALLEST else FileSortOption.SIZE_LARGEST
        VaultSortField.FILE_COUNT -> if (ascending) FileSortOption.FILE_COUNT_LOWEST else FileSortOption.FILE_COUNT_HIGHEST
    }
    FileViewOptionsSheet(
        title = stringResource(UiR.string.sort_folder_title),
        selectedPreferences = FileListingPreferences(
            sortOption = sortOption,
            viewMode = if (options.grid) FileViewMode.GRID else FileViewMode.LIST,
            listZoom = options.listZoom,
            gridMinCellSize = options.gridMinCellSize,
            showThumbnails = options.showThumbnails,
            foldersFirst = options.foldersFirst
        ),
        showApplyToSubfolders = true,
        initialApplyToSubfolders = applyToSubfolders,
        showFoldersFirst = true,
        gridContentWidth = gridContentWidth,
        onDismiss = onDismiss,
        onApply = { listing, inherit ->
            val (field, direction) = when (listing.sortOption) {
                FileSortOption.NAME_ASC -> VaultSortField.NAME to VaultSortDirection.ASCENDING
                FileSortOption.NAME_DESC -> VaultSortField.NAME to VaultSortDirection.DESCENDING
                FileSortOption.DATE_NEWEST -> VaultSortField.MODIFIED to VaultSortDirection.DESCENDING
                FileSortOption.DATE_OLDEST -> VaultSortField.MODIFIED to VaultSortDirection.ASCENDING
                FileSortOption.SIZE_LARGEST -> VaultSortField.SIZE to VaultSortDirection.DESCENDING
                FileSortOption.SIZE_SMALLEST -> VaultSortField.SIZE to VaultSortDirection.ASCENDING
                FileSortOption.FILE_COUNT_HIGHEST -> VaultSortField.FILE_COUNT to VaultSortDirection.DESCENDING
                FileSortOption.FILE_COUNT_LOWEST -> VaultSortField.FILE_COUNT to VaultSortDirection.ASCENDING
            }
            onApply(options.copy(
                grid = listing.viewMode == FileViewMode.GRID,
                sortField = field,
                sortDirection = direction,
                listZoom = listing.listZoom,
                gridMinCellSize = listing.gridMinCellSize,
                showThumbnails = listing.showThumbnails,
                foldersFirst = listing.foldersFirst
            ), inherit)
        }
    )
}

@Composable
internal fun VaultPropertiesDialog(
    nodes: List<VaultNodeMetadata>,
    location: String,
    loadSelectionTotals: suspend (List<VaultNodeRef>) -> Result<VaultFolderTotals>,
    openReader: (VaultNodeRef) -> Result<VaultSeekableReader>,
    onDismiss: () -> Unit
) {
    val single = nodes.singleOrNull()
    val hasFolders = nodes.any(VaultNodeMetadata::isDirectory)
    var totals by remember(nodes) { mutableStateOf<VaultFolderTotals?>(null) }
    var isLoading by remember(nodes) { mutableStateOf(hasFolders) }
    var imageMetadata by remember(single) { mutableStateOf<VisualMediaMetadata?>(null) }
    LaunchedEffect(nodes) {
        if (hasFolders) {
            totals = loadSelectionTotals(nodes.map(VaultNodeMetadata::ref)).getOrNull()
            isLoading = false
        }
        if (single?.isViewableImage() == true) imageMetadata = readVaultImageMetadata(single, openReader)
    }
    val title = single?.name ?: pluralStringResource(UiR.plurals.clipboard_item_count, nodes.size, nodes.size)
    val folderTotals = totals
    val properties = remember(nodes, location, title, folderTotals) {
        val folderCount = nodes.count(VaultNodeMetadata::isDirectory)
        val modified = nodes.map(VaultNodeMetadata::modifiedAtMillis).filter { it > 0L }
        PropertiesUiModel(
            title = title,
            pathSummary = location,
            itemCount = nodes.size,
            fileCount = folderTotals?.fileCount?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt() ?: nodes.size - folderCount,
            folderCount = folderTotals?.folderCount?.let { if (single?.isDirectory == true) it - 1 else it }
                ?: folderCount,
            totalBytes = folderTotals?.totalBytes ?: nodes.sumOf(VaultNodeMetadata::sizeBytes),
            newestModifiedAt = modified.maxOrNull(),
            oldestModifiedAt = modified.minOrNull(),
            mimeTypeSummary = single?.mimeType,
            extensionSummary = nodes.map(VaultNodeMetadata::extension).filter(String::isNotBlank)
                .distinct().joinToString(", ").ifBlank { null },
            hiddenCount = nodes.count { it.name.startsWith('.') },
            accessStatus = if (folderCount == 0 || folderTotals != null) PropertiesAccessStatus.Full
                else PropertiesAccessStatus.Partial,
            folderFileCount = folderTotals?.fileCount,
            folderTotalBytes = folderTotals?.totalBytes,
            isSingleItem = single != null,
            isDirectory = single?.isDirectory
        )
    }
    PropertiesDialog(
        properties = properties.takeUnless { isLoading },
        isLoading = isLoading,
        onDismiss = onDismiss,
        readLocalImageMetadata = false,
        sourceImageMetadata = imageMetadata
    )
}

@Composable
internal fun HealthDialog(
    report: VaultHealthReport,
    busy: Boolean,
    onDismiss: () -> Unit,
    onRecover: () -> Unit
) {
    val needsRecovery = report.issues.any { it.code == "pending_transaction" }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(stringResource(R.string.onlyfiles_health_result)) },
        text = { Column {
            Text(stringResource(if (report.isHealthy) R.string.onlyfiles_health_ok else R.string.onlyfiles_health_problems))
            Text(stringResource(R.string.onlyfiles_health_counts, report.checkedObjects, report.checkedChunks, report.issues.size))
            report.issues.take(8).forEach { issue ->
                Text(stringResource(R.string.onlyfiles_health_issue,
                    stringResource(vaultHealthSeverityLabel(issue.severity)),
                    vaultHealthIssueText(issue, report.orphanObjectIds.size).asString()), style = MaterialTheme.typography.bodySmall)
            }
        } },
        confirmButton = {
            TextButton(
                onClick = if (needsRecovery) onRecover else onDismiss,
                enabled = !busy,
                shape = ExpressiveShapes.medium,
            ) {
                Text(stringResource(if (needsRecovery) R.string.onlyfiles_recover_transaction else R.string.onlyfiles_close))
            }
        },
        dismissButton = {
            if (needsRecovery) TextButton(onClick = onDismiss, enabled = !busy, shape = ExpressiveShapes.medium) {
                Text(stringResource(R.string.onlyfiles_close))
            }
        }
    )
}

@Composable
internal fun VaultMoveToTrashDialog(onDismiss: () -> Unit, onMove: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.onlyfiles_move_to_main_trash)) },
        text = { Text(stringResource(R.string.onlyfiles_move_to_main_trash_message)) },
        confirmButton = {
            Button(
                onClick = onMove,
                shape = ExpressiveShapes.medium,
                modifier = Modifier.bounceClickable(onClick = onMove)
            ) { Text(stringResource(R.string.onlyfiles_move_to_main_trash)) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = ExpressiveShapes.medium,
                modifier = Modifier.bounceClickable(onClick = onDismiss)
            ) { Text(stringResource(R.string.onlyfiles_cancel)) }
        }
    )
}

@Composable
internal fun BoundaryTransferDialog(
    move: Boolean,
    onDismiss: () -> Unit,
    onChooseDestination: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (move) R.string.onlyfiles_move_out else R.string.onlyfiles_export)) },
        text = {
            Text(stringResource(if (move) R.string.onlyfiles_move_out_warning else R.string.onlyfiles_export_warning))
        },
        confirmButton = {
            Button(
                onClick = onChooseDestination,
                shape = ExpressiveShapes.medium,
                modifier = Modifier.bounceClickable { onChooseDestination() }
            ) {
                Text(stringResource(R.string.onlyfiles_choose_destination))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = ExpressiveShapes.medium,
                modifier = Modifier.bounceClickable(onClick = onDismiss)
            ) { Text(stringResource(R.string.onlyfiles_cancel)) }
        }
    )
}

@Composable
internal fun VaultExternalAccessDialogs(
    action: ExternalAction,
    nodes: List<VaultNodeMetadata>,
    viewModel: OnlyFilesViewModel,
    openAs: FileOpenAsType? = null,
    plugin: VaultPluginViewer? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val errorMessage = stringResource(R.string.onlyfiles_external_launch_failed)
    var compatibilityCopy by remember(action, nodes, openAs, plugin) { mutableStateOf(false) }
    val title = when {
        compatibilityCopy -> R.string.onlyfiles_compatibility_copy
        action == ExternalAction.SHARE -> R.string.onlyfiles_share
        else -> R.string.onlyfiles_open_with
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (plugin == null) stringResource(title)
            else stringResource(R.string.onlyfiles_open_in_plugin, plugin.name))
        },
        text = {
            Text(stringResource(if (compatibilityCopy) R.string.onlyfiles_compatibility_copy_warning
            else R.string.onlyfiles_external_warning))
        },
        confirmButton = {
            Button(onClick = {
                onDismiss()
                val launch: (List<VaultExternalGrant>) -> Unit = { grants ->
                    runCatchingCancellable {
                        if (plugin == null) launchExternalIntent(context, action, grants, openAs)
                        else check(plugin.launch(grants.single()))
                    }.onFailure {
                        viewModel.revokeExternalAccess(grants)
                        viewModel.reportExternalLaunchFailure(errorMessage)
                    }
                }
                if (compatibilityCopy) viewModel.issuePlaintextFallbackAccess(nodes, launch)
                else viewModel.issueExternalAccess(nodes, launch)
            }) {
                Text(stringResource(if (compatibilityCopy) R.string.onlyfiles_create_copy
                else R.string.onlyfiles_continue))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!compatibilityCopy && plugin == null) {
                    TextButton(onClick = { compatibilityCopy = true }) {
                        Text(stringResource(R.string.onlyfiles_compatibility_copy))
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.onlyfiles_cancel)) }
            }
        }
    )
}

private fun launchExternalIntent(
    context: Context,
    action: ExternalAction,
    grants: List<VaultExternalGrant>,
    openAs: FileOpenAsType?
) {
    val intent = buildVaultExternalIntent(context, action, grants, openAs)
    context.startActivity(Intent.createChooser(intent, grants.first().displayName))
}

internal fun buildVaultExternalIntent(
    context: Context,
    action: ExternalAction,
    grants: List<VaultExternalGrant>,
    openAs: FileOpenAsType? = null
): Intent {
    require(grants.isNotEmpty())
    val uris = ArrayList(grants.map { it.contentUri.toUri() })
    val intent = when {
        action == ExternalAction.OPEN_WITH -> Intent(Intent.ACTION_VIEW)
            .setDataAndType(uris.single(), openAs?.mimeType ?: grants.single().mimeType)
        grants.size == 1 -> Intent(Intent.ACTION_SEND).setType(grants.single().mimeType).putExtra(Intent.EXTRA_STREAM, uris.single())
        else -> Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*").putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
    }.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    intent.clipData = ClipData.newUri(context.contentResolver, grants.first().displayName, uris.first()).apply {
        uris.drop(1).forEach { addItem(ClipData.Item(it)) }
    }
    return intent
}

@Composable
internal fun PasswordDialog(title: String, actionLabel: String, onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var password by remember { mutableStateOf("") }; var reveal by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) },
        text = { PasswordField(password, { password = it }, reveal, { reveal = !reveal }) },
        confirmButton = {
            Button(
                onClick = { onSubmit(password) },
                enabled = password.isNotEmpty(),
                shape = ExpressiveShapes.medium,
                modifier = Modifier.bounceClickable(enabled = password.isNotEmpty()) { onSubmit(password) }
            ) { Text(actionLabel) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = ExpressiveShapes.medium,
                modifier = Modifier.bounceClickable(onClick = onDismiss)
            ) { Text(stringResource(R.string.onlyfiles_cancel)) }
        }
    )
}

@Composable
internal fun OnlyFilesSecurityDisclosureDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(dev.qtremors.arcile.core.ui.R.string.onlyfiles_security_disclosure_title)) },
        text = { Text(stringResource(dev.qtremors.arcile.core.ui.R.string.onlyfiles_security_disclosure)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.onlyfiles_cancel)) } }
    )
}
