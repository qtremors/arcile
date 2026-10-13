package dev.qtremors.arcile.feature.onlyfiles

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.ui.platform.LocalContext
import dev.qtremors.arcile.core.ui.ArcileTopBar
import dev.qtremors.arcile.core.ui.ArcileTopBarActions
import dev.qtremors.arcile.core.ui.ArcileTopBarMenuAction
import dev.qtremors.arcile.core.ui.ArcileTopBarOptions
import dev.qtremors.arcile.core.ui.TopBarAction
import dev.qtremors.arcile.core.ui.rememberArcileHaptics

import dev.qtremors.arcile.core.ui.arcileTopAppBarNestedScroll
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import dev.qtremors.arcile.core.ui.dialogs.CreateFileDialog
import dev.qtremors.arcile.core.ui.dialogs.CreateFolderDialog
import dev.qtremors.arcile.core.ui.dialogs.CreateSyntheticFileDialog
import dev.qtremors.arcile.core.ui.dialogs.RenameDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalDensity
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
import dev.qtremors.arcile.core.ui.ArcileSnackbarHost
import dev.qtremors.arcile.core.ui.SearchTopBar
import dev.qtremors.arcile.core.ui.SearchFiltersSheet
import dev.qtremors.arcile.core.ui.ExpressiveFilterChip
import dev.qtremors.arcile.core.ui.lists.ActiveFiltersRow
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import dev.qtremors.arcile.core.ui.theme.bounceClickable
import dev.qtremors.arcile.core.ui.theme.ExpressiveShapes
import dev.qtremors.arcile.core.ui.EmptyState
import dev.qtremors.arcile.core.ui.EmptyStateVariant
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.graphicsLayer
import dev.qtremors.arcile.core.vault.domain.VaultConflictDecision
import dev.qtremors.arcile.core.vault.domain.VaultBiometricChallenge
import dev.qtremors.arcile.core.vault.domain.VaultExternalGrant
import dev.qtremors.arcile.core.vault.domain.VaultHealthMode
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultImportState
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import dev.qtremors.arcile.core.vault.domain.VaultSortDirection
import dev.qtremors.arcile.core.vault.domain.VaultSortField
import dev.qtremors.arcile.core.vault.domain.VaultSummary
import java.text.DateFormat
import kotlinx.coroutines.flow.collect

internal enum class CreateItemKind { FOLDER, FILE, SYNTHETIC }
internal enum class ExternalAction { SHARE, OPEN_WITH, INSTALL_APK }

@Composable
internal fun OnlyFilesRoute(
    entry: OnlyFilesEntry? = null,
    onNavigateBack: () -> Unit,
    onPlayVideo: (VideoPlaybackSession) -> Unit,
    viewModel: OnlyFilesViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    VaultSecureWindowEffect(state.selectedVaultId != null && state.screenshotProtectionEnabled)
    OnlyFilesScreen(
        entry = entry,
        state = state,
        viewModel = viewModel,
        onNavigateBack = { viewModel.lockAllAndExit(onNavigateBack) },
        onPlayVideo = onPlayVideo,
        onImportFiles = viewModel::beginLocalFileImport,
        onImportFolder = viewModel::beginLocalFolderImport,
        onBoundaryTransfer = viewModel::beginLocalExport
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OnlyFilesScreen(
    entry: OnlyFilesEntry?,
    state: OnlyFilesUiState,
    viewModel: OnlyFilesViewModel,
    onNavigateBack: () -> Unit,
    onPlayVideo: (VideoPlaybackSession) -> Unit,
    onImportFiles: () -> Unit,
    onImportFolder: () -> Unit,
    onBoundaryTransfer: (List<VaultNodeMetadata>, Boolean) -> Unit
) {
    val snackbarHost = remember(state.mountGeneration) { SnackbarHostState() }
    val listingScroll = remember(state.mountGeneration) { VaultListingScroll() }
    var showCreateVault by remember { mutableStateOf(false) }
    var unlockVaultId by remember { mutableStateOf<VaultId?>(null) }
    var createItem by remember(state.mountGeneration) { mutableStateOf<CreateItemKind?>(null) }
    var renameNode by remember(state.mountGeneration) { mutableStateOf<VaultNodeMetadata?>(null) }
    var deleteNodes by remember(state.mountGeneration) { mutableStateOf<List<VaultNodeMetadata>>(emptyList()) }
    var showViewOptions by remember(state.mountGeneration) { mutableStateOf(false) }
    var externalRequest by remember(state.mountGeneration) { mutableStateOf<Pair<ExternalAction, List<VaultNodeMetadata>>?>(null) }
    var boundaryRequest by remember(state.mountGeneration) { mutableStateOf<Pair<List<VaultNodeMetadata>, Boolean>?>(null) }
    var fabExpanded by remember(state.mountGeneration) { mutableStateOf(false) }
    var showSearch by remember(state.mountGeneration) { mutableStateOf(state.hasSearch) }
    var showSearchFilters by remember(state.mountGeneration) { mutableStateOf(false) }
    var listingWidth by remember(state.mountGeneration) { mutableStateOf<Dp?>(null) }
    val density = LocalDensity.current
    var predictiveBackProgress by remember { mutableFloatStateOf(0f) }
    var isBackPredicting by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val navigateBackThroughState = {
        when {
            state.viewer != null -> viewModel.navigateUp()
            state.pendingConflict != null -> viewModel.resolveConflict(VaultConflictDecision.SKIP, false)
            state.locationPicker != null -> viewModel.closeLocationPicker()
            state.localPicker != null -> viewModel.navigateLocalPickerUp()
            state.folderPicker != null -> viewModel.navigateVaultFolderUp()
            fabExpanded -> fabExpanded = false
            showSearch -> {
                showSearch = false
                viewModel.clearSearch()
            }
            state.hasSearch -> viewModel.clearSearch()
            state.selectedNodeIds.isNotEmpty() -> viewModel.clearSelection()
            state.selectedVault != null -> viewModel.navigateUp()
            else -> onNavigateBack()
        }
    }
    PredictiveBackHandler(enabled = state.viewer?.isViewableText() != true) { progressFlow ->
        try {
            isBackPredicting = true
            progressFlow.collect { event -> predictiveBackProgress = event.progress }
            navigateBackThroughState()
        } finally {
            isBackPredicting = false
            predictiveBackProgress = 0f
        }
    }
    val predictiveBackModifier = Modifier.graphicsLayer {
        if (isBackPredicting) {
            translationX = predictiveBackProgress * size.width * 0.12f
            scaleX = 1f - predictiveBackProgress * 0.02f
            scaleY = 1f - predictiveBackProgress * 0.02f
            alpha = 1f - predictiveBackProgress * 0.15f
        }
    }

    LaunchedEffect(snackbarHost, state.message) {
        state.message?.let { message ->
            snackbarHost.showSnackbar(
                message = message,
                withDismissAction = true,
                duration = SnackbarDuration.Short
            )
            viewModel.clearMessage()
        }
    }

    val unlockVault = pendingUnlockVault(unlockVaultId, state.selectedVaultId, state.vaults)
    val entryVault = state.vaults.firstOrNull { it.id == entry?.vaultId }
    LaunchedEffect(entry, entryVault) {
        if (entry != null && entryVault != null && !viewModel.openEntry(entry, entryVault)) {
            unlockVaultId = entryVault.id
        }
    }
    LaunchedEffect(unlockVaultId, state.selectedVaultId, state.vaults) {
        if (unlockVaultId != null && unlockVault == null) unlockVaultId = null
    }

    state.viewer?.let { node ->
        Box(Modifier.fillMaxSize()) {
            androidx.compose.runtime.key(state.mountGeneration) {
                ViewerScreen(
                    node, state.viewerQueue, state.propertiesLocation(listOf(node)), viewModel,
                    { navigateBackThroughState() }, predictiveBackModifier
                )
            }
            ArcileSnackbarHost(snackbarHost, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
        }
        return
    }

    ArcileScreenScaffold(
        modifier = predictiveBackModifier.arcileTopAppBarNestedScroll(scrollBehavior),
        snackbarHost = { ArcileSnackbarHost(snackbarHost) },
        topBar = {
            if (showSearch && state.selectedVault != null && state.selectedNodeIds.isEmpty()) {
                Column {
                    SearchTopBar(
                        query = state.searchQuery,
                        onQueryChange = viewModel::updateSearch,
                        onClose = { showSearch = false; viewModel.clearSearch() },
                        onFilterClick = { showSearchFilters = true },
                        placeholder = stringResource(R.string.onlyfiles_search),
                        filtersActive = state.searchFilters.hasActiveFilters || !state.recursiveSearch || state.searchScope != null
                    )
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val scopeLabel = state.searchScope?.lastOrNull()?.name?.ifBlank { state.selectedVault?.name.orEmpty() }
                        ExpressiveFilterChip(
                            selected = scopeLabel != null,
                            onClick = viewModel::openSearchScope,
                            label = {
                                Text(scopeLabel ?: stringResource(R.string.onlyfiles_search_scope), maxLines = 1)
                            },
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (scopeLabel != null) TextButton(onClick = viewModel::clearSearchScope) {
                            Text(stringResource(R.string.onlyfiles_clear_scope))
                        }
                    }
                    if (state.searchFilters.hasActiveFilters) {
                        ActiveFiltersRow(state.searchFilters, viewModel::updateSearchFilters)
                    }
                }
            } else {
                val vault = state.selectedVault
                val selected = state.selectedNodes
                val lockAllLabel = stringResource(R.string.onlyfiles_lock_all)
                val loadedOnlyMessage = stringResource(R.string.onlyfiles_selection_loaded_only)
                val haptics = rememberArcileHaptics()
                val vaultOverflow: (@Composable (Boolean, () -> Unit) -> Unit)? = if (vault == null) null else {
                    { expanded, dismiss ->
                        VaultActionsMenu(
                            expanded, vault, viewModel, state.viewOptions.showHiddenFiles,
                            state.currentDirectory?.id,
                            state.currentDirectory?.id?.let { it in state.pinnedDirectoryIds } == true,
                            state.hasExactFolderView,
                            dismiss
                        )
                    }
                }
                ArcileTopBar(
                    title = state.currentDirectory?.name?.takeIf(String::isNotBlank)
                        ?: vault?.name ?: stringResource(R.string.onlyfiles_title),
                    selectionCount = selected.size,
                    selectedSize = selected.takeIf { it.isNotEmpty() }?.let {
                        formatBytes(LocalContext.current, it.sumOf(VaultNodeMetadata::sizeBytes))
                    },
                    scrollBehavior = scrollBehavior,
                    options = ArcileTopBarOptions(
                        showBackArrow = true,
                        showSettingsIcon = vault == null,
                        showSearchAction = vault != null,
                        showSortAction = vault != null,
                        showNewFolderAction = false
                    ),
                    menuActions = if (vault == null && state.vaults.any(VaultSummary::isUnlocked)) {
                        listOf(ArcileTopBarMenuAction(lockAllLabel, Icons.Default.Lock, onClick = viewModel::lockAll))
                    } else emptyList(),
                    overflowContent = vaultOverflow,
                    actions = ArcileTopBarActions(
                        onClearSelection = viewModel::clearSelection,
                        onSearchClick = { showSearch = true },
                        onSortClick = { showViewOptions = true },
                        onActionSelected = { action ->
                            when (action) {
                                TopBarAction.SelectAll -> {
                                    haptics.selectionChanged()
                                    viewModel.selectAll(loadedOnlyMessage)
                                }
                                TopBarAction.InvertSelection -> {
                                    haptics.selectionChanged()
                                    viewModel.invertSelection(loadedOnlyMessage)
                                }
                                else -> Unit
                            }
                        },
                        onBackClick = { navigateBackThroughState() },
                        onSettingsClick = viewModel::openSettings
                    )
                )
            }
        },
        floatingActionButton = {
            if (state.selectedNodeIds.isEmpty() && !showSearch &&
                (state.selectedVaultId == null || state.clipboard == null) && state.transferProgress == null
            ) ExpandableFabMenu(
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
                        }),
                        FabMenuItem(stringResource(dev.qtremors.arcile.core.ui.R.string.title_create_synthetic_file), Icons.Default.Description, onClick = {
                            fabExpanded = false; createItem = CreateItemKind.SYNTHETIC
                        })
                    )
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            OnlyFilesMainContent(
                onListingWidthChanged = { listingWidth = with(density) { it.toDp() } },
                state = state,
                viewModel = viewModel,
                listingScroll = listingScroll,
                contentPadding = padding,
                onPlayVideo = onPlayVideo,
                onDirectoryOpened = { showSearch = false },
                onImportFiles = onImportFiles,
                onImportFolder = onImportFolder,
                onUnlockRequired = { unlockVaultId = it },
                onShowCreateVault = { showCreateVault = true }
            )
            if (fabExpanded) {
                Box(
                    Modifier.fillMaxSize().clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { fabExpanded = false }
                    )
                )
            }
            val toolbarModifier = Modifier.align(Alignment.BottomCenter)
                .consumeWindowInsets(padding)
                .padding(bottom = padding.calculateBottomPadding())
            if (state.selectedNodeIds.isNotEmpty()) {
                val selected = state.selectedNodes
                VaultSelectionToolbar(
                    nodes = selected,
                    viewModel = viewModel,
                    onRename = { renameNode = selected.singleOrNull() },
                    onDelete = { deleteNodes = selected },
                    onShare = {
                        externalRequest = ExternalAction.SHARE to selected.filter {
                            !it.isDirectory && it.ref.capabilities.canShare
                        }
                    },
                    onOpenWith = { externalRequest = ExternalAction.OPEN_WITH to selected },
                    onExport = { boundaryRequest = selected to false },
                    onMoveOut = { boundaryRequest = selected to true },
                    onReveal = selected.singleOrNull()?.takeIf { state.hasSearch }?.let { node ->
                        { showSearch = false; viewModel.revealInParent(node) }
                    },
                    modifier = toolbarModifier
                )
            } else {
                OnlyFilesClipboardToolbar(state, viewModel, toolbarModifier)
            }
        }
    }

    if (showCreateVault) CreateVaultDialog(
        onDismiss = { showCreateVault = false },
        onCreateAppPrivate = { name, password -> showCreateVault = false; viewModel.createAppPrivateVault(name, password) },
        onChooseFolder = { name, password -> showCreateVault = false; viewModel.beginFolderVaultCreation(name, password) }
    )
    unlockVault?.let { vault ->
        VaultUnlockDialog(
            vault = vault,
            biometricEnrolled = vault.id in state.biometricVaultIds,
            viewModel = viewModel,
            onDismiss = { unlockVaultId = null }
        )
    }
    if (createItem != null || renameNode != null) {
        val existingNames = remember(state.nodes) { state.nodes.mapTo(mutableSetOf(), VaultNodeMetadata::name) }
        val destination = state.directoryStack.joinToString(" / ") { it.name }
        when (createItem) {
            CreateItemKind.FOLDER -> CreateFolderDialog(
                onDismiss = { createItem = null },
                onConfirm = { name -> createItem = null; viewModel.createFolder(name) },
                existingNames = existingNames,
                destinationPath = destination
            )
            CreateItemKind.FILE -> CreateFileDialog(
                onDismiss = { createItem = null },
                onConfirm = { name -> createItem = null; viewModel.createEmptyFile(name) },
                existingNames = existingNames,
                destinationPath = destination
            )
            CreateItemKind.SYNTHETIC -> CreateSyntheticFileDialog(
                onDismiss = { createItem = null },
                onConfirm = { name, size -> createItem = null; viewModel.createSyntheticFile(name, size) }
            )
            null -> Unit
        }
        renameNode?.let { node ->
            RenameDialog(
                currentName = node.name,
                onDismiss = { renameNode = null },
                onConfirm = { name -> renameNode = null; viewModel.rename(node, name) },
                existingNames = existingNames.takeIf { node.ref.parentId == state.currentDirectory?.id }.orEmpty(),
                isDirectory = node.isDirectory,
                saveInput = false
            )
        }
    }
    if (deleteNodes.isNotEmpty()) {
        VaultDeleteConfirmation(deleteNodes, onDismiss = { deleteNodes = emptyList() }) {
            val nodes = deleteNodes
            deleteNodes = emptyList()
            viewModel.delete(nodes)
        }
    }
    if (showSearchFilters) {
        var recursive by remember { mutableStateOf(state.recursiveSearch) }
        CompositionLocalProvider(LocalSaveableStateRegistry provides null) {
            SearchFiltersSheet(
                currentFilters = state.searchFilters,
                onApplyFilters = { filters ->
                    showSearchFilters = false
                    if (recursive != state.recursiveSearch) {
                        viewModel.setRecursiveSearch(recursive)
                    }
                    viewModel.updateSearchFilters(filters)
                },
                onDismiss = { showSearchFilters = false },
                showStorageFilters = false,
                onClearScope = { recursive = true },
                scopeContent = {
                    ExpressiveFilterChip(
                        selected = recursive,
                        onClick = { recursive = !recursive },
                        label = { Text(stringResource(R.string.onlyfiles_subfolders)) }
                    )
                }
            )
        }
    }
    if (showViewOptions) VaultViewOptionsSheet(
        options = state.viewOptions,
        applyToSubfolders = state.viewOptionsApplyToSubfolders,
        gridContentWidth = listingWidth,
        onDismiss = { showViewOptions = false },
        onApply = { options, inherit ->
            showViewOptions = false
            viewModel.setFolderViewOptions(options, inherit)
        }
    )
    if (state.properties.isNotEmpty()) {
        VaultPropertiesDialog(
            nodes = state.properties,
            location = state.propertiesLocation(state.properties),
            loadFolderTotals = viewModel::folderTotals,
            onDismiss = viewModel::dismissProperties
        )
    }
    state.locationPicker?.let { OnlyFilesLocationSheet(it, state.selectedVault?.name.orEmpty(), viewModel) }
    if (state.selectedVaultId != null) {
        state.pendingConflict?.let { prompt -> ConflictDialog(prompt, viewModel::resolveConflict) }
    }
    state.healthReport?.let { HealthDialog(it, viewModel::dismissHealthReport) }
    externalRequest?.let { (action, nodes) ->
        VaultExternalAccessDialogs(action, nodes, viewModel) { externalRequest = null }
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
    if (state.showSettingsSheet) OnlyFilesSettingsSheet(
        state = state,
        onDismiss = viewModel::closeSettings,
        onSetScreenshotProtection = viewModel::setScreenshotProtection,
        onClearThumbnails = viewModel::clearVaultThumbnails,
        onRevokeExternalAccess = viewModel::revokeAllVaultExternalAccess,
        onShowDisclosure = viewModel::openSecurityDisclosure
    )
    if (state.showSecurityDisclosure) OnlyFilesSecurityDisclosureDialog(
        onDismiss = viewModel::closeSecurityDisclosure
    )
    OnlyFilesPickerDialogs(state, viewModel)
}
