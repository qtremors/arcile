package dev.qtremors.arcile.feature.storagecleaner

import dev.qtremors.arcile.core.storage.domain.CleanerCandidate
import dev.qtremors.arcile.core.storage.domain.CleanerGroup
import dev.qtremors.arcile.core.storage.domain.CleanerGroupType
import dev.qtremors.arcile.core.storage.domain.CleanerRiskLevel
import dev.qtremors.arcile.core.storage.domain.CleanerRiskReason
import dev.qtremors.arcile.core.storage.domain.CleanerSectionRule
import dev.qtremors.arcile.core.storage.domain.NoOpStorageCleanerPreferencesStore
import dev.qtremors.arcile.core.storage.domain.StorageCleanerPreferencesStore
import dev.qtremors.arcile.core.storage.domain.StorageCleanerResult
import dev.qtremors.arcile.core.storage.domain.StorageCleanerRules
import dev.qtremors.arcile.core.storage.domain.StorageCleanerScanner
import dev.qtremors.arcile.core.storage.domain.StorageCleanerScanLimits
import dev.qtremors.arcile.core.storage.domain.StorageMutationEvent
import dev.qtremors.arcile.core.storage.domain.StorageMutationNotifier
import dev.qtremors.arcile.core.storage.domain.StorageKind
import dev.qtremors.arcile.testutil.FakeStorageRepositoryBundle
import dev.qtremors.arcile.testutil.testVolume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
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
    val scannedRules = mutableListOf<StorageCleanerRules>()
    val invalidatedPaths = mutableListOf<List<String>>()
    override suspend fun scan(
        rootPaths: List<String>,
        now: Long,
        limits: StorageCleanerScanLimits,
        rules: StorageCleanerRules
    ): StorageCleanerResult {
        scannedRules += rules
        return result
    }

    override suspend fun invalidateStorageCleaner(paths: Collection<String>) {
        invalidatedPaths += paths.toList()
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
    override val rulesFlow = rules.asStateFlow()

    override suspend fun updateRules(rules: StorageCleanerRules) {
        this.rules.value = rules.normalized()
    }

    override suspend fun updateSectionRule(type: CleanerGroupType, rule: CleanerSectionRule) {
        rules.value = rules.value.withSection(type, rule)
    }

    override suspend fun ignorePath(path: String) {
        rules.value = rules.value.withIgnoredPath(path)
    }

    override suspend fun unignorePath(path: String) {
        rules.value = rules.value.withoutIgnoredPath(path)
    }

    override suspend fun resetSection(type: CleanerGroupType) {
        rules.value = rules.value.withSection(type, CleanerSectionRule())
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

        val apks = viewModel.state.value.group(CleanerGroupType.Apks).candidates
        assertEquals(listOf("keep.apk"), apks.map { it.name })
        assertFalse(viewModel.state.value.isScanning)
    }

    @Test
    fun `clean moves selected files to trash and exposes success`() = runTest(dispatcher) {
        val root = File("internal")
        val apkPath = File(root, "remove.apk").absolutePath
        val repository = FakeStorageRepositoryBundle(volumes = listOf(volume("internal", root, StorageKind.INTERNAL)))

        val viewModel = StorageCleanerViewModel(
            repository.volumeRepository,
            repository.trashRepository,
            fakeScanner,
            NoOpStorageCleanerPreferencesStore
        )
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

        viewModel.clean(listOf(apkPath))
        advanceUntilIdle()

        assertEquals("blocked", viewModel.state.value.errorMessage)
        assertTrue(viewModel.state.value.group(CleanerGroupType.Apks).candidates.any { it.absolutePath == apkPath })
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

        viewModel.clean(listOf(logPath), acknowledgedHighRisk = false)
        advanceUntilIdle()

        assertTrue(repository.moveToTrashRequests.isEmpty())
        assertEquals("Review high-risk files before cleanup.", viewModel.state.value.errorMessage)
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

        StorageCleanerViewModel(
            repository.volumeRepository,
            repository.trashRepository,
            fakeScanner,
            FakeStorageCleanerPreferencesStore(rules)
        )
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
        val initialScanCount = fakeScanner.scannedRules.size

        viewModel.ignorePath(ignoredPath)
        advanceUntilIdle()

        assertTrue(ignoredPath in viewModel.state.value.rules.ignoredPaths)
        assertTrue(fakeScanner.scannedRules.size > initialScanCount)
        assertTrue(ignoredPath in fakeScanner.scannedRules.last().ignoredPaths)
    }

    @Test
    fun `storage mutation invalidates cleaner snapshot and rescans`() = runTest(dispatcher) {
        val root = File("internal")
        val changedPath = File(root, "Download/new.apk").absolutePath
        val repository = FakeStorageRepositoryBundle(volumes = listOf(volume("internal", root, StorageKind.INTERNAL)))
        val notifier = FakeStorageMutationNotifier()
        StorageCleanerViewModel(
            repository.volumeRepository,
            repository.trashRepository,
            fakeScanner,
            NoOpStorageCleanerPreferencesStore,
            notifier
        )
        advanceUntilIdle()
        val initialScanCount = fakeScanner.scannedRules.size

        notifier.notify(listOf(changedPath))
        advanceTimeBy(300)
        advanceUntilIdle()

        assertEquals(listOf(changedPath), fakeScanner.invalidatedPaths.last())
        assertTrue(fakeScanner.scannedRules.size > initialScanCount)
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
