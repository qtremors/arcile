package dev.qtremors.arcile.feature.onlyfiles

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import dev.qtremors.arcile.core.vault.domain.VaultConflictDecision
import dev.qtremors.arcile.core.vault.domain.VaultExternalGrant
import dev.qtremors.arcile.core.vault.domain.VaultHealthReport
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import dev.qtremors.arcile.core.vault.domain.VaultSortDirection
import dev.qtremors.arcile.core.vault.domain.VaultSortField
import dev.qtremors.arcile.core.ui.ExpressiveSegmentedRow
import dev.qtremors.arcile.core.ui.ExpressiveFilterChip
import dev.qtremors.arcile.core.ui.ExpressiveSwitch
import dev.qtremors.arcile.core.ui.theme.bounceClickable
import dev.qtremors.arcile.core.ui.theme.ExpressiveShapes
import dev.qtremors.arcile.core.ui.theme.sheet
import java.text.DateFormat

@Composable
internal fun CreateItemDialog(initialKind: CreateItemKind, onDismiss: () -> Unit, onCreate: (CreateItemKind, String) -> Unit) {
    var kind by remember { mutableStateOf(initialKind) }
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(stringResource(R.string.onlyfiles_new_item)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ExpressiveSegmentedRow(
                options = listOf(CreateItemKind.FOLDER, CreateItemKind.FILE),
                selectedOption = kind,
                onOptionSelected = { kind = it },
                modifier = Modifier.fillMaxWidth()
            ) { option ->
                val icon = if (option == CreateItemKind.FOLDER) Icons.Default.CreateNewFolder else Icons.Default.Description
                val label = stringResource(if (option == CreateItemKind.FOLDER) R.string.onlyfiles_folder else R.string.onlyfiles_empty_file)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(icon, null, modifier = Modifier.size(16.dp))
                    Text(label)
                }
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.onlyfiles_name)) },
                singleLine = true,
                shape = ExpressiveShapes.medium,
                modifier = Modifier.fillMaxWidth()
            )
        } },
        confirmButton = {
            Button(
                onClick = { onCreate(kind, name.trim()) },
                enabled = name.isNotBlank(),
                shape = ExpressiveShapes.medium,
                modifier = Modifier.bounceClickable(enabled = name.isNotBlank()) { onCreate(kind, name.trim()) }
            ) {
                Text(stringResource(R.string.onlyfiles_create_action))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = ExpressiveShapes.medium,
                modifier = Modifier.bounceClickable(onClick = onDismiss)
            ) {
                Text(stringResource(R.string.onlyfiles_cancel))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SortDialog(
    state: OnlyFilesUiState,
    onDismiss: () -> Unit,
    onSort: (VaultSortField, VaultSortDirection) -> Unit,
    onLayoutChange: (OnlyFilesLayout) -> Unit
) {
    var field by remember { mutableStateOf(state.sortField) }
    var direction by remember { mutableStateOf(state.sortDirection) }
    var layout by remember { mutableStateOf(state.layout) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.sheet
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.onlyfiles_sort),
                style = MaterialTheme.typography.titleLarge
            )

            Text(
                text = stringResource(dev.qtremors.arcile.core.ui.R.string.browser_layout_view_mode),
                style = MaterialTheme.typography.titleMedium
            )

            ExpressiveSegmentedRow(
                options = listOf(OnlyFilesLayout.LIST, OnlyFilesLayout.GRID),
                selectedOption = layout,
                onOptionSelected = { layout = it },
                modifier = Modifier.fillMaxWidth()
            ) { mode ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (mode == OnlyFilesLayout.LIST) {
                            Icons.AutoMirrored.Filled.ViewList
                        } else {
                            Icons.Default.GridView
                        },
                        contentDescription = null
                    )
                    Text(
                        stringResource(
                            if (mode == OnlyFilesLayout.LIST) dev.qtremors.arcile.core.ui.R.string.list_view
                            else dev.qtremors.arcile.core.ui.R.string.grid_view
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Sort Field",
                style = MaterialTheme.typography.titleMedium
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ExpressiveFilterChip(
                        selected = field == VaultSortField.NAME,
                        onClick = { field = VaultSortField.NAME },
                        label = { Text("Name", modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center) },
                        modifier = Modifier.weight(1f)
                    )
                    ExpressiveFilterChip(
                        selected = field == VaultSortField.MODIFIED,
                        onClick = { field = VaultSortField.MODIFIED },
                        label = { Text("Date", modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ExpressiveFilterChip(
                        selected = field == VaultSortField.SIZE,
                        onClick = { field = VaultSortField.SIZE },
                        label = { Text("Size", modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center) },
                        modifier = Modifier.weight(1f)
                    )
                    ExpressiveFilterChip(
                        selected = field == VaultSortField.TYPE,
                        onClick = { field = VaultSortField.TYPE },
                        label = { Text("Type", modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Direction",
                style = MaterialTheme.typography.titleMedium
            )

            ExpressiveSegmentedRow(
                options = VaultSortDirection.entries,
                selectedOption = direction,
                onOptionSelected = { direction = it },
                modifier = Modifier.fillMaxWidth()
            ) { option ->
                Text(
                    stringResource(
                        if (option == VaultSortDirection.ASCENDING) R.string.onlyfiles_ascending
                        else R.string.onlyfiles_descending
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onDismiss,
                    shape = ExpressiveShapes.medium,
                    modifier = Modifier.bounceClickable(onClick = onDismiss)
                ) {
                    Text(stringResource(R.string.onlyfiles_cancel))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        onSort(field, direction)
                        onLayoutChange(layout)
                    },
                    shape = ExpressiveShapes.medium,
                    modifier = Modifier.bounceClickable {
                        onSort(field, direction)
                        onLayoutChange(layout)
                    }
                ) {
                    Text(stringResource(R.string.onlyfiles_apply))
                }
            }
        }
    }
}

@Composable
internal fun PropertiesDialog(nodes: List<VaultNodeMetadata>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(stringResource(R.string.onlyfiles_properties)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.onlyfiles_property_items, nodes.size))
            Text(stringResource(R.string.onlyfiles_property_size, formatBytes(nodes.sumOf(VaultNodeMetadata::sizeBytes))))
            if (nodes.size == 1) {
                Text(nodes.single().name)
                Text(nodes.single().mimeType ?: stringResource(R.string.onlyfiles_unknown_type))
                Text(DateFormat.getDateTimeInstance().format(nodes.single().modifiedAtMillis))
            }
        } },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                shape = ExpressiveShapes.medium,
                modifier = Modifier.bounceClickable(onClick = onDismiss)
            ) {
                Text(stringResource(R.string.onlyfiles_close))
            }
        }
    )
}

@Composable
internal fun ConflictDialog(prompt: VaultConflictPrompt, onDecision: (VaultConflictDecision, Boolean) -> Unit) {
    var applyAll by remember(prompt.requestId) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { onDecision(VaultConflictDecision.SKIP, false) },
        title = { Text(stringResource(R.string.onlyfiles_conflict)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.onlyfiles_conflict_message, prompt.conflict.destinationName))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .bounceClickable { applyAll = !applyAll }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.onlyfiles_apply_all), style = MaterialTheme.typography.bodyMedium)
                ExpressiveSwitch(checked = applyAll, onCheckedChange = { applyAll = it })
            }
        } },
        confirmButton = { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (prompt.conflict.sourceIsDirectory && prompt.conflict.destinationIsDirectory) {
                TextButton(
                    onClick = { onDecision(VaultConflictDecision.MERGE_DIRECTORIES, applyAll) },
                    shape = ExpressiveShapes.medium,
                    modifier = Modifier.bounceClickable { onDecision(VaultConflictDecision.MERGE_DIRECTORIES, applyAll) }
                ) { Text(stringResource(R.string.onlyfiles_merge)) }
            }
            TextButton(
                onClick = { onDecision(VaultConflictDecision.KEEP_BOTH, applyAll) },
                shape = ExpressiveShapes.medium,
                modifier = Modifier.bounceClickable { onDecision(VaultConflictDecision.KEEP_BOTH, applyAll) }
            ) { Text(stringResource(R.string.onlyfiles_keep_both)) }
            Button(
                onClick = { onDecision(VaultConflictDecision.REPLACE, applyAll) },
                shape = ExpressiveShapes.medium,
                modifier = Modifier.bounceClickable { onDecision(VaultConflictDecision.REPLACE, applyAll) }
            ) { Text(stringResource(R.string.onlyfiles_replace)) }
        } },
        dismissButton = {
            TextButton(
                onClick = { onDecision(VaultConflictDecision.SKIP, applyAll) },
                shape = ExpressiveShapes.medium,
                modifier = Modifier.bounceClickable { onDecision(VaultConflictDecision.SKIP, applyAll) }
            ) { Text(stringResource(R.string.onlyfiles_skip)) }
        }
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
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                shape = ExpressiveShapes.medium,
                modifier = Modifier.bounceClickable(onClick = onDismiss)
            ) {
                Text(stringResource(R.string.onlyfiles_close))
            }
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
internal fun PasswordField(value: String, onValueChange: (String) -> Unit, reveal: Boolean, onReveal: () -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.onlyfiles_password)) },
        visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = { IconButton(onClick = onReveal) {
            Icon(if (reveal) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, stringResource(if (reveal) R.string.onlyfiles_hide_password else R.string.onlyfiles_show_password))
        } },
        singleLine = true,
        shape = ExpressiveShapes.medium,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
internal fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var value by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                shape = ExpressiveShapes.medium,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(value.trim()) },
                enabled = value.isNotBlank(),
                shape = ExpressiveShapes.medium,
                modifier = Modifier.bounceClickable(enabled = value.isNotBlank()) { onSubmit(value.trim()) }
            ) { Text(title) }
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
