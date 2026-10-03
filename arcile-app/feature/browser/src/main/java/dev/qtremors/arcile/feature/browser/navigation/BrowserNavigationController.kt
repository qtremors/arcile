package dev.qtremors.arcile.feature.browser.navigation

import androidx.lifecycle.SavedStateHandle
import dev.qtremors.arcile.feature.browser.controller.BrowserArchiveWorkflowState
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.core.storage.domain.ArchiveRepository
import dev.qtremors.arcile.core.storage.domain.BrowserLocationPreferencesStore
import dev.qtremors.arcile.core.storage.domain.FileBrowserRepository
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.SearchRepository
import dev.qtremors.arcile.core.storage.domain.FileListingPreferences
import dev.qtremors.arcile.core.storage.domain.StorageBrowserLocation
import dev.qtremors.arcile.core.storage.domain.StorageScope
import dev.qtremors.arcile.core.storage.domain.isStorageDescendantOrSelf
import dev.qtremors.arcile.core.storage.domain.storageParentPath
import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.core.ui.image.ArchiveEntryThumbnailData
import dev.qtremors.arcile.feature.browser.BrowserNavigationEvent
import dev.qtremors.arcile.feature.browser.BrowserNavigationState
import dev.qtremors.arcile.feature.browser.applyNavigationPreferences
import dev.qtremors.arcile.feature.browser.reduce
import dev.qtremors.arcile.feature.browser.withUpdatedDisplayState
import dev.qtremors.arcile.feature.browser.withArchivePrompt
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class BrowserNavigationController(
    initialState: BrowserNavigationState,
    internal val viewModelScope: CoroutineScope,
    internal val fileBrowserRepository: FileBrowserRepository,
    internal val archiveRepository: ArchiveRepository,
    private val searchRepository: SearchRepository,
    internal val browserPreferencesRepository: BrowserLocationPreferencesStore,
    private val savedStateHandle: SavedStateHandle,
    internal val onLocationChanged: () -> Unit
) {
    internal val state = MutableStateFlow(initialState)
    internal val navigationPersistence = BrowserNavigationPersistence(savedStateHandle)
    internal var activeLoadJob: Job? = null
    private var activeLoadGeneration = 0L
    private var lastLocationWriteJob: Job? = null
    private var folderStatBatchJob: Job? = null
    private val pendingFolderStats = mutableMapOf<String, dev.qtremors.arcile.core.storage.domain.FolderStats>()

    internal fun persistLocation(path: String, volumeId: String) {
        val previous = lastLocationWriteJob
        lastLocationWriteJob = viewModelScope.launch {
            previous?.join()
            try {
                browserPreferencesRepository.updateLastOpenedLocation(path, volumeId)
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                dev.qtremors.arcile.core.runtime.logging.AppLogger.e("Browser", "Unable to remember folder", error)
            }
        }
    }

    fun restoreLocationFromState(): StorageBrowserLocation? =
        navigationPersistence.restoreLocation()

    fun initializeFromArgs() {
        val path = savedStateHandle.get<String>("path")?.takeIf { it.isNotEmpty() }
        val archivePath = savedStateHandle.get<String>("archivePath")?.takeIf { it.isNotEmpty() }
        val archiveEntryPrefix = savedStateHandle.get<String>("archiveEntryPrefix")?.takeIf { it.isNotEmpty() }
        val category = savedStateHandle.get<String>("category")?.takeIf { it.isNotEmpty() }
        val volumeId = savedStateHandle.get<String>("volumeId")
        val seedInitialPathHistory = savedStateHandle.get<Boolean>("seedInitialPathHistory") ?: true
        val restorePersistentLocation = savedStateHandle.get<Boolean>("restorePersistentLocation") ?: true

        when {
            archivePath != null -> openArchive(
                archivePath,
                archiveEntryPrefix,
                seedHistory = seedInitialPathHistory,
                isRootStorageScope = archivePath.normalizeStorageSeparators() == "/"
            )
            path != null -> navigateToSpecificFolder(
                path,
                seedInitialPathHistory = seedInitialPathHistory,
                isRootStorageScope = path.normalizeStorageSeparators() == "/"
            )
            category != null -> navigateToCategory(category, volumeId)
            else -> openFileBrowser(restorePersistentLocation = restorePersistentLocation)
        }
    }

    private fun saveNavState() = navigationPersistence.save(state.value)

    internal fun nextLoadGeneration(): Long {
        activeLoadJob?.cancel()
        folderStatBatchJob?.cancel()
        pendingFolderStats.clear()
        activeLoadGeneration += 1
        return activeLoadGeneration
    }

    internal fun isActiveLoad(generation: Long): Boolean = generation == activeLoadGeneration

    internal fun saveNavStateIfActive(generation: Long) {
        if (isActiveLoad(generation)) saveNavState()
    }

    fun volumeFiles() = state.value.storageVolumes.map { volume ->
        FileModel(
            name = volume.name,
            reference = volume.path,
            size = volume.totalBytes - volume.freeBytes,
            lastModified = 0L,
            isDirectory = true,
            extension = "",
            isHidden = false
        )
    }

    internal fun findVolumeForPath(path: String) =
        state.value.storageVolumes
            .sortedByDescending { it.path.length }
            .firstOrNull { isStorageDescendantOrSelf(path, it.path) }

    fun openFileBrowser(restorePersistentLocation: Boolean = false, errorMessage: UiText? = null) {
        viewModelScope.launch {
            if (restorePersistentLocation) {
                val prefs = browserPreferencesRepository.locationPreferencesFlow.first()
                val lastPath = prefs.lastOpenedPath.takeIf { prefs.rememberLastFolder }
                val lastVolumeId = prefs.lastOpenedVolumeId.takeIf { prefs.rememberLastFolder }

                if (!lastPath.isNullOrEmpty() && !lastVolumeId.isNullOrEmpty()) {
                    val volume = state.value.storageVolumes.firstOrNull { it.id == lastVolumeId }
                    if (volume != null) {
                        val alreadyAtRestoredLocation =
                            state.value.archiveContext == null &&
                                !state.value.isVolumeRootScreen &&
                                !state.value.isCategoryScreen &&
                                state.value.currentPath == lastPath &&
                                state.value.currentVolumeId == lastVolumeId
                        loadDirectory(
                            lastPath,
                            lastVolumeId,
                            clearHistory = !alreadyAtRestoredLocation,
                            errorMessage = errorMessage,
                            isRootStorageScope = false
                        )
                        return@launch
                    }
                }
            }

            val volumes = state.value.storageVolumes
            if (volumes.size > 1) {
                openVolumeRoots(errorMessage)
            } else {
                val primaryVolume = volumes.find { it.isPrimary } ?: volumes.firstOrNull()

                if (primaryVolume != null) {
                    loadDirectory(
                        primaryVolume.path,
                        primaryVolume.id,
                        clearHistory = true,
                        errorMessage = errorMessage,
                        persistAsLastOpened = false,
                        isRootStorageScope = false
                    )
                } else {
                    openVolumeRoots(errorMessage)
                }
            }
        }
    }

    fun openPrimaryStorage(errorMessage: UiText? = null) {
        val primaryVolume = state.value.storageVolumes.firstOrNull { it.isPrimary }
        if (primaryVolume == null) {
            openFileBrowser(errorMessage = errorMessage)
            return
        }
        navigationPersistence.clear()
        loadDirectory(
            path = primaryVolume.path,
            volumeId = primaryVolume.id,
            clearHistory = true,
            errorMessage = errorMessage,
            persistAsLastOpened = false,
            isRootStorageScope = false
        )
    }

    fun openVolumeRoots(errorMessage: UiText? = null) {
        navigationPersistence.clear()
        onLocationChanged()
        val generation = nextLoadGeneration()
        viewModelScope.launch {
            val prefs = browserPreferencesRepository.locationPreferencesFlow.first()
            if (!isActiveLoad(generation)) return@launch
            val presentation = prefs.getPresentationForPath("/")
            update {
                it.reduce(BrowserNavigationEvent.OpenVolumeRoots(volumeFiles())).withValues(
                    error = errorMessage,
                    browserSortOption = presentation.sortOption,
                    browserViewMode = presentation.viewMode,
                    browserListZoom = presentation.listZoom,
                    browserGridMinCellSize = presentation.gridMinCellSize,
                    browserShowThumbnails = presentation.showThumbnails,
                    browserFoldersFirst = presentation.foldersFirst
                ).withUpdatedDisplayState()
            }
            saveNavStateIfActive(generation)
        }
    }

    fun navigateToSpecificFolder(
        path: String,
        seedInitialPathHistory: Boolean = true,
        allowDirectPath: Boolean = false,
        isRootStorageScope: Boolean = path.normalizeStorageSeparators() == "/"
    ) {
        if (ArchiveFormat.isSupported(path)) {
            openArchive(
                path,
                seedHistory = seedInitialPathHistory,
                isRootStorageScope = isRootStorageScope
            )
            return
        }
        val volume = if (isRootStorageScope) null else findVolumeForPath(path)
        val directPathAllowed = allowDirectPath || isRootStorageScope
        if (volume == null && !directPathAllowed) {
            openFileBrowser(errorMessage = UiText.StringResource(R.string.error_storage_for_path_unavailable))
            return
        }
        navigationPersistence.clear()
        if (seedInitialPathHistory && volume != null && path != volume.path) {
            navigationPersistence.push(BrowserHistoryEntry.Directory(volume.path))
        }
        loadDirectory(
            path = path,
            volumeId = volume?.id,
            clearHistory = false,
            allowDirectPath = directPathAllowed,
            isRootStorageScope = isRootStorageScope
        )
    }

    fun navigateToCategory(categoryName: String, volumeId: String? = null) {
        navigationPersistence.clear()
        loadCategory(categoryName, volumeId)
    }

    fun navigateToFolder(path: String) {
        state.value.archiveContext?.let {
            if (path.startsWith(ARCHIVE_VIRTUAL_PREFIX)) {
                ArchiveEntryThumbnailData.entryPathFromVirtualPath(path)?.let(::openArchiveFolder)
                return
            }
            openArchive(path)
            return
        }

        if (state.value.isVolumeRootScreen) {
            val volume = state.value.storageVolumes.firstOrNull { it.path == path }
            if (volume != null) {
                loadDirectory(volume.path, volume.id, clearHistory = true)
            }
            return
        }

        if (state.value.currentPath.isNotEmpty() && state.value.currentPath != path) {
            state.value.historyEntry()?.let(navigationPersistence::push)
        }
        loadDirectory(
            path,
            state.value.currentVolumeId,
            clearHistory = false,
            isRootStorageScope = state.value.isRootStorageScope
        )
    }

    fun navigateBack(allowVolumeRootFallback: Boolean = true): Boolean {
        if (state.value.isCategoryScreen) {
            return false
        }

        state.value.archiveContext?.let { archive ->
            val prefix = archive.entryPrefix?.trimEnd('/')?.takeIf { it.isNotBlank() }
            if (prefix != null) {
                val parent = prefix.substringBeforeLast('/', missingDelimiterValue = "").takeIf { it.isNotBlank() }
                loadArchiveEntries(archive.archivePath, parent, archive.password, archive.nameEncoding, pushHistory = false)
                return true
            }
            val parentPath = storageParentPath(archive.archivePath)
            if (!parentPath.isNullOrBlank()) {
                navigationPersistence.clear()
                loadDirectory(
                    parentPath,
                    state.value.currentVolumeId,
                    clearHistory = false,
                    isRootStorageScope = state.value.isRootStorageScope
                )
                return true
            }
            return false
        }

        if (navigationPersistence.isNotEmpty()) {
            when (val previous = navigationPersistence.pop()) {
                is BrowserHistoryEntry.Directory -> {
                    val volume = if (previous.isRootStorageScope) null else findVolumeForPath(previous.path)
                    loadDirectory(
                        previous.path,
                        volume?.id,
                        clearHistory = false,
                        isRootStorageScope = previous.isRootStorageScope
                    )
                    return true
                }
                is BrowserHistoryEntry.Archive -> {
                    loadArchiveEntries(
                        archivePath = previous.archivePath,
                        entryPrefix = previous.entryPrefix,
                        password = state.value.archiveContext?.takeIf { it.archivePath == previous.archivePath }?.password,
                        nameEncoding = state.value.archiveContext?.takeIf { it.archivePath == previous.archivePath }?.nameEncoding
                            ?: dev.qtremors.arcile.core.storage.domain.ArchiveNameEncoding.UTF_8,
                        pushHistory = false
                    )
                    return true
                }
            }
        }

        if (allowVolumeRootFallback) {
            val currentPath = state.value.currentPath.takeIf { it.isNotBlank() }
            val volume = currentPath
                ?.takeUnless { state.value.isRootStorageScope }
                ?.let(::findVolumeForPath)
            val parentPath = currentPath
                ?.let(::storageParentPath)
                ?.takeIf {
                    it.isNotBlank() && when {
                        volume != null -> currentPath != volume.path &&
                            isStorageDescendantOrSelf(it, volume.path)
                        else -> isStorageDescendantOrSelf(it, "/")
                    }
                }
            if (parentPath != null) {
                loadDirectory(
                    parentPath,
                    volume?.id,
                    clearHistory = false,
                    isRootStorageScope = state.value.isRootStorageScope
                )
                return true
            }
        }

        if (allowVolumeRootFallback && !state.value.isVolumeRootScreen && state.value.storageVolumes.size > 1) {
            openVolumeRoots()
            return true
        }

        return false
    }

    fun refresh(pullToRefresh: Boolean = false) {
        update { it.withValues(isPullToRefreshing = pullToRefresh) }
        saveNavState()
        when {
            state.value.isVolumeRootScreen -> openVolumeRoots()
            state.value.isCategoryScreen -> loadCategory(state.value.activeCategoryName, state.value.currentVolumeId)
            state.value.archiveContext != null -> {
                val archive = state.value.archiveContext ?: return
                loadArchiveEntries(
                    archivePath = archive.archivePath,
                    entryPrefix = archive.entryPrefix,
                    password = archive.password,
                    nameEncoding = archive.nameEncoding,
                    pushHistory = false
                )
            }
            state.value.currentPath.isNotEmpty() -> loadDirectory(
                state.value.currentPath,
                state.value.currentVolumeId,
                clearHistory = false,
                isRootStorageScope = state.value.isRootStorageScope
            )
        }
    }

    fun setStorageVolumes(volumes: List<dev.qtremors.arcile.core.storage.domain.StorageVolume>) {
        update {
            it.withValues(storageVolumes = volumes.toPersistentList())
        }
    }

    fun updateFolderStat(
        path: String,
        stats: dev.qtremors.arcile.core.storage.domain.FolderStats
    ) {
        val sort = state.value.browserSortOption
        if (sort == dev.qtremors.arcile.core.storage.domain.FileSortOption.FILE_COUNT_HIGHEST ||
            sort == dev.qtremors.arcile.core.storage.domain.FileSortOption.FILE_COUNT_LOWEST) {
            if ((pendingFolderStats[path]?.cachedAt ?: Long.MIN_VALUE) <= stats.cachedAt) {
                pendingFolderStats[path] = stats
            }
            if (folderStatBatchJob?.isActive != true) {
                val generation = activeLoadGeneration
                folderStatBatchJob = viewModelScope.launch {
                    kotlinx.coroutines.delay(50)
                    if (isActiveLoad(generation)) applyFolderStats(pendingFolderStats.toMap())
                    pendingFolderStats.clear()
                }
            }
        } else applyFolderStats(mapOf(path to stats))
    }

    private fun applyFolderStats(updates: Map<String, dev.qtremors.arcile.core.storage.domain.FolderStats>) {
        update { current ->
            val paths = current.files.asSequence().filter { file ->
                file.isDirectory && file.reference in updates &&
                    (current.folderStatsByPath[file.reference]?.cachedAt ?: Long.MIN_VALUE) <=
                    updates.getValue(file.reference).cachedAt
            }
                .map(FileModel::reference).toSet()
            if (current.isVolumeRootScreen ||
                current.isCategoryScreen ||
                current.archiveContext != null ||
                paths.isEmpty()
            ) {
                current
            } else {
                current.withValues(
                    folderStatsByPath = (current.folderStatsByPath + updates.filterKeys { it in paths }).toPersistentMap(),
                    folderStatsLoadingPaths = (current.folderStatsLoadingPaths - paths).toPersistentSet()
                ).withUpdatedDisplayState(paths)
            }
        }
    }

    fun applyPreferences(preferences: dev.qtremors.arcile.core.storage.domain.BrowserLocationPreferences) {
        update { it.applyNavigationPreferences(preferences) }
    }

    fun selectFolderTab(path: String?) {
        update { it.reduce(BrowserNavigationEvent.SelectFolderTab(path)) }
    }

    fun updatePresentation(
        presentation: FileListingPreferences,
        applyToSubfolders: Boolean
    ) {
        applyPresentation(presentation.normalized())
        update {
            it.withValues(
                browserPresentationAppliesToSubfolders = applyToSubfolders
            )
        }
    }

    fun applyArchiveWorkflow(state: BrowserArchiveWorkflowState) {
        update {
            it.withValues(
                archiveContext = it.archiveContext.withArchivePrompt(state.passwordPrompt)
            )
        }
    }

    fun dismissArchivePasswordPrompt() {
        update {
            it.withValues(
                archiveContext = it.archiveContext?.copy(passwordRequired = false)
            )
        }
    }

    fun clearError() {
        update { it.withValues(error = null) }
    }

    private fun loadCategory(categoryName: String, volumeId: String?) {
        val generation = nextLoadGeneration()
        val preserveCurrentListing = state.value.archiveContext == null &&
            state.value.isCategoryScreen &&
            state.value.activeCategoryName == categoryName &&
            state.value.currentVolumeId == volumeId
        if (!preserveCurrentListing) onLocationChanged()
        update {
            it.reduce(BrowserNavigationEvent.OpenCategory(categoryName, volumeId)).withValues(
                isLoading = true,
                error = null,
            ).withUpdatedDisplayState()
        }
        saveNavStateIfActive(generation)
        activeLoadJob = viewModelScope.launch {
            val prefs = browserPreferencesRepository.locationPreferencesFlow.first()
            if (!isActiveLoad(generation)) return@launch
            val categoryPresentation = prefs.getPresentationForCategory(categoryName)

            val scope = StorageScope.Category(volumeId?.takeIf { it.isNotEmpty() }, categoryName)
            searchRepository.getFilesByCategory(scope, categoryName).onSuccess { files ->
                if (!isActiveLoad(generation)) return@onSuccess
                update {
                    it.withValues(
                        isLoading = false,
                        isPullToRefreshing = false,
                        files = files.toPersistentList(),
                        browserSortOption = categoryPresentation.sortOption,
                        browserViewMode = categoryPresentation.viewMode,
                        browserListZoom = categoryPresentation.listZoom,
                        browserGridMinCellSize = categoryPresentation.gridMinCellSize,
                        browserShowThumbnails = categoryPresentation.showThumbnails,
                        browserFoldersFirst = categoryPresentation.foldersFirst
                    ).withUpdatedDisplayState()
                }
                saveNavStateIfActive(generation)
            }.onFailure { error ->
                if (!isActiveLoad(generation)) return@onFailure
                update {
                    it.withValues(
                        isLoading = false,
                        isPullToRefreshing = false,
                        error = error.message?.let(UiText::Dynamic) ?: UiText.StringResource(R.string.error_load_category_failed)
                    )
                }
            }
        }
    }

    internal fun applyPresentation(presentation: FileListingPreferences, generation: Long? = null) {
        if (generation != null && !isActiveLoad(generation)) return
        update {
            it.withValues(
                browserSortOption = presentation.sortOption,
                browserViewMode = presentation.viewMode,
                browserListZoom = presentation.listZoom,
                browserGridMinCellSize = presentation.gridMinCellSize,
                browserShowThumbnails = presentation.showThumbnails,
                browserFoldersFirst = presentation.foldersFirst
            ).withUpdatedDisplayState()
        }
    }

    internal inline fun update(transform: (BrowserNavigationState) -> BrowserNavigationState) {
        state.update(transform)
    }

    companion object {
        const val ARCHIVE_VIRTUAL_PREFIX = ArchiveEntryThumbnailData.VIRTUAL_PREFIX
    }
}

internal fun String.normalizeStorageSeparators(): String = replace('\\', '/')
