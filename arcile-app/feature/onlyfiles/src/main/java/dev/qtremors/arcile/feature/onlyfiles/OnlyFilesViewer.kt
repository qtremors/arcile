package dev.qtremors.arcile.feature.onlyfiles

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import dev.qtremors.arcile.core.ui.video.VideoPlaybackItem
import dev.qtremors.arcile.core.ui.video.VideoPlaybackSession
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import dev.qtremors.arcile.core.vault.domain.VaultNodeRef
import dev.qtremors.arcile.core.vault.domain.VaultSeekableReader
import java.text.DateFormat

@Composable
internal fun ViewerScreen(
    node: VaultNodeMetadata,
    viewModel: OnlyFilesViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var showInfo by remember(node.ref.nodeId) { mutableStateOf(false) }
    var confirmDelete by remember(node.ref.nodeId) { mutableStateOf(false) }
    var externalAction by remember(node.ref.nodeId) { mutableStateOf<ExternalAction?>(null) }
    var fallbackAction by remember(node.ref.nodeId) { mutableStateOf<ExternalAction?>(null) }
    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text(node.name, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.onlyfiles_back))
                    }
                },
                actions = {
                    IconButton(onClick = { showInfo = true }) { Icon(Icons.Default.Info, stringResource(R.string.onlyfiles_properties)) }
                    IconButton(onClick = { externalAction = ExternalAction.SHARE }) { Icon(Icons.Default.Share, stringResource(R.string.onlyfiles_share)) }
                    IconButton(onClick = { externalAction = ExternalAction.OPEN_WITH }) { Icon(Icons.AutoMirrored.Filled.OpenInNew, stringResource(R.string.onlyfiles_open_with)) }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, stringResource(R.string.onlyfiles_delete)) }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            if (node.isViewableImage()) VaultImage(node, viewModel::openReader)
            IconButton(onClick = { viewModel.navigateViewer(-1) }, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.onlyfiles_previous))
            }
            IconButton(onClick = { viewModel.navigateViewer(1) }, modifier = Modifier.align(Alignment.CenterEnd)) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.onlyfiles_next))
            }
        }
    }
    if (showInfo) AlertDialog(
        onDismissRequest = { showInfo = false }, title = { Text(node.name) },
        text = { Text("${formatBytes(node.sizeBytes)}\n${node.mimeType ?: stringResource(R.string.onlyfiles_unknown_type)}\n${DateFormat.getDateTimeInstance().format(node.modifiedAtMillis)}") },
        confirmButton = { Button(onClick = { showInfo = false }) { Text(stringResource(R.string.onlyfiles_close)) } }
    )
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false }, title = { Text(stringResource(R.string.onlyfiles_delete)) },
        text = { Text(pluralStringResource(R.plurals.onlyfiles_delete_many_confirm, 1, 1)) },
        confirmButton = { Button(onClick = { confirmDelete = false; viewModel.delete(listOf(node)) }) { Text(stringResource(R.string.onlyfiles_delete)) } },
        dismissButton = { Button(onClick = { confirmDelete = false }) { Text(stringResource(R.string.onlyfiles_cancel)) } }
    )
    externalAction?.let { action -> AlertDialog(
        onDismissRequest = { externalAction = null },
        title = { Text(stringResource(if (action == ExternalAction.SHARE) R.string.onlyfiles_share else R.string.onlyfiles_open_with)) },
        text = { Text(stringResource(R.string.onlyfiles_external_warning)) },
        confirmButton = { Button(onClick = {
            externalAction = null
            viewModel.issueExternalAccess(listOf(node)) { grants ->
                runCatching { launchExternalIntent(context, action, grants) }.onFailure { viewModel.revokeExternalAccess(grants) }
            }
        }) { Text(stringResource(R.string.onlyfiles_continue)) } },
        dismissButton = {
            androidx.compose.foundation.layout.Row {
                Button(onClick = {
                    fallbackAction = externalAction
                    externalAction = null
                }) { Text(stringResource(R.string.onlyfiles_compatibility_copy)) }
                Button(onClick = { externalAction = null }) { Text(stringResource(R.string.onlyfiles_cancel)) }
            }
        }
    ) }
    fallbackAction?.let { action -> AlertDialog(
        onDismissRequest = { fallbackAction = null },
        title = { Text(stringResource(R.string.onlyfiles_compatibility_copy)) },
        text = { Text(stringResource(R.string.onlyfiles_compatibility_copy_warning)) },
        confirmButton = { Button(onClick = {
            fallbackAction = null
            viewModel.issuePlaintextFallbackAccess(listOf(node)) { grants ->
                runCatching { launchExternalIntent(context, action, grants) }
                    .onFailure { viewModel.revokeExternalAccess(grants) }
            }
        }) { Text(stringResource(R.string.onlyfiles_create_copy)) } },
        dismissButton = { Button(onClick = { fallbackAction = null }) { Text(stringResource(R.string.onlyfiles_cancel)) } }
    ) }
}

@Composable
private fun VaultImage(
    node: VaultNodeMetadata,
    openReader: (VaultNodeRef) -> Result<VaultSeekableReader>
) {
    val windowSize = LocalWindowInfo.current.containerSize
    val targetWidth = windowSize.width.coerceAtLeast(1)
    val targetHeight = windowSize.height.coerceAtLeast(1)
    val decoded by produceState<Result<Bitmap?>?>(null, node.ref.nodeId, node.revision, targetWidth, targetHeight) {
        value = decodeSampledVaultImageOnWorker(node.ref, targetWidth, targetHeight, openReader)
    }
    when {
        decoded == null -> CircularProgressIndicator()
        decoded?.getOrNull() == null -> Text(stringResource(R.string.onlyfiles_error_image))
        else -> Image(
            requireNotNull(decoded?.getOrNull()).asImageBitmap(),
            node.name,
            Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
internal fun createVaultVideoPlaybackSession(
    nodes: List<VaultNodeMetadata>,
    vaultId: VaultId,
    selectedNode: VaultNodeMetadata,
    openReader: (VaultNodeRef) -> Result<VaultSeekableReader>
) : VideoPlaybackSession {
    val queue = nodes.filter(VaultNodeMetadata::isViewableVideo).takeIf { selectedNode in it }
        ?: listOf(selectedNode)
    val refsByOpaqueId = queue.associate { it.ref.nodeId.value to it.ref }
    return VideoPlaybackSession(
        items = queue.map { node ->
            VideoPlaybackItem(
                mediaItem = MediaItem.Builder()
                    .setUri("onlyfiles://playback/${node.ref.nodeId.value}")
                    .setMediaId(node.ref.nodeId.value)
                    .setMimeType(node.mimeType)
                    .build(),
                title = node.name
            )
        },
        startIndex = queue.indexOf(selectedNode).coerceAtLeast(0),
        dataSourceFactory = androidx.media3.datasource.DataSource.Factory {
            VaultMediaDataSource(refsByOpaqueId, openReader)
        },
        securityScopeId = vaultSecurityScope(vaultId)
    )
}

internal fun vaultSecurityScope(vaultId: VaultId): String = "vault:${vaultId.value}"
