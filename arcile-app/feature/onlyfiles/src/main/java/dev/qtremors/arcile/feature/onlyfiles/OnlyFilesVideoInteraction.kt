package dev.qtremors.arcile.feature.onlyfiles

import android.media.MediaDataSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.ui.metadata.readVideoVisualMetadata
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import dev.qtremors.arcile.core.vault.domain.VaultNodeRef
import dev.qtremors.arcile.core.vault.domain.VaultSeekableReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class OnlyFilesVideoInteraction(
    private val viewModel: OnlyFilesViewModel,
    private val vaultId: VaultId,
    private val mountGeneration: Long
) {
    private var request by mutableStateOf<Pair<ExternalAction, VaultNodeMetadata>?>(null)

    fun request(action: ExternalAction, node: VaultNodeMetadata) {
        if (isCurrent(viewModel.state.value)) request = action to node
    }

    private fun isCurrent(state: OnlyFilesUiState): Boolean =
        state.selectedVaultId == vaultId && state.mountGeneration == mountGeneration

    @Composable
    fun Host(onClosed: () -> Unit, content: @Composable () -> Unit) {
        val state by viewModel.state.collectAsStateWithLifecycle()
        if (isCurrent(state)) {
            content()
            request?.let { (action, node) ->
                VaultExternalAccessDialogs(action, listOf(node), viewModel) { request = null }
            }
        } else {
            LaunchedEffect(Unit) {
                request = null
                onClosed()
            }
        }
    }
}

internal suspend fun readVaultVideoMetadata(
    node: VaultNodeMetadata,
    file: FileModel,
    openReader: (VaultNodeRef) -> Result<VaultSeekableReader>
) = withContext(Dispatchers.IO) {
    val reader = openReader(node.ref).getOrNull() ?: return@withContext null
    reader.use {
        val source = object : MediaDataSource() {
            override fun getSize(): Long = reader.sizeBytes
            override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int =
                reader.readAt(position, buffer, offset, size)
            override fun close() = Unit
        }
        readVideoVisualMetadata(file.reference, reader.sizeBytes, file.mimeType) { it.setDataSource(source) }
    }
}
