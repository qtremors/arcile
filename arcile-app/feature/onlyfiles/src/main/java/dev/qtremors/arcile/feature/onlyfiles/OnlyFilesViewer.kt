package dev.qtremors.arcile.feature.onlyfiles

import android.graphics.BitmapFactory
import android.net.Uri
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DataSource
import dev.qtremors.arcile.core.ui.ArcileVideoPlayer
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultNode
import dev.qtremors.arcile.core.vault.domain.VaultPath
import dev.qtremors.arcile.core.vault.domain.VaultSeekableReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun ViewerScreen(
    state: OnlyFilesUiState,
    node: VaultNode,
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
            when {
                node.isViewableImage() -> VaultImage(node, viewModel)
                node.isViewableVideo() -> {
                    val vaultId = state.selectedVaultId
                    if (vaultId != null) VaultVideo(vaultId, node, viewModel::openReader)
                }
            }
        }
    }
}

@Composable
private fun VaultImage(node: VaultNode, viewModel: OnlyFilesViewModel) {
    val decoded by produceState<Result<androidx.compose.ui.graphics.ImageBitmap?>?>(null, node.id) {
        value = viewModel.readViewerImage(node).mapCatching { bytes ->
            try {
                withContext(Dispatchers.Default) {
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                }
            } finally {
                bytes.fill(0)
            }
        }
    }
    when {
        decoded == null -> CircularProgressIndicator()
        decoded?.getOrNull() == null -> Text(stringResource(R.string.onlyfiles_error_image))
        else -> Image(requireNotNull(decoded?.getOrNull()), node.name, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
    }
}

@Composable
private fun VaultVideo(
    vaultId: VaultId,
    node: VaultNode,
    openReader: (VaultId, VaultPath) -> Result<VaultSeekableReader>
) {
    val factory = remember(vaultId, node.id) {
        DataSource.Factory { VaultMediaDataSource(vaultId, node.path, openReader) }
    }
    val mediaItem = remember(vaultId, node.id) {
        MediaItem.Builder()
            .setUri("onlyfiles://${vaultId.value}/${Uri.encode(node.path.value)}")
            .setMimeType(node.mimeType)
            .build()
    }
    ArcileVideoPlayer(
        mediaItem = mediaItem,
        modifier = Modifier.fillMaxSize(),
        dataSourceFactory = factory
    )
}
