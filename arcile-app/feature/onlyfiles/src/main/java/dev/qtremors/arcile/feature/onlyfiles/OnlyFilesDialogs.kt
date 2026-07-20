package dev.qtremors.arcile.feature.onlyfiles

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.core.net.toUri
import dev.qtremors.arcile.core.vault.domain.VaultConflictDecision
import dev.qtremors.arcile.core.vault.domain.VaultExternalGrant
import dev.qtremors.arcile.core.vault.domain.VaultHealthReport
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import dev.qtremors.arcile.core.vault.domain.VaultSortDirection
import dev.qtremors.arcile.core.vault.domain.VaultSortField
import java.text.DateFormat

@Composable
internal fun CreateItemDialog(initialKind: CreateItemKind, onDismiss: () -> Unit, onCreate: (CreateItemKind, String) -> Unit) {
    var kind by remember { mutableStateOf(initialKind) }
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(stringResource(R.string.onlyfiles_new_item)) },
        text = { Column {
            Row {
                TextButton(onClick = { kind = CreateItemKind.FOLDER }) { Icon(Icons.Default.CreateNewFolder, null); Text(stringResource(R.string.onlyfiles_folder)) }
                TextButton(onClick = { kind = CreateItemKind.FILE }) { Icon(Icons.Default.Description, null); Text(stringResource(R.string.onlyfiles_empty_file)) }
            }
            OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.onlyfiles_name)) }, singleLine = true)
        } },
        confirmButton = { Button(onClick = { onCreate(kind, name.trim()) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.onlyfiles_create_action)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.onlyfiles_cancel)) } }
    )
}

@Composable
internal fun SortDialog(state: OnlyFilesUiState, onDismiss: () -> Unit, onSort: (VaultSortField, VaultSortDirection) -> Unit) {
    var field by remember { mutableStateOf(state.sortField) }
    var direction by remember { mutableStateOf(state.sortDirection) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(stringResource(R.string.onlyfiles_sort)) },
        text = { Column {
            VaultSortField.entries.forEach { option ->
                TextButton(onClick = { field = option }) { Text((if (field == option) "✓ " else "") + option.name.lowercase().replaceFirstChar(Char::uppercase)) }
            }
            TextButton(onClick = { direction = if (direction == VaultSortDirection.ASCENDING) VaultSortDirection.DESCENDING else VaultSortDirection.ASCENDING }) {
                Text(stringResource(if (direction == VaultSortDirection.ASCENDING) R.string.onlyfiles_ascending else R.string.onlyfiles_descending))
            }
        } },
        confirmButton = { Button(onClick = { onSort(field, direction) }) { Text(stringResource(R.string.onlyfiles_apply)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.onlyfiles_cancel)) } }
    )
}

@Composable
internal fun PropertiesDialog(nodes: List<VaultNodeMetadata>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(stringResource(R.string.onlyfiles_properties)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(androidx.compose.ui.unit.Dp(8f))) {
            Text(stringResource(R.string.onlyfiles_property_items, nodes.size))
            Text(stringResource(R.string.onlyfiles_property_size, formatBytes(nodes.sumOf(VaultNodeMetadata::sizeBytes))))
            if (nodes.size == 1) {
                Text(nodes.single().name)
                Text(nodes.single().mimeType ?: stringResource(R.string.onlyfiles_unknown_type))
                Text(DateFormat.getDateTimeInstance().format(nodes.single().modifiedAtMillis))
            }
        } },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.onlyfiles_close)) } }
    )
}

@Composable
internal fun ConflictDialog(prompt: VaultConflictPrompt, onDecision: (VaultConflictDecision, Boolean) -> Unit) {
    var applyAll by remember(prompt.requestId) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { onDecision(VaultConflictDecision.SKIP, false) },
        title = { Text(stringResource(R.string.onlyfiles_conflict)) },
        text = { Column {
            Text(stringResource(R.string.onlyfiles_conflict_message, prompt.conflict.destinationName))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(applyAll, { applyAll = it }); Text(stringResource(R.string.onlyfiles_apply_all))
            }
        } },
        confirmButton = { Row {
            if (prompt.conflict.sourceIsDirectory && prompt.conflict.destinationIsDirectory) {
                TextButton(onClick = { onDecision(VaultConflictDecision.MERGE_DIRECTORIES, applyAll) }) { Text(stringResource(R.string.onlyfiles_merge)) }
            }
            TextButton(onClick = { onDecision(VaultConflictDecision.KEEP_BOTH, applyAll) }) { Text(stringResource(R.string.onlyfiles_keep_both)) }
            TextButton(onClick = { onDecision(VaultConflictDecision.REPLACE, applyAll) }) { Text(stringResource(R.string.onlyfiles_replace)) }
        } },
        dismissButton = { TextButton(onClick = { onDecision(VaultConflictDecision.SKIP, applyAll) }) { Text(stringResource(R.string.onlyfiles_skip)) } }
    )
}

@Composable
internal fun HealthDialog(report: VaultHealthReport, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(stringResource(R.string.onlyfiles_health_result)) },
        text = { Column {
            Text(stringResource(if (report.isHealthy) R.string.onlyfiles_health_ok else R.string.onlyfiles_health_problems))
            Text(stringResource(R.string.onlyfiles_health_counts, report.checkedObjects, report.checkedChunks, report.issues.size))
            report.issues.take(8).forEach { Text("${it.severity}: ${it.message}", style = MaterialTheme.typography.bodySmall) }
        } },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.onlyfiles_close)) } }
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
            Button(onClick = onChooseDestination) {
                Text(stringResource(R.string.onlyfiles_choose_destination))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.onlyfiles_cancel)) }
        }
    )
}

internal fun launchExternalIntent(context: Context, action: ExternalAction, grants: List<VaultExternalGrant>) {
    require(grants.isNotEmpty())
    val uris = ArrayList(grants.map { it.contentUri.toUri() })
    val intent = when {
        action == ExternalAction.OPEN_WITH -> Intent(Intent.ACTION_VIEW).setDataAndType(uris.single(), grants.single().mimeType)
        grants.size == 1 -> Intent(Intent.ACTION_SEND).setType(grants.single().mimeType).putExtra(Intent.EXTRA_STREAM, uris.single())
        else -> Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*").putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
    }.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    intent.clipData = ClipData.newUri(context.contentResolver, grants.first().displayName, uris.first()).apply {
        uris.drop(1).forEach { addItem(ClipData.Item(it)) }
    }
    context.startActivity(Intent.createChooser(intent, grants.first().displayName))
}

@Composable
internal fun PasswordDialog(title: String, actionLabel: String, onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var password by remember { mutableStateOf("") }; var reveal by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) },
        text = { PasswordField(password, { password = it }, reveal, { reveal = !reveal }) },
        confirmButton = { Button(onClick = { onSubmit(password) }, enabled = password.isNotEmpty()) { Text(actionLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.onlyfiles_cancel)) } }
    )
}

@Composable
internal fun PasswordField(value: String, onValueChange: (String) -> Unit, reveal: Boolean, onReveal: () -> Unit) {
    OutlinedTextField(
        value, onValueChange, label = { Text(stringResource(R.string.onlyfiles_password)) },
        visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = { IconButton(onClick = onReveal) {
            Icon(if (reveal) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, stringResource(if (reveal) R.string.onlyfiles_hide_password else R.string.onlyfiles_show_password))
        } }, singleLine = true
    )
}

@Composable
internal fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var value by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) }, text = { OutlinedTextField(value, { value = it }, singleLine = true) },
        confirmButton = { Button(onClick = { onSubmit(value.trim()) }, enabled = value.isNotBlank()) { Text(title) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.onlyfiles_cancel)) } }
    )
}
