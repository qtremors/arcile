package dev.qtremors.arcile.feature.browser.delegate

import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.core.storage.domain.ArchiveNameEncoding
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.feature.browser.ArchivePasswordAction
import dev.qtremors.arcile.feature.browser.BrowserArchiveContext
import dev.qtremors.arcile.feature.browser.withUpdatedDisplayState
import java.io.File
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.launch

internal fun BrowserNavigationController.openArchive(
    archivePath: String,
    entryPrefix: String? = null,
    seedHistory: Boolean = true
) {
    val volume = findVolumeForPath(archivePath)
    if (volume == null) {
        openFileBrowser(errorMessage = UiText.StringResource(R.string.error_storage_for_path_unavailable))
        return
    }
    navigationPersistence.clear()
    val parent = File(archivePath).parent?.normalizeStorageSeparators()
    if (seedHistory && !parent.isNullOrBlank()) {
        navigationPersistence.push(BrowserHistoryEntry.Directory(parent))
    }
    loadArchiveEntries(
        archivePath = archivePath,
        entryPrefix = entryPrefix,
        password = state.value.archiveContext
            ?.takeIf { it.archivePath == archivePath }
            ?.password,
        nameEncoding = state.value.archiveContext
            ?.takeIf { it.archivePath == archivePath }
            ?.nameEncoding
            ?: ArchiveNameEncoding.UTF_8,
        pushHistory = false
    )
}

internal fun BrowserNavigationController.submitArchivePassword(password: String) {
    val archive = state.value.archiveContext ?: return
    loadArchiveEntries(
        archivePath = archive.archivePath,
        entryPrefix = archive.entryPrefix,
        password = password,
        nameEncoding = archive.nameEncoding,
        pushHistory = false
    )
}

internal fun BrowserNavigationController.openArchiveFolder(entryPrefix: String) {
    val archive = state.value.archiveContext ?: return
    loadArchiveEntries(
        archivePath = archive.archivePath,
        entryPrefix = entryPrefix,
        password = archive.password,
        nameEncoding = archive.nameEncoding,
        pushHistory = true
    )
}

internal fun BrowserNavigationController.loadArchiveEntries(
    archivePath: String,
    entryPrefix: String?,
    password: String?,
    nameEncoding: ArchiveNameEncoding,
    pushHistory: Boolean
) {
    val previous = state.value.archiveContext
    val generation = nextLoadGeneration()
    if (pushHistory && previous?.entryPrefix != entryPrefix) {
        navigationPersistence.push(
            BrowserHistoryEntry.Archive(
                archivePath = previous?.archivePath ?: archivePath,
                entryPrefix = previous?.entryPrefix
            )
        )
    }
    val volume = findVolumeForPath(archivePath)
    onLocationChanged()
    update {
        it.withValues(
            archiveContext = BrowserArchiveContext(
                archivePath = archivePath,
                entryPrefix = entryPrefix,
                password = password,
                nameEncoding = nameEncoding,
                entries = previous?.takeIf { context ->
                    context.archivePath == archivePath
                }?.entries.orEmpty()
            ),
            currentPath = archivePath,
            currentVolumeId = volume?.id,
            isVolumeRootScreen = false,
            isCategoryScreen = false,
            activeCategoryName = "",
            selectedFolderTabPath = null,
            files = persistentListOf(),
            folderStatsByPath = persistentMapOf(),
            folderStatsLoadingPaths = persistentSetOf(),
            isLoading = true,
            error = null
        ).withUpdatedDisplayState()
    }
    saveNavStateIfActive(generation)
    activeLoadJob = viewModelScope.launch {
        archiveRepository.listArchiveEntries(archivePath, password, nameEncoding)
            .onSuccess { entries ->
                if (!isActiveLoad(generation)) return@onSuccess
                update {
                    it.withValues(
                        isLoading = false,
                        isPullToRefreshing = false,
                        archiveContext = BrowserArchiveContext(
                            archivePath = archivePath,
                            entryPrefix = entryPrefix,
                            password = password,
                            nameEncoding = nameEncoding,
                            entries = entries
                        ),
                        files = BrowserArchiveListingMapper.map(
                            archivePath,
                            entries,
                            entryPrefix
                        ).toPersistentList()
                    ).withUpdatedDisplayState()
                }
                saveNavStateIfActive(generation)
            }
            .onFailure { error ->
                if (!isActiveLoad(generation)) return@onFailure
                val passwordError = error.isArchivePasswordError()
                update {
                    it.withValues(
                        isLoading = false,
                        isPullToRefreshing = false,
                        archiveContext = BrowserArchiveContext(
                            archivePath = archivePath,
                            entryPrefix = entryPrefix,
                            password = password,
                            nameEncoding = nameEncoding,
                            entries = previous?.entries.orEmpty(),
                            passwordRequired = passwordError,
                            pendingPasswordAction = ArchivePasswordAction.OPEN
                        ),
                        error = if (passwordError) {
                            null
                        } else {
                            error.message?.let(UiText::Dynamic)
                                ?: UiText.StringResource(R.string.error_unsupported_archive)
                        }
                    ).withUpdatedDisplayState()
                }
            }
    }
}

private fun Throwable.isArchivePasswordError(): Boolean =
    message.orEmpty().contains("password", ignoreCase = true) ||
        message.orEmpty().contains("encrypted", ignoreCase = true)
