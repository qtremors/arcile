package dev.qtremors.arcile.feature.importing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.qtremors.arcile.core.storage.domain.StorageVolume
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class SaveToArcileState(
    val incoming: List<IncomingSharedFile>,
    val volumes: List<StorageVolume> = emptyList(),
    val currentDirectory: File? = null,
    val childDirectories: List<File> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false
) {
    val canUseCurrentDirectory: Boolean
        get() = currentDirectory != null &&
            isValidSaveToArcileDirectory(currentDirectory, volumes)
}

internal data class SaveToArcileActions(
    val navigateBack: () -> Unit,
    val selectVolume: (StorageVolume) -> Unit,
    val selectDirectory: (File) -> Unit,
    val saveAsDefault: () -> Unit,
    val saveHere: () -> Unit
)

@Composable
internal fun SaveToArcileRoute(
    incoming: List<IncomingSharedFile>,
    loadVolumes: suspend () -> List<StorageVolume>,
    loadDefaultPath: suspend () -> String?,
    saveDefaultPath: suspend (String) -> Unit,
    copyTo: suspend (File) -> Result<SaveIncomingResult>,
    onCancel: () -> Unit,
    onDefaultSaved: () -> Unit,
    onFinished: (SaveIncomingResult) -> Unit,
    onFailed: (Throwable) -> Unit
) {
    val scope = rememberCoroutineScope()
    var state by remember(incoming) { mutableStateOf(SaveToArcileState(incoming)) }

    LaunchedEffect(incoming) {
        try {
            val volumes = loadVolumes()
            val initialDirectory = resolveInitialSaveToArcileDirectory(loadDefaultPath(), volumes)
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
        val children = withContext(Dispatchers.IO) {
            try {
                selected
                    ?.listFiles { file -> file.isDirectory && file.canRead() }
                    ?.asSequence()
                    ?.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                    ?.toList()
                    .orEmpty()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                emptyList()
            }
        }
        if (state.currentDirectory == selected) {
            state = state.copy(childDirectories = children)
        }
    }

    fun selectDirectory(directory: File?) {
        state = state.copy(currentDirectory = directory, childDirectories = emptyList())
    }

    SaveToArcileScreen(
        state = state,
        actions = SaveToArcileActions(
            navigateBack = {
                val selected = state.currentDirectory
                if (selected == null) {
                    onCancel()
                } else {
                    val parent = selected.parentFile
                    val containingVolume = state.volumes.firstOrNull { volume ->
                        runCatching { File(volume.path).canonicalFile == selected.canonicalFile }
                            .getOrDefault(false)
                    }
                    selectDirectory(if (containingVolume != null) null else parent)
                }
            },
            selectVolume = { volume -> selectDirectory(File(volume.path)) },
            selectDirectory = ::selectDirectory,
            saveAsDefault = {
                val destination = state.currentDirectory
                    ?.takeIf { isValidSaveToArcileDirectory(it, state.volumes) }
                    ?: return@SaveToArcileActions
                if (!state.isSaving) {
                    scope.launch {
                        try {
                            saveDefaultPath(destination.absolutePath)
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
                val destination = state.currentDirectory
                    ?.takeIf { isValidSaveToArcileDirectory(it, state.volumes) }
                    ?: return@SaveToArcileActions
                if (!state.isSaving) {
                    state = state.copy(isSaving = true)
                    scope.launch {
                        try {
                            copyTo(destination).onSuccess(onFinished).onFailure(onFailed)
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
