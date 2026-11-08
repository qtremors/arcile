package dev.qtremors.arcile.feature.onlyfiles

import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import dev.qtremors.arcile.core.storage.domain.BatchRenameEngine
import dev.qtremors.arcile.core.ui.dialogs.AlertDialog
import dev.qtremors.arcile.core.ui.dialogs.BatchRenameDialog
import dev.qtremors.arcile.core.vault.domain.DirectoryId
import dev.qtremors.arcile.core.vault.domain.VaultName
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import dev.qtremors.arcile.core.vault.domain.VaultRenameItem
import dev.qtremors.arcile.core.ui.asString
import dev.qtremors.arcile.core.ui.R as UiR

@Composable
internal fun OnlyFilesBatchRenameDialog(
    nodes: List<VaultNodeMetadata>,
    loadSiblingNames: suspend (List<VaultRenameItem>) -> Result<Map<DirectoryId, Set<String>>>,
    onDismiss: () -> Unit,
    onRename: (List<VaultRenameItem>) -> Unit
) {
    val files = remember(nodes) { nodes.map(VaultNodeMetadata::toSharedFileModel) }
    val itemsByReference = remember(nodes, files) {
        files.zip(nodes).associate { (file, node) -> file.reference to VaultRenameItem(node.ref, node.name) }
    }
    var siblingNames by remember(nodes) { mutableStateOf<Result<Map<DirectoryId, Set<String>>>?>(null) }
    LaunchedEffect(nodes) { siblingNames = loadSiblingNames(itemsByReference.values.toList()) }
    val names = siblingNames?.getOrNull()
    if (names == null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(UiR.string.activity_log_operation_batch_rename)) },
            text = {
                val error = siblingNames?.exceptionOrNull()
                if (error == null) LinearProgressIndicator()
                else Text(vaultOperationErrorText(error, R.string.onlyfiles_batch_rename_unavailable).asString())
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(UiR.string.cancel)) } }
        )
        return
    }
    val namesByFolder = remember(names) { names.mapKeys { (folder, _) -> folder.value } }
    BatchRenameDialog(
        files = files,
        onDismiss = onDismiss,
        onConfirm = { changes ->
            onRename(changes.map { (file, name) -> itemsByReference.getValue(file.reference).copy(newName = name) })
        },
        evaluatePreview = { models, rule ->
            BatchRenameEngine.evaluateByFolder(
                files = models,
                rule = rule,
                folderKey = { itemsByReference.getValue(it.reference).ref.parentId.value },
                unselectedNamesByFolder = namesByFolder,
                nameKey = { runCatching { VaultName.of(it).comparisonKey }.getOrNull() }
            )
        }
    )
}
