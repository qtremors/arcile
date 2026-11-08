package dev.qtremors.arcile.feature.onlyfiles

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import dev.qtremors.arcile.core.ui.theme.LocalReducedMotionEnabled
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.Settings
import dev.qtremors.arcile.core.ui.ArcileSectionHeader
import dev.qtremors.arcile.core.ui.rememberArcileHaptics
import androidx.compose.material.icons.filled.PushPin
import dev.qtremors.arcile.core.ui.ArcileDropdownMenu
import dev.qtremors.arcile.core.ui.ArcileDropdownMenuItem
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.Fingerprint
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.FolderStats
import dev.qtremors.arcile.core.storage.domain.SearchFilters
import dev.qtremors.arcile.core.storage.domain.FileListingPreferences
import dev.qtremors.arcile.core.storage.domain.StorageNodeCapabilities
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import dev.qtremors.arcile.core.storage.domain.VaultThumbnailRequest
import dev.qtremors.arcile.core.ui.lists.FileGrid
import dev.qtremors.arcile.core.ui.lists.FileItemPresentation
import dev.qtremors.arcile.core.ui.lists.FileList
import dev.qtremors.arcile.core.ui.lists.pinchToResizeGrid
import dev.qtremors.arcile.core.ui.scrollbar.ArcileFastScrollbar
import dev.qtremors.arcile.core.ui.scrollbar.LazyGridScrollbarState
import dev.qtremors.arcile.core.ui.scrollbar.LazyListScrollbarState
import androidx.compose.foundation.layout.size
import dev.qtremors.arcile.core.ui.EmptyState
import dev.qtremors.arcile.core.ui.EmptyStateVariant
import dev.qtremors.arcile.core.ui.theme.bounceClickable
import dev.qtremors.arcile.core.ui.theme.storageCard
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultFolderTotals
import dev.qtremors.arcile.core.vault.domain.VaultNodeRef
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collectLatest
import dev.qtremors.arcile.core.vault.domain.DirectoryId
import dev.qtremors.arcile.core.vault.domain.VaultImportState
import dev.qtremors.arcile.core.vault.domain.VaultLocationKind
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import dev.qtremors.arcile.core.vault.domain.VaultSummary
import java.text.DateFormat

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun VaultBrowser(
    state: OnlyFilesUiState,
    listingScroll: VaultListingScroll,
    loadFolderTotals: suspend (VaultNodeRef) -> Result<VaultFolderTotals>,
    onOpen: (VaultNodeMetadata) -> Unit,
    onToggleSelection: (String) -> Unit,
    onSelectRange: (List<String>) -> Unit,
    onLoadMore: () -> Unit,
    onRevealFinished: (Boolean) -> Unit,
    onGridSizeFinalized: (Float) -> Unit,
    onImportFiles: () -> Unit,
    onImportFolder: () -> Unit
) {
    val displayedNodes = remember(state.nodes, state.searchHits, state.hasSearch) { state.displayedNodes }
    val models = remember(displayedNodes) { displayedNodes.map(VaultNodeMetadata::toSharedFileModel) }
    val byOpaquePath = remember(displayedNodes, models) {
        models.zip(displayedNodes).associate { it.first.reference to it.second }
    }
    val parents = remember(state.searchHits) {
        state.searchHits.associate { it.metadata.ref.nodeId to it.parentNames.joinToString(" / ") }
    }
    val scroll = remember(
        listingScroll, state.currentDirectory?.id, state.searchScope?.lastOrNull()?.id,
        state.searchQuery, state.searchFilters, state.recursiveSearch
    ) {
        listingScroll.forState(state)
    }
    val pendingViewport = scroll.pendingViewport.value
    val nextToken = if (state.hasSearch) state.searchNextPageToken else state.nextPageToken
    LaunchedEffect(scroll, pendingViewport, displayedNodes, state.isLoadingRows, nextToken, state.revealNodeId) {
        val viewport = pendingViewport ?: return@LaunchedEffect
        if (state.isLoadingRows) return@LaunchedEffect
        if (state.revealNodeId == null && viewport.grid == state.viewOptions.grid) {
            val index = viewport.restoreIndex(displayedNodes, nextToken != null && nextToken != scroll.requestedToken)
            if (index == null) {
                scroll.requestedToken = nextToken
                onLoadMore()
                return@LaunchedEffect
            }
            if (displayedNodes.isNotEmpty()) {
                if (viewport.grid) scroll.grid.scrollToItem(index, viewport.offset)
                else scroll.list.scrollToItem(index, viewport.offset)
            }
        }
        scroll.pendingViewport.value = null
        scroll.requestedToken = null
    }
    val revealIndex = displayedNodes.indexOfFirst { it.ref.nodeId.value == state.revealNodeId }
    val revealPath = models.getOrNull(revealIndex)?.reference
    LaunchedEffect(state.revealNodeId, revealIndex, state.isListing, state.nextPageToken, state.viewOptions.grid, scroll) {
        if (state.revealNodeId == null || state.hasSearch || state.isListing) return@LaunchedEffect
        when {
            revealIndex >= 0 -> {
                if (state.viewOptions.grid) scroll.grid.scrollToItem(revealIndex)
                else scroll.list.scrollToItem(revealIndex)
                kotlinx.coroutines.delay(1600L)
                onRevealFinished(true)
            }
            state.nextPageToken != null -> onLoadMore()
            else -> onRevealFinished(false)
        }
    }
    val totalsByPath = remember(state.mountGeneration, state.listingRevision, scroll) {
        mutableStateMapOf<String, FolderStats?>()
    }
    LaunchedEffect(scroll, state.viewOptions.grid, models, state.mountGeneration, state.listingRevision) {
        snapshotFlow {
            if (state.viewOptions.grid) scroll.grid.layoutInfo.visibleItemsInfo.map { it.index }
            else scroll.list.layoutInfo.visibleItemsInfo.map { it.index }
        }.collectLatest { visibleIndices ->
            val folders = visibleIndices.mapNotNull(models::getOrNull).filter(FileModel::isDirectory)
            val visiblePaths = folders.map(FileModel::reference).toSet()
            for (folder in folders) {
                if (totalsByPath.containsKey(folder.reference)) continue
                val node = byOpaquePath[folder.reference] ?: continue
                val totals = loadFolderTotals(node.ref).getOrNull()
                ensureActive()
                if (totalsByPath.size >= 128) {
                    val evicted = totalsByPath.keys.firstOrNull { it !in visiblePaths } ?: break
                    totalsByPath.remove(evicted)
                }
                totalsByPath[folder.reference] = totals?.let {
                    FolderStats(it.fileCount, it.totalBytes, System.currentTimeMillis())
                }
            }
        }
    }
    val selectedPaths = models.filter { model ->
        byOpaquePath[model.reference]?.ref?.nodeId?.value in state.selectedNodeIds
    }.map(FileModel::reference).toSet()
    if (models.isEmpty()) {
        if (state.isLoadingRows) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator()
            }
        } else {
            EmptyState(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                variant = if (state.hasSearch) EmptyStateVariant.Search else EmptyStateVariant.Folder,
                icon = if (state.hasSearch) null else Icons.Outlined.FolderOpen,
                title = stringResource(if (state.hasSearch) dev.qtremors.arcile.core.ui.R.string.no_results_found else R.string.onlyfiles_empty_folder),
                description = when {
                    !state.hasSearch -> ""
                    state.searchQuery.isBlank() -> stringResource(dev.qtremors.arcile.core.ui.R.string.no_results_for_filters)
                    else -> stringResource(dev.qtremors.arcile.core.ui.R.string.no_results_description, state.searchQuery)
                },
                action = if (!state.hasSearch) {
                    {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            FilledTonalButton(
                                onClick = onImportFiles,
                                modifier = Modifier.bounceClickable { onImportFiles() }
                            ) { Text(stringResource(R.string.onlyfiles_import_files)) }
                            TextButton(
                                onClick = onImportFolder,
                                modifier = Modifier.bounceClickable { onImportFolder() }
                            ) { Text(stringResource(R.string.onlyfiles_import_folder)) }
                        }
                    }
                } else null
            )
        }
        return
    }
    Column(Modifier.fillMaxSize()) {
        val folderLabel = stringResource(R.string.onlyfiles_folder)
        val folderStats = totalsByPath.mapNotNull { (path, totals) -> totals?.let { path to it } }.toMap()
        val presentation = FileItemPresentation(
            showThumbnails = state.viewOptions.showThumbnails,
            zoom = state.viewOptions.listZoom.coerceIn(
                FileListingPreferences.MIN_LIST_ZOOM, FileListingPreferences.MAX_LIST_ZOOM
            ),
            showDetails = true,
            openFileFromThumbnailInSelectionMode = true,
            contextLabel = { file ->
                byOpaquePath[file.reference]?.ref?.nodeId?.let(parents::get)?.takeIf(String::isNotBlank)
                    ?: folderLabel.takeIf { file.isDirectory && file.reference !in folderStats }
            },
            thumbnailData = { file, size ->
                byOpaquePath[file.reference]?.takeIf { it.isViewableImage() || it.isViewableVideo() }?.let { node ->
                    node.thumbnailRequest(size)
                }
            }
        )
        val open: (String) -> Unit = { path -> byOpaquePath[path]?.let(onOpen) }
        val toggle: (String) -> Unit = { path -> byOpaquePath[path]?.ref?.nodeId?.value?.let(onToggleSelection) }
        val range: (List<String>) -> Unit = { paths -> onSelectRange(paths.mapNotNull { byOpaquePath[it]?.ref?.nodeId?.value }) }
        Box(Modifier.weight(1f).fillMaxWidth()) {
        if (!state.viewOptions.grid) {
            FileList(
                files = models, selectedFiles = selectedPaths, onNavigateTo = open, onOpenFile = open,
                onToggleSelection = toggle, onSelectMultiple = range, modifier = Modifier.fillMaxSize(),
                listState = scroll.list,
                folderStatsByPath = folderStats,
                presentation = presentation,
                highlightedPath = revealPath
            )
        } else {
            var cellSize by remember(state.viewOptions.gridMinCellSize) {
                mutableFloatStateOf(
                    state.viewOptions.gridMinCellSize.coerceIn(
                        FileListingPreferences.MIN_GRID_MIN_CELL_SIZE, FileListingPreferences.MAX_GRID_MIN_CELL_SIZE
                    )
                )
            }
            val currentCellSize = rememberUpdatedState(cellSize)
            val updateCellSize = rememberUpdatedState<(Float) -> Unit> { cellSize = it }
            val finalizeCellSize = rememberUpdatedState<(Float) -> Unit> {
                cellSize = it
                onGridSizeFinalized(it)
            }
            FileGrid(
                files = models, selectedFiles = selectedPaths, onNavigateTo = open, onOpenFile = open,
                onToggleSelection = toggle, onSelectMultiple = range,
                modifier = Modifier.fillMaxSize().pinchToResizeGrid(
                    currentCellSize, updateCellSize, finalizeCellSize, horizontalPaddingDp = 32f
                ),
                gridState = scroll.grid,
                folderStatsByPath = folderStats,
                presentation = presentation,
                minCellSize = cellSize.dp,
                highlightedPath = revealPath
            )
        }
        val scrollbar = remember(scroll, state.viewOptions.grid) {
            if (state.viewOptions.grid) LazyGridScrollbarState(scroll.grid) else LazyListScrollbarState(scroll.list)
        }
        ArcileFastScrollbar(
            scrollbarState = scrollbar,
            labelForIndex = { models.getOrNull(it)?.name.orEmpty() },
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
        )
        }
        val hasMore = if (state.hasSearch) state.searchNextPageToken != null else state.nextPageToken != null
        if (hasMore) TextButton(onClick = onLoadMore, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.onlyfiles_load_more))
        }
        if (state.isLoadingRows) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator(modifier = Modifier.size(36.dp))
            }
        }
    }
}

internal class VaultDirectoryScroll {
    val list = LazyListState()
    val grid = LazyGridState()
    val pendingViewport = mutableStateOf<VaultViewport?>(null)
    var requestedToken: String? = null
}

internal data class VaultViewport(val index: Int, val offset: Int, val nodeId: String?, val grid: Boolean) {
    fun restoreIndex(nodes: List<VaultNodeMetadata>, canLoadMore: Boolean): Int? {
        if (nodeId != null) {
            val anchor = nodes.indexOfFirst { it.ref.nodeId.value == nodeId }
            if (anchor >= 0) return anchor
        }
        if (index >= nodes.size && canLoadMore) return null
        return index.coerceAtMost(nodes.lastIndex.coerceAtLeast(0))
    }
}

internal class VaultWorkspaceScroll {
    private val byTab = mutableMapOf<Int, VaultListingScroll>()

    fun forTab(id: Int): VaultListingScroll = byTab.getOrPut(id) { VaultListingScroll() }

    fun retain(tabs: List<VaultWorkspaceTab>) {
        byTab.keys.retainAll(tabs.map(VaultWorkspaceTab::id).toSet())
    }
}

internal class VaultListingScroll {
    private data class SearchKey(
        val directoryId: DirectoryId,
        val query: String,
        val filters: SearchFilters,
        val recursive: Boolean
    )

    private val directories = LinkedHashMap<DirectoryId, VaultDirectoryScroll>(32, 0.75f, true)
    private var searchKey: SearchKey? = null
    private var searchScroll = VaultDirectoryScroll()

    fun forState(state: OnlyFilesUiState): VaultDirectoryScroll {
        val directory = state.currentDirectory?.id ?: return VaultDirectoryScroll()
        if (!state.hasSearch) return forDirectory(directory)
        val searchDirectory = state.searchScope?.lastOrNull()?.id ?: directory
        return forSearch(searchDirectory, state.searchQuery, state.searchFilters, state.recursiveSearch)
    }

    fun capture(state: OnlyFilesUiState) {
        val nodes = state.displayedNodes
        if (nodes.isEmpty() || state.currentDirectory == null) return
        val scroll = forState(state)
        if (scroll.pendingViewport.value != null) return
        val grid = state.viewOptions.grid
        val index = if (grid) scroll.grid.firstVisibleItemIndex else scroll.list.firstVisibleItemIndex
        val offset = if (grid) scroll.grid.firstVisibleItemScrollOffset else scroll.list.firstVisibleItemScrollOffset
        if (index == 0 && offset == 0) return
        scroll.pendingViewport.value = VaultViewport(index, offset, nodes.getOrNull(index)?.ref?.nodeId?.value, grid)
        scroll.requestedToken = null
    }

    fun forDirectory(id: DirectoryId): VaultDirectoryScroll = directories[id] ?: VaultDirectoryScroll().also {
        directories[id] = it
        if (directories.size > 32) directories.remove(directories.keys.first())
    }

    fun forSearch(id: DirectoryId, query: String, filters: SearchFilters, recursive: Boolean): VaultDirectoryScroll {
        val key = SearchKey(id, query, filters, recursive)
        if (searchKey != key) {
            searchKey = key
            searchScroll = VaultDirectoryScroll()
        }
        return searchScroll
    }
}

internal fun VaultNodeMetadata.thumbnailRequest(sizePx: Int) = VaultThumbnailRequest(
    ref.vaultId.value, ref.nodeId.value, ref.parentId.value, revision, sizePx
)

internal fun VaultNodeMetadata.toSharedFileModel(): FileModel {
    val capabilities = StorageNodeCapabilities(
        canRead = ref.capabilities.canRead,
        canWrite = ref.capabilities.canRename,
        canDelete = ref.capabilities.canDeletePermanently,
        canRename = ref.capabilities.canRename,
        canCopy = ref.capabilities.canCopy,
        canMove = ref.capabilities.canMove,
        canExport = ref.capabilities.canExport,
        canShare = ref.capabilities.canShare,
        canOpenWith = ref.capabilities.canOpenWith,
        canArchive = ref.capabilities.canRead
    )
    val storageRef = StorageNodeRef.vault(ref.vaultId.value, ref.nodeId.value, capabilities)
    return FileModel(
        name, storageRef.displayPath.absolutePath, sizeBytes, modifiedAtMillis, isDirectory, extension,
        name.startsWith('.'), mimeType, nodeRef = storageRef
    )
}

@Composable
internal fun VaultList(
    vaults: List<VaultSummary>,
    pinnedVaults: Set<VaultId>,
    onPinVault: (VaultId, Boolean) -> Unit,
    imports: Map<VaultId, VaultImportState>,
    biometricVaultIds: Set<VaultId>,
    onOpen: (VaultSummary) -> Unit,
    onOpenSettings: (VaultSummary) -> Unit,
    onUnlockBiometric: (VaultSummary) -> Unit,
    onCancelImport: (VaultId) -> Unit
) {
    val (pinned, others) = remember(vaults, pinnedVaults) { vaults.partition { it.id in pinnedVaults } }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Headers appear only once something is pinned; a plain list needs no labels.
        if (pinned.isNotEmpty()) {
            item(key = "header-pinned") { ArcileSectionHeader(stringResource(R.string.onlyfiles_pinned_vaults)) }
            items(pinned, key = { it.id.value }) { vault ->
                VaultCard(vault, true, imports[vault.id], vault.id in biometricVaultIds, onOpen, onOpenSettings, onPinVault, onUnlockBiometric, onCancelImport)
            }
            if (others.isNotEmpty()) item(key = "header-others") {
                ArcileSectionHeader(stringResource(R.string.onlyfiles_other_vaults), Modifier.padding(top = 8.dp))
            }
        }
        items(others, key = { it.id.value }) { vault ->
            VaultCard(vault, false, imports[vault.id], vault.id in biometricVaultIds, onOpen, onOpenSettings, onPinVault, onUnlockBiometric, onCancelImport)
        }
    }
}

/** One vault. Tap opens it; long-press shows its options, the same gesture files use. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VaultCard(
    vault: VaultSummary,
    pinned: Boolean,
    import: VaultImportState?,
    biometricEnrolled: Boolean,
    onOpen: (VaultSummary) -> Unit,
    onOpenSettings: (VaultSummary) -> Unit,
    onPinVault: (VaultId, Boolean) -> Unit,
    onUnlockBiometric: (VaultSummary) -> Unit,
    onCancelImport: (VaultId) -> Unit
) {
    var showOptions by remember(vault.id) { mutableStateOf(false) }
    val haptics = rememberArcileHaptics()
    val optionsLabel = stringResource(R.string.onlyfiles_vault_actions, vault.name)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reducedMotion = LocalReducedMotionEnabled.current
    // The same springy press as Arcile's other cards; long-press support rules out bounceClickable.
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && !reducedMotion) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "vaultCardPress"
    )
    Box {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
                .clip(MaterialTheme.shapes.storageCard)
                .combinedClickable(
                    interactionSource = interaction,
                    indication = LocalIndication.current,
                    onClick = { onOpen(vault) },
                    onLongClick = {
                        haptics.selectionStart()
                        showOptions = true
                    },
                    onLongClickLabel = optionsLabel
                ),
            shape = MaterialTheme.shapes.storageCard,
            color = if (vault.isUnlocked) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = if (vault.isUnlocked) 1.dp else 0.dp
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = if (vault.isUnlocked) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Icon(
                            if (vault.isUnlocked) Icons.Outlined.FolderOpen else Icons.Outlined.Lock,
                            null,
                            Modifier.padding(12.dp),
                            tint = if (vault.isUnlocked) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(vault.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (vault.isAvailable) {
                                stringResource(
                                    if (vault.locationKind == VaultLocationKind.APP_PRIVATE) R.string.onlyfiles_app_storage
                                    else R.string.onlyfiles_user_folder
                                ) + " \u00b7 " + DateFormat.getDateInstance().format(vault.createdAtMillis)
                            } else stringResource(R.string.onlyfiles_folder_unavailable),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (biometricEnrolled && !vault.isUnlocked) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                .bounceClickable { onUnlockBiometric(vault) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = stringResource(R.string.onlyfiles_biometric_enabled),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                import?.let { active ->
                    Spacer(Modifier.height(12.dp))
                    val progress = active.progress
                    val total = progress?.totalBytes
                    if (total != null && total > 0L) {
                        LinearProgressIndicator({ progress.bytesProcessed.toFloat() / total }, Modifier.fillMaxWidth().clip(CircleShape))
                    } else LinearProgressIndicator(Modifier.fillMaxWidth().clip(CircleShape))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.onlyfiles_importing, progress?.completedItems ?: 0, progress?.totalItems ?: 0),
                            Modifier.weight(1f), style = MaterialTheme.typography.bodySmall
                        )
                        TextButton(onClick = { onCancelImport(vault.id) }, enabled = !active.isCancelling) {
                            Text(stringResource(R.string.onlyfiles_cancel_import))
                        }
                    }
                }
            }
        }
        ArcileDropdownMenu(showOptions, onDismissRequest = { showOptions = false }, items = buildList {
            add {
                ArcileDropdownMenuItem(
                    text = { Text(stringResource(if (pinned) R.string.onlyfiles_unpin_vault else R.string.onlyfiles_pin_vault)) },
                    leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null) },
                    onClick = {
                        showOptions = false
                        onPinVault(vault.id, !pinned)
                    }
                )
            }
            // Vault settings need the vault's session, so they are offered once it is unlocked.
            if (vault.isUnlocked) add {
                ArcileDropdownMenuItem(
                    text = { Text(stringResource(R.string.onlyfiles_vault_settings)) },
                    leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    onClick = {
                        showOptions = false
                        onOpenSettings(vault)
                    }
                )
            }
        })
    }
}
