package dev.qtremors.arcile.feature.onlyfiles

import dev.qtremors.arcile.core.storage.domain.ArchiveFormat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.storage.domain.ArchiveCompressionLevel
import dev.qtremors.arcile.core.ui.ClipboardToolbar
import dev.qtremors.arcile.core.ui.ExpressiveSwitch
import dev.qtremors.arcile.core.ui.OperationProgressDetailsSheet
import dev.qtremors.arcile.core.ui.dialogs.AlertDialog
import dev.qtremors.arcile.core.ui.dialogs.FileNameInput
import dev.qtremors.arcile.core.ui.dialogs.validateFileName
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import java.util.zip.Deflater
import dev.qtremors.arcile.core.ui.R as UiR

internal data class VaultZipExtraction(
    val node: VaultNodeMetadata,
    val selection: VaultZipSelection,
    val name: String,
    val keepBoth: Boolean,
    val successMessage: String,
    val password: CharArray? = null
)

@Composable
internal fun VaultCreateZipDialog(
    nodes: List<VaultNodeMetadata>,
    existingNames: Set<String>,
    onDismiss: () -> Unit,
    destinationPath: String,
    onCreate: (String, Int, String, CharArray?) -> Unit
) {
    val success = stringResource(R.string.onlyfiles_zip_created)
    dev.qtremors.arcile.core.ui.archive.CreateArchiveDialog(
        defaultName = nodes.singleOrNull()?.name?.substringBeforeLast('.').orEmpty(),
        selectedCount = nodes.size,
        destinationPath = destinationPath,
        existingNames = existingNames,
        onDismiss = onDismiss,
        formats = listOf(ArchiveFormat.ZIP, ArchiveFormat.SEVEN_Z) + dev.qtremors.arcile.core.archive.TarArchiveWriter.formats,
        privateSession = true,
        onConfirm = { name, format, level, password ->
            val compression = when (level) {
                ArchiveCompressionLevel.STORE -> Deflater.NO_COMPRESSION
                ArchiveCompressionLevel.FAST -> Deflater.BEST_SPEED
                ArchiveCompressionLevel.DEFAULT -> Deflater.DEFAULT_COMPRESSION
                ArchiveCompressionLevel.MAXIMUM -> Deflater.BEST_COMPRESSION
            }
            onCreate("$name.${format.extension}", compression, success, password?.toCharArray())
        }
    )
}

@Composable
internal fun VaultExtractZipDialog(
    node: VaultNodeMetadata,
    selection: VaultZipSelection,
    onDismiss: () -> Unit,
    onExtract: (VaultZipExtraction, Boolean) -> Unit
) {
    val format = ArchiveFormat.fromPath(node.name)
    val defaultName = selection.folder.lastOrNull()
        ?: format?.let { node.name.dropLast(it.extension.length + 1) }?.ifBlank { node.name }
        ?: node.name
    var name by remember(defaultName) { mutableStateOf(defaultName) }
    var keepBoth by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var revealPassword by remember { mutableStateOf(false) }
    val validation = validateFileName(name)
    val success = stringResource(R.string.onlyfiles_zip_extracted)
    val canExtract = validation.isValid && (!selection.requiresPassword || password.isNotEmpty())
    fun extract(choose: Boolean) {
        val secret = password.takeIf { it.isNotEmpty() }?.toCharArray()
        onExtract(VaultZipExtraction(node, selection, validation.sanitizedName, keepBoth, success, secret), choose)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.onlyfiles_extract_zip)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.onlyfiles_extract_zip_description))
                FileNameInput(
                    value = name,
                    onValueChange = { name = it },
                    label = stringResource(R.string.onlyfiles_new_folder),
                    modifier = Modifier.fillMaxWidth()
                )
                if (selection.requiresPassword || format == ArchiveFormat.SEVEN_Z) {
                    PasswordField(password, { password = it }, revealPassword, { revealPassword = !revealPassword }, allowKeyboardSwitch = false)
                }
                androidx.compose.foundation.layout.Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    ExpressiveSwitch(checked = keepBoth, onCheckedChange = { keepBoth = it })
                    Text(stringResource(R.string.onlyfiles_extract_keep_both))
                }
            }
        },
        confirmButton = {
            FilledTonalButton(enabled = canExtract, onClick = { extract(false) }) {
                Text(stringResource(R.string.onlyfiles_extract_here))
            }
        },
        dismissButton = {
            Column {
                TextButton(enabled = canExtract, onClick = { extract(true) }) {
                    Text(stringResource(R.string.onlyfiles_choose_destination))
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.onlyfiles_cancel)) }
            }
        }
    )
}

@Composable
internal fun OnlyFilesArchiveToolbar(
    state: OnlyFilesArchiveProgress,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var details by remember { mutableStateOf(false) }
    val creating = state.action == VaultArchiveAction.CREATE
    val title = stringResource(if (creating) R.string.onlyfiles_creating_zip else R.string.onlyfiles_extracting_zip)
    val progress = state.progress
    val total = progress?.total ?: 0
    val completed = progress?.completed ?: 0
    val fraction = if (total > 0) completed.toFloat() / total else null
    ClipboardToolbar(
        title = title,
        subtitle = progress?.currentName
            ?: pluralStringResource(UiR.plurals.transfer_progress_items, total, completed, total),
        icon = if (creating) Icons.Default.FolderZip else Icons.Default.Unarchive,
        onStatusClick = { details = true },
        onPaste = null,
        onCancel = onCancel,
        modifier = modifier,
        progress = fraction
    )
    if (details) OperationProgressDetailsSheet(
        totalItems = total,
        completedItems = completed,
        onCancel = onCancel,
        onDismissRequest = { details = false },
        currentName = progress?.currentName
    )
}
