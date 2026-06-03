package dev.qtremors.arcile.feature.browser.delegate

import androidx.lifecycle.SavedStateHandle
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.storage.domain.ArchiveEntryModel
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.core.storage.domain.ArchiveRepository
import dev.qtremors.arcile.core.storage.domain.BrowserPreferencesStore
import dev.qtremors.arcile.core.storage.domain.FileBrowserRepository
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.SearchRepository
import dev.qtremors.arcile.core.storage.domain.BrowserPresentationPreferences
import dev.qtremors.arcile.core.storage.domain.FolderStatsCachePolicy
import dev.qtremors.arcile.core.storage.domain.StorageBrowserLocation
import dev.qtremors.arcile.core.storage.domain.StorageScope
import dev.qtremors.arcile.core.ui.UiText
import dev.qtremors.arcile.image.ArchiveEntryThumbnailData
import dev.qtremors.arcile.feature.browser.BrowserNavigationEvent
import dev.qtremors.arcile.feature.browser.ArchivePasswordAction
import dev.qtremors.arcile.feature.browser.BrowserArchiveContext
import dev.qtremors.arcile.feature.browser.BrowserState
import dev.qtremors.arcile.feature.browser.reduce
import dev.qtremors.arcile.feature.browser.withUpdatedDisplayState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.ArrayDeque

class NavigationDelegate(
    private val state: MutableStateFlow<BrowserState>,
    private val viewModelScope: CoroutineScope,
    private val fileBrowserRepository: FileBrowserRepository,
    private val archiveRepository: ArchiveRepository = object : ArchiveRepository {},
    private val searchRepository: SearchRepository,
    private val browserPreferencesRepository: BrowserPreferencesStore,
    private val savedStateHandle: SavedStateHandle,
    private val onClearSearch: () -> Unit
) {
    private val pathHistory = ArrayDeque<BrowserHistoryEntry>()

    fun restoreLocationFromState(): StorageBrowserLocation? {
        val isVolumeRootScreen = savedStateHandle.get<Boolean>("isVolumeRootScreen")
        val restoredPath = savedStateHandle.get<String>("currentPath")
        val restoredVolumeId = savedStateHandle.get<String>("currentVolumeId")
        val restoredIsCategory = savedStateHandle.get<Boolean>("isCategoryScreen")
        val restoredCategoryName = savedStateHandle.get<String>("activeCategoryName")
        val restoredHistory = savedStateHandle.get<Array<String>>("pathHistory")
        val restoredArchivePath = savedStateHandle.get<String>("archivePath")

        if (restoredHistory != null) {
            pathHistory.clear()
            pathHistory.addAll(restoredHistory.mapNotNull { BrowserHistoryEntry.fromSavedValue(it) })
        }

        if (!restoredArchivePath.isNullOrEmpty()) return null

        return when {
            isVolumeRootScreen == true -> StorageBrowserLocation.Roots
            restoredIsCategory == true && !restoredCategoryName.isNullOrEmpty() -> {
                StorageBrowserLocation.Category(StorageScope.Category(restoredVolumeId, restoredCategoryName))
            }
            !restoredPath.isNullOrEmpty() && !restoredVolumeId.isNullOrEmpty() ->
                StorageBrowserLocation.Directory(StorageScope.Path(restoredVolumeId, restoredPath))
            else -> null
        }
    }

    fun initializeFromArgs() {
        val path = savedStateHandle.get<String>("path")?.takeIf { it.isNotEmpty() }
        val archivePath = savedStateHandle.get<String>("archivePath")?.takeIf { it.isNotEmpty() }
        val archiveEntryPrefix = savedStateHandle.get<String>("archiveEntryPrefix")?.takeIf { it.isNotEmpty() }
        val category = savedStateHandle.get<String>("category")?.takeIf { it.isNotEmpty() }
        val volumeId = savedStateHandle.get<String>("volumeId")
        val seedInitialPathHistory = savedStateHandle.get<Boolean>("seedInitialPathHistory") ?: true
        val restorePersistentLocation = savedStateHandle.get<Boolean>("restorePersistentLocation") ?: true

        when {
            archivePath != null -> openArchive(archivePath, archiveEntryPrefix, seedHistory = seedInitialPathHistory)
            path != null -> navigateToSpecificFolder(
                path,
                seedInitialPathHistory = seedInitialPathHistory
            )
            category != null -> navigateToCategory(category, volumeId)
            else -> openFileBrowser(restorePersistentLocation = restorePersistentLocation)
        }
    }

    private fun saveNavState() {
        savedStateHandle["currentPath"] = state.value.currentPath
        savedStateHandle["currentVolumeId"] = state.value.currentVolumeId
        savedStateHandle["isVolumeRootScreen"] = state.value.isVolumeRootScreen
        savedStateHandle["isCategoryScreen"] = state.value.isCategoryScreen
        savedStateHandle["activeCategoryName"] = state.value.activeCategoryName
        savedStateHandle["pathHistory"] = pathHistory.map { it.toSavedValue() }.toTypedArray()
        savedStateHandle["archivePath"] = state.value.archiveContext?.archivePath
        savedStateHandle["archiveEntryPrefix"] = state.value.archiveContext?.entryPrefix
    }

    fun volumeFiles() = state.value.storageVolumes.map { volume ->
        FileModel(
            name = volume.name,
            absolutePath = volume.path,
            size = volume.totalBytes - volume.freeBytes,
            lastModified = 0L,
            isDirectory = true,
            extension = "",
            isHidden = false
        )
    }

    private fun findVolumeForPath(path: String) =
        state.value.storageVolumes
            .sortedByDescending { it.path.length }
            .firstOrNull {
                path == it.path ||
                    path.startsWith(it.path + "/") ||
                    path.startsWith(it.path + java.io.File.separator)
            }

    fun openFileBrowser(restorePersistentLocation: Boolean = false, errorMessage: UiText? = null) {
        viewModelScope.launch {
            if (restorePersistentLocation) {
                val prefs = browserPreferencesRepository.preferencesFlow.first()
                val lastPath = prefs.lastOpenedPath
                val lastVolumeId = prefs.lastOpenedVolumeId

                if (!lastPath.isNullOrEmpty() && !lastVolumeId.isNullOrEmpty()) {
                    val volume = state.value.storageVolumes.firstOrNull { it.id == lastVolumeId }
                    if (volume != null) {
                        loadDirectory(lastPath, lastVolumeId, clearHistory = true, errorMessage = errorMessage)
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
                        persistAsLastOpened = false
                    )
                } else {
                    openVolumeRoots(errorMessage)
                }
            }
        }
    }

    fun openVolumeRoots(errorMessage: UiText? = null) {
        pathHistory.clear()
        viewModelScope.launch {
            val prefs = browserPreferencesRepository.preferencesFlow.first()
            val presentation = prefs.getPresentationForPath("/")
            state.update {
                it.reduce(BrowserNavigationEvent.OpenVolumeRoots(volumeFiles())).copy(
                    error = errorMessage,
                    browserSortOption = presentation.sortOption,
                    browserViewMode = presentation.viewMode,
                    browserListZoom = presentation.listZoom,
                    browserGridMinCellSize = presentation.gridMinCellSize,
                    browserShowThumbnails = presentation.showThumbnails
                ).withUpdatedDisplayState()
            }
            saveNavState()
        }
    }

    fun navigateToSpecificFolder(path: String, seedInitialPathHistory: Boolean = true) {
        if (ArchiveFormat.isSupported(path)) {
            openArchive(path, seedHistory = seedInitialPathHistory)
            return
        }
        val volume = findVolumeForPath(path)
        if (volume == null) {
            openFileBrowser(errorMessage = UiText.StringResource(R.string.error_storage_for_path_unavailable))
            return
        }
        pathHistory.clear()
        if (seedInitialPathHistory && path != volume.path) {
            pathHistory.push(BrowserHistoryEntry.Directory(volume.path))
        }
        loadDirectory(path, volume.id, clearHistory = false)
    }

    fun navigateToCategory(categoryName: String, volumeId: String? = null) {
        pathHistory.clear()
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
            state.value.historyEntry()?.let { pathHistory.push(it) }
        }
        loadDirectory(path, state.value.currentVolumeId, clearHistory = false)
    }

    fun navigateBack(): Boolean {
        if (state.value.browserSearchQuery.isNotEmpty()) {
            onClearSearch()
            return true
        }

        if (state.value.selectedFiles.isNotEmpty()) {
            state.update {
                it.copy(
                    selectedFiles = persistentSetOf(),
                    selectedFilesTotalSize = 0L,
                    isPropertiesVisible = false,
                    isPropertiesLoading = false,
                    properties = null
                )
            }
            return true
        }

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
            val parentPath = File(archive.archivePath).parent?.normalizeStorageSeparators()
            if (!parentPath.isNullOrBlank()) {
                pathHistory.clear()
                loadDirectory(parentPath, state.value.currentVolumeId, clearHistory = false)
                return true
            }
            return false
        }

        if (pathHistory.isNotEmpty()) {
            when (val previous = pathHistory.pop()) {
                is BrowserHistoryEntry.Directory -> {
                    val volume = findVolumeForPath(previous.path)
                    if (volume != null) {
                        loadDirectory(previous.path, volume.id, clearHistory = false)
                        return true
                    }
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

        if (!state.value.isVolumeRootScreen && state.value.storageVolumes.size > 1) {
            openVolumeRoots()
            return true
        }

        return false
    }

    fun refresh(pullToRefresh: Boolean = false) {
        state.update { it.copy(isPullToRefreshing = pullToRefresh) }
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
            state.value.currentPath.isNotEmpty() -> loadDirectory(state.value.currentPath, state.value.currentVolumeId, clearHistory = false)
        }
    }

    fun openArchive(
        archivePath: String,
        entryPrefix: String? = null,
        seedHistory: Boolean = true
    ) {
        val volume = findVolumeForPath(archivePath)
        if (volume == null) {
            openFileBrowser(errorMessage = UiText.StringResource(R.string.error_storage_for_path_unavailable))
            return
        }
        pathHistory.clear()
        val parent = File(archivePath).parent?.normalizeStorageSeparators()
        if (seedHistory && !parent.isNullOrBlank()) {
            pathHistory.push(BrowserHistoryEntry.Directory(parent))
        }
        loadArchiveEntries(
            archivePath = archivePath,
            entryPrefix = entryPrefix,
            password = state.value.archiveContext?.takeIf { it.archivePath == archivePath }?.password,
            nameEncoding = state.value.archiveContext?.takeIf { it.archivePath == archivePath }?.nameEncoding
                ?: dev.qtremors.arcile.core.storage.domain.ArchiveNameEncoding.UTF_8,
            pushHistory = false
        )
    }

    fun submitArchivePassword(password: String) {
        val archive = state.value.archiveContext ?: return
        loadArchiveEntries(
            archivePath = archive.archivePath,
            entryPrefix = archive.entryPrefix,
            password = password,
            nameEncoding = archive.nameEncoding,
            pushHistory = false
        )
    }

    private fun openArchiveFolder(entryPrefix: String) {
        val archive = state.value.archiveContext ?: return
        loadArchiveEntries(
            archivePath = archive.archivePath,
            entryPrefix = entryPrefix,
            password = archive.password,
            nameEncoding = archive.nameEncoding,
            pushHistory = true
        )
    }

    private fun loadDirectory(
        path: String,
        volumeId: String?,
        clearHistory: Boolean,
        errorMessage: UiText? = null,
        persistAsLastOpened: Boolean = true
    ) {
        val resolvedVolumeId = volumeId ?: findVolumeForPath(path)?.id
        if (resolvedVolumeId == null) {
            openFileBrowser(errorMessage = UiText.StringResource(R.string.error_storage_for_path_unavailable))
            return
        }
        if (clearHistory) {
            pathHistory.clear()
        }
        state.update {
            it.reduce(BrowserNavigationEvent.OpenDirectory(path, resolvedVolumeId)).copy(
                isLoading = true,
                error = errorMessage,
            ).withUpdatedDisplayState()
        }
        saveNavState()
        viewModelScope.launch {
            if (persistAsLastOpened) {
                browserPreferencesRepository.updateLastOpenedLocation(path, resolvedVolumeId)
            }
            val prefs = browserPreferencesRepository.preferencesFlow.first()
            applyPresentation(prefs.getPresentationForPath(path))

            fileBrowserRepository.listFilePages(path).collect { page ->
                page.error?.let { error ->
                    state.update {
                        it.copy(
                            isLoading = false,
                            isPullToRefreshing = false,
                            error = error.message?.let(UiText::Dynamic) ?: UiText.StringResource(R.string.error_load_directory_failed)
                        )
                    }
                    return@collect
                }

                val updatedFiles = if (page.pageIndex == 0) {
                    page.files
                } else {
                    state.value.files + page.files
                }
                val folderPaths = page.files.filter { it.isDirectory }.map { it.absolutePath }
                val cachedStats = fileBrowserRepository.getCachedFolderStats(folderPaths)
                val now = System.currentTimeMillis()
                val pathsToQueue = folderPaths.filter { folderPath ->
                    val cached = cachedStats[folderPath] ?: return@filter true
                    val ttl = if (cached.status == dev.qtremors.arcile.core.storage.domain.FolderStatsStatus.Unavailable) {
                        FolderStatsCachePolicy.FAILURE_TTL_MS
                    } else {
                        FolderStatsCachePolicy.FRESH_TTL_MS
                    }
                    now - cached.cachedAt > ttl
                }
                state.update {
                    it.copy(
                        isLoading = false,
                        isPullToRefreshing = if (page.isComplete) false else it.isPullToRefreshing,
                        files = updatedFiles.toPersistentList(),
                        folderStatsByPath = (it.folderStatsByPath + cachedStats).toPersistentMap(),
                        folderStatsLoadingPaths = (it.folderStatsLoadingPaths + pathsToQueue).toPersistentSet()
                    ).withUpdatedDisplayState()
                }
                fileBrowserRepository.queueFolderStats(pathsToQueue)
                if (page.isComplete) saveNavState()
            }
        }
    }

    private fun loadArchiveEntries(
        archivePath: String,
        entryPrefix: String?,
        password: String?,
        nameEncoding: dev.qtremors.arcile.core.storage.domain.ArchiveNameEncoding,
        pushHistory: Boolean
    ) {
        val previous = state.value.archiveContext
        if (pushHistory && previous?.entryPrefix != entryPrefix) {
            pathHistory.push(
                BrowserHistoryEntry.Archive(
                    archivePath = previous?.archivePath ?: archivePath,
                    entryPrefix = previous?.entryPrefix
                )
            )
        }
        val volume = findVolumeForPath(archivePath)
        state.update {
            it.copy(
                archiveContext = BrowserArchiveContext(
                    archivePath = archivePath,
                    entryPrefix = entryPrefix,
                    password = password,
                    nameEncoding = nameEncoding,
                    entries = previous?.takeIf { ctx -> ctx.archivePath == archivePath }?.entries.orEmpty()
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
                selectedFiles = persistentSetOf(),
                selectedFilesTotalSize = 0L,
                isLoading = true,
                error = null
            ).withUpdatedDisplayState()
        }
        saveNavState()
        viewModelScope.launch {
            archiveRepository.listArchiveEntries(archivePath, password, nameEncoding).onSuccess { entries ->
                state.update {
                    it.copy(
                        isLoading = false,
                        isPullToRefreshing = false,
                        archiveContext = BrowserArchiveContext(
                            archivePath = archivePath,
                            entryPrefix = entryPrefix,
                            password = password,
                            nameEncoding = nameEncoding,
                            entries = entries
                        ),
                        files = buildArchiveFiles(archivePath, entries, entryPrefix).toPersistentList()
                    ).withUpdatedDisplayState()
                }
                saveNavState()
            }.onFailure { error ->
                val passwordError = error.isArchivePasswordError()
                state.update {
                    it.copy(
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
                        error = if (passwordError) null else error.message?.let(UiText::Dynamic)
                            ?: UiText.StringResource(R.string.error_unsupported_archive)
                    ).withUpdatedDisplayState()
                }
            }
        }
    }

    private fun buildArchiveFiles(archivePath: String, entries: List<ArchiveEntryModel>, prefix: String?): List<FileModel> {
        val normalizedPrefix = prefix?.trimEnd('/')?.takeIf { it.isNotBlank() }
        val directoryPaths = entries
            .asSequence()
            .flatMap { entry ->
                val path = entry.path.trim('/').takeIf { it.isNotBlank() } ?: return@flatMap emptySequence()
                val explicit = if (entry.isDirectory) sequenceOf(path.trimEnd('/')) else emptySequence()
                val implicit = path.split('/')
                    .dropLast(1)
                    .runningFold("") { parent, segment ->
                        if (parent.isBlank()) segment else "$parent/$segment"
                    }
                    .drop(1)
                    .asSequence()
                explicit + implicit
            }
            .toSet()
        val children = linkedMapOf<String, FileModel>()
        entries.forEach { entry ->
            val path = entry.path.trim('/')
            val remainder = if (normalizedPrefix == null) {
                path
            } else if (path == normalizedPrefix) {
                ""
            } else if (path.startsWith("$normalizedPrefix/")) {
                path.removePrefix("$normalizedPrefix/")
            } else {
                return@forEach
            }
            if (remainder.isBlank()) return@forEach
            val childName = remainder.substringBefore('/')
            val childPath = if (normalizedPrefix == null) childName else "$normalizedPrefix/$childName"
            val isDirectory = remainder.contains('/') || childPath in directoryPaths
            val existing = children[childPath]
            if (existing == null || (!isDirectory && existing.isDirectory)) {
                children[childPath] = FileModel(
                    name = childName,
                    absolutePath = ArchiveEntryThumbnailData.virtualPath(archivePath, childPath),
                    size = if (isDirectory) 0L else entry.size,
                    lastModified = entry.lastModified ?: 0L,
                    isDirectory = isDirectory,
                    extension = if (isDirectory) "" else childName.substringAfterLast('.', missingDelimiterValue = "").lowercase(),
                    isHidden = childName.startsWith(".")
                )
            }
        }
        return children.values.toList()
    }

    private fun loadCategory(categoryName: String, volumeId: String?) {
        state.update {
            it.reduce(BrowserNavigationEvent.OpenCategory(categoryName, volumeId)).copy(
                isLoading = true,
                error = null,
            ).withUpdatedDisplayState()
        }
        saveNavState()
        viewModelScope.launch {
            val prefs = browserPreferencesRepository.preferencesFlow.first()
            val categoryPresentation = prefs.getPresentationForCategory(categoryName)

            val scope = StorageScope.Category(volumeId?.takeIf { it.isNotEmpty() }, categoryName)
            searchRepository.getFilesByCategory(scope, categoryName).onSuccess { files ->
                state.update {
                    it.copy(
                        isLoading = false,
                        isPullToRefreshing = false,
                        files = files.toPersistentList(),
                        browserSortOption = categoryPresentation.sortOption,
                        browserViewMode = categoryPresentation.viewMode,
                        browserListZoom = categoryPresentation.listZoom,
                        browserGridMinCellSize = categoryPresentation.gridMinCellSize,
                        browserShowThumbnails = categoryPresentation.showThumbnails
                    ).withUpdatedDisplayState()
                }
                saveNavState()
            }.onFailure { error ->
                state.update {
                    it.copy(
                        isLoading = false,
                        isPullToRefreshing = false,
                        error = error.message?.let(UiText::Dynamic) ?: UiText.StringResource(R.string.error_load_category_failed)
                    )
                }
            }
        }
    }

    private fun applyPresentation(presentation: BrowserPresentationPreferences) {
        state.update {
            it.copy(
                browserSortOption = presentation.sortOption,
                browserViewMode = presentation.viewMode,
                browserListZoom = presentation.listZoom,
                browserGridMinCellSize = presentation.gridMinCellSize,
                browserShowThumbnails = presentation.showThumbnails
            ).withUpdatedDisplayState()
        }
    }

    private fun Throwable.isArchivePasswordError(): Boolean =
        message.orEmpty().contains("password", ignoreCase = true) ||
            message.orEmpty().contains("encrypted", ignoreCase = true)

    companion object {
        const val ARCHIVE_VIRTUAL_PREFIX = ArchiveEntryThumbnailData.VIRTUAL_PREFIX
    }
}

private sealed interface BrowserHistoryEntry {
    data class Directory(val path: String) : BrowserHistoryEntry
    data class Archive(val archivePath: String, val entryPrefix: String?) : BrowserHistoryEntry

    fun toSavedValue(): String = when (this) {
        is Directory -> "dir:$path"
        is Archive -> "archive:$archivePath|${entryPrefix.orEmpty()}"
    }

    companion object {
        fun fromSavedValue(value: String): BrowserHistoryEntry? = when {
            value.startsWith("dir:") -> Directory(value.removePrefix("dir:"))
            value.startsWith("archive:") -> {
                val payload = value.removePrefix("archive:")
                val archivePath = payload.substringBefore('|').takeIf { it.isNotBlank() } ?: return null
                val entryPrefix = payload.substringAfter('|', "").takeIf { it.isNotBlank() }
                Archive(archivePath, entryPrefix)
            }
            value.startsWith(NavigationDelegate.ARCHIVE_VIRTUAL_PREFIX) -> null
            else -> Directory(value)
        }
    }
}

private fun BrowserState.historyEntry(): BrowserHistoryEntry? =
    archiveContext?.let { BrowserHistoryEntry.Archive(it.archivePath, it.entryPrefix) }
        ?: currentPath.takeIf { it.isNotBlank() && !it.startsWith(NavigationDelegate.ARCHIVE_VIRTUAL_PREFIX) }
            ?.let { BrowserHistoryEntry.Directory(it) }

private fun String.normalizeStorageSeparators(): String = replace('\\', '/')
