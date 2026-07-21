package dev.qtremors.arcile.feature.onlyfiles

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
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
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.pluralStringResource
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
import dev.qtremors.arcile.core.ui.menus.ExpandableFabMenu
import dev.qtremors.arcile.core.ui.menus.FabMenuItem
import dev.qtremors.arcile.core.ui.ArcileScreenScaffold
import dev.qtremors.arcile.core.ui.SearchTopBar
import dev.qtremors.arcile.core.ui.theme.bounceClickable
import dev.qtremors.arcile.core.ui.theme.ExpressiveShapes
import dev.qtremors.arcile.core.ui.SplitButtonGroup
import dev.qtremors.arcile.core.ui.ToolbarAction
import dev.qtremors.arcile.core.ui.EmptyState
import dev.qtremors.arcile.core.ui.EmptyStateVariant
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import dev.qtremors.arcile.core.vault.domain.VaultConflictDecision
import dev.qtremors.arcile.core.vault.domain.VaultBiometricChallenge
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
    VaultSecureWindowEffect(state.selectedVaultId != null && state.screenshotProtectionEnabled)
    OnlyFilesScreen(
        state = state,
        viewModel = viewModel,
        onNavigateBack = onNavigateBack,
        onPlayVideo = onPlayVideo,
        onImportFiles = viewModel::beginLocalFileImport,
        onImportFolder = viewModel::beginLocalFolderImport,
        onBoundaryTransfer = viewModel::beginLocalExport
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OnlyFilesScreen(
    state: OnlyFilesUiState,
    viewModel: OnlyFilesViewModel,
    onNavigateBack: () -> Unit,
    onPlayVideo: (VideoPlaybackSession) -> Unit,
    onImportFiles: () -> Unit,
    onImportFolder: () -> Unit,
    onBoundaryTransfer: (List<VaultNodeMetadata>, Boolean) -> Unit
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
    var fallbackRequest by remember { mutableStateOf<Pair<ExternalAction, List<VaultNodeMetadata>>?>(null) }
    var boundaryRequest by remember { mutableStateOf<Pair<List<VaultNodeMetadata>, Boolean>?>(null) }
    var fabExpanded by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }

    val backHandlerEnabled = state.pendingConflict != null ||
            state.selectedNodeIds.isNotEmpty() ||
            showSearch ||
            state.searchQuery.isNotEmpty() ||
            state.localPicker != null ||
            state.folderPicker != null ||
            state.viewer != null ||
            state.selectedVault != null

    BackHandler(enabled = backHandlerEnabled) {
        when {
            state.pendingConflict != null -> viewModel.resolveConflict(VaultConflictDecision.SKIP, false)
            state.selectedNodeIds.isNotEmpty() -> viewModel.clearSelection()
            showSearch -> {
                showSearch = false
                viewModel.updateSearch("")
            }
            state.searchQuery.isNotEmpty() -> viewModel.updateSearch("")
            state.localPicker != null -> viewModel.navigateLocalPickerUp()
            state.folderPicker != null -> viewModel.navigateVaultFolderUp()
            state.viewer != null -> viewModel.navigateUp()
            state.selectedVault != null -> viewModel.navigateUp()
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let { snackbarHost.showSnackbar(it); viewModel.clearMessage() }
    }

    state.viewer?.let { node ->
        ViewerScreen(node, viewModel, viewModel::navigateUp)
        return
    }

    ArcileScreenScaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            if (showSearch && state.selectedVault != null && state.selectedNodeIds.isEmpty()) {
                SearchTopBar(
                    query = state.searchQuery,
                    onQueryChange = viewModel::updateSearch,
                    onClose = { showSearch = false; viewModel.updateSearch("") },
                    onFilterClick = viewModel::toggleRecursiveSearch,
                    placeholder = stringResource(R.string.onlyfiles_search)
                )
            } else Column {
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
                        },
                        onExport = { boundaryRequest = state.selectedNodes to false },
                        onMoveOut = { boundaryRequest = state.selectedNodes to true }
                    )
                } else {
                    LargeTopAppBar(
                        title = {
                            Column {
                                Text(state.selectedVault?.name ?: stringResource(R.string.onlyfiles_title))
                                if (state.selectedVault != null) {
                                    Text(
                                        state.currentDirectory?.name?.takeIf(String::isNotBlank) ?: "/",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .bounceClickable { if (!viewModel.navigateUp()) onNavigateBack() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.onlyfiles_back))
                            }
                        },
                        actions = {
                            if (state.selectedVault != null) {
                                val topActions = remember {
                                    listOf(
                                        ToolbarAction(
                                            icon = Icons.Default.Search,
                                            contentDescription = context.getString(R.string.onlyfiles_search),
                                            onClick = { showSearch = true }
                                        ),
                                        ToolbarAction(
                                            icon = Icons.AutoMirrored.Filled.Sort,
                                            contentDescription = context.getString(R.string.onlyfiles_sort),
                                            onClick = { showSort = true }
                                        )
                                    )
                                }
                                SplitButtonGroup(actions = topActions)
                                Spacer(modifier = Modifier.width(4.dp))
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                        .bounceClickable { viewModel.refresh() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Refresh, stringResource(R.string.onlyfiles_refresh))
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                        .bounceClickable { showOverflow = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.MoreVert, stringResource(R.string.onlyfiles_more))
                                }
                                /* Vault administration stays memory-only and outside navigation arguments. */
                                VaultActionsMenu(
                                    expanded = showOverflow,
                                    vault = requireNotNull(state.selectedVault),
                                    viewModel = viewModel,
                                    onDismiss = { showOverflow = false }
                                )
                            } else {
                                if (state.vaults.any(VaultSummary::isUnlocked)) {
                                    TextButton(
                                        onClick = viewModel::lockAll,
                                        modifier = Modifier.bounceClickable { viewModel.lockAll() }
                                    ) { Text(stringResource(R.string.onlyfiles_lock_all)) }
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                    )
                }
            }
        },
        floatingActionButton = {
            ExpandableFabMenu(
                isExpanded = fabExpanded,
                onToggleExpand = { fabExpanded = !fabExpanded },
                fabIconRotation = if (fabExpanded) 45f else 0f,
                items = if (state.selectedVault == null) {
                    listOf(
                        FabMenuItem(stringResource(R.string.onlyfiles_create), Icons.Default.Lock, onClick = {
                            fabExpanded = false; showCreateVault = true
                        }),
                        FabMenuItem(stringResource(R.string.onlyfiles_add_existing), Icons.Outlined.FolderOpen, onClick = {
                            fabExpanded = false; viewModel.beginAttachVault()
                        })
                    )
                } else {
                    listOf(
                        FabMenuItem(stringResource(R.string.onlyfiles_import_files), Icons.Default.UploadFile, onClick = {
                            fabExpanded = false; onImportFiles()
                        }),
                        FabMenuItem(stringResource(R.string.onlyfiles_import_folder), Icons.Outlined.FolderOpen, onClick = {
                            fabExpanded = false; onImportFolder()
                        }),
                        FabMenuItem(stringResource(R.string.onlyfiles_new_folder), Icons.Default.CreateNewFolder, onClick = {
                            fabExpanded = false; createItem = CreateItemKind.FOLDER
                        }),
                        FabMenuItem(stringResource(R.string.onlyfiles_new_item), Icons.Default.Description, onClick = {
                            fabExpanded = false; createItem = CreateItemKind.FILE
                        })
                    )
                }
            )
        },
        bottomBar = {
            if (state.clipboard != null || state.transferProgress != null) {
                Column {
                    state.clipboard?.let { clipboard ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            tonalElevation = 4.dp
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    pluralStringResource(
                                        R.plurals.onlyfiles_clipboard_count,
                                        clipboard.sources.size,
                                        clipboard.sources.size
                                    ),
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                TextButton(
                                    onClick = viewModel::clearClipboard,
                                    modifier = Modifier.bounceClickable { viewModel.clearClipboard() }
                                ) { Text(stringResource(R.string.onlyfiles_clear)) }
                                Spacer(Modifier.width(8.dp))
                                Button(
                                    onClick = viewModel::paste,
                                    enabled = !state.busy,
                                    shape = ExpressiveShapes.medium,
                                    modifier = Modifier.bounceClickable(enabled = !state.busy) { viewModel.paste() }
                                ) { Text(stringResource(R.string.onlyfiles_paste)) }
                            }
                        }
                    }
                    state.transferProgress?.let { progress ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            tonalElevation = 6.dp
                        ) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                LinearProgressIndicator(
                                    progress = { progress.completedTopLevelItems.toFloat() / progress.totalTopLevelItems.coerceAtLeast(1) },
                                    modifier = Modifier.fillMaxWidth().clip(CircleShape)
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        progress.currentName ?: stringResource(R.string.onlyfiles_processing),
                                        Modifier.weight(1f),
                                        maxLines = 1,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    TextButton(
                                        onClick = viewModel::cancelTransfer,
                                        modifier = Modifier.bounceClickable { viewModel.cancelTransfer() }
                                    ) { Text(stringResource(R.string.onlyfiles_cancel)) }
                                }
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
                    vaults = state.vaults,
                    imports = state.activeImports,
                    onOpen = { if (!viewModel.openVault(it)) unlockVault = it },
                    onUnlockBiometric = { vault ->
                        viewModel.prepareBiometricUnlock(vault.id) { challenge ->
                            val activity = context.findActivity() ?: run { challenge.close(); return@prepareBiometricUnlock }
                            showBiometricPrompt(activity, challenge) { result ->
                                if (result.isSuccess) {
                                    viewModel.biometricCompleted(vault.id)
                                }
                            }
                        }
                    },
                    onCancelImport = viewModel::cancelImport
                )
                if (state.vaults.isEmpty()) {
                    EmptyState(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        variant = EmptyStateVariant.Folder,
                        icon = Icons.Default.Lock,
                        title = stringResource(R.string.onlyfiles_empty),
                        description = "",
                        action = {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = { showCreateVault = true },
                                    modifier = Modifier.bounceClickable { showCreateVault = true }
                                ) {
                                    Text(stringResource(R.string.onlyfiles_create))
                                }
                                TextButton(
                                    onClick = viewModel::beginAttachVault,
                                    modifier = Modifier.bounceClickable { viewModel.beginAttachVault() }
                                ) {
                                    Text(stringResource(R.string.onlyfiles_add_existing))
                                }
                            }
                        }
                    )
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
                    Box(contentAlignment = Alignment.Center) { LoadingIndicator() }
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
        VaultUnlockDialog(vault, viewModel) { unlockVault = null }
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
            text = {
                Text(pluralStringResource(
                    R.plurals.onlyfiles_delete_many_confirm,
                    deleteNodes.size,
                    deleteNodes.size
                ))
            },
            confirmButton = { Button(onClick = { val nodes = deleteNodes; deleteNodes = emptyList(); viewModel.delete(nodes) }) { Text(stringResource(R.string.onlyfiles_delete)) } },
            dismissButton = { TextButton(onClick = { deleteNodes = emptyList() }) { Text(stringResource(R.string.onlyfiles_cancel)) } }
        )
    }
    if (showSort) SortDialog(state, onDismiss = { showSort = false }, onSort = { field, direction ->
        showSort = false; viewModel.setSort(field, direction)
    }, onLayoutChange = { layout ->
        viewModel.setLayout(layout)
    })
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
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        fallbackRequest = externalRequest
                        externalRequest = null
                    }) { Text(stringResource(R.string.onlyfiles_compatibility_copy)) }
                    TextButton(onClick = { externalRequest = null }) {
                        Text(stringResource(R.string.onlyfiles_cancel))
                    }
                }
            }
        )
    }
    fallbackRequest?.let { (action, nodes) ->
        AlertDialog(
            onDismissRequest = { fallbackRequest = null },
            title = { Text(stringResource(R.string.onlyfiles_compatibility_copy)) },
            text = { Text(stringResource(R.string.onlyfiles_compatibility_copy_warning)) },
            confirmButton = {
                Button(onClick = {
                    fallbackRequest = null
                    viewModel.issuePlaintextFallbackAccess(nodes) { grants ->
                        runCatching { launchExternalIntent(context, action, grants) }
                            .onFailure { viewModel.revokeExternalAccess(grants) }
                    }
                }) { Text(stringResource(R.string.onlyfiles_create_copy)) }
            },
            dismissButton = {
                TextButton(onClick = { fallbackRequest = null }) {
                    Text(stringResource(R.string.onlyfiles_cancel))
                }
            }
        )
    }
    boundaryRequest?.let { (nodes, move) ->
        BoundaryTransferDialog(
            move = move,
            onDismiss = { boundaryRequest = null },
            onChooseDestination = {
                boundaryRequest = null
                onBoundaryTransfer(nodes, move)
            }
        )
    }
    OnlyFilesPickerDialogs(state, viewModel)
}
