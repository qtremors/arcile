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
import dev.qtremors.arcile.core.storage.domain.StorageNodeCapabilities
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
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
    val scannedPaths = mutableListOf<List<String>>()
    val scannedNodes = mutableListOf<List<StorageNodeRef>>()
    val cachedNodeRequests = mutableListOf<List<StorageNodeRef>>()
    val invalidatedPaths = mutableListOf<List<String>>()
    val invalidatedNodes = mutableListOf<List<StorageNodeRef>>()
    override suspend fun scan(
        rootPaths: List<String>,
        now: Long,
        limits: StorageCleanerScanLimits,
        rules: StorageCleanerRules
    ): StorageCleanerResult {
        scannedPaths += rootPaths
        scannedRules += rules
        scanGate?.await()
        return result
    }

    override suspend fun scanNodes(
        roots: List<StorageNodeRef>,
        now: Long,
        limits: StorageCleanerScanLimits,
        rules: StorageCleanerRules
    ): StorageCleanerResult {
        scannedNodes += roots
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

    override suspend fun cachedNodeScanForGroups(
        roots: List<StorageNodeRef>,
        groupTypes: Set<CleanerGroupType>,
        limits: StorageCleanerScanLimits,
        rules: StorageCleanerRules
    ): CachedStorageCleanerResult? {
        cachedNodeRequests += roots
        cachedScanGate?.await()
        return cachedResult?.let { cached ->
            cached.copy(result = cached.result.copy(groups = cached.result.groups.filter { it.type in groupTypes }))
        }
    }

    override suspend fun invalidateStorageCleaner(paths: Collection<String>) {
        invalidatedPaths += paths.toList()
    }

    override suspend fun invalidateStorageCleanerNodes(nodes: Collection<StorageNodeRef>) {
        invalidatedNodes += nodes.toList()
    }
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
        fakeScanner.scannedPaths.clear()
        fakeScanner.scannedNodes.clear()
        fakeScanner.cachedNodeRequests.clear()
        fakeScanner.invalidatedPaths.clear()
        fakeScanner.invalidatedNodes.clear()
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
    fun `explicit root scope uses backend aware cache and scan requests`() = runTest(dispatcher) {
        val root = protectedRoot(
            backendId = StorageNodeRef.ROOT_BACKEND_ID,
            remoteIdentity = "root-device:/data/local/tmp"
        )
        val repository = FakeStorageRepositoryBundle()
        val viewModel = StorageCleanerViewModel(
            repository.volumeRepository,
            repository.trashRepository,
            fakeScanner,
            NoOpStorageCleanerPreferencesStore
        )
        advanceUntilIdle()

        viewModel.configureExplicitScope(root)
        advanceUntilIdle()
        viewModel.scanGroup(CleanerGroupType.Junk)
        advanceUntilIdle()

        assertEquals(root, viewModel.state.value.explicitScope)
        assertTrue(fakeScanner.cachedNodeRequests.flatten().any { it == root })
        assertEquals(listOf(root), fakeScanner.scannedNodes.last())
        assertTrue(fakeScanner.scannedPaths.isEmpty())
        assertFalse(viewModel.state.value.isScanning)
    }

    @Test
    fun `switching protected scopes does not reuse the previous backend identity`() = runTest(dispatcher) {
        val rootScope = protectedRoot(
            backendId = StorageNodeRef.ROOT_BACKEND_ID,
            remoteIdentity = "root-device:/data/shared"
        )
        val shizukuScope = protectedRoot(
            backendId = StorageNodeRef.SHIZUKU_BACKEND_ID,
            remoteIdentity = "shizuku-user-0:/data/shared"
        )
        val repository = FakeStorageRepositoryBundle()
        val viewModel = StorageCleanerViewModel(
            repository.volumeRepository,
            repository.trashRepository,
            fakeScanner,
            NoOpStorageCleanerPreferencesStore
        )
        advanceUntilIdle()

        viewModel.configureExplicitScope(rootScope)
        advanceUntilIdle()
        viewModel.scanGroup(CleanerGroupType.Apks)
        advanceUntilIdle()
        viewModel.configureExplicitScope(shizukuScope)
        advanceUntilIdle()
        viewModel.scanGroup(CleanerGroupType.Apks)
        advanceUntilIdle()

        assertEquals(
            listOf(StorageNodeRef.ROOT_BACKEND_ID, StorageNodeRef.SHIZUKU_BACKEND_ID),
            fakeScanner.scannedNodes.takeLast(2).map { it.single().backendId }
        )
        assertEquals(shizukuScope.canonicalIdentity, viewModel.state.value.explicitScope?.canonicalIdentity)
        assertTrue(viewModel.state.value.loadedGroups.contains(CleanerGroupType.Apks))
    }

    @Test
    fun `protected cleanup trashes exact node references and invalidates their snapshot`() = runTest(dispatcher) {
        val root = protectedRoot(
            backendId = StorageNodeRef.ROOT_BACKEND_ID,
            remoteIdentity = "root-device:/data/local/tmp"
        )
        val candidateNode = StorageNodeRef.root(
            displayPath = "/data/local/tmp/stale.apk",
            remoteCanonicalIdentity = "root-device:/data/local/tmp/stale.apk",
            capabilities = StorageNodeCapabilities(canTrash = true)
        )
        fakeScanner.result = cleanerResult(protectedCandidate(candidateNode, CleanerGroupType.Apks))
        val repository = FakeStorageRepositoryBundle()
        val viewModel = StorageCleanerViewModel(
            repository.volumeRepository,
            repository.trashRepository,
            fakeScanner,
            NoOpStorageCleanerPreferencesStore
        )
        advanceUntilIdle()
        viewModel.configureExplicitScope(root)
        advanceUntilIdle()
        viewModel.scanGroup(CleanerGroupType.Apks)
        advanceUntilIdle()

        viewModel.clean(listOf(candidateNode.displayPath.absolutePath))
        advanceUntilIdle()

        assertTrue(repository.moveToTrashRequests.isEmpty())
        assertEquals(listOf(listOf(candidateNode)), repository.trashRepository.moveNodesToTrashRequests)
        assertEquals(listOf(candidateNode), fakeScanner.invalidatedNodes.last())
        assertEquals(1, viewModel.state.value.successMessage?.cleanedCount)
        assertTrue(viewModel.state.value.group(CleanerGroupType.Apks).candidates.isEmpty())
    }

    @Test
    fun `protected cleanup restores candidate when trash capability is unavailable`() = runTest(dispatcher) {
        val root = protectedRoot(
            backendId = StorageNodeRef.SHIZUKU_BACKEND_ID,
            remoteIdentity = "shizuku-user-0:/storage/emulated/0/Android/data"
        )
        val candidateNode = StorageNodeRef.shizuku(
            displayPath = "/storage/emulated/0/Android/data/app/cache.tmp",
            remoteCanonicalIdentity = "shizuku-user-0:/storage/emulated/0/Android/data/app/cache.tmp",
            capabilities = StorageNodeCapabilities(canTrash = false)
        )
        fakeScanner.result = cleanerResult(protectedCandidate(candidateNode, CleanerGroupType.Junk))
        val repository = FakeStorageRepositoryBundle()
        val viewModel = StorageCleanerViewModel(
            repository.volumeRepository,
            repository.trashRepository,
            fakeScanner,
            NoOpStorageCleanerPreferencesStore
        )
        advanceUntilIdle()
        viewModel.configureExplicitScope(root)
        advanceUntilIdle()
        viewModel.scanGroup(CleanerGroupType.Junk)
        advanceUntilIdle()

        viewModel.clean(listOf(candidateNode.displayPath.absolutePath))
        advanceUntilIdle()

        assertTrue(repository.trashRepository.moveNodesToTrashRequests.isEmpty())
        assertTrue(repository.moveToTrashRequests.isEmpty())
        assertEquals(
            "One or more protected items cannot be moved to Trash.",
            viewModel.state.value.errorMessage
        )
        assertEquals(
            listOf(candidateNode.displayPath.absolutePath),
            viewModel.state.value.group(CleanerGroupType.Junk).candidates.map { it.absolutePath }
        )
    }

    @Test
    fun `protected cleanup rejects results that mix node and path identities`() = runTest(dispatcher) {
        val root = protectedRoot(
            backendId = StorageNodeRef.ROOT_BACKEND_ID,
            remoteIdentity = "root-device:/data/local/tmp"
        )
        val node = StorageNodeRef.root(
            displayPath = "/data/local/tmp/first.tmp",
            remoteCanonicalIdentity = "root-device:/data/local/tmp/first.tmp",
            capabilities = StorageNodeCapabilities(canTrash = true)
        )
        val pathOnly = CleanerCandidate(
            name = "second.tmp",
            absolutePath = "/data/local/tmp/second.tmp",
            size = 2L,
            lastModified = 2L,
            groupTypes = setOf(CleanerGroupType.Junk)
        )
        fakeScanner.result = StorageCleanerResult(
            groups = listOf(
                CleanerGroup(
                    CleanerGroupType.Junk,
                    listOf(protectedCandidate(node, CleanerGroupType.Junk), pathOnly)
                )
            ),
            scannedFiles = 2,
            isPartial = false
        )
        val repository = FakeStorageRepositoryBundle()
        val viewModel = StorageCleanerViewModel(
            repository.volumeRepository,
            repository.trashRepository,
            fakeScanner,
            NoOpStorageCleanerPreferencesStore
        )
        advanceUntilIdle()
        viewModel.configureExplicitScope(root)
        advanceUntilIdle()
        viewModel.scanGroup(CleanerGroupType.Junk)
        advanceUntilIdle()

        viewModel.clean(listOf(node.displayPath.absolutePath, pathOnly.absolutePath))
        advanceUntilIdle()

        assertTrue(repository.trashRepository.moveNodesToTrashRequests.isEmpty())
        assertTrue(repository.moveToTrashRequests.isEmpty())
        assertEquals("Refresh cleaner results before cleanup.", viewModel.state.value.errorMessage)
        assertEquals(2, viewModel.state.value.group(CleanerGroupType.Junk).candidates.size)
    }

    @Test
    fun `protected mutation invalidates matching node identity before rescan`() = runTest(dispatcher) {
        val root = protectedRoot(
            backendId = StorageNodeRef.ROOT_BACKEND_ID,
            remoteIdentity = "root-device:/data/local/tmp"
        )
        val candidateNode = StorageNodeRef.root(
            displayPath = "/data/local/tmp/changed.apk",
            remoteCanonicalIdentity = "root-device:/data/local/tmp/changed.apk",
            capabilities = StorageNodeCapabilities(canTrash = true)
        )
        fakeScanner.result = cleanerResult(protectedCandidate(candidateNode, CleanerGroupType.Apks))
        val notifier = FakeStorageMutationNotifier()
        val repository = FakeStorageRepositoryBundle()
        val viewModel = StorageCleanerViewModel(
            repository.volumeRepository,
            repository.trashRepository,
            fakeScanner,
            NoOpStorageCleanerPreferencesStore,
            notifier
        )
        advanceUntilIdle()
        viewModel.configureExplicitScope(root)
        advanceUntilIdle()
        viewModel.scanGroup(CleanerGroupType.Apks)
        advanceUntilIdle()
        val scansBeforeMutation = fakeScanner.scannedNodes.size

        notifier.notify(listOf(candidateNode.displayPath.absolutePath))
        advanceTimeBy(300L)
        advanceUntilIdle()

        assertEquals(listOf(candidateNode), fakeScanner.invalidatedNodes.first())
        assertTrue(fakeScanner.invalidatedPaths.isEmpty())
        assertTrue(fakeScanner.scannedNodes.size > scansBeforeMutation)
    }

    @Test
    fun `protected rule change invalidates the scoped backend instead of local paths`() = runTest(dispatcher) {
        val root = protectedRoot(
            backendId = StorageNodeRef.SHIZUKU_BACKEND_ID,
            remoteIdentity = "shizuku-user-0:/storage/emulated/0/Android/data"
        )
        val repository = FakeStorageRepositoryBundle()
        val preferences = FakeStorageCleanerPreferencesStore()
        val viewModel = StorageCleanerViewModel(
            repository.volumeRepository,
            repository.trashRepository,
            fakeScanner,
            preferences
        )
        advanceUntilIdle()
        viewModel.configureExplicitScope(root)
        advanceUntilIdle()

        viewModel.updateSectionRule(
            CleanerGroupType.Junk,
            CleanerSectionRule(enabled = false)
        )
        advanceUntilIdle()

        assertTrue(fakeScanner.invalidatedNodes.any { it == listOf(root) })
        assertTrue(fakeScanner.invalidatedPaths.isEmpty())
        assertFalse(viewModel.state.value.rules.section(CleanerGroupType.Junk).enabled)
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

    private fun protectedRoot(
        backendId: String,
        remoteIdentity: String
    ): StorageNodeRef = StorageNodeRef.privileged(
        backendId = backendId,
        displayPath = remoteIdentity.substringAfter(':'),
        remoteCanonicalIdentity = remoteIdentity,
        capabilities = StorageNodeCapabilities(canTrash = true)
    )

    private fun protectedCandidate(
        node: StorageNodeRef,
        groupType: CleanerGroupType
    ) = CleanerCandidate(
        name = File(node.displayPath.absolutePath).name,
        absolutePath = node.displayPath.absolutePath,
        size = 1L,
        lastModified = 1L,
        groupTypes = setOf(groupType),
        nodeRef = node
    )

    private fun cleanerResult(candidate: CleanerCandidate) = StorageCleanerResult(
        groups = listOf(
            CleanerGroup(
                type = candidate.groupTypes.single(),
                candidates = listOf(candidate)
            )
        ),
        scannedFiles = 1,
        isPartial = false
    )
}
