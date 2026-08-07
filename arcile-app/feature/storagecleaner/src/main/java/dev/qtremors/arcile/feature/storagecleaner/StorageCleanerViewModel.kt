package dev.qtremors.arcile.feature.storagecleaner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.qtremors.arcile.core.storage.domain.CleanerCandidate
import dev.qtremors.arcile.core.storage.domain.CleanerGroup
import dev.qtremors.arcile.core.storage.domain.CleanerGroupType
import dev.qtremors.arcile.core.storage.domain.CleanerRiskLevel
import dev.qtremors.arcile.core.storage.domain.CleanerSectionRule
import dev.qtremors.arcile.core.storage.domain.NoOpStorageMutationNotifier
import dev.qtremors.arcile.core.storage.domain.StorageCleanerPreferencesStore
import dev.qtremors.arcile.core.storage.domain.StorageCleanerRules
import dev.qtremors.arcile.core.storage.domain.StorageCleanerScanner
import dev.qtremors.arcile.core.storage.domain.StorageCleanerScanProgress
import dev.qtremors.arcile.core.storage.domain.StorageCleanerScanPhase
import dev.qtremors.arcile.core.storage.domain.StorageMutationNotifier
import dev.qtremors.arcile.core.storage.domain.TrashRepository
import dev.qtremors.arcile.core.storage.domain.VolumeRepository
import dev.qtremors.arcile.core.storage.domain.isIndexed
import dev.qtremors.arcile.core.storage.domain.onFailure
import dev.qtremors.arcile.core.storage.domain.onSuccess
import dev.qtremors.arcile.core.ui.image.NoOpThumbnailCacheService
import dev.qtremors.arcile.core.ui.image.ThumbnailCacheService
import dev.qtremors.arcile.core.ui.image.ThumbnailCacheStats
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class StorageCleanerState(
    val groups: List<CleanerGroup> = CleanerGroupType.entries.map { CleanerGroup(it, emptyList()) },
    val isScanning: Boolean = false,
    val scanningGroups: Set<CleanerGroupType> = emptySet(),
    val loadedGroups: Set<CleanerGroupType> = emptySet(),
    val scanProgress: StorageCleanerScanProgress? = null,
    val isCleaning: Boolean = false,
    val scannedFiles: Int = 0,
    val isPartial: Boolean = false,
    val rules: StorageCleanerRules = StorageCleanerRules(),
    val errorMessage: String? = null,
    val successMessage: CleanerSuccessMessage? = null,
    val thumbnailCache: CleanerThumbnailCacheState = CleanerThumbnailCacheState()
) {
    val totalBytes: Long get() = groups.sumOf { it.totalBytes }

    fun group(type: CleanerGroupType): CleanerGroup =
        groups.firstOrNull { it.type == type } ?: CleanerGroup(type, emptyList())

    fun candidatesFor(paths: Set<String>): List<CleanerCandidate> =
        groups.flatMap { it.candidates }.distinctBy { it.absolutePath }.filter { it.absolutePath in paths }
}

internal data class CleanerThumbnailCacheState(
    val stats: ThumbnailCacheStats = ThumbnailCacheStats(),
    val isLoading: Boolean = true,
    val isClearing: Boolean = false
)

internal data class CleanerSuccessMessage(
    val cleanedCount: Int,
    val undoTrashIds: List<String> = emptyList()
)

@HiltViewModel
internal class StorageCleanerViewModel @Inject constructor(
    private val volumeRepository: VolumeRepository,
    private val trashRepository: TrashRepository,
    private val scanner: StorageCleanerScanner,
    private val preferencesStore: StorageCleanerPreferencesStore,
    private val storageMutationNotifier: StorageMutationNotifier = NoOpStorageMutationNotifier,
    private val thumbnailCacheService: ThumbnailCacheService = NoOpThumbnailCacheService
) : ViewModel() {

    private val _state = MutableStateFlow(StorageCleanerState())
    val state: StateFlow<StorageCleanerState> = _state.asStateFlow()

    private var scanJob: Job? = null
    private var scanGeneration = 0L
    private var currentScanGroups: Set<CleanerGroupType> = emptySet()
    private var lastOpenedGroup: CleanerGroupType? = null
    private var lastCleanedGroups: Set<CleanerGroupType> = emptySet()
    private var pendingRuleRescanGroups: Set<CleanerGroupType> = emptySet()
    private val pendingIgnoredPaths = mutableSetOf<String>()

    init {
        refreshThumbnailCache()
        viewModelScope.launch {
            var firstEmission = true
            preferencesStore.rulesFlow.collectLatest { rules ->
                _state.update { it.copy(rules = rules) }
                if (firstEmission) {
                    firstEmission = false
                    loadCachedGroups(rules)
                } else {
                    scanner.invalidateStorageCleaner()
                    val groupsToRefresh = pendingRuleRescanGroups.ifEmpty {
                        lastOpenedGroup?.let(::setOf).orEmpty()
                    }
                    pendingRuleRescanGroups = emptySet()
                    if (groupsToRefresh.isNotEmpty()) {
                        startScan(groupsToRefresh, force = true, clearMessages = true)
                    } else {
                        scanGeneration++
                        _state.update { current ->
                            current.copy(groups = emptyGroups(), loadedGroups = emptySet())
                        }
                    }
                }
            }
        }
        viewModelScope.launch {
            @OptIn(kotlinx.coroutines.FlowPreview::class)
            storageMutationNotifier.events
                .debounce(300L)
                .collectLatest { event ->
                    val groupsBeingViewed = currentScanGroups.ifEmpty { lastOpenedGroup?.let(::setOf).orEmpty() }
                    scanGeneration++
                    scanner.invalidateStorageCleaner(event.paths)
                    _state.update { current ->
                        if (event.paths.isEmpty()) {
                            current.copy(
                                groups = emptyGroups(),
                                loadedGroups = emptySet(),
                                scanProgress = null
                            )
                        } else {
                            current.copy(groups = current.groups.withoutPaths(event.paths.toSet()))
                        }
                    }
                    if (groupsBeingViewed.isNotEmpty()) {
                        startScan(groupsBeingViewed, force = true, clearMessages = false)
                    }
                }
        }
    }

    fun refreshThumbnailCache() {
        viewModelScope.launch {
            _state.update { current ->
                current.copy(thumbnailCache = current.thumbnailCache.copy(isLoading = true))
            }
            thumbnailCacheService.stats().fold(
                onSuccess = { stats ->
                    _state.update { current ->
                        current.copy(thumbnailCache = current.thumbnailCache.copy(stats = stats, isLoading = false))
                    }
                },
                onFailure = { error ->
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    _state.update { current ->
                        current.copy(
                            thumbnailCache = current.thumbnailCache.copy(isLoading = false),
                            errorMessage = error.message.orEmpty()
                        )
                    }
                }
            )
        }
    }

    fun clearThumbnailCache() {
        if (_state.value.thumbnailCache.isLoading || _state.value.thumbnailCache.isClearing) return
        _state.update { current ->
            current.copy(thumbnailCache = current.thumbnailCache.copy(isClearing = true))
        }
        viewModelScope.launch {
            thumbnailCacheService.clear().fold(
                onSuccess = { stats ->
                    _state.update { current ->
                        current.copy(thumbnailCache = current.thumbnailCache.copy(stats = stats, isClearing = false))
                    }
                },
                onFailure = { error ->
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    _state.update { current ->
                        current.copy(
                            thumbnailCache = current.thumbnailCache.copy(isClearing = false),
                            errorMessage = error.message.orEmpty()
                        )
                    }
                }
            )
        }
    }

    fun scan() {
        lastOpenedGroup = null
        startScan(CleanerGroupType.entries.toSet(), force = true, clearMessages = true)
    }

    fun scanGroup(type: CleanerGroupType) {
        lastOpenedGroup = type
        startScan(setOf(type), force = false, clearMessages = true)
    }

    fun refreshGroup(type: CleanerGroupType) {
        lastOpenedGroup = type
        startScan(setOf(type), force = true, clearMessages = true)
    }

    private suspend fun loadCachedGroups(
        rules: StorageCleanerRules,
        expectedGeneration: Long = scanGeneration
    ) {
        val indexedPaths = indexedPaths() ?: return
        if (indexedPaths.isEmpty()) return
        val cachedGroups = coroutineScope {
            CleanerGroupType.entries.map { type ->
                async { type to scanner.cachedScanForGroups(indexedPaths, setOf(type), rules = rules) }
            }.awaitAll()
        }
        if (expectedGeneration != scanGeneration) return
        _state.update { current ->
            cachedGroups.fold(current) { state, (type, cached) ->
                if (cached == null) state else state.copy(
                    groups = state.groups.merge(cached.result.groups),
                    loadedGroups = state.loadedGroups + type,
                    scannedFiles = maxOf(state.scannedFiles, cached.result.scannedFiles),
                    isPartial = state.isPartial || cached.result.isPartial
                )
            }
        }
    }

    private fun startScan(
        groups: Set<CleanerGroupType>,
        force: Boolean,
        clearMessages: Boolean
    ) {
        if (groups.isEmpty()) return
        scanJob?.cancel()
        _state.update { current ->
            current.copy(
                isScanning = false,
                scanningGroups = emptySet(),
                scanProgress = null
            )
        }
        val generation = ++scanGeneration
        currentScanGroups = groups
        scanJob = viewModelScope.launch {
            val indexedPaths = indexedPaths() ?: run {
                currentScanGroups = emptySet()
                return@launch
            }
            if (indexedPaths.isEmpty()) {
                _state.update { current ->
                    current.copy(
                        groups = current.groups.merge(groups.map { CleanerGroup(it, emptyList()) }),
                        isScanning = false,
                        scanningGroups = emptySet(),
                        loadedGroups = current.loadedGroups + groups,
                        scanProgress = null
                    )
                }
                currentScanGroups = emptySet()
                return@launch
            }

            val rules = _state.value.rules
            val cached = scanner.cachedScanForGroups(indexedPaths, groups, rules = rules)
            if (cached != null) {
                _state.update { current ->
                    current.copy(
                        groups = current.groups.merge(cached.result.groups),
                        loadedGroups = current.loadedGroups + groups,
                        scannedFiles = cached.result.scannedFiles,
                        isPartial = cached.result.isPartial,
                        errorMessage = null
                    )
                }
                val isFresh = cached.cachedAt > 0L &&
                    System.currentTimeMillis() - cached.cachedAt <= CACHE_FRESHNESS_MILLIS
                if (!force && isFresh) {
                    currentScanGroups = emptySet()
                    return@launch
                }
            }

            _state.update { current ->
                current.copy(
                    isScanning = true,
                    scanningGroups = groups,
                    scanProgress = StorageCleanerScanProgress(),
                    errorMessage = null,
                    successMessage = if (clearMessages) null else current.successMessage
                )
            }
            val scanResult = runCatching {
                scanner.scanGroupUpdates(indexedPaths, groups, rules = rules).collect { update ->
                    _state.update { current ->
                        val result = update.result
                        val isComplete = update.progress.phase == StorageCleanerScanPhase.Complete
                        current.copy(
                            groups = result?.let { current.groups.merge(it.groups) } ?: current.groups,
                            scannedFiles = update.progress.scannedFiles,
                            isPartial = result?.isPartial ?: current.isPartial,
                            isScanning = !isComplete,
                            scanningGroups = if (isComplete) emptySet() else groups,
                            loadedGroups = if (isComplete) current.loadedGroups + groups else current.loadedGroups,
                            scanProgress = update.progress,
                            errorMessage = null
                        )
                    }
                }
            }.onFailure { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
                _state.update { current ->
                    current.copy(
                        isScanning = false,
                        scanningGroups = emptySet(),
                        scanProgress = null,
                        errorMessage = error.message.orEmpty()
                    )
                }
            }
            if (scanResult.isSuccess) loadCachedGroups(rules, expectedGeneration = generation)
            currentScanGroups = emptySet()
        }
    }

    private suspend fun indexedPaths(): List<String>? {
        val volumes = volumeRepository.getStorageVolumes().getOrElse { error ->
            _state.update { current ->
                current.copy(
                    isScanning = false,
                    scanningGroups = emptySet(),
                    scanProgress = null,
                    errorMessage = error.message.orEmpty()
                )
            }
            return null
        }
        return volumes.filter { it.kind.isIndexed }.map { it.path }.distinct()
    }

    fun clean(paths: List<String>, acknowledgedHighRisk: Boolean = false) {
        val uniquePaths = paths.distinct()
        if (uniquePaths.isEmpty()) return
        val selectedCandidates = _state.value.candidatesFor(uniquePaths.toSet())
        if (selectedCandidates.any { it.riskLevel == CleanerRiskLevel.High } && !acknowledgedHighRisk) {
            _state.update { it.copy(errorMessage = "Review high-risk files before cleanup.") }
            return
        }
        val groupsBeforeCleanup = _state.value.groups
        val groupsAfterCleanup = groupsBeforeCleanup.withoutPaths(uniquePaths.toSet())
        val remainingPathsByGroup = groupsAfterCleanup.associate { group ->
            group.type to group.candidates.mapTo(hashSetOf()) { it.absolutePath }
        }
        val removedByGroup = groupsBeforeCleanup.associate { group ->
            group.type to group.candidates.filterNot {
                it.absolutePath in remainingPathsByGroup[group.type].orEmpty()
            }
        }.filterValues { it.isNotEmpty() }
        lastCleanedGroups = removedByGroup.keys
        _state.update { current ->
            current.copy(
                isCleaning = true,
                groups = groupsAfterCleanup,
                errorMessage = null,
                successMessage = null
            )
        }
        viewModelScope.launch {
            trashRepository.moveToTrash(uniquePaths)
                .onSuccess {
                    scanner.invalidateStorageCleaner(uniquePaths)
                    val undoIds = trashRepository.getTrashFiles().getOrNull()
                        ?.filter { it.originalPath in uniquePaths }
                        ?.sortedByDescending { it.deletionTime }
                        ?.map { it.id }
                        ?.take(uniquePaths.size)
                        .orEmpty()
                    _state.update { current ->
                        current.copy(
                            isCleaning = false,
                            successMessage = CleanerSuccessMessage(uniquePaths.size, undoIds)
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { current ->
                        current.copy(
                            isCleaning = false,
                            groups = current.groups.restore(removedByGroup),
                            errorMessage = error.message.orEmpty()
                        )
                    }
                }
        }
    }

    fun undoClean(trashIds: List<String>) {
        if (trashIds.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(isCleaning = true, errorMessage = null, successMessage = null) }
            trashRepository.restoreFromTrash(trashIds)
                .onSuccess {
                    _state.update { it.copy(isCleaning = false) }
                    scanner.invalidateStorageCleaner()
                    if (lastCleanedGroups.isNotEmpty()) {
                        startScan(lastCleanedGroups, force = true, clearMessages = false)
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(isCleaning = false, errorMessage = error.message.orEmpty()) }
                }
        }
    }

    fun clearMessages() {
        _state.update { it.copy(errorMessage = null, successMessage = null) }
    }

    fun updateSectionRule(type: CleanerGroupType, rule: CleanerSectionRule) {
        pendingRuleRescanGroups = setOf(type)
        viewModelScope.launch { preferencesStore.updateSectionRule(type, rule) }
    }

    fun resetSectionRule(type: CleanerGroupType) {
        pendingRuleRescanGroups = setOf(type)
        viewModelScope.launch { preferencesStore.resetSection(type) }
    }

    fun ignorePath(path: String) {
        if (path.isBlank() || !pendingIgnoredPaths.add(path)) return
        pendingRuleRescanGroups = _state.value.groups
            .filter { group -> group.candidates.any { it.absolutePath == path } }
            .mapTo(linkedSetOf()) { it.type }
        _state.update { current ->
            current.copy(
                groups = current.groups.withoutPaths(setOf(path)),
                rules = current.rules.withIgnoredPath(path)
            )
        }
        viewModelScope.launch {
            runCatching { preferencesStore.ignorePath(path) }
                .onFailure { error ->
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    _state.update { it.copy(errorMessage = error.message.orEmpty()) }
                }
            pendingIgnoredPaths.remove(path)
        }
    }

    fun unignorePath(path: String) {
        if (path.isBlank()) return
        pendingRuleRescanGroups = lastOpenedGroup?.let(::setOf).orEmpty()
        viewModelScope.launch { preferencesStore.unignorePath(path) }
    }

    private companion object {
        const val CACHE_FRESHNESS_MILLIS = 10L * 60L * 1000L
    }
}

private fun emptyGroups(): List<CleanerGroup> =
    CleanerGroupType.entries.map { CleanerGroup(it, emptyList()) }

private fun List<CleanerGroup>.merge(updates: List<CleanerGroup>): List<CleanerGroup> {
    val updatesByType = updates.associateBy { it.type }
    return CleanerGroupType.entries.map { type -> updatesByType[type] ?: firstOrNull { it.type == type } ?: CleanerGroup(type, emptyList()) }
}

private fun List<CleanerGroup>.withoutPaths(paths: Set<String>): List<CleanerGroup> = map { group ->
    val remainingCandidates = group.candidates.filterNot { it.absolutePath in paths }
    group.copy(
        candidates = if (group.type == CleanerGroupType.Duplicates) {
            remainingCandidates.groupBy { it.duplicateGroupKey ?: it.absolutePath }
                .values
                .filter { it.size > 1 }
                .flatten()
        } else {
            remainingCandidates
        }
    )
}

private fun List<CleanerGroup>.restore(
    removedByGroup: Map<CleanerGroupType, List<CleanerCandidate>>
): List<CleanerGroup> = map { group ->
    val restored = removedByGroup[group.type].orEmpty()
    if (restored.isEmpty()) group else group.copy(
        candidates = (group.candidates + restored)
            .distinctBy { it.absolutePath }
            .sortedWith(compareByDescending<CleanerCandidate> { it.size }.thenBy { it.name.lowercase() })
    )
}
