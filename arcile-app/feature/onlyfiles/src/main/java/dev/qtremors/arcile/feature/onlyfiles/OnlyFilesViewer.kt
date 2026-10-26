package dev.qtremors.arcile.feature.onlyfiles

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.qtremors.arcile.core.ui.video.VideoPlaybackItem
import dev.qtremors.arcile.core.ui.video.VideoPlaybackSession
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultExternalGrant
import dev.qtremors.arcile.core.storage.domain.BuiltInFileOpen
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import dev.qtremors.arcile.core.vault.domain.VaultNodeRef
import dev.qtremors.arcile.core.vault.domain.VaultSeekableReader
import dev.qtremors.arcile.core.ui.theme.bounceClickable
import dev.qtremors.arcile.core.ui.dialogs.RenameDialog
import dev.qtremors.arcile.core.ui.viewer.ViewerActionPolicy
import dev.qtremors.arcile.core.ui.viewer.ImageViewerBottomChrome
import dev.qtremors.arcile.core.ui.viewer.ImageViewerChromeActions
import dev.qtremors.arcile.core.ui.viewer.ImageViewerTopChrome
import dev.qtremors.arcile.core.ui.theme.LocalMarqueeFilenames
import dev.qtremors.arcile.core.ui.viewer.ZoomableImageViewer
import dev.qtremors.arcile.core.ui.viewer.ViewerFileAction
import dev.qtremors.arcile.core.ui.viewer.ViewerOverflowMenu
import dev.qtremors.arcile.core.ui.viewer.ViewerSourceScope
import java.text.DateFormat

@Composable
internal fun ViewerScreen(
    node: VaultNodeMetadata,
    mediaQueue: List<VaultNodeMetadata>,
    imageSelection: Set<String>?,
    nextMediaPage: String?,
    location: String,
    viewModel: OnlyFilesViewModel,
    archiveOperationActive: Boolean,
    onBack: () -> Unit,
    packageInstaller: @Composable (VaultExternalGrant, () -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    var showInfo by remember(node.ref.nodeId) { mutableStateOf(false) }
    var confirmDelete by remember(node.ref.nodeId) { mutableStateOf(false) }
    var externalAction by remember(node.ref.nodeId) { mutableStateOf<ExternalAction?>(null) }
    var installRequested by remember(node.ref) { mutableStateOf(false) }
    var showRename by remember(node.ref.nodeId) { mutableStateOf(false) }
    var archiveFolder by remember(node.ref.nodeId, node.revision) { mutableStateOf(emptyList<String>()) }
    var archiveSelection by remember(node.ref.nodeId, node.revision) { mutableStateOf<VaultZipSelection?>(null) }
    val navigateBack = {
        if (archiveFolder.isEmpty()) onBack() else archiveFolder = archiveFolder.dropLast(1)
    }
    BackHandler(enabled = archiveFolder.isNotEmpty(), onBack = navigateBack)
    val sharedFile = remember(node) { node.toSharedFileModel() }
    val allowedActions = remember(sharedFile.nodeRef.capabilities) {
        ViewerActionPolicy.resolveAllowedActions(
            scope = ViewerSourceScope.Vault,
            capabilities = sharedFile.nodeRef.capabilities
        )
    }
    val onAction: (ViewerFileAction) -> Unit = { action ->
        when (action) {
            ViewerFileAction.Rename -> showRename = true
            ViewerFileAction.Copy -> viewModel.copy(listOf(node))
            ViewerFileAction.Cut -> viewModel.move(listOf(node))
            ViewerFileAction.Delete -> confirmDelete = true
            ViewerFileAction.Share -> externalAction = ExternalAction.SHARE
            ViewerFileAction.OpenWith -> externalAction = ExternalAction.OPEN_WITH
            ViewerFileAction.Properties -> showInfo = true
            ViewerFileAction.CreateArchive -> Unit
        }
    }

    val openKind = remember(node) { node.builtInOpen() }
    if (openKind == BuiltInFileOpen.IMAGE) {
        VaultImagePager(
            node = node,
            queue = mediaQueue,
            selectedNodeIds = imageSelection,
            nextPageToken = nextMediaPage,
            allowedActions = allowedActions,
            viewModel = viewModel,
            onBack = onBack,
            onAction = onAction,
            modifier = modifier
        )
    } else if (openKind == BuiltInFileOpen.PDF) {
        key(node.ref, node.revision) {
            VaultPdfPreview(
                node = node,
                openReader = viewModel::openReader,
                onBack = onBack,
                onShare = { externalAction = ExternalAction.SHARE },
                onOpenWith = { externalAction = ExternalAction.OPEN_WITH },
                modifier = modifier
            )
        }
    } else if (openKind == BuiltInFileOpen.TEXT) {
        key(node.ref) {
            VaultTextEditor(
                node, viewModel, onBack,
                onShare = { externalAction = ExternalAction.SHARE },
                onOpenWith = { externalAction = ExternalAction.OPEN_WITH },
                modifier = modifier
            )
        }
    } else if (openKind == BuiltInFileOpen.ARCHIVE) {
        val state by viewModel.state.collectAsStateWithLifecycle()
        VaultArchivePreview(
            node, viewModel::openReader, archiveFolder, archiveOperationActive,
            onOpenFolder = { archiveFolder = it },
            onExtract = { archiveSelection = it },
            onBack = onBack, allowedActions = allowedActions, onAction = onAction,
            progress = state.archiveProgress, onCancel = viewModel::cancelArchiveOperation,
            destination = location
        )
    } else if (openKind == BuiltInFileOpen.AUDIO) {
        VaultAudioPreview(
            node, mediaQueue, viewModel::openReader,
            onTrackChanged = viewModel::showViewerAudio, onQueueEmpty = onBack,
            onBack = onBack, allowedActions = allowedActions, onAction = onAction,
            location = location, nextPageToken = nextMediaPage,
            onLoadMore = viewModel::loadNextPage, modifier = modifier
        )
    } else {
        Box(modifier.fillMaxSize().background(Color.Black)) {
            key(node.ref, node.revision) {
                when (openKind) {
                    BuiltInFileOpen.PACKAGE ->
                        Column(
                            Modifier.align(Alignment.Center).padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(stringResource(R.string.onlyfiles_android_package), color = Color.White)
                            if (ViewerFileAction.OpenWith in allowedActions) {
                                Button(onClick = { installRequested = true }) {
                                    Text(stringResource(R.string.onlyfiles_open_installer))
                                }
                            }
                        }
                    else -> Column(
                        Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(stringResource(R.string.onlyfiles_preview_unavailable), color = Color.White)
                        if (ViewerFileAction.OpenWith in allowedActions) {
                            Button(onClick = { externalAction = ExternalAction.OPEN_WITH }) {
                                Text(stringResource(R.string.onlyfiles_open_with))
                            }
                        }
                    }
                }
            }

            // Overlay Navigation and Title
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .align(Alignment.TopCenter),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .bounceClickable { navigateBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.onlyfiles_back),
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = node.name,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                ViewerOverflowMenu(
                    currentFile = sharedFile,
                    allowedActions = allowedActions,
                    onAction = onAction
                )
            }

        }
    }

    if (showInfo) {
        VaultPropertiesDialog(listOf(node), location, viewModel::selectionTotals, viewModel::openReader) { showInfo = false }
    }
    if (installRequested) {
        key(node.ref, node.revision) {
            VaultPackageInstaller(node, viewModel, packageInstaller, onDismiss = { installRequested = false })
        }
    }
    archiveSelection?.let { selection ->
        VaultExtractZipDialog(node, selection, onDismiss = { archiveSelection = null }) { request, choose ->
            archiveSelection = null
            viewModel.requestZipExtraction(request, choose)
        }
    }
    if (confirmDelete) VaultDeleteConfirmation(listOf(node), onDismiss = { confirmDelete = false }) {
        confirmDelete = false
        viewModel.delete(listOf(node))
    }
    externalAction?.let { action ->
        VaultExternalAccessDialogs(action, listOf(node), viewModel) { externalAction = null }
    }
    if (showRename) {
        RenameDialog(
            currentName = node.name,
            onDismiss = { showRename = false },
            onConfirm = { newName ->
                showRename = false
                viewModel.rename(node, newName)
            },
            saveInput = false
        )
    }
}

@Composable
private fun VaultImagePager(
    node: VaultNodeMetadata,
    queue: List<VaultNodeMetadata>,
    selectedNodeIds: Set<String>?,
    nextPageToken: String?,
    allowedActions: Set<ViewerFileAction>,
    viewModel: OnlyFilesViewModel,
    onBack: () -> Unit,
    onAction: (ViewerFileAction) -> Unit,
    modifier: Modifier
) {
    val nodes = queue.ifEmpty { listOf(node) }
    val files = remember(nodes) { nodes.map(VaultNodeMetadata::toSharedFileModel) }
    val nodesByReference = remember(files, nodes) { files.map { it.reference }.zip(nodes).toMap() }
    val pager = rememberPagerState(
        initialPage = nodes.indexOfFirst { it.ref == node.ref }.coerceAtLeast(0),
        pageCount = { nodes.size }
    )
    val rotations = remember { mutableStateMapOf<String, Float>() }
    val scales = remember { mutableStateMapOf<String, Float>() }
    var chromeVisible by remember { mutableStateOf(true) }
    val file = remember(node) { node.toSharedFileModel() }
    val context = LocalContext.current
    val pageScale = files.getOrNull(pager.currentPage)?.reference?.let(scales::get) ?: 1f
    var resolution by remember(node.ref, node.revision) { mutableStateOf("") }
    LaunchedEffect(node.ref, node.revision) {
        resolution = readVaultImageResolution(node.ref, viewModel::openReader).orEmpty()
    }
    LaunchedEffect(pager, nodes) {
        snapshotFlow { pager.settledPage }.collect { page ->
            nodes.getOrNull(page)?.let { viewModel.showViewerImage(it.ref) }
        }
    }
    LaunchedEffect(pager, nodes.size, nextPageToken) {
        if (nextPageToken != null) {
            snapshotFlow { pager.settledPage }.collect { page ->
                if (page >= nodes.lastIndex - 1) viewModel.loadNextPage()
            }
        }
    }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pager,
            key = { files[it].reference },
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = pageScale <= 1.05f
        ) { page ->
            val image = nodes[page]
            val reference = files[page].reference
            DisposableEffect(reference) {
                onDispose { scales.remove(reference) }
            }
            key(image.ref, image.revision) {
                VaultImage(
                    node = image,
                    openReader = viewModel::openReader,
                    rotation = rotations[reference] ?: 0f,
                    onBack = onBack,
                    onTap = { chromeVisible = !chromeVisible },
                    onScaleChanged = { scales[reference] = it },
                    onShowMetadata = { if (!pager.isScrollInProgress) onAction(ViewerFileAction.Properties) },
                    onOpenWith = { if (!pager.isScrollInProgress) onAction(ViewerFileAction.OpenWith) }
                )
            }
        }
        ImageViewerTopChrome(
            visible = chromeVisible,
            currentFile = file,
            positionText = "${pager.settledPage + 1}/${files.size}",
            dateText = remember(node.modifiedAtMillis) {
                DateFormat.getDateTimeInstance().format(node.modifiedAtMillis)
            },
            resolutionText = resolution,
            sizeText = formatBytes(context, node.sizeBytes),
            marqueeEnabled = LocalMarqueeFilenames.current,
            modifier = Modifier.align(Alignment.TopCenter)
        )
        ImageViewerBottomChrome(
            visible = chromeVisible,
            files = files,
            currentPage = pager.currentPage,
            currentFile = file,
            favoriteFiles = emptySet(),
            selectedFiles = if (selectedNodeIds == null) emptySet() else nodesByReference.keys.filterTo(mutableSetOf()) {
                nodesByReference.getValue(it).ref.nodeId.value in selectedNodeIds
            },
            selectionModeEnabled = selectedNodeIds != null,
            readOnly = false,
            viewerActions = allowedActions,
            onViewerAction = { if (!pager.isScrollInProgress) onAction(it) },
            actions = ImageViewerChromeActions(
                onPageSelected = { pager.animateScrollToPage(it) },
                onToggleFavorite = {},
                onRotate = { reference -> rotations[reference] = (rotations[reference] ?: 0f) + 90f },
                onDelete = { if (!pager.isScrollInProgress) onAction(ViewerFileAction.Delete) },
                onToggleSelection = { reference ->
                    if (!pager.isScrollInProgress) {
                        nodesByReference[reference]?.let { viewModel.toggleSelection(it.ref.nodeId.value) }
                    }
                },
                onShowMetadata = { if (!pager.isScrollInProgress) onAction(ViewerFileAction.Properties) }
            ),
            showFavorite = false,
            showDelete = ViewerFileAction.Delete in allowedActions,
            thumbnailData = { thumbnailFile, size ->
                nodesByReference[thumbnailFile.reference]?.thumbnailRequest(size)
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VaultImage(
    node: VaultNodeMetadata,
    openReader: (VaultNodeRef) -> Result<VaultSeekableReader>,
    rotation: Float,
    onBack: () -> Unit,
    onTap: () -> Unit,
    onScaleChanged: (Float) -> Unit,
    onShowMetadata: () -> Unit,
    onOpenWith: () -> Unit
) {
    val windowSize = LocalWindowInfo.current.containerSize
    val targetWidth = windowSize.width.coerceIn(1, 1_536)
    val targetHeight = windowSize.height.coerceIn(1, 1_536)
    val decoded by produceState<Result<Any>?>(null, targetWidth, targetHeight) {
        value = decodeVaultViewerImage(node, targetWidth, targetHeight, openReader)
    }
    when {
        decoded == null -> LoadingIndicator()
        decoded?.getOrNull() == null -> Text(stringResource(R.string.onlyfiles_error_image))
        else -> ZoomableImageViewer(
            requestData = requireNotNull(decoded?.getOrNull()),
            rotation = rotation,
            onDismiss = onBack,
            onTap = onTap,
            onScaleChanged = onScaleChanged,
            onSwipeUp = onShowMetadata,
            onOpenWith = onOpenWith,
            sensitive = true,
            contentDescription = node.name
        )
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
internal fun createVaultVideoPlaybackSession(
    nodes: List<VaultNodeMetadata>,
    vaultId: VaultId,
    selectedNode: VaultNodeMetadata,
    screenshotProtectionEnabled: Boolean,
    openReader: (VaultNodeRef) -> Result<VaultSeekableReader>,
    interaction: OnlyFilesVideoInteraction? = null
) : VideoPlaybackSession {
    require(selectedNode.ref.vaultId == vaultId)
    val queue = nodes.filter { it.ref.vaultId == vaultId && it.isViewableVideo() }.takeIf { selectedNode in it }
        ?: listOf(selectedNode)
    val liveQueue = OnlyFilesVideoQueue(queue) { node ->
        val sharedFile = node.toSharedFileModel()
        VideoPlaybackItem(
            mediaItem = MediaItem.Builder()
                .setUri("onlyfiles://playback/${node.ref.nodeId.value}")
                .setMediaId(sharedFile.reference)
                .setMimeType(node.mimeType)
                .build(),
            title = node.name,
            onShare = interaction?.takeIf { node.ref.capabilities.canShare }?.let {
                { it.request(ExternalAction.SHARE, node) }
            },
            onOpenWith = interaction?.takeIf { node.ref.capabilities.canOpenWith }?.let {
                { it.request(ExternalAction.OPEN_WITH, node) }
            },
            readMetadata = { readVaultVideoMetadata(node, sharedFile, openReader) }
        )
    }
    interaction?.attach(liveQueue)
    val initial = liveQueue.sourceUpdates.value
    return VideoPlaybackSession(
        items = initial.items,
        startIndex = queue.indexOf(selectedNode).coerceAtLeast(0),
        dataSourceFactory = androidx.media3.datasource.DataSource.Factory {
            VaultMediaDataSource(liveQueue.refsByOpaqueId, openReader)
        },
        securityScopeId = vaultSecurityScope(vaultId),
        files = initial.files,
        screenshotProtectionEnabled = screenshotProtectionEnabled,
        thumbnailData = { file, size -> liveQueue.node(file.reference)?.thumbnailRequest(size) },
        sourceHost = if (interaction == null) null else { onClosed, content ->
            interaction.Host(onClosed, content)
        },
        sourceUpdates = liveQueue.sourceUpdates,
        onSourceAction = interaction?.let { it::action },
        onLoadMore = interaction?.let { it::loadMore }
    )
}

internal fun vaultSecurityScope(vaultId: VaultId): String = "vault:${vaultId.value}"
