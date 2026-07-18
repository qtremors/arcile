package dev.qtremors.arcile.feature.onlyfiles

import android.content.ClipData
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.StorageNodeCapabilities
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import dev.qtremors.arcile.core.ui.lists.FileGrid
import dev.qtremors.arcile.core.ui.lists.FileItemPresentation
import dev.qtremors.arcile.core.ui.lists.FileList
import dev.qtremors.arcile.core.ui.video.VideoPlaybackSession
import dev.qtremors.arcile.core.vault.domain.VaultConflictDecision
import dev.qtremors.arcile.core.vault.domain.VaultExternalGrant
import dev.qtremors.arcile.core.vault.domain.VaultHealthMode
import dev.qtremors.arcile.core.vault.domain.VaultImportState
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import dev.qtremors.arcile.core.vault.domain.VaultSortDirection
import dev.qtremors.arcile.core.vault.domain.VaultSortField
import dev.qtremors.arcile.core.vault.domain.VaultSummary
import java.text.DateFormat

internal enum class CreateItemKind { FOLDER, FILE }
internal enum class ExternalAction { SHARE, OPEN_WITH }

@Composable
internal fun OnlyFilesRoute(
    onNavigateBack: () -> Unit,
    onPlayVideo: (VideoPlaybackSession) -> Unit,
    viewModel: OnlyFilesViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val filesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri ->
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
        viewModel.finishImportSelection(uris.map(Uri::toString))
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            runCatching { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
        viewModel.finishImportSelection(listOfNotNull(uri?.toString()))
    }
    BackHandler {
        when {
            state.pendingConflict != null -> viewModel.resolveConflict(VaultConflictDecision.SKIP, false)
            state.selectedNodeIds.isNotEmpty() -> viewModel.clearSelection()
            state.searchQuery.isNotEmpty() -> viewModel.updateSearch("")
            state.folderPicker != null -> viewModel.navigateVaultFolderUp()
            !viewModel.navigateUp() -> onNavigateBack()
        }
    }
    OnlyFilesScreen(
        state = state,
        viewModel = viewModel,
        onNavigateBack = onNavigateBack,
        onPlayVideo = onPlayVideo,
        onImportFiles = { if (viewModel.beginImportSelection()) filesLauncher.launch(arrayOf("*/*")) },
        onImportFolder = { if (viewModel.beginImportSelection()) folderLauncher.launch(null) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnlyFilesScreen(
    state: OnlyFilesUiState,
    viewModel: OnlyFilesViewModel,
    onNavigateBack: () -> Unit,
    onPlayVideo: (VideoPlaybackSession) -> Unit,
    onImportFiles: () -> Unit,
    onImportFolder: () -> Unit
) {
    val context = LocalContext.current
    val snackbarHost = remember { SnackbarHostState() }
    var showCreateVault by remember { mutableStateOf(false) }
    var unlockVault by remember { mutableStateOf<VaultSummary?>(null) }
    var createItem by remember { mutableStateOf<CreateItemKind?>(null) }
    var renameNode by remember { mutableStateOf<VaultNodeMetadata?>(null) }
    var deleteNodes by remember { mutableStateOf<List<VaultNodeMetadata>>(emptyList()) }
    var showSort by remember { mutableStateOf(false) }
    var showOverflow by remember { mutableStateOf(false) }
    var externalRequest by remember { mutableStateOf<Pair<ExternalAction, List<VaultNodeMetadata>>?>(null) }

    LaunchedEffect(state.message) {
        state.message?.let { snackbarHost.showSnackbar(it); viewModel.clearMessage() }
    }

    state.viewer?.let { node ->
        ViewerScreen(node, viewModel, viewModel::navigateUp)
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            Column {
                if (state.selectedNodeIds.isNotEmpty()) {
                    SelectionTopBar(
                        state,
                        viewModel,
                        onRename = { renameNode = state.selectedNodes.singleOrNull() },
                        onDelete = { deleteNodes = state.selectedNodes },
                        onShare = { externalRequest = ExternalAction.SHARE to state.selectedNodes },
                        onOpenWith = {
                            state.selectedNodes.singleOrNull()?.takeUnless(VaultNodeMetadata::isDirectory)?.let {
                                externalRequest = ExternalAction.OPEN_WITH to listOf(it)
                            }
                        }
                    )
                } else {
                    TopAppBar(
                        title = { Text(state.selectedVault?.name ?: stringResource(R.string.onlyfiles_title)) },
                        navigationIcon = {
                            IconButton(onClick = { if (!viewModel.navigateUp()) onNavigateBack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.onlyfiles_back))
                            }
                        },
                        actions = {
                            if (state.selectedVault != null) {
                                IconButton(onClick = viewModel::toggleLayout) {
                                    Icon(
                                        if (state.layout == OnlyFilesLayout.LIST) Icons.Default.GridView else Icons.Default.ViewList,
                                        stringResource(R.string.onlyfiles_change_layout)
                                    )
                                }
                                IconButton(onClick = { showSort = true }) {
                                    Icon(Icons.Default.Sort, stringResource(R.string.onlyfiles_sort))
                                }
                                IconButton(onClick = { showOverflow = true }) {
                                    Icon(Icons.Default.MoreVert, stringResource(R.string.onlyfiles_more))
                                }
                                DropdownMenu(expanded = showOverflow, onDismissRequest = { showOverflow = false }) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.onlyfiles_refresh)) },
                                        leadingIcon = { Icon(Icons.Default.Refresh, null) },
                                        onClick = { showOverflow = false; viewModel.refresh() }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.onlyfiles_import_files)) },
                                        leadingIcon = { Icon(Icons.Default.UploadFile, null) },
                                        onClick = { showOverflow = false; onImportFiles() }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.onlyfiles_import_folder)) },
                                        leadingIcon = { Icon(Icons.Default.CreateNewFolder, null) },
                                        onClick = { showOverflow = false; onImportFolder() }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.onlyfiles_health_check)) },
                                        leadingIcon = { Icon(Icons.Default.HealthAndSafety, null) },
                                        onClick = { showOverflow = false; viewModel.verifyHealth(VaultHealthMode.QUICK) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.onlyfiles_lock)) },
                                        leadingIcon = { Icon(Icons.Default.Lock, null) },
                                        onClick = { showOverflow = false; viewModel.lockCurrent() }
                                    )
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
                }
                if (state.selectedVault != null && state.selectedNodeIds.isEmpty()) {
                    SearchRow(state, viewModel)
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                if (state.selectedVault == null) showCreateVault = true else createItem = CreateItemKind.FOLDER
            }) {
                Icon(Icons.Default.Add, stringResource(if (state.selectedVault == null) R.string.onlyfiles_create else R.string.onlyfiles_new_item))
            }
        },
        bottomBar = {
            Column {
                state.clipboard?.let { clipboard ->
                    Surface(tonalElevation = 4.dp) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(R.string.onlyfiles_clipboard_count, clipboard.sources.size),
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = viewModel::clearClipboard) { Text(stringResource(R.string.onlyfiles_clear)) }
                            Button(onClick = viewModel::paste, enabled = !state.busy) { Text(stringResource(R.string.onlyfiles_paste)) }
                        }
                    }
                }
                state.transferProgress?.let { progress ->
                    Surface(tonalElevation = 6.dp) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            LinearProgressIndicator(
                                progress = { progress.completedTopLevelItems.toFloat() / progress.totalTopLevelItems.coerceAtLeast(1) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(progress.currentName ?: stringResource(R.string.onlyfiles_processing), Modifier.weight(1f), maxLines = 1)
                                TextButton(onClick = viewModel::cancelTransfer) { Text(stringResource(R.string.onlyfiles_cancel)) }
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (state.selectedVault == null) {
                VaultList(
                    state.vaults,
                    state.activeImports,
                    onOpen = { if (!viewModel.openVault(it)) unlockVault = it },
                    onCancelImport = viewModel::cancelImport
                )
                if (state.vaults.isEmpty()) {
                    Text(stringResource(R.string.onlyfiles_empty), Modifier.align(Alignment.Center))
                }
            } else {
                VaultBrowser(
                    state = state,
                    onOpen = { node ->
                        val vaultId = state.selectedVaultId
                        if (node.isViewableVideo() && vaultId != null) {
                            onPlayVideo(createVaultVideoPlaybackSession(state.displayedNodes, vaultId, node, viewModel::openReader))
                        } else viewModel.open(node)
                    },
                    onToggleSelection = viewModel::toggleSelection,
                    onSelectRange = viewModel::selectRange,
                    onLoadMore = viewModel::loadNextPage,
                    onImportFiles = onImportFiles,
                    onImportFolder = onImportFolder
                )
            }
            if (state.busy && state.transferProgress == null && state.pendingConflict == null) {
                Surface(
                    color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.25f),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
            }
        }
    }

    if (showCreateVault) CreateVaultDialog(
        onDismiss = { showCreateVault = false },
        onCreateAppPrivate = { name, password -> showCreateVault = false; viewModel.createAppPrivateVault(name, password) },
        onChooseFolder = { name, password -> showCreateVault = false; viewModel.beginFolderVaultCreation(name, password) }
    )
    unlockVault?.let { vault ->
        PasswordDialog(vault.name, stringResource(R.string.onlyfiles_unlock), { unlockVault = null }) {
            unlockVault = null; viewModel.unlock(vault.id, it)
        }
    }
    createItem?.let { kind ->
        CreateItemDialog(
            initialKind = kind,
            onDismiss = { createItem = null },
            onCreate = { selectedKind, name ->
                createItem = null
                if (selectedKind == CreateItemKind.FOLDER) viewModel.createFolder(name) else viewModel.createEmptyFile(name)
            }
        )
    }
    renameNode?.let { node ->
        NameDialog(stringResource(R.string.onlyfiles_rename), node.name, { renameNode = null }) {
            renameNode = null; viewModel.rename(node, it)
        }
    }
    if (deleteNodes.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { deleteNodes = emptyList() },
            title = { Text(stringResource(R.string.onlyfiles_delete)) },
            text = { Text(stringResource(R.string.onlyfiles_delete_many_confirm, deleteNodes.size)) },
            confirmButton = { Button(onClick = { val nodes = deleteNodes; deleteNodes = emptyList(); viewModel.delete(nodes) }) { Text(stringResource(R.string.onlyfiles_delete)) } },
            dismissButton = { TextButton(onClick = { deleteNodes = emptyList() }) { Text(stringResource(R.string.onlyfiles_cancel)) } }
        )
    }
    if (showSort) SortDialog(state, onDismiss = { showSort = false }) { field, direction ->
        showSort = false; viewModel.setSort(field, direction)
    }
    state.properties.takeIf(List<VaultNodeMetadata>::isNotEmpty)?.let { PropertiesDialog(it, viewModel::dismissProperties) }
    state.pendingConflict?.let { prompt -> ConflictDialog(prompt, viewModel::resolveConflict) }
    state.healthReport?.let { HealthDialog(it, viewModel::dismissHealthReport) }
    externalRequest?.let { (action, nodes) ->
        AlertDialog(
            onDismissRequest = { externalRequest = null },
            title = { Text(stringResource(if (action == ExternalAction.SHARE) R.string.onlyfiles_share else R.string.onlyfiles_open_with)) },
            text = { Text(stringResource(R.string.onlyfiles_external_warning)) },
            confirmButton = {
                Button(onClick = {
                    externalRequest = null
                    viewModel.issueExternalAccess(nodes) { grants ->
                        runCatching { launchExternalIntent(context, action, grants) }
                            .onFailure { viewModel.revokeExternalAccess(grants) }
                    }
                }) { Text(stringResource(R.string.onlyfiles_continue)) }
            },
            dismissButton = { TextButton(onClick = { externalRequest = null }) { Text(stringResource(R.string.onlyfiles_cancel)) } }
        )
    }
    state.folderPicker?.let { picker ->
        VaultFolderPickerDialog(
            picker,
            viewModel::openVaultFolder,
            viewModel::navigateVaultFolderUp,
            viewModel::chooseVaultFolder,
            viewModel::cancelVaultFolderPicker
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    state: OnlyFilesUiState,
    viewModel: OnlyFilesViewModel,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onOpenWith: () -> Unit
) {
    var more by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text(stringResource(R.string.onlyfiles_selected_count, state.selectedNodeIds.size)) },
        navigationIcon = { IconButton(onClick = viewModel::clearSelection) { Icon(Icons.Default.Close, stringResource(R.string.onlyfiles_close)) } },
        actions = {
            IconButton(onClick = viewModel::selectAll) { Icon(Icons.Default.SelectAll, stringResource(R.string.onlyfiles_select_all)) }
            IconButton(onClick = { viewModel.copy(state.selectedNodes) }) { Icon(Icons.Default.ContentCopy, stringResource(R.string.onlyfiles_copy)) }
            IconButton(onClick = { viewModel.move(state.selectedNodes) }) { Icon(Icons.Default.ContentCut, stringResource(R.string.onlyfiles_move)) }
            IconButton(onClick = onShare) { Icon(Icons.Default.Share, stringResource(R.string.onlyfiles_share)) }
            IconButton(onClick = { more = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.onlyfiles_more)) }
            DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.onlyfiles_rename)) },
                    enabled = state.selectedNodes.size == 1,
                    onClick = { more = false; onRename() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.onlyfiles_open_with)) },
                    enabled = state.selectedNodes.singleOrNull()?.isDirectory == false,
                    onClick = { more = false; onOpenWith() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.onlyfiles_invert_selection)) },
                    onClick = { more = false; viewModel.invertSelection() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.onlyfiles_properties)) },
                    leadingIcon = { Icon(Icons.Default.Info, null) },
                    onClick = { more = false; viewModel.showProperties(state.selectedNodes) }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.onlyfiles_delete)) },
                    leadingIcon = { Icon(Icons.Default.Delete, null) },
                    onClick = { more = false; onDelete() }
                )
            }
        }
    )
}
