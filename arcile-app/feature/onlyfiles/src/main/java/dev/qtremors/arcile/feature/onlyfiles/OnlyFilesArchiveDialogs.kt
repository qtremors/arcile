package dev.qtremors.arcile.feature.onlyfiles

import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.core.storage.domain.archiveExtractionFolderName

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import dev.qtremors.arcile.core.storage.domain.ArchiveCompressionLevel
import dev.qtremors.arcile.core.ui.ClipboardToolbar
import dev.qtremors.arcile.core.ui.OperationProgressDetailsSheet
import dev.qtremors.arcile.core.ui.archive.ArchiveExtractionChoice
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import java.util.zip.Deflater
import dev.qtremors.arcile.core.ui.R as UiR

internal data class VaultZipExtraction(
    val node: VaultNodeMetadata,
    val selection: VaultZipSelection,
    val name: String,
    val password: CharArray? = null,
    val intoExisting: Boolean = false
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
    val name = archiveExtractionFolderName(node.name)
    var password by remember(node.ref, node.revision) { mutableStateOf("") }
    var revealPassword by remember(node.ref, node.revision) { mutableStateOf(false) }
    dev.qtremors.arcile.core.ui.archive.ExtractArchiveDialog(
        archiveName = node.name,
        defaultDestinationPath = "",
        onDismiss = onDismiss,
        onConfirm = { target, _ ->
            val secret = password.takeIf { it.isNotEmpty() }?.toCharArray()
            onExtract(VaultZipExtraction(node, selection, name, secret,
                intoExisting = target != ArchiveExtractionChoice.NAMED_FOLDER), target == ArchiveExtractionChoice.CUSTOM_FOLDER)
        },
        privateSession = true,
        customDestinationContent = {}, // Confirmation opens the existing protected folder picker.
        canConfirmTarget = { !selection.requiresPassword || password.isNotEmpty() },
        extraContent = {
            if (selection.requiresPassword || format == ArchiveFormat.SEVEN_Z) {
                PasswordField(password, { password = it }, revealPassword, { revealPassword = !revealPassword }, allowKeyboardSwitch = false)
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
