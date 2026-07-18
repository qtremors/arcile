package dev.qtremors.arcile.feature.onlyfiles

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import dev.qtremors.arcile.core.ui.video.VideoPlaybackItem
import dev.qtremors.arcile.core.ui.video.VideoPlaybackSession
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import dev.qtremors.arcile.core.vault.domain.VaultNodeRef
import dev.qtremors.arcile.core.vault.domain.VaultSeekableReader

@Composable
internal fun ViewerScreen(
    node: VaultNodeMetadata,
    viewModel: OnlyFilesViewModel,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text(node.name, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.onlyfiles_back))
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            if (node.isViewableImage()) VaultImage(node, viewModel::openReader)
        }
    }
}

@Composable
private fun VaultImage(
    node: VaultNodeMetadata,
    openReader: (VaultNodeRef) -> Result<VaultSeekableReader>
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val targetWidth = with(density) { configuration.screenWidthDp.dp.roundToPx() }.coerceAtLeast(1)
    val targetHeight = with(density) { configuration.screenHeightDp.dp.roundToPx() }.coerceAtLeast(1)
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
