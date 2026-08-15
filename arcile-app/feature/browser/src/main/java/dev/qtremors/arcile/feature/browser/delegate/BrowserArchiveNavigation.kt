package dev.qtremors.arcile.feature.browser.delegate

import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.core.storage.domain.ArchiveNameEncoding
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import dev.qtremors.arcile.core.storage.domain.storageParentPath
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.feature.browser.ArchivePasswordAction
import dev.qtremors.arcile.feature.browser.BrowserArchiveContext
import dev.qtremors.arcile.feature.browser.withUpdatedDisplayState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.launch

internal fun BrowserNavigationController.openArchive(
    archivePath: String,
    entryPrefix: String? = null,
    seedHistory: Boolean = true,
    archiveNodeRef: StorageNodeRef? = null,
    isRootStorageScope: Boolean = state.value.isRootStorageScope,
    backendId: String? = archiveNodeRef?.backendId
) {
    val volume = if (isRootStorageScope) null else findVolumeForPath(archivePath)
    val explicitNodeRef = when (backendId) {
        StorageNodeRef.ROOT_BACKEND_ID -> StorageNodeRef.root(
            archivePath,
            archivePath,
            volumeId = volume?.id
        )
        StorageNodeRef.SHIZUKU_BACKEND_ID -> StorageNodeRef.shizuku(
            archivePath,
            archivePath,
            volumeId = volume?.id
        )
        else -> null
    }
    if (volume == null && !isRootStorageScope && explicitNodeRef == null) {
        openFileBrowser(errorMessage = UiText.StringResource(R.string.error_storage_for_path_unavailable))
        return
    }
    navigationPersistence.clear()
    val parent = storageParentPath(archivePath)
    if (seedHistory && !parent.isNullOrBlank()) {
        navigationPersistence.push(
            BrowserHistoryEntry.Directory(
                path = parent,
                nodeRef = if (isRootStorageScope) StorageNodeRef.root(parent, parent) else null,
                isRootStorageScope = isRootStorageScope
            )
        )
    }
    loadArchiveEntries(
        archivePath = archivePath,
        archiveNodeRef = archiveNodeRef
            ?: explicitNodeRef
            ?: state.value.files.firstOrNull { it.absolutePath == archivePath }?.nodeRef
            ?: state.value.archiveContext?.takeIf { it.archivePath == archivePath }?.archiveNodeRef
            ?: navigationPersistence.restoredArchiveNodeRef(archivePath),
        entryPrefix = entryPrefix,
        password = state.value.archiveContext
            ?.takeIf { it.archivePath == archivePath }
            ?.password,
        nameEncoding = state.value.archiveContext
            ?.takeIf { it.archivePath == archivePath }
            ?.nameEncoding
            ?: ArchiveNameEncoding.UTF_8,
        pushHistory = false,
        isRootStorageScope = isRootStorageScope
    )
}

internal fun BrowserNavigationController.submitArchivePassword(password: String) {
    val archive = state.value.archiveContext ?: return
    loadArchiveEntries(
        archivePath = archive.archivePath,
        archiveNodeRef = archive.archiveNodeRef,
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
        archiveNodeRef = archive.archiveNodeRef,
        entryPrefix = entryPrefix,
        password = archive.password,
        nameEncoding = archive.nameEncoding,
        pushHistory = true
    )
}

internal fun BrowserNavigationController.loadArchiveEntries(
    archivePath: String,
    archiveNodeRef: StorageNodeRef?,
    entryPrefix: String?,
    password: String?,
    nameEncoding: ArchiveNameEncoding,
    pushHistory: Boolean,
    isRootStorageScope: Boolean = state.value.isRootStorageScope
) {
    val previous = state.value.archiveContext
    val generation = nextLoadGeneration()
    if (pushHistory && previous?.entryPrefix != entryPrefix) {
        navigationPersistence.push(
            BrowserHistoryEntry.Archive(
                archivePath = previous?.archivePath ?: archivePath,
                entryPrefix = previous?.entryPrefix,
                archiveNodeRef = previous?.archiveNodeRef ?: archiveNodeRef
            )
        )
    }
    val volume = if (isRootStorageScope) null else findVolumeForPath(archivePath)
    onLocationChanged()
    update {
        it.withValues(
            archiveContext = BrowserArchiveContext(
                archivePath = archivePath,
                archiveNodeRef = archiveNodeRef,
                entryPrefix = entryPrefix,
                password = password,
                nameEncoding = nameEncoding,
                entries = previous?.takeIf { context ->
                    context.archivePath == archivePath
                }?.entries.orEmpty()
            ),
            currentPath = archivePath,
            currentVolumeId = volume?.id,
            isRootStorageScope = isRootStorageScope,
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
        val result = archiveNodeRef?.let {
            archiveRepository.listArchiveEntries(it, password, nameEncoding)
        } ?: archiveRepository.listArchiveEntries(archivePath, password, nameEncoding)
        result
            .onSuccess { entries ->
                if (!isActiveLoad(generation)) return@onSuccess
                update {
                    it.withValues(
                        isLoading = false,
                        isPullToRefreshing = false,
                        archiveContext = BrowserArchiveContext(
                            archivePath = archivePath,
                            archiveNodeRef = archiveNodeRef,
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
                            archiveNodeRef = archiveNodeRef,
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
