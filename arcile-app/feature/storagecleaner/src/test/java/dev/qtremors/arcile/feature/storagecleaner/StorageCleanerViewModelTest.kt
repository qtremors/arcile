package dev.qtremors.arcile.feature.storagecleaner

import dev.qtremors.arcile.core.storage.domain.CleanerCandidate
import dev.qtremors.arcile.core.storage.domain.CleanerGroup
import dev.qtremors.arcile.core.storage.domain.CleanerGroupType
import dev.qtremors.arcile.core.storage.domain.CleanerRiskLevel
import dev.qtremors.arcile.core.storage.domain.CleanerRiskReason
import dev.qtremors.arcile.core.storage.domain.CleanerSectionRule
import dev.qtremors.arcile.core.storage.domain.CachedStorageCleanerResult
import dev.qtremors.arcile.core.storage.domain.NoOpStorageCleanerPreferencesStore
import dev.qtremors.arcile.core.storage.domain.StorageCleanerPreferencesStore
import dev.qtremors.arcile.core.storage.domain.StorageCleanerResult
import dev.qtremors.arcile.core.storage.domain.StorageCleanerRules
import dev.qtremors.arcile.core.storage.domain.StorageCleanerScanner
import dev.qtremors.arcile.core.storage.domain.StorageCleanerScanLimits
import dev.qtremors.arcile.core.storage.domain.StorageMutationEvent
import dev.qtremors.arcile.core.storage.domain.StorageMutationNotifier
import dev.qtremors.arcile.core.storage.domain.StorageKind
import dev.qtremors.arcile.core.vault.domain.OnlyFilesVaultFormat
import dev.qtremors.arcile.core.ui.image.ThumbnailCacheService
import dev.qtremors.arcile.core.ui.image.ThumbnailCacheStats
import dev.qtremors.arcile.testutil.FakeStorageRepositoryBundle
import dev.qtremors.arcile.testutil.testVolume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class FakeStorageCleanerScanner : StorageCleanerScanner {
    var result = StorageCleanerResult(groups = emptyList(), scannedFiles = 0, isPartial = false)
    var cachedResult: CachedStorageCleanerResult? = null
    var cachedScanGate: CompletableDeferred<Unit>? = null
    var scanGate: CompletableDeferred<Unit>? = null
    val scannedRules = mutableListOf<StorageCleanerRules>()
    val invalidatedPaths = mutableListOf<List<String>>()
    override suspend fun scan(
        rootPaths: List<String>,
        now: Long,
        limits: StorageCleanerScanLimits,
        rules: StorageCleanerRules
    ): StorageCleanerResult {
        scannedRules += rules
        scanGate?.await()
        return result
    }

    override suspend fun cachedScanForGroups(
        rootPaths: List<String>,
        groupTypes: Set<CleanerGroupType>,
        limits: StorageCleanerScanLimits,
        rules: StorageCleanerRules
    ): CachedStorageCleanerResult? {
        cachedScanGate?.await()
        return cachedResult?.let { cached ->
            cached.copy(result = cached.result.copy(groups = cached.result.groups.filter { it.type in groupTypes }))
        }
    }

    override suspend fun invalidateStorageCleaner(paths: Collection<String>) {
        invalidatedPaths += paths.toList()
    }

    override suspend fun protectedCleanerPaths(paths: Collection<String>): Set<String> =
        OnlyFilesVaultFormat.pathsInsideVault(paths)
}

private class FakeStorageMutationNotifier : StorageMutationNotifier {
    private val _events = MutableSharedFlow<StorageMutationEvent>(extraBufferCapacity = 16)
    override val events = _events
    override fun notify(paths: Collection<String>) {
        _events.tryEmit(StorageMutationEvent(paths.toList()))
    }
}

class FakeStorageCleanerPreferencesStore(
    initialRules: StorageCleanerRules = StorageCleanerRules()
) : StorageCleanerPreferencesStore {
    private val rules = MutableStateFlow(initialRules)
    var ignoreCalls = 0
    override val rulesFlow = rules.asStateFlow()

    override suspend fun updateRules(rules: StorageCleanerRules) {
        this.rules.value = rules.normalized()
    }

    override suspend fun updateSectionRule(type: CleanerGroupType, rule: CleanerSectionRule) {
        rules.value = rules.value.withSection(type, rule)
    }

    override suspend fun ignorePath(path: String) {
        ignoreCalls++
        rules.value = rules.value.withIgnoredPath(path)
    }

    override suspend fun unignorePath(path: String) {
        rules.value = rules.value.withoutIgnoredPath(path)
    }

    override suspend fun resetSection(type: CleanerGroupType) {
        rules.value = rules.value.withSection(type, CleanerSectionRule())
    }
}

private class FakeThumbnailCacheService(
    var currentStats: ThumbnailCacheStats = ThumbnailCacheStats()
) : ThumbnailCacheService {
    var statsCalls = 0
    var clearCalls = 0
    var clearGate: CompletableDeferred<Unit>? = null

    override suspend fun stats(): Result<ThumbnailCacheStats> {
        statsCalls++
        return Result.success(currentStats)
    }

    override suspend fun clear(): Result<ThumbnailCacheStats> {
        clearCalls++
        clearGate?.await()
        currentStats = ThumbnailCacheStats()
        return Result.success(currentStats)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class StorageCleanerViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val fakeScanner = FakeStorageCleanerScanner()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        fakeScanner.result = StorageCleanerResult(groups = emptyList(), scannedFiles = 0, isPartial = false)
        fakeScanner.cachedResult = null
        fakeScanner.cachedScanGate = null
        fakeScanner.scanGate = null
        fakeScanner.scannedRules.clear()
        fakeScanner.invalidatedPaths.clear()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `scan uses indexed volumes and populates groups`() = runTest(dispatcher) {
        val repository = FakeStorageRepositoryBundle(
            volumes = listOf(
                volume("internal", File("internal"), StorageKind.INTERNAL),
                volume("usb", File("usb"), StorageKind.OTG)
            )
        )
        fakeScanner.result = StorageCleanerResult(
            groups = listOf(
                CleanerGroup(
                    type = CleanerGroupType.Apks,
                    candidates = listOf(
                        CleanerCandidate(
                            name = "keep.apk",
                            absolutePath = File("internal/keep.apk").absolutePath,
                            size = 1L,
                            lastModified = 0L,
                            groupTypes = setOf(CleanerGroupType.Apks)
                        )
                    )
                )
            ),
            scannedFiles = 1,
            isPartial = false
        )

        val viewModel = StorageCleanerViewModel(
            repository.volumeRepository,
            repository.trashRepository,
            fakeScanner,
            NoOpStorageCleanerPreferencesStore
        )
        advanceUntilIdle()
        viewModel.scan()
        advanceUntilIdle()

        val apks = viewModel.state.value.group(CleanerGroupType.Apks).candidates
        assertEquals(listOf("keep.apk"), apks.map { it.name })
        assertFalse(viewModel.state.value.isScanning)
    }

    @Test
    fun `pull refresh indicator is limited to manual scans`() = runTest(dispatcher) {
        val repository = FakeStorageRepositoryBundle(
            volumes = listOf(volume("internal", File("internal"), StorageKind.INTERNAL))
        )
        val viewModel = StorageCleanerViewModel(
            repository.volumeRepository,
            repository.trashRepository,
            fakeScanner,
            NoOpStorageCleanerPreferencesStore
        )
        advanceUntilIdle()

        val automaticGate = CompletableDeferred<Unit>()
        fakeScanner.scanGate = automaticGate
        viewModel.scan()
        runCurrent()
        assertTrue(viewModel.state.value.isScanning)
        assertFalse(viewModel.state.value.isPullToRefreshing)
        automaticGate.complete(Unit)
        advanceUntilIdle()

        val pullGate = CompletableDeferred<Unit>()
        fakeScanner.scanGate = pullGate
        viewModel.scan(pullToRefresh = true)
        runCurrent()
        assertTrue(viewModel.state.value.isScanning)
        assertTrue(viewModel.state.value.isPullToRefreshing)
        pullGate.complete(Unit)
        advanceUntilIdle()
        assertFalse(viewModel.state.value.isPullToRefreshing)
    }

    @Test
    fun `manual refresh stays active while cached results are prepared`() = runTest(dispatcher) {
        val repository = FakeStorageRepositoryBundle(
            volumes = listOf(volume("internal", File("internal"), StorageKind.INTERNAL))
        )
        val viewModel = StorageCleanerViewModel(
            repository.volumeRepository,
            repository.trashRepository,
            fakeScanner,
            NoOpStorageCleanerPreferencesStore
        )
        advanceUntilIdle()

        val cacheGate = CompletableDeferred<Unit>()
        fakeScanner.cachedScanGate = cacheGate
        viewModel.scan(pullToRefresh = true)
        runCurrent()

        assertTrue(viewModel.state.value.isPullToRefreshing)
        assertEquals(CleanerGroupType.entries.toSet(), viewModel.state.value.scanningGroups)
        cacheGate.complete(Unit)
        advanceUntilIdle()
        assertFalse(viewModel.state.value.isPullToRefreshing)
    }

    @Test
    fun `opening cleaner loads cached categories without starting a scan`() = runTest(dispatcher) {
        val root = File("internal")
        val cachedPath = File(root, "cached.apk").absolutePath
        fakeScanner.cachedResult = CachedStorageCleanerResult(
            result = StorageCleanerResult(
                groups = listOf(
                    CleanerGroup(
                        CleanerGroupType.Apks,
                        listOf(
                            CleanerCandidate(
                                name = "cached.apk",
                                absolutePath = cachedPath,
                                size = 1L,
                                lastModified = 0L,
                                groupTypes = setOf(CleanerGroupType.Apks)
                            )
                        )
                    )
                ),
                scannedFiles = 1,
                isPartial = false
            ),
            cachedAt = System.currentTimeMillis()
        )
        val repository = FakeStorageRepositoryBundle(
            volumes = listOf(volume("internal", root, StorageKind.INTERNAL))
        )

        val viewModel = StorageCleanerViewModel(
            repository.volumeRepository,
            repository.trashRepository,
            fakeScanner,
            NoOpStorageCleanerPreferencesStore
        )
        advanceUntilIdle()

        assertEquals(listOf("cached.apk"), viewModel.state.value.group(CleanerGroupType.Apks).candidates.map { it.name })
        assertTrue(fakeScanner.scannedRules.isEmpty())
        assertFalse(viewModel.state.value.isScanning)
    }

    @Test
    fun `individual refresh bypasses a fresh category cache`() = runTest(dispatcher) {
        val root = File("internal")
        fakeScanner.cachedResult = CachedStorageCleanerResult(
            result = StorageCleanerResult(
                groups = listOf(CleanerGroup(CleanerGroupType.Junk, emptyList())),
                scannedFiles = 10,
                isPartial = false
            ),
            cachedAt = System.currentTimeMillis()
        )
        val repository = FakeStorageRepositoryBundle(
            volumes = listOf(volume("internal", root, StorageKind.INTERNAL))
        )
        val viewModel = StorageCleanerViewModel(
            repository.volumeRepository,
            repository.trashRepository,
            fakeScanner,
            NoOpStorageCleanerPreferencesStore
        )
        advanceUntilIdle()

        viewModel.scanGroup(CleanerGroupType.Junk)
        advanceUntilIdle()
        assertTrue(fakeScanner.scannedRules.isEmpty())

        viewModel.refreshGroup(CleanerGroupType.Junk)
        advanceUntilIdle()
        assertEquals(1, fakeScanner.scannedRules.size)
    }

    @Test
    fun `clean moves selected files to trash and exposes success`() = runTest(dispatcher) {
        val root = File("internal")
        val apkPath = File(root, "remove.apk").absolutePath
        val repository = FakeStorageRepositoryBundle(volumes = listOf(volume("internal", root, StorageKind.INTERNAL)))
        fakeScanner.result = StorageCleanerResult(
            groups = listOf(
                CleanerGroup(
                    CleanerGroupType.Apks,
                    listOf(
                        CleanerCandidate(
                            name = "remove.apk",
                            absolutePath = apkPath,
                            size = 1L,
                            lastModified = 0L,
                            groupTypes = setOf(CleanerGroupType.Apks)
                        )
                    )
                )
            ),
            scannedFiles = 1,
            isPartial = false
        )

        val viewModel = StorageCleanerViewModel(
            repository.volumeRepository,
            repository.trashRepository,
            fakeScanner,
            NoOpStorageCleanerPreferencesStore
        )
        advanceUntilIdle()
        viewModel.scanGroup(CleanerGroupType.Apks)
        advanceUntilIdle()

        viewModel.clean(listOf(apkPath))
        advanceUntilIdle()

        assertEquals(listOf(listOf(apkPath)), repository.moveToTrashRequests)
        assertNotNull(viewModel.state.value.successMessage)
    }

    @Test
    fun `clean failure preserves candidates and exposes error`() = runTest(dispatcher) {
        val root = File("internal")
        val apkPath = File(root, "remove.apk").absolutePath
        val repository = FakeStorageRepositoryBundle(volumes = listOf(volume("internal", root, StorageKind.INTERNAL))).apply {
            moveToTrashResultProvider = { _, _ -> Result.failure(IllegalStateException("blocked")) }
        }
        fakeScanner.result = StorageCleanerResult(
            groups = listOf(
                CleanerGroup(
                    type = CleanerGroupType.Apks,
                    candidates = listOf(
                        CleanerCandidate(
                            name = "remove.apk",
                            absolutePath = apkPath,
                            size = 1L,
                            lastModified = 0L,
                            groupTypes = setOf(CleanerGroupType.Apks)
                        )
                    )
                )
            ),
            scannedFiles = 1,
            isPartial = false
        )

        val viewModel = StorageCleanerViewModel(
            repository.volumeRepository,
            repository.trashRepository,
            fakeScanner,
            NoOpStorageCleanerPreferencesStore
        )
        advanceUntilIdle()
        viewModel.scanGroup(CleanerGroupType.Apks)
        advanceUntilIdle()

        viewModel.clean(listOf(apkPath))
        advanceUntilIdle()

        assertEquals("blocked", viewModel.state.value.errorMessage)
        assertTrue(viewModel.state.value.group(CleanerGroupType.Apks).candidates.any { it.absolutePath == apkPath })
    }

    @Test
    fun `clean hides candidates before the trash operation completes`() = runTest(dispatcher) {
        val root = File("internal")
        val apkPath = File(root, "remove.apk").absolutePath
        val gate = CompletableDeferred<Unit>()
        val repository = FakeStorageRepositoryBundle(
            volumes = listOf(volume("internal", root, StorageKind.INTERNAL))
        ).apply {
            moveToTrashResultProvider = { _, _ ->
                gate.await()
                Result.success(Unit)
            }
        }
        fakeScanner.result = StorageCleanerResult(
            groups = listOf(
                CleanerGroup(
                    CleanerGroupType.Apks,
                    listOf(
                        CleanerCandidate(
                            name = "remove.apk",
                            absolutePath = apkPath,
                            size = 1L,
                            lastModified = 0L,
                            groupTypes = setOf(CleanerGroupType.Apks)
                        )
                    )
                )
            ),
            scannedFiles = 1,
            isPartial = false
        )
        val viewModel = StorageCleanerViewModel(
            repository.volumeRepository,
            repository.trashRepository,
            fakeScanner,
            NoOpStorageCleanerPreferencesStore
        )
        advanceUntilIdle()
        viewModel.scanGroup(CleanerGroupType.Apks)
        advanceUntilIdle()

        viewModel.clean(listOf(apkPath))
        runCurrent()

        assertTrue(viewModel.state.value.group(CleanerGroupType.Apks).candidates.isEmpty())
        assertTrue(viewModel.state.value.isCleaning)

        gate.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `clean blocks high risk candidates without acknowledgement`() = runTest(dispatcher) {
        val root = File("internal")
        val logPath = File(root, "Android/data/com.example/cache/debug.log").absolutePath
        val repository = FakeStorageRepositoryBundle(volumes = listOf(volume("internal", root, StorageKind.INTERNAL)))
        fakeScanner.result = StorageCleanerResult(
            groups = listOf(
                CleanerGroup(
                    type = CleanerGroupType.Junk,
                    candidates = listOf(
                        CleanerCandidate(
                            name = "debug.log",
                            absolutePath = logPath,
                            size = 1L,
                            lastModified = 0L,
                            groupTypes = setOf(CleanerGroupType.Junk),
                            riskLevel = CleanerRiskLevel.High,
                            riskReasons = setOf(CleanerRiskReason.SystemOwnedPath)
                        )
                    )
                )
            ),
            scannedFiles = 1,
            isPartial = false
        )
        val viewModel = StorageCleanerViewModel(
            repository.volumeRepository,
            repository.trashRepository,
            fakeScanner,
            NoOpStorageCleanerPreferencesStore
        )
        advanceUntilIdle()
        viewModel.scanGroup(CleanerGroupType.Junk)
        advanceUntilIdle()

        viewModel.clean(listOf(logPath), acknowledgedHighRisk = false)
        advanceUntilIdle()

        assertTrue(repository.moveToTrashRequests.isEmpty())
        assertEquals("Review high-risk files before cleanup.", viewModel.state.value.errorMessage)
    }

    @Test
    fun `clean blocks paths missing from current cleaner results`() = runTest(dispatcher) {
        val root = File("internal")
        val missingPath = File(root, "missing.tmp").absolutePath
        val repository = FakeStorageRepositoryBundle(
            volumes = listOf(volume("internal", root, StorageKind.INTERNAL))
        )
        val viewModel = StorageCleanerViewModel(
            repository.volumeRepository,
            repository.trashRepository,
            fakeScanner,
            NoOpStorageCleanerPreferencesStore
        )
        advanceUntilIdle()

        viewModel.clean(listOf(missingPath), acknowledgedHighRisk = true)
        advanceUntilIdle()

        assertTrue(repository.moveToTrashRequests.isEmpty())
        assertEquals("Refresh cleaner results before cleanup.", viewModel.state.value.errorMessage)
    }

    @Test
    fun `clean allows high risk candidates after acknowledgement`() = runTest(dispatcher) {
        val root = File("internal")
        val logPath = File(root, "Android/data/com.example/cache/debug.log").absolutePath
        val repository = FakeStorageRepositoryBundle(volumes = listOf(volume("internal", root, StorageKind.INTERNAL)))
        fakeScanner.result = StorageCleanerResult(
            groups = listOf(
                CleanerGroup(
                    type = CleanerGroupType.Junk,
                    candidates = listOf(
                        CleanerCandidate(
                            name = "debug.log",
                            absolutePath = logPath,
                            size = 1L,
                            lastModified = 0L,
                            groupTypes = setOf(CleanerGroupType.Junk),
                            riskLevel = CleanerRiskLevel.High,
                            riskReasons = setOf(CleanerRiskReason.SystemOwnedPath)
                        )
                    )
                )
            ),
            scannedFiles = 1,
            isPartial = false
        )
        val viewModel = StorageCleanerViewModel(
            repository.volumeRepository,
            repository.trashRepository,
            fakeScanner,
            NoOpStorageCleanerPreferencesStore
        )
        advanceUntilIdle()
        viewModel.scanGroup(CleanerGroupType.Junk)
        advanceUntilIdle()

        viewModel.clean(listOf(logPath), acknowledgedHighRisk = true)
        advanceUntilIdle()

        assertEquals(listOf(listOf(logPath)), repository.moveToTrashRequests)
    }

    @Test
    fun `scan uses cleaner rules from preferences`() = runTest(dispatcher) {
        val root = File("internal")
        val repository = FakeStorageRepositoryBundle(volumes = listOf(volume("internal", root, StorageKind.INTERNAL)))
        val rules = StorageCleanerRules(
            ignoredPaths = setOf(File(root, "ignored.tmp").absolutePath),
            sections = StorageCleanerRules.defaultSections() + (
                CleanerGroupType.Apks to CleanerSectionRule(enabled = false)
                )
        )

        val viewModel = StorageCleanerViewModel(
            repository.volumeRepository,
            repository.trashRepository,
            fakeScanner,
            FakeStorageCleanerPreferencesStore(rules)
        )
        advanceUntilIdle()
        viewModel.scanGroup(CleanerGroupType.Apks)
        advanceUntilIdle()

        assertEquals(rules.normalized(), fakeScanner.scannedRules.last())
    }

    @Test
    fun `ignore path updates preferences and triggers rescan`() = runTest(dispatcher) {
        val root = File("internal")
        val ignoredPath = File(root, "skip.tmp").absolutePath
        val repository = FakeStorageRepositoryBundle(volumes = listOf(volume("internal", root, StorageKind.INTERNAL)))
        val preferences = FakeStorageCleanerPreferencesStore()
        val viewModel = StorageCleanerViewModel(
            repository.volumeRepository,
            repository.trashRepository,
            fakeScanner,
            preferences
        )
        advanceUntilIdle()
        viewModel.scanGroup(CleanerGroupType.Junk)
        advanceUntilIdle()
        val initialScanCount = fakeScanner.scannedRules.size

        viewModel.ignorePath(ignoredPath)
        advanceUntilIdle()

        assertTrue(ignoredPath in viewModel.state.value.rules.ignoredPaths)
        assertTrue(fakeScanner.scannedRules.size > initialScanCount)
        assertTrue(ignoredPath in fakeScanner.scannedRules.last().ignoredPaths)
    }

    @Test
    fun `ignore path hides the item immediately and ignores repeated taps`() = runTest(dispatcher) {
        val root = File("internal")
        val ignoredPath = File(root, "skip.tmp").absolutePath
        val candidate = CleanerCandidate(
            name = "skip.tmp",
            absolutePath = ignoredPath,
            size = 12L,
            lastModified = 1L,
            groupTypes = setOf(CleanerGroupType.Junk),
            riskLevel = CleanerRiskLevel.Low,
            riskReasons = emptySet()
        )
        fakeScanner.result = StorageCleanerResult(
            groups = listOf(CleanerGroup(CleanerGroupType.Junk, listOf(candidate))),
            scannedFiles = 1,
            isPartial = false
        )
        val repository = FakeStorageRepositoryBundle(
            volumes = listOf(volume("internal", root, StorageKind.INTERNAL))
        )
        val preferences = FakeStorageCleanerPreferencesStore()
        val viewModel = StorageCleanerViewModel(
            repository.volumeRepository,
            repository.trashRepository,
            fakeScanner,
            preferences
        )
        advanceUntilIdle()
        viewModel.scanGroup(CleanerGroupType.Junk)
        advanceUntilIdle()

        viewModel.ignorePath(ignoredPath)
        viewModel.ignorePath(ignoredPath)

        assertTrue(viewModel.state.value.group(CleanerGroupType.Junk).candidates.isEmpty())
        advanceUntilIdle()
        assertEquals(1, preferences.ignoreCalls)
    }

    @Test
    fun `storage mutation invalidates cleaner snapshot and rescans`() = runTest(dispatcher) {
        val root = File("internal")
        val changedPath = File(root, "Download/new.apk").absolutePath
        val repository = FakeStorageRepositoryBundle(volumes = listOf(volume("internal", root, StorageKind.INTERNAL)))
        val notifier = FakeStorageMutationNotifier()
        val viewModel = StorageCleanerViewModel(
            repository.volumeRepository,
            repository.trashRepository,
            fakeScanner,
            NoOpStorageCleanerPreferencesStore,
            notifier
        )
        advanceUntilIdle()
        viewModel.scanGroup(CleanerGroupType.Apks)
        advanceUntilIdle()
        val initialScanCount = fakeScanner.scannedRules.size

        notifier.notify(listOf(changedPath))
        advanceTimeBy(300)
        advanceUntilIdle()

        assertEquals(listOf(changedPath), fakeScanner.invalidatedPaths.last())
        assertTrue(fakeScanner.scannedRules.size > initialScanCount)
    }

    @Test
    fun `thumbnail cache state is loaded and duplicate clear requests are ignored`() = runTest(dispatcher) {
        val repository = FakeStorageRepositoryBundle(
            volumes = listOf(volume("internal", File("internal"), StorageKind.INTERNAL))
        )
        val cache = FakeThumbnailCacheService(
            ThumbnailCacheStats(diskBytes = 512L, memoryBytes = 256L, loadedCount = 3, failedCount = 1)
        )
        val viewModel = StorageCleanerViewModel(
            repository.volumeRepository,
            repository.trashRepository,
            fakeScanner,
            NoOpStorageCleanerPreferencesStore,
            thumbnailCacheService = cache
        )
        advanceUntilIdle()

        assertEquals(512L, viewModel.state.value.thumbnailCache.stats.diskBytes)
        assertEquals(256L, viewModel.state.value.thumbnailCache.stats.memoryBytes)
        assertEquals(768L, viewModel.state.value.thumbnailCache.stats.totalBytes)
        assertFalse(viewModel.state.value.thumbnailCache.isLoading)

        cache.currentStats = ThumbnailCacheStats(memoryBytes = 1_024L, loadedCount = 4)
        viewModel.refreshThumbnailCache()
        advanceUntilIdle()

        assertEquals(1_024L, viewModel.state.value.thumbnailCache.stats.totalBytes)
        assertEquals(4, viewModel.state.value.thumbnailCache.stats.loadedCount)

        cache.clearGate = CompletableDeferred()
        viewModel.clearThumbnailCache()
        viewModel.clearThumbnailCache()
        runCurrent()

        assertEquals(1, cache.clearCalls)
        assertTrue(viewModel.state.value.thumbnailCache.isClearing)

        cache.clearGate?.complete(Unit)
        advanceUntilIdle()

        assertEquals(0L, viewModel.state.value.thumbnailCache.stats.totalBytes)
        assertFalse(viewModel.state.value.thumbnailCache.isClearing)
    }

    @Test
    fun `clean rejects paths inside onlyfiles vault and removes stale candidates`() = runTest(dispatcher) {
        val root = File("build/tmp/test-vault-cleaner").apply { mkdirs() }
        val vaultDir = File(root, "MyVault").apply { mkdirs() }
        File(vaultDir, "vault.onlyfiles").createNewFile()
        val secretFile = File(vaultDir, "secret.tmp").apply { createNewFile() }
        val candidate = CleanerCandidate(
            name = "secret.tmp",
            absolutePath = secretFile.absolutePath,
            size = 12L,
            lastModified = 1L,
            groupTypes = setOf(CleanerGroupType.Junk),
            riskLevel = CleanerRiskLevel.Low,
            riskReasons = emptySet()
        )
        fakeScanner.result = StorageCleanerResult(
            groups = listOf(CleanerGroup(CleanerGroupType.Junk, listOf(candidate))),
            scannedFiles = 1,
            isPartial = false
        )
        val repository = FakeStorageRepositoryBundle(volumes = listOf(volume("internal", root, StorageKind.INTERNAL)))
        val viewModel = StorageCleanerViewModel(
            repository.volumeRepository,
            repository.trashRepository,
            fakeScanner,
            NoOpStorageCleanerPreferencesStore
        )
        advanceUntilIdle()
        viewModel.scanGroup(CleanerGroupType.Junk)
        advanceUntilIdle()

        viewModel.clean(listOf(secretFile.absolutePath))
        advanceUntilIdle()

        assertEquals("Protected vault contents cannot be cleaned.", viewModel.state.value.errorMessage)
        assertFalse(viewModel.state.value.isCleaning)
        assertTrue(viewModel.state.value.group(CleanerGroupType.Junk).candidates.isEmpty())
        assertTrue(repository.trashRepository.getTrashFiles().getOrNull().isNullOrEmpty())
        root.deleteRecursively()
    }

    @Test
    fun `clean rejects a mixed vault selection without hiding safe candidates`() = runTest(dispatcher) {
        val root = File("build/tmp/test-mixed-vault-cleaner").apply { mkdirs() }
        val vaultDir = File(root, "MyVault").apply { mkdirs() }
        File(vaultDir, "vault.onlyfiles").createNewFile()
        val secretFile = File(vaultDir, "secret.tmp").apply { createNewFile() }
        val safeFile = File(root, "safe.tmp").apply { createNewFile() }
        val secretCandidate = CleanerCandidate(
            name = secretFile.name,
            absolutePath = secretFile.absolutePath,
            size = 0L,
            lastModified = 1L,
            groupTypes = setOf(CleanerGroupType.Junk),
            riskLevel = CleanerRiskLevel.Low,
            riskReasons = emptySet()
        )
        val safeCandidate = secretCandidate.copy(
            name = safeFile.name,
            absolutePath = safeFile.absolutePath
        )
        fakeScanner.result = StorageCleanerResult(
            groups = listOf(CleanerGroup(CleanerGroupType.Junk, listOf(secretCandidate, safeCandidate))),
            scannedFiles = 2,
            isPartial = false
        )
        val repository = FakeStorageRepositoryBundle(volumes = listOf(volume("internal", root, StorageKind.INTERNAL)))
        val viewModel = StorageCleanerViewModel(
            repository.volumeRepository,
            repository.trashRepository,
            fakeScanner,
            NoOpStorageCleanerPreferencesStore
        )
        advanceUntilIdle()
        viewModel.scanGroup(CleanerGroupType.Junk)
        advanceUntilIdle()

        viewModel.clean(listOf(secretFile.absolutePath, safeFile.absolutePath))
        advanceUntilIdle()

        val remaining = viewModel.state.value.group(CleanerGroupType.Junk).candidates
        assertEquals(listOf(safeFile.absolutePath), remaining.map { it.absolutePath })
        assertTrue(safeFile.exists())
        assertTrue(repository.trashRepository.getTrashFiles().getOrNull().isNullOrEmpty())
        root.deleteRecursively()
    }

    private fun volume(id: String, root: File, kind: StorageKind) = testVolume(
        id = id,
        storageKey = id,
        name = id,
        path = root.absolutePath,
        totalBytes = 100L,
        freeBytes = 50L,
        isPrimary = kind == StorageKind.INTERNAL,
        isRemovable = kind != StorageKind.INTERNAL,
        kind = kind
    )
}
