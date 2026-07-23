package dev.qtremors.arcile.feature.onlyfiles

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import dev.qtremors.arcile.core.ui.video.VideoPlaybackItem
import dev.qtremors.arcile.core.ui.video.VideoPlaybackSession
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import dev.qtremors.arcile.core.vault.domain.VaultNodeRef
import dev.qtremors.arcile.core.vault.domain.VaultSeekableReader
import dev.qtremors.arcile.core.ui.theme.bounceClickable
import dev.qtremors.arcile.core.ui.theme.ExpressiveShapes
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

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (node.isViewableImage()) VaultImage(node, viewModel::openReader)

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
                    .bounceClickable { onBack() },
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .bounceClickable { showInfo = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Info, stringResource(R.string.onlyfiles_properties), tint = Color.White)
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .bounceClickable { externalAction = ExternalAction.SHARE },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Share, stringResource(R.string.onlyfiles_share), tint = Color.White)
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .bounceClickable { externalAction = ExternalAction.OPEN_WITH },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, stringResource(R.string.onlyfiles_open_with), tint = Color.White)
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .bounceClickable { confirmDelete = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Delete, stringResource(R.string.onlyfiles_delete), tint = Color.White)
                }
            }
        }

        // Left/Right Navigation Arrows
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(16.dp)
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f))
                .bounceClickable { viewModel.navigateViewer(-1) },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.onlyfiles_previous), tint = Color.White)
        }
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(16.dp)
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f))
                .bounceClickable { viewModel.navigateViewer(1) },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.onlyfiles_next), tint = Color.White)
        }
    }

    if (showInfo) AlertDialog(
        onDismissRequest = { showInfo = false }, title = { Text(node.name) },
        text = { Text("${formatBytes(node.sizeBytes)}\n${node.mimeType ?: stringResource(R.string.onlyfiles_unknown_type)}\n${DateFormat.getDateTimeInstance().format(node.modifiedAtMillis)}") },
        confirmButton = {
            Button(
                onClick = { showInfo = false },
                shape = ExpressiveShapes.medium,
                modifier = Modifier.bounceClickable { showInfo = false }
            ) { Text(stringResource(R.string.onlyfiles_close)) }
        }
    )
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false }, title = { Text(stringResource(R.string.onlyfiles_delete)) },
        text = { Text(pluralStringResource(R.plurals.onlyfiles_delete_many_confirm, 1, 1)) },
        confirmButton = {
            Button(
                onClick = { confirmDelete = false; viewModel.delete(listOf(node)) },
                shape = ExpressiveShapes.medium,
                modifier = Modifier.bounceClickable { confirmDelete = false; viewModel.delete(listOf(node)) }
            ) { Text(stringResource(R.string.onlyfiles_delete)) }
        },
        dismissButton = {
            TextButton(
                onClick = { confirmDelete = false },
                shape = ExpressiveShapes.medium,
                modifier = Modifier.bounceClickable { confirmDelete = false }
            ) { Text(stringResource(R.string.onlyfiles_cancel)) }
        }
    )
    externalAction?.let { action -> AlertDialog(
        onDismissRequest = { externalAction = null },
        title = { Text(stringResource(if (action == ExternalAction.SHARE) R.string.onlyfiles_share else R.string.onlyfiles_open_with)) },
        text = { Text(stringResource(R.string.onlyfiles_external_warning)) },
        confirmButton = {
            Button(
                onClick = {
                    externalAction = null
                    viewModel.issueExternalAccess(listOf(node)) { grants ->
                        runCatching { launchExternalIntent(context, action, grants) }.onFailure { viewModel.revokeExternalAccess(grants) }
                    }
                },
                shape = ExpressiveShapes.medium,
                modifier = Modifier.bounceClickable {
                    externalAction = null
                    viewModel.issueExternalAccess(listOf(node)) { grants ->
                        runCatching { launchExternalIntent(context, action, grants) }.onFailure { viewModel.revokeExternalAccess(grants) }
                    }
                }
            ) { Text(stringResource(R.string.onlyfiles_continue)) }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = {
                        fallbackAction = externalAction
                        externalAction = null
                    },
                    shape = ExpressiveShapes.medium,
                    modifier = Modifier.bounceClickable {
                        fallbackAction = externalAction
                        externalAction = null
                    }
                ) { Text(stringResource(R.string.onlyfiles_compatibility_copy)) }
                TextButton(
                    onClick = { externalAction = null },
                    shape = ExpressiveShapes.medium,
                    modifier = Modifier.bounceClickable { externalAction = null }
                ) { Text(stringResource(R.string.onlyfiles_cancel)) }
            }
        }
    ) }
    fallbackAction?.let { action -> AlertDialog(
        onDismissRequest = { fallbackAction = null },
        title = { Text(stringResource(R.string.onlyfiles_compatibility_copy)) },
        text = { Text(stringResource(R.string.onlyfiles_compatibility_copy_warning)) },
        confirmButton = {
            Button(
                onClick = {
                    fallbackAction = null
                    viewModel.issuePlaintextFallbackAccess(listOf(node)) { grants ->
                        runCatching { launchExternalIntent(context, action, grants) }
                            .onFailure { viewModel.revokeExternalAccess(grants) }
                    }
                },
                shape = ExpressiveShapes.medium,
                modifier = Modifier.bounceClickable {
                    fallbackAction = null
                    viewModel.issuePlaintextFallbackAccess(listOf(node)) { grants ->
                        runCatching { launchExternalIntent(context, action, grants) }
                            .onFailure { viewModel.revokeExternalAccess(grants) }
                    }
                }
            ) { Text(stringResource(R.string.onlyfiles_create_copy)) }
        },
        dismissButton = {
            TextButton(
                onClick = { fallbackAction = null },
                shape = ExpressiveShapes.medium,
                modifier = Modifier.bounceClickable { fallbackAction = null }
            ) { Text(stringResource(R.string.onlyfiles_cancel)) }
        }
    ) }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
        decoded == null -> LoadingIndicator()
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
        securityScopeId = vaultSecurityScope(vaultId),
        files = queue.map { it.toSharedFileModel() }
    )
}

internal fun vaultSecurityScope(vaultId: VaultId): String = "vault:${vaultId.value}"
