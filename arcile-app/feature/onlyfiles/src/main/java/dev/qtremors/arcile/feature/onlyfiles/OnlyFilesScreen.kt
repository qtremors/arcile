package dev.qtremors.arcile.feature.onlyfiles

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.qtremors.arcile.core.vault.domain.VaultNode
import dev.qtremors.arcile.core.vault.domain.VaultSummary
import java.text.DateFormat

@Composable
internal fun OnlyFilesRoute(
    onNavigateBack: () -> Unit,
    viewModel: OnlyFilesViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val filesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        viewModel.finishImportSelection(uris.map(Uri::toString))
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        viewModel.finishImportSelection(listOfNotNull(uri?.toString()))
    }
    BackHandler {
        if (state.folderPicker != null) viewModel.navigateVaultFolderUp()
        else if (!viewModel.navigateUp()) onNavigateBack()
    }

    OnlyFilesScreen(
        state = state,
        viewModel = viewModel,
        onNavigateBack = onNavigateBack,
        onImportFiles = {
            if (viewModel.beginImportSelection()) filesLauncher.launch(arrayOf("*/*"))
        },
        onImportFolder = {
            if (viewModel.beginImportSelection()) folderLauncher.launch(null)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnlyFilesScreen(
    state: OnlyFilesUiState,
    viewModel: OnlyFilesViewModel,
    onNavigateBack: () -> Unit,
    onImportFiles: () -> Unit,
    onImportFolder: () -> Unit
) {
    val snackbarHost = remember { SnackbarHostState() }
    var showCreate by remember { mutableStateOf(false) }
    var unlockVault by remember { mutableStateOf<VaultSummary?>(null) }
    var folderDialog by remember { mutableStateOf(false) }
    var actionNode by remember { mutableStateOf<VaultNode?>(null) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHost.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    state.viewer?.let { node ->
        ViewerScreen(state, node, viewModel, viewModel::navigateUp)
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = { Text(state.selectedVault?.name ?: stringResource(R.string.onlyfiles_title)) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (!viewModel.navigateUp()) onNavigateBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.onlyfiles_back))
                    }
                },
                actions = {
                    if (state.selectedVault != null) {
                        IconButton(onClick = onImportFiles) {
                            Icon(Icons.Default.UploadFile, stringResource(R.string.onlyfiles_import_files))
                        }
                        IconButton(onClick = { folderDialog = true }) {
                            Icon(Icons.Default.CreateNewFolder, stringResource(R.string.onlyfiles_new_folder))
                        }
                        IconButton(onClick = viewModel::lockCurrent) {
                            Icon(Icons.Default.Lock, stringResource(R.string.onlyfiles_lock))
                        }
                    } else {
                        TextButton(onClick = viewModel::beginAttachVault) {
                            Text(stringResource(R.string.onlyfiles_add_existing))
                        }
                        if (state.vaults.any(VaultSummary::isUnlocked)) {
                            TextButton(onClick = viewModel::lockAll) { Text(stringResource(R.string.onlyfiles_lock_all)) }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = {
            if (state.selectedVault == null) {
                FloatingActionButton(onClick = { showCreate = true }) {
                    Icon(Icons.Default.Add, stringResource(R.string.onlyfiles_create))
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (state.selectedVault == null) {
                VaultList(
                    vaults = state.vaults,
                    imports = state.activeImports,
                    onOpen = { vault -> if (!viewModel.openVault(vault)) unlockVault = vault },
                    onCancelImport = viewModel::cancelImport
                )
                if (state.vaults.isEmpty()) {
                    Text(
                        stringResource(R.string.onlyfiles_empty),
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                VaultBrowser(
                    nodes = state.nodes,
                    onOpen = viewModel::open,
                    onMore = { actionNode = it },
                    onImportFiles = onImportFiles,
                    onImportFolder = onImportFolder
                )
            }
            if (state.busy) {
                Surface(color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.25f), modifier = Modifier.fillMaxSize()) {
                    Box(contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
            }
        }
    }

    if (showCreate) {
        CreateVaultDialog(
            onDismiss = { showCreate = false },
            onCreateAppPrivate = { name, password ->
                showCreate = false
                viewModel.createAppPrivateVault(name, password)
            },
            onChooseFolder = { name, password ->
                showCreate = false
                viewModel.beginFolderVaultCreation(name, password)
            }
        )
    }
    unlockVault?.let { vault ->
        PasswordDialog(
            title = vault.name,
            actionLabel = stringResource(R.string.onlyfiles_unlock),
            onDismiss = { unlockVault = null },
            onSubmit = { password ->
                unlockVault = null
                viewModel.unlock(vault.id, password)
            }
        )
    }
    if (folderDialog) {
        NameDialog(
            title = stringResource(R.string.onlyfiles_new_folder),
            initial = "",
            onDismiss = { folderDialog = false },
            onSubmit = {
                folderDialog = false
                viewModel.createFolder(it)
            }
        )
    }
    actionNode?.let { node ->
        ItemActionDialog(
            node = node,
            onDismiss = { actionNode = null },
            onRename = { newName ->
                actionNode = null
                viewModel.rename(node, newName)
            },
            onDelete = {
                actionNode = null
                viewModel.delete(node)
            }
        )
    }
    state.folderPicker?.let { picker ->
        VaultFolderPickerDialog(
            state = picker,
            onOpen = viewModel::openVaultFolder,
            onUp = viewModel::navigateVaultFolderUp,
            onChoose = viewModel::chooseVaultFolder,
            onDismiss = viewModel::cancelVaultFolderPicker
        )
    }
}

@Composable
private fun VaultList(
    vaults: List<VaultSummary>,
    imports: Map<dev.qtremors.arcile.core.vault.domain.VaultId, dev.qtremors.arcile.core.vault.domain.VaultImportState>,
    onOpen: (VaultSummary) -> Unit,
    onCancelImport: (dev.qtremors.arcile.core.vault.domain.VaultId) -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(vaults, key = { it.id.value }) { vault ->
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { onOpen(vault) },
                shape = RoundedCornerShape(20.dp),
                tonalElevation = 2.dp
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Icon(if (vault.isUnlocked) Icons.Outlined.FolderOpen else Icons.Outlined.Lock, null)
                        Column(Modifier.weight(1f)) {
                            Text(vault.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (vault.isAvailable) {
                                    stringResource(
                                        if (vault.locationKind == dev.qtremors.arcile.core.vault.domain.VaultLocationKind.USER_FOLDER) {
                                            R.string.onlyfiles_user_folder
                                        } else {
                                            R.string.onlyfiles_app_storage
                                        }
                                    ) + " · " + DateFormat.getDateInstance().format(vault.createdAtMillis)
                                } else {
                                    stringResource(R.string.onlyfiles_folder_unavailable)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    imports[vault.id]?.let { import ->
                        Spacer(Modifier.height(12.dp))
                        val progress = import.progress
                        val totalBytes = progress?.totalBytes
                        if (totalBytes != null && totalBytes > 0L) {
                            LinearProgressIndicator(
                                progress = { progress.bytesCopied.toFloat() / totalBytes.toFloat() },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(
                                    R.string.onlyfiles_importing,
                                    progress?.completedItems ?: 0,
                                    progress?.totalItems ?: 0
                                ),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall
                            )
                            TextButton(onClick = { onCancelImport(vault.id) }, enabled = !import.isCancelling) {
                                Text(stringResource(R.string.onlyfiles_cancel_import))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VaultBrowser(
    nodes: List<VaultNode>,
    onOpen: (VaultNode) -> Unit,
    onMore: (VaultNode) -> Unit,
    onImportFiles: () -> Unit,
    onImportFolder: () -> Unit
) {
    if (nodes.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(stringResource(R.string.onlyfiles_empty_folder))
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(onClick = onImportFiles) { Text(stringResource(R.string.onlyfiles_import_files)) }
            TextButton(onClick = onImportFolder) { Text(stringResource(R.string.onlyfiles_import_folder)) }
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        items(nodes, key = VaultNode::id) { node ->
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(node) }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(nodeIcon(node), null, modifier = Modifier.size(30.dp))
                Column(Modifier.weight(1f)) {
                    Text(node.name, maxLines = 1)
                    if (!node.isDirectory) {
                        Text(formatBytes(node.sizeBytes), style = MaterialTheme.typography.bodySmall)
                    }
                }
                IconButton(onClick = { onMore(node) }) {
                    Icon(Icons.Default.MoreVert, stringResource(R.string.onlyfiles_more))
                }
            }
            HorizontalDivider(Modifier.padding(start = 62.dp))
        }
    }
}

@Composable
private fun PasswordDialog(title: String, actionLabel: String, onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { PasswordField(password, { password = it }, reveal, { reveal = !reveal }) },
        confirmButton = { Button(onClick = { onSubmit(password) }, enabled = password.isNotEmpty()) { Text(actionLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.onlyfiles_cancel)) } }
    )
}

@Composable
internal fun PasswordField(value: String, onValueChange: (String) -> Unit, reveal: Boolean, onReveal: () -> Unit) {
    OutlinedTextField(
        value,
        onValueChange,
        label = { Text(stringResource(R.string.onlyfiles_password)) },
        visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = onReveal) {
                Icon(
                    if (reveal) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    stringResource(if (reveal) R.string.onlyfiles_hide_password else R.string.onlyfiles_show_password)
                )
            }
        },
        singleLine = true
    )
}

@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var value by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value, { value = it }, singleLine = true) },
        confirmButton = { Button(onClick = { onSubmit(value.trim()) }, enabled = value.isNotBlank()) { Text(title) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.onlyfiles_cancel)) } }
    )
}

@Composable
private fun ItemActionDialog(node: VaultNode, onDismiss: () -> Unit, onRename: (String) -> Unit, onDelete: () -> Unit) {
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    when {
        renaming -> NameDialog(stringResource(R.string.onlyfiles_rename), node.name, onDismiss, onRename)
        deleting -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.onlyfiles_delete)) },
            text = { Text(stringResource(R.string.onlyfiles_delete_confirm, node.name)) },
            confirmButton = { Button(onClick = onDelete) { Text(stringResource(R.string.onlyfiles_delete)) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.onlyfiles_cancel)) } }
        )
        else -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(node.name) },
            text = {
                Column {
                    TextButton(onClick = { renaming = true }) {
                        Icon(Icons.Default.CreateNewFolder, null)
                        Text(stringResource(R.string.onlyfiles_rename), Modifier.padding(start = 8.dp))
                    }
                    TextButton(onClick = { deleting = true }) {
                        Icon(Icons.Default.Delete, null)
                        Text(stringResource(R.string.onlyfiles_delete), Modifier.padding(start = 8.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.onlyfiles_close)) } }
        )
    }
}
