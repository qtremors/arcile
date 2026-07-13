package dev.qtremors.arcile.feature.importing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.qtremors.arcile.core.storage.domain.SaveDestinationBrowser
import dev.qtremors.arcile.core.storage.domain.SaveDestinationDirectory
import dev.qtremors.arcile.core.storage.domain.StorageVolume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal data class SaveToArcileState(
    val incoming: List<IncomingSharedFile>,
    val volumes: List<StorageVolume> = emptyList(),
    val currentDirectory: SaveDestinationDirectory? = null,
    val childDirectories: List<SaveDestinationDirectory> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false
) {
    val canUseCurrentDirectory: Boolean get() = currentDirectory?.canSave == true
}

internal data class SaveToArcileActions(
    val navigateBack: () -> Unit,
    val selectVolume: (StorageVolume) -> Unit,
    val selectDirectory: (SaveDestinationDirectory) -> Unit,
    val saveAsDefault: () -> Unit,
    val saveHere: () -> Unit
)

@Composable
internal fun SaveToArcileRoute(
    incoming: List<IncomingSharedFile>,
    loadVolumes: suspend () -> List<StorageVolume>,
    loadDefaultPath: suspend () -> String?,
    destinationBrowser: SaveDestinationBrowser,
    saveDefaultPath: suspend (String) -> Unit,
    copyTo: suspend (String) -> Result<SaveIncomingResult>,
    onCancel: () -> Unit,
    onDefaultSaved: () -> Unit,
    onFinished: (SaveIncomingResult) -> Unit,
    onFailed: (Throwable) -> Unit
) {
    val scope = rememberCoroutineScope()
    var state by remember(incoming) { mutableStateOf(SaveToArcileState(incoming)) }

    fun selectDirectory(directory: SaveDestinationDirectory?) {
        state = state.copy(currentDirectory = directory, childDirectories = emptyList())
    }

    fun reportFailure(error: Throwable) {
        if (error is CancellationException) throw error
        onFailed(error)
    }

    fun selectPath(path: String) {
        if (state.isLoading || state.isSaving) return
        scope.launch {
            state = state.copy(isLoading = true)
            destinationBrowser.resolve(path, state.volumes)
                .onSuccess { directory ->
                    if (directory == null) {
                        reportFailure(IllegalStateException("Save destination is unavailable"))
                    } else {
                        selectDirectory(directory)
                    }
                }
                .onFailure(::reportFailure)
            state = state.copy(isLoading = false)
        }
    }

    LaunchedEffect(incoming) {
        try {
            val volumes = loadVolumes()
            val initialDirectory = destinationBrowser.resolve(loadDefaultPath(), volumes).getOrThrow()
            state = state.copy(
                volumes = volumes,
                currentDirectory = initialDirectory,
                isLoading = false
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            state = state.copy(isLoading = false)
            onFailed(error)
        }
    }

    LaunchedEffect(state.currentDirectory) {
        val selected = state.currentDirectory
        val children = selected?.let {
            destinationBrowser.children(it.path, state.volumes).getOrElse { error ->
                reportFailure(error)
                emptyList()
            }
        }.orEmpty()
        if (state.currentDirectory == selected) {
            state = state.copy(childDirectories = children)
        }
    }

    SaveToArcileScreen(
        state = state,
        actions = SaveToArcileActions(
            navigateBack = {
                val selected = state.currentDirectory
                if (selected == null) {
                    onCancel()
                } else if (!state.isLoading && !state.isSaving) {
                    scope.launch {
                        state = state.copy(isLoading = true)
                        try {
                            destinationBrowser.parent(selected.path, state.volumes)
                                .onSuccess(::selectDirectory)
                                .onFailure(::reportFailure)
                        } finally {
                            state = state.copy(isLoading = false)
                        }
                    }
                }
            },
            selectVolume = { volume -> selectPath(volume.path) },
            selectDirectory = { directory -> selectPath(directory.path) },
            saveAsDefault = {
                val destination = state.currentDirectory ?: return@SaveToArcileActions
                if (!state.isSaving) {
                    scope.launch {
                        try {
                            val verified = destinationBrowser.resolve(destination.path, state.volumes)
                                .getOrThrow() ?: error("Save destination is unavailable")
                            check(verified.canSave) { "Save destination is not writable" }
                            saveDefaultPath(verified.path)
                            onDefaultSaved()
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Throwable) {
                            onFailed(error)
                        }
                    }
                }
            },
            saveHere = {
                val destination = state.currentDirectory ?: return@SaveToArcileActions
                if (!state.isSaving) {
                    state = state.copy(isSaving = true)
                    scope.launch {
                        try {
                            val verified = destinationBrowser.resolve(destination.path, state.volumes)
                                .getOrThrow() ?: error("Save destination is unavailable")
                            check(verified.canSave) { "Save destination is not writable" }
                            copyTo(verified.path).onSuccess(onFinished).onFailure(onFailed)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Throwable) {
                            onFailed(error)
                        } finally {
                            state = state.copy(isSaving = false)
                        }
                    }
                }
            }
        )
    )
}
