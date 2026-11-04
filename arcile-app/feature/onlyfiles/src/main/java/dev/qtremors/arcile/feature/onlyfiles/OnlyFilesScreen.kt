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
import dev.qtremors.arcile.core.ui.dialogs.AlertDialog
import dev.qtremors.arcile.core.ui.dialogs.OpenAsDialog
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
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
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
import dev.qtremors.arcile.core.storage.domain.FileOpenAsType
import dev.qtremors.arcile.core.storage.domain.FileOpenBehavior
import dev.qtremors.arcile.core.storage.domain.StorageNodeCapabilities
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import dev.qtremors.arcile.core.ui.lists.FileGrid
import dev.qtremors.arcile.core.ui.lists.FileItemPresentation
import dev.qtremors.arcile.core.ui.lists.FileList
import dev.qtremors.arcile.core.ui.video.VideoPlaybackSession
import dev.qtremors.arcile.core.ui.menus.ExpandableFabMenu
import dev.qtremors.arcile.core.ui.menus.FabMenuItem
import dev.qtremors.arcile.core.ui.ArcileScreenScaffold
import dev.qtremors.arcile.core.ui.ArcileFeedbackSeverity
import dev.qtremors.arcile.core.ui.ArcileFeedbackEvent
import dev.qtremors.arcile.core.ui.showArcileFeedback
import dev.qtremors.arcile.core.presentation.UiText
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
internal enum class ExternalAction { SHARE, OPEN_WITH }
private data class ExternalRequest(
    val action: ExternalAction,
    val nodes: List<VaultNodeMetadata>,
    val openAs: FileOpenAsType? = null,
    val plugin: VaultPluginViewer? = null
)

@Composable
internal fun OnlyFilesRoute(
    entry: OnlyFilesEntry? = null,
    importRequestId: String? = null,
    getFileOpenBehaviors: () -> Map<String, FileOpenBehavior>,
    onNavigateBack: () -> Unit,
    onPlayVideo: (VideoPlaybackSession) -> Unit,
    onOpenOperationDestination: (String?) -> Unit,
    findPlugin: (VaultNodeMetadata) -> VaultPluginViewer?,
    packageInstaller: @Composable (dev.qtremors.arcile.core.vault.domain.VaultExternalGrant, () -> Unit) -> Unit,
    viewModel: OnlyFilesViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(importRequestId) {
        importRequestId?.let(viewModel::acceptBrowserImport)
    }
    VaultSecureWindowEffect(state.selectedVaultId != null && state.screenshotProtectionEnabled)
    VaultPasswordKeyboardProvider(state.numericPasswordKeyboard, viewModel::setNumericPasswordKeyboard) {
        OnlyFilesScreen(
            entry = entry,
            state = state,
            viewModel = viewModel,
            getFileOpenBehaviors = getFileOpenBehaviors,
            onNavigateBack = { viewModel.lockAllAndExit(onNavigateBack) },
            onPlayVideo = onPlayVideo,
            onOpenOperationDestination = { path -> viewModel.lockAllAndExit { onOpenOperationDestination(path) } },
            findPlugin = findPlugin,
            packageInstaller = packageInstaller,
            onImportFiles = viewModel::beginLocalFileImport,
            onImportFolder = viewModel::beginLocalFolderImport,
            onBoundaryTransfer = viewModel::beginLocalExport
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OnlyFilesScreen(
    entry: OnlyFilesEntry?,
    state: OnlyFilesUiState,
    viewModel: OnlyFilesViewModel,
    getFileOpenBehaviors: () -> Map<String, FileOpenBehavior>,
    onNavigateBack: () -> Unit,
    onPlayVideo: (VideoPlaybackSession) -> Unit,
    onOpenOperationDestination: (String?) -> Unit,
    findPlugin: (VaultNodeMetadata) -> VaultPluginViewer?,
    packageInstaller: @Composable (dev.qtremors.arcile.core.vault.domain.VaultExternalGrant, () -> Unit) -> Unit,
    onImportFiles: () -> Unit,
    onImportFolder: () -> Unit,
    onBoundaryTransfer: (List<VaultNodeMetadata>, Boolean) -> Unit
) {
    val snackbarHost = remember(state.mountGeneration) { SnackbarHostState() }
    var feedbackSeverity by remember(state.mountGeneration) { mutableStateOf(ArcileFeedbackSeverity.Info) }
    val feedbackContext = LocalContext.current
    val workspaceScroll = remember(state.mountGeneration) { VaultWorkspaceScroll() }
    val activeTabId = state.workspace.activeTabId
    val listingScroll = remember(workspaceScroll, activeTabId) { workspaceScroll.forTab(activeTabId) }
    LaunchedEffect(workspaceScroll, state.workspace.tabs) { workspaceScroll.retain(state.workspace.tabs) }
    var showCreateVault by remember { mutableStateOf(false) }
    var unlockVaultId by remember { mutableStateOf<VaultId?>(null) }
    var createItem by remember(state.mountGeneration) { mutableStateOf<CreateItemKind?>(null) }
    var renameNode by remember(state.mountGeneration) { mutableStateOf<VaultNodeMetadata?>(null) }
    var batchRenameNodes by remember(state.mountGeneration) { mutableStateOf<List<VaultNodeMetadata>>(emptyList()) }
    var deleteNodes by remember(state.mountGeneration) { mutableStateOf<List<VaultNodeMetadata>>(emptyList()) }
    var trashNodes by remember(state.mountGeneration) { mutableStateOf<List<VaultNodeMetadata>>(emptyList()) }
    var showViewOptions by remember(state.mountGeneration) { mutableStateOf(false) }
    var vaultDetails by remember(state.mountGeneration) { mutableStateOf<VaultDetails?>(null) }
    var externalRequest by remember(state.mountGeneration) { mutableStateOf<ExternalRequest?>(null) }
    var openAsNode by remember(state.mountGeneration) { mutableStateOf<VaultNodeMetadata?>(null) }
    var boundaryRequest by remember(state.mountGeneration) { mutableStateOf<Pair<List<VaultNodeMetadata>, Boolean>?>(null) }
    var createZipNodes by remember(state.mountGeneration) { mutableStateOf<List<VaultNodeMetadata>>(emptyList()) }
    var fabExpanded by remember(state.mountGeneration) { mutableStateOf(false) }
    var showSearch by remember(state.mountGeneration, activeTabId) { mutableStateOf(state.hasSearch) }
    var showSearchFilters by remember(state.mountGeneration) { mutableStateOf(false) }
    var listingWidth by remember(state.mountGeneration) { mutableStateOf<Dp?>(null) }
    val density = LocalDensity.current
    var predictiveBackProgress by remember { mutableFloatStateOf(0f) }
    var isBackPredicting by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val navigateBackThroughState = {
        when {
            state.pendingConflict != null -> viewModel.cancelTransfer()
            state.locationPicker != null -> viewModel.closeLocationPicker()
            state.viewer != null -> viewModel.navigateUp()
            state.localPicker != null -> viewModel.navigateLocalPickerUp()
            state.folderPicker != null -> viewModel.navigateVaultFolderUp()
            state.workspace.switching -> viewModel.navigateUp()
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

    val revealLabel = stringResource(R.string.onlyfiles_reveal_in_folder)
    LaunchedEffect(snackbarHost, state.message, state.completedArchive, state.operationFeedback?.id) {
        val operation = state.operationFeedback?.takeIf { state.message == null }
        if (operation != null && operation.belongsTo(state)) {
            feedbackSeverity = operation.severity
            snackbarHost.showArcileFeedback(feedbackContext, operation.event(
                onReveal = {
                    showSearch = false
                    viewModel.revealOperationDestination(operation, onOpenOperationDestination)
                },
                onDismiss = { viewModel.dismissOperationFeedback(operation.id) },
                onUndo = { viewModel.undoOperationFeedback(operation) }
            ))
        } else state.message?.let { message ->
            val output = state.completedArchive
            feedbackSeverity = ArcileFeedbackSeverity.Info
            snackbarHost.showArcileFeedback(feedbackContext, ArcileFeedbackEvent(
                message = UiText.Dynamic(message),
                actionLabel = output?.let { UiText.Dynamic(revealLabel) },
                onAction = {
                    viewModel.clearMessage()
                    if (output != null) {
                        showSearch = false
                        viewModel.revealInParent(output)
                    }
                },
                onDismiss = viewModel::clearMessage
            ))
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
                    node = node,
                    mediaQueue = state.viewerQueue,
                    imageSelection = state.selectedNodeIds.takeIf { state.viewerSelectionEnabled },
                    nextMediaPage = state.viewerNextPageToken,
                    location = state.propertiesLocation(listOf(node)),
                    viewModel = viewModel,
                    archiveOperationActive = state.archiveProgress != null,
                    onBack = { navigateBackThroughState() },
                    packageInstaller = packageInstaller,
                    modifier = predictiveBackModifier
                )
            }
            state.locationPicker?.let { OnlyFilesLocationSheet(it, state.selectedVault?.name.orEmpty(), viewModel) }
            state.archiveProgress?.takeUnless {
                node.builtInOpen() == dev.qtremors.arcile.core.storage.domain.BuiltInFileOpen.ARCHIVE &&
                    it.action == VaultArchiveAction.EXTRACT
            }?.let {
                OnlyFilesArchiveToolbar(it, viewModel::cancelArchiveOperation, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
            }
            ArcileSnackbarHost(snackbarHost, Modifier.align(Alignment.BottomCenter).navigationBarsPadding()) { feedbackSeverity }
        }
        return
    }

    ArcileScreenScaffold(
        modifier = predictiveBackModifier.arcileTopAppBarNestedScroll(scrollBehavior),
        snackbarHost = { ArcileSnackbarHost(snackbarHost, severityFor = { feedbackSeverity }) },
        topBar = { Column {
            if (showSearch && state.selectedVault != null && state.selectedNodeIds.isEmpty() && !state.workspace.switching) {
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
                val haptics = rememberArcileHaptics()
                val vaultOverflow: (@Composable (Boolean, () -> Unit) -> Unit)? = if (vault == null) null else {
                    { expanded, dismiss ->
                        VaultActionsMenu(
                            expanded, vault, viewModel, state.viewOptions.showHiddenFiles,
                            state.currentDirectory?.id,
                            state.currentDirectory?.id?.let { it in state.pinnedDirectoryIds } == true,
                            state.hasExactFolderView,
                            state.workspaceTabsEnabled,
                            state.exportRetries.isNotEmpty() && !state.busy,
                            { vaultDetails = it },
                            dismiss
                        )
                    }
                }
                ArcileTopBar(
                    title = state.currentDirectory?.name?.takeIf(String::isNotBlank)
                        ?: vault?.name ?: stringResource(
                            if (state.browserImportCount > 0) R.string.onlyfiles_choose_import_vault else R.string.onlyfiles_title
                        ),
                    selectionCount = selected.size,
                    selectedSize = selected.takeIf { it.isNotEmpty() }?.let {
                        formatBytes(LocalContext.current, it.sumOf(VaultNodeMetadata::sizeBytes))
                    },
                    scrollBehavior = scrollBehavior,
                    options = ArcileTopBarOptions(
                        showBackArrow = true,
                        showSettingsIcon = vault == null,
                        showSearchAction = vault != null && !state.workspace.switching,
                        showSortAction = vault != null && !state.workspace.switching,
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
                                    viewModel.selectAll()
                                }
                                TopBarAction.InvertSelection -> {
                                    haptics.selectionChanged()
                                    viewModel.invertSelection()
                                }
                                else -> Unit
                            }
                        },
                        onBackClick = { navigateBackThroughState() },
                        onSettingsClick = viewModel::openSettings
                    )
                )
            }
            OnlyFilesWorkspaceTabs(state, listingScroll, viewModel)
        } },
        floatingActionButton = {
            if (state.selectedNodeIds.isEmpty() && !showSearch && !state.workspace.switching &&
                (state.selectedVaultId == null || state.clipboard == null) &&
                state.transferProgress == null && state.archiveProgress == null
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
                        }),
                        FabMenuItem(stringResource(R.string.onlyfiles_restore_backup), Icons.Outlined.FolderOpen, onClick = {
                            fabExpanded = false; viewModel.beginRestoreBackup()
                        })
                    )
                } else {
                    listOf(
                        FabMenuItem(stringResource(R.string.onlyfiles_import_files), Icons.Default.UploadFile, onClick = {
                            fabExpanded = false; onImportFiles()
                        }),
                        FabMenuItem(stringResource(R.string.onlyfiles_move_into_vault), Icons.Default.UploadFile, onClick = {
                            fabExpanded = false; viewModel.beginLocalMoveIntoVault()
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
                getFileOpenBehaviors = getFileOpenBehaviors,
                onOpenExternally = { node -> externalRequest = ExternalRequest(ExternalAction.OPEN_WITH, listOf(node)) },
                onOpenPlugin = { node ->
                    val plugin = findPlugin(node)
                    if (plugin != null) {
                        externalRequest = ExternalRequest(ExternalAction.OPEN_WITH, listOf(node), plugin = plugin)
                    }
                    plugin != null
                },
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
            val archiveProgress = state.archiveProgress
            if (archiveProgress != null) {
                OnlyFilesArchiveToolbar(archiveProgress, viewModel::cancelArchiveOperation, toolbarModifier)
            } else if (state.selectedNodeIds.isNotEmpty()) {
                val selected = state.selectedNodes
                VaultSelectionToolbar(
                    nodes = selected,
                    viewModel = viewModel,
                    onRename = {
                        if (selected.size == 1) renameNode = selected.single()
                        else if (selected.size > 1) batchRenameNodes = selected
                    },
                    onDelete = { deleteNodes = selected },
                    onShare = {
                        externalRequest = ExternalRequest(ExternalAction.SHARE, selected.filter {
                            !it.isDirectory && it.ref.capabilities.canShare
                        })
                    },
                    onOpenWith = { externalRequest = ExternalRequest(ExternalAction.OPEN_WITH, selected) },
                    onOpenAs = { openAsNode = selected.singleOrNull() },
                    onPreviewImages = selected.firstOrNull()?.takeIf {
                        selected.all(VaultNodeMetadata::isViewableImage)
                    }?.let { image -> { viewModel.open(image, keepSelection = true) } },
                    onExport = { boundaryRequest = selected to false },
                    onMoveOut = { boundaryRequest = selected to true },
                    onMoveToTrash = { trashNodes = selected },
                    onReveal = selected.singleOrNull()?.takeIf { state.hasSearch }?.let { node ->
                        { showSearch = false; viewModel.revealInParent(node) }
                    },
                    onCreateZip = { createZipNodes = selected }.takeIf {
                        !state.busy && selected.isNotEmpty() && selected.all { node -> node.ref.capabilities.canRead }
                    },
                    modifier = toolbarModifier,
                    onEditAudio = {
                        viewModel.editVaultAudio(selected, feedbackContext.cacheDir.path) { launchPrivateAudioEditor(feedbackContext, it) }
                        viewModel.clearSelection()
                    }.takeIf { !state.busy && canEditVaultAudio(selected) }
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
    if (state.isSelectingRows) {
        AlertDialog(
            onDismissRequest = viewModel::cancelSelection,
            title = { Text(stringResource(R.string.onlyfiles_selecting_rows)) },
            text = { LinearProgressIndicator(Modifier.fillMaxWidth()) },
            confirmButton = {
                TextButton(onClick = viewModel::cancelSelection) {
                    Text(stringResource(R.string.onlyfiles_cancel))
                }
            }
        )
    }
    if (batchRenameNodes.isNotEmpty()) {
        OnlyFilesBatchRenameDialog(
            nodes = batchRenameNodes,
            loadSiblingNames = viewModel::batchRenameSiblingNames,
            onDismiss = { batchRenameNodes = emptyList() },
            onRename = {
                batchRenameNodes = emptyList()
                viewModel.batchRename(it)
            }
        )
    }
    if (deleteNodes.isNotEmpty()) {
        VaultDeleteConfirmation(deleteNodes, onDismiss = { deleteNodes = emptyList() }) {
            val nodes = deleteNodes
            deleteNodes = emptyList()
            viewModel.delete(nodes)
        }
    }
    if (trashNodes.isNotEmpty()) {
        VaultMoveToTrashDialog(
            onDismiss = { trashNodes = emptyList() },
            onMove = {
                val nodes = trashNodes
                trashNodes = emptyList()
                viewModel.moveVaultItemsToTrash(nodes)
            }
        )
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
            loadSelectionTotals = viewModel::selectionTotals,
            openReader = viewModel::openReader,
            onDismiss = viewModel::dismissProperties
        )
    }
    state.locationPicker?.let { OnlyFilesLocationSheet(it, state.selectedVault?.name.orEmpty(), viewModel) }
    if (createZipNodes.isNotEmpty()) {
        VaultCreateZipDialog(
            createZipNodes, state.nodes.mapTo(mutableSetOf(), VaultNodeMetadata::name),
            onDismiss = { createZipNodes = emptyList() },
            destinationPath = state.directoryStack.joinToString("/") { it.name }
        ) { name, compression, success, password ->
            val nodes = createZipNodes
            createZipNodes = emptyList()
            viewModel.createZip(nodes, name, compression, success, password)
        }
    }
    if (state.selectedVaultId != null) {
        state.pendingConflict?.let { prompt ->
            VaultConflictDialog(prompt, viewModel::resolveConflict, viewModel::cancelTransfer)
        }
        state.pendingUndoConfirmation?.takeIf { it.belongsTo(state) }?.let { feedback ->
            OnlyFilesUndoConfirmationDialog(
                feedback,
                onConfirm = { viewModel.confirmUndo(feedback) },
                onDismiss = { viewModel.cancelUndoConfirmation(feedback) }
            )
        }
    }
    state.healthReport?.let {
        HealthDialog(it, state.busy, viewModel::dismissHealthReport, viewModel::recoverVaultTransactions)
    }
    state.recovery?.let { recovery ->
        OnlyFilesRecoveryDialog(recovery, viewModel::resumeRecovery, viewModel::discardRecovery, viewModel::closeRecovery)
    }
    OnlyFilesBackupDialogs(state, viewModel)
    val vaultId = state.selectedVaultId
    if (vaultId != null) {
        when (vaultDetails) {
            VaultDetails.STORAGE -> VaultStorageUsageDialog(vaultId, viewModel::vaultStorageUsage) { vaultDetails = null }
            VaultDetails.EXTERNAL_ACCESS -> VaultExternalGrantsDialog(vaultId, state.mountGeneration, viewModel) { vaultDetails = null }
            null -> Unit
        }
    }
    if (vaultId != null && (state.pendingMoveIntoVault.isNotEmpty() || state.browserImportMovePending)) {
        val browserMove = state.browserImportMovePending
        OnlyFilesMoveIntoVaultDialog(
            onConfirm = {
                if (browserMove) viewModel.resolveBrowserMoveIntoVault(true)
                else viewModel.resolveLocalMoveIntoVault(true)
            },
            onDismiss = {
                if (browserMove) viewModel.resolveBrowserMoveIntoVault(false)
                else viewModel.resolveLocalMoveIntoVault(false)
            }
        )
    }
    openAsNode?.let { node ->
        OpenAsDialog(
            onSelect = { type ->
                openAsNode = null
                externalRequest = ExternalRequest(ExternalAction.OPEN_WITH, listOf(node), type)
            },
            onDismiss = { openAsNode = null }
        )
    }
    externalRequest?.let { (action, nodes, openAs, plugin) ->
        VaultExternalAccessDialogs(action, nodes, viewModel, openAs, plugin) { externalRequest = null }
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
        onSetBackgroundLockDelay = viewModel::setBackgroundLockDelay,
        onSetLockOnScreenOff = viewModel::setLockOnScreenOff,
        onSetNumericKeyboard = viewModel::setNumericPasswordKeyboard,
        onClearThumbnails = viewModel::clearVaultThumbnails,
        onRevokeExternalAccess = viewModel::revokeAllVaultExternalAccess,
        onShowDisclosure = viewModel::openSecurityDisclosure
    )
    if (state.showSecurityDisclosure) OnlyFilesSecurityDisclosureDialog(
        onDismiss = viewModel::closeSecurityDisclosure
    )
    OnlyFilesPickerDialogs(state, viewModel)
}
