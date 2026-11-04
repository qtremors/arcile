package dev.qtremors.arcile.feature.onlyfiles

import android.content.ContentResolver
import dev.qtremors.arcile.core.ui.audio.PrivateAudioEditSessions
import dev.qtremors.arcile.core.vault.domain.VaultFileSystem
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Vault audio editing entry points: the shared tag editor and in-memory editor sessions, scoped to the open mount. */
internal class OnlyFilesAudioEditing(
    private val files: VaultFileSystem,
    private val unlockedVaults: StateFlow<Set<VaultId>>,
    private val state: MutableStateFlow<OnlyFilesUiState>,
    private val scope: CoroutineScope,
    private val showError: (Throwable) -> Unit,
    private val refresh: () -> Unit
) {
    /** Opens the shared audio editor on in-memory copies; [launch] receives the opaque session ID. */
    fun edit(nodes: List<VaultNodeMetadata>, cacheDirectory: String, launch: (String) -> Unit) {
        if (!canEditVaultAudio(nodes)) return
        val mount = state.value.mountGeneration
        scope.launch {
            val session = try {
                VaultAudioEditSession.create(nodes, files, cacheDirectory, unlockedVaults)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                showError(error)
                return@launch
            }
            if (state.value.mountGeneration != mount) {
                session.close()
                return@launch
            }
            val id = PrivateAudioEditSessions.register(session)
            try {
                launch(id)
            } catch (error: Exception) {
                PrivateAudioEditSessions.end(id)
                showError(error)
            }
        }
    }

    /** One editor per open track; saves refresh the viewer only within the mount that opened it. */
    fun metadataEditor(node: VaultNodeMetadata, contentResolver: ContentResolver): VaultAudioMetadataEditor {
        val mount = state.value.mountGeneration
        return VaultAudioMetadataEditor(node, files, contentResolver, onSaved = { saved ->
            scope.launch {
                if (state.value.mountGeneration == mount && state.value.selectedVaultId == saved.ref.vaultId) {
                    state.update { it.withUpdatedViewerNode(saved) }
                    refresh()
                }
            }
        })
    }
}
