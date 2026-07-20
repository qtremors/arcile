package dev.qtremors.arcile.feature.home

import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.storage.domain.StorageClassification
import dev.qtremors.arcile.core.storage.domain.StorageClassificationStore
import dev.qtremors.arcile.core.storage.domain.CategoryStorage
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.SearchFilters
import dev.qtremors.arcile.core.storage.domain.StorageInfo
import dev.qtremors.arcile.core.storage.domain.StorageKind
import dev.qtremors.arcile.core.storage.domain.StorageScope
import dev.qtremors.arcile.core.storage.domain.StorageVolume
import dev.qtremors.arcile.core.storage.domain.TrashStorageUsage
import dev.qtremors.arcile.core.storage.domain.UtilityPreferencesStore
import dev.qtremors.arcile.testutil.FakeStorageRepositoryBundle
import dev.qtremors.arcile.testutil.MainDispatcherRule
import dev.qtremors.arcile.testutil.testFile
import dev.qtremors.arcile.testutil.testVolume
import dev.qtremors.arcile.core.presentation.UiText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `silent refresh requested during loading runs once after active refresh`() =
        runTest(mainDispatcherRule.dispatcher) {
            val releaseFirstRequest = CompletableDeferred<Unit>()
            var recentRequestCount = 0
            val repository = FakeStorageRepositoryBundle().apply {
                recentFilesResultProvider = { _, _, _, _ ->
                    recentRequestCount += 1
                    if (recentRequestCount == 1) releaseFirstRequest.await()
                    Result.success(listOf(homeFile("request-$recentRequestCount.jpg")))
                }
            }
            val quickAccessRepo =
                io.mockk.mockk<dev.qtremors.arcile.core.storage.domain.QuickAccessPreferencesStore> {
                    io.mockk.every { quickAccessItems } returns kotlinx.coroutines.flow.flowOf(emptyList())
                }
            val viewModel = HomeViewModel(
                repository.volumeRepository,
                repository.storageAnalyticsRepository,
                repository.searchRepository,
                HomeFakeStorageClassificationStore(),
                quickAccessRepo
            )

            runCurrent()
            assertEquals(1, recentRequestCount)

            viewModel.loadHomeData(HomeRefreshMode.SILENT, forceAnalytics = true)
            releaseFirstRequest.complete(Unit)
            advanceUntilIdle()

            assertEquals(2, recentRequestCount)
            assertEquals("request-2.jpg", viewModel.state.value.recentFiles.single().name)
        }

    @Test
    fun `loadHomeData exposes repository errors and clears loading flags`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeStorageRepositoryBundle().apply {
            storageInfoResultProvider = { Result.failure(IllegalStateException("storage failed")) }
        }
        val quickAccessRepo = io.mockk.mockk<dev.qtremors.arcile.core.storage.domain.QuickAccessPreferencesStore> { io.mockk.every { quickAccessItems } returns kotlinx.coroutines.flow.flowOf(emptyList()) }
        val viewModel = HomeViewModel(repository.volumeRepository, repository.storageAnalyticsRepository, repository.searchRepository, HomeFakeStorageClassificationStore(), quickAccessRepo)

        viewModel.loadHomeData()
        advanceUntilIdle()

        assertEquals(UiText.Dynamic("storage failed"), viewModel.state.value.error)
        assertFalse(viewModel.state.value.isLoading)
        assertFalse(viewModel.state.value.isCalculatingStorage)
        assertFalse(viewModel.state.value.isPullToRefreshing)
    }

    @Test
    fun `updateHomeSearchQuery searches all storage with active filters after debounce`() = runTest(mainDispatcherRule.dispatcher) {
        val filters = SearchFilters(fileType = "image", minSize = 10L)
        val expectedResults = listOf(homeFile("holiday.jpg"))
        val repository = FakeStorageRepositoryBundle().apply {
            searchFilesResultProvider = { _, _, _ -> Result.success(expectedResults) }
        }
        val quickAccessRepo = io.mockk.mockk<dev.qtremors.arcile.core.storage.domain.QuickAccessPreferencesStore> { io.mockk.every { quickAccessItems } returns kotlinx.coroutines.flow.flowOf(emptyList()) }
        val viewModel = HomeViewModel(repository.volumeRepository, repository.storageAnalyticsRepository, repository.searchRepository, HomeFakeStorageClassificationStore(), quickAccessRepo)

        advanceTimeBy(1_000)
        advanceUntilIdle()

        viewModel.updateSearchFilters(filters)
        viewModel.updateHomeSearchQuery("holiday")
        advanceTimeBy(399)
        assertFalse(viewModel.state.value.isSearching)
        advanceTimeBy(1)
        advanceUntilIdle()

        assertEquals("holiday", repository.searchRequests.last().query)
        assertEquals(StorageScope.AllStorage, repository.searchRequests.last().scope)
        assertEquals(filters, repository.searchRequests.last().filters)
        assertEquals(expectedResults, viewModel.state.value.searchResults)
        assertFalse(viewModel.state.value.isSearching)
    }

    @Test
    fun `blank home search clears current search state immediately`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeStorageRepositoryBundle()
        val quickAccessRepo = io.mockk.mockk<dev.qtremors.arcile.core.storage.domain.QuickAccessPreferencesStore> { io.mockk.every { quickAccessItems } returns kotlinx.coroutines.flow.flowOf(emptyList()) }
        val viewModel = HomeViewModel(repository.volumeRepository, repository.storageAnalyticsRepository, repository.searchRepository, HomeFakeStorageClassificationStore(), quickAccessRepo)

        advanceTimeBy(1_000)
        advanceUntilIdle()

        viewModel.updateHomeSearchQuery("photos")
        advanceTimeBy(400)
        advanceUntilIdle()

        viewModel.updateHomeSearchQuery("")

        assertEquals(emptyList<FileModel>(), viewModel.state.value.searchResults)
        assertFalse(viewModel.state.value.isSearching)
    }

    @Test
    fun `setVolumeClassification restores optimistic state when persistence fails`() = runTest(mainDispatcherRule.dispatcher) {
        val volume = homeVolume(
            id = "otg",
            storageKey = "otg",
            name = "USB",
            path = "/storage/otg",
            kind = StorageKind.EXTERNAL_UNCLASSIFIED,
            isPrimary = false,
            isRemovable = true
        )
        val repository = FakeStorageRepositoryBundle(volumes = listOf(volume))
        val store = HomeFakeStorageClassificationStore(setFailure = IllegalStateException("disk full"))
        val quickAccessRepo = io.mockk.mockk<dev.qtremors.arcile.core.storage.domain.QuickAccessPreferencesStore> { io.mockk.every { quickAccessItems } returns kotlinx.coroutines.flow.flowOf(emptyList()) }
        val viewModel = HomeViewModel(repository.volumeRepository, repository.storageAnalyticsRepository, repository.searchRepository, store, quickAccessRepo)

        advanceTimeBy(1_000)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.showClassificationPrompt)

        viewModel.setVolumeClassification(volume.storageKey, StorageKind.OTG)

        assertFalse(viewModel.state.value.showClassificationPrompt)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.showClassificationPrompt)
        assertEquals(listOf(volume), viewModel.state.value.unclassifiedVolumes)
        assertEquals(UiText.StringResource(R.string.error_save_classification_failed, listOf("disk full")), viewModel.state.value.error)
    }

    @Test
    fun `hideClassificationPrompt suppresses only the selected volume`() = runTest(mainDispatcherRule.dispatcher) {
        val first = homeVolume(
            id = "u1",
            storageKey = "u1",
            name = "USB 1",
            path = "/storage/u1",
            kind = StorageKind.EXTERNAL_UNCLASSIFIED,
            isPrimary = false,
            isRemovable = true
        )
        val second = homeVolume(
            id = "u2",
            storageKey = "u2",
            name = "USB 2",
            path = "/storage/u2",
            kind = StorageKind.EXTERNAL_UNCLASSIFIED,
            isPrimary = false,
            isRemovable = true
        )
        val quickAccessRepo = io.mockk.mockk<dev.qtremors.arcile.core.storage.domain.QuickAccessPreferencesStore> { io.mockk.every { quickAccessItems } returns kotlinx.coroutines.flow.flowOf(emptyList()) }
        val repo = FakeStorageRepositoryBundle(volumes = listOf(first, second))
        val viewModel = HomeViewModel(
            repo.volumeRepository,
            repo.storageAnalyticsRepository,
            repo.searchRepository,
            HomeFakeStorageClassificationStore(),
            quickAccessRepo
        )

        advanceTimeBy(1_000)
        advanceUntilIdle()

        viewModel.hideClassificationPrompt("u1")

        assertEquals(listOf(second), viewModel.state.value.unclassifiedVolumes)
        assertTrue(viewModel.state.value.showClassificationPrompt)
    }

    @Test
    fun `storage volume emissions update home volumes immediately`() = runTest(mainDispatcherRule.dispatcher) {
        val volume1 = homeVolume("v1", "v1", "Vol1", "/v1", StorageKind.INTERNAL, true, false)
        val volume2 = homeVolume("v2", "v2", "Vol2", "/v2", StorageKind.SD_CARD, false, true)

        val repository = FakeStorageRepositoryBundle().apply {
            storageInfoResultProvider = { Result.success(StorageInfo(emptyList())) }
        }
        val quickAccessRepo = io.mockk.mockk<dev.qtremors.arcile.core.storage.domain.QuickAccessPreferencesStore> { io.mockk.every { quickAccessItems } returns kotlinx.coroutines.flow.flowOf(emptyList()) }
        val viewModel = HomeViewModel(repository.volumeRepository, repository.storageAnalyticsRepository, repository.searchRepository, HomeFakeStorageClassificationStore(), quickAccessRepo)

        advanceTimeBy(1_000)
        advanceUntilIdle()

        repository.emitVolumes(listOf(volume1))
        runCurrent()

        assertEquals(listOf(volume1), viewModel.state.value.allStorageVolumes)

        repository.emitVolumes(listOf(volume1, volume2))
        runCurrent()

        assertEquals(listOf(volume1, volume2), viewModel.state.value.allStorageVolumes)

        repository.emitVolumes(listOf(volume2))
        runCurrent()

        assertEquals(listOf(volume2), viewModel.state.value.allStorageVolumes)
    }

    @Test
    fun `loadHomeData times out and preserves partial results`() = runTest(mainDispatcherRule.dispatcher) {
        val volume = homeVolume("primary", "primary", "Internal", "/storage/emulated/0", StorageKind.INTERNAL, true, false)
        val repository = FakeStorageRepositoryBundle(
            volumes = listOf(volume),
            initialRecentFilesByScope = mapOf(StorageScope.AllStorage to listOf(homeFile("recent.txt")))
        ).apply {
            storageInfoResultProvider = {
                kotlinx.coroutines.delay(20_000)
                Result.success(StorageInfo(listOf(volume)))
            }
        }
        val quickAccessRepo = io.mockk.mockk<dev.qtremors.arcile.core.storage.domain.QuickAccessPreferencesStore> { io.mockk.every { quickAccessItems } returns kotlinx.coroutines.flow.flowOf(emptyList()) }
        val viewModel = HomeViewModel(repository.volumeRepository, repository.storageAnalyticsRepository, repository.searchRepository, HomeFakeStorageClassificationStore(), quickAccessRepo)

        viewModel.loadHomeData()
        advanceTimeBy(15_000)
        advanceUntilIdle()

        assertEquals(UiText.StringResource(R.string.error_home_data_timeout), viewModel.state.value.error)
        assertEquals(listOf("recent.txt"), viewModel.state.value.recentFiles.map { it.name })
        assertFalse(viewModel.state.value.isLoading)
        assertFalse(viewModel.state.value.isCalculatingStorage)
    }

    @Test
    fun `loadHomeData loads trash storage usage with analytics`() = runTest(mainDispatcherRule.dispatcher) {
        val volume = homeVolume("primary", "primary", "Internal", "/storage/emulated/0", StorageKind.INTERNAL, true, false)
        val repository = FakeStorageRepositoryBundle(volumes = listOf(volume)).apply {
            trashStorageUsageResult = Result.success(TrashStorageUsage(42L, mapOf("primary" to 42L)))
        }
        val quickAccessRepo = io.mockk.mockk<dev.qtremors.arcile.core.storage.domain.QuickAccessPreferencesStore> { io.mockk.every { quickAccessItems } returns kotlinx.coroutines.flow.flowOf(emptyList()) }
        val viewModel = HomeViewModel(repository.volumeRepository, repository.storageAnalyticsRepository, repository.searchRepository, HomeFakeStorageClassificationStore(), quickAccessRepo)

        viewModel.loadHomeData()
        advanceUntilIdle()

        assertEquals(42L, viewModel.state.value.trashStorageUsage.totalBytes)
        assertEquals(42L, viewModel.state.value.trashStorageUsage.byVolumeId["primary"])
    }

    @Test
    fun `loadHomeData preserves previous trash usage when refresh fails`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeStorageRepositoryBundle().apply {
            trashStorageUsageResult = Result.success(TrashStorageUsage(24L, mapOf("primary" to 24L)))
        }
        val quickAccessRepo = io.mockk.mockk<dev.qtremors.arcile.core.storage.domain.QuickAccessPreferencesStore> { io.mockk.every { quickAccessItems } returns kotlinx.coroutines.flow.flowOf(emptyList()) }
        val viewModel = HomeViewModel(repository.volumeRepository, repository.storageAnalyticsRepository, repository.searchRepository, HomeFakeStorageClassificationStore(), quickAccessRepo)

        viewModel.loadHomeData()
        advanceUntilIdle()
        repository.trashStorageUsageResult = Result.failure(IllegalStateException("trash failed"))

        viewModel.loadHomeData()
        advanceUntilIdle()

        assertEquals(24L, viewModel.state.value.trashStorageUsage.totalBytes)
        assertEquals(UiText.Dynamic("trash failed"), viewModel.state.value.error)
    }

    @Test
    fun `ensure dashboard category breakdown skips repository when selected volume is already loaded`() = runTest(mainDispatcherRule.dispatcher) {
        val volume = homeVolume("primary", "primary", "Internal", "/storage/emulated/0", StorageKind.INTERNAL, true, false)
        val repository = FakeStorageRepositoryBundle(
            volumes = listOf(volume),
            initialCategorySizesByScope = mapOf(
                StorageScope.Volume("primary") to listOf(CategoryStorage("Images", 7L, setOf("jpg")))
            )
        )
        val quickAccessRepo = io.mockk.mockk<dev.qtremors.arcile.core.storage.domain.QuickAccessPreferencesStore> { io.mockk.every { quickAccessItems } returns kotlinx.coroutines.flow.flowOf(emptyList()) }
        val viewModel = HomeViewModel(repository.volumeRepository, repository.storageAnalyticsRepository, repository.searchRepository, HomeFakeStorageClassificationStore(), quickAccessRepo)
        advanceUntilIdle()

        viewModel.loadDashboardCategoryBreakdown("primary")
        advanceUntilIdle()
        repository.requestedCategoryScopes.clear()

        viewModel.ensureDashboardCategoryBreakdown("primary")
        advanceUntilIdle()

        assertTrue(repository.requestedCategoryScopes.none { it == StorageScope.Volume("primary") })
    }

    @Test
    fun `ensure dashboard category breakdown loads missing indexed volume`() = runTest(mainDispatcherRule.dispatcher) {
        val volume = homeVolume("primary", "primary", "Internal", "/storage/emulated/0", StorageKind.INTERNAL, true, false)
        val repository = FakeStorageRepositoryBundle(
            volumes = listOf(volume),
            initialCategorySizesByScope = mapOf(
                StorageScope.Volume("primary") to listOf(CategoryStorage("Images", 7L, setOf("jpg")))
            )
        )
        val quickAccessRepo = io.mockk.mockk<dev.qtremors.arcile.core.storage.domain.QuickAccessPreferencesStore> { io.mockk.every { quickAccessItems } returns kotlinx.coroutines.flow.flowOf(emptyList()) }
        val viewModel = HomeViewModel(repository.volumeRepository, repository.storageAnalyticsRepository, repository.searchRepository, HomeFakeStorageClassificationStore(), quickAccessRepo)
        advanceUntilIdle()
        repository.requestedCategoryScopes.clear()

        viewModel.ensureDashboardCategoryBreakdown("primary")
        advanceUntilIdle()

        assertTrue(repository.requestedCategoryScopes.contains(StorageScope.Volume("primary")))
        assertEquals(listOf(CategoryStorage("Images", 7L, setOf("jpg"))), viewModel.state.value.categoryStoragesByVolume["primary"])
    }

    @Test
    fun `loadHomeData seeds single indexed volume dashboard categories from global home categories`() = runTest(mainDispatcherRule.dispatcher) {
        val volume = homeVolume("primary", "primary", "Internal", "/storage/emulated/0", StorageKind.INTERNAL, true, false)
        val globalCategories = listOf(
            CategoryStorage("Images", 7L, setOf("jpg")),
            CategoryStorage("Videos", 20L, setOf("mp4"))
        )
        val repository = FakeStorageRepositoryBundle(
            volumes = listOf(volume),
            initialCategorySizesByScope = mapOf(StorageScope.AllStorage to globalCategories)
        )
        val quickAccessRepo = io.mockk.mockk<dev.qtremors.arcile.core.storage.domain.QuickAccessPreferencesStore> { io.mockk.every { quickAccessItems } returns kotlinx.coroutines.flow.flowOf(emptyList()) }
        val viewModel = HomeViewModel(repository.volumeRepository, repository.storageAnalyticsRepository, repository.searchRepository, HomeFakeStorageClassificationStore(), quickAccessRepo)

        viewModel.loadHomeData()
        advanceUntilIdle()
        repository.requestedCategoryScopes.clear()

        viewModel.ensureDashboardCategoryBreakdown("primary")
        advanceUntilIdle()

        assertEquals(
            listOf("Videos", "Images"),
            viewModel.state.value.categoryStoragesByVolume["primary"]?.map { it.name }
        )
        assertTrue(repository.requestedCategoryScopes.none { it == StorageScope.Volume("primary") })
    }

    @Test
    fun `manual refresh invalidates analytics cache before loading home categories`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeStorageRepositoryBundle(
            initialCategorySizesByScope = mapOf(
                StorageScope.AllStorage to listOf(CategoryStorage("Images", 7L, setOf("jpg")))
            )
        )
        val quickAccessRepo = io.mockk.mockk<dev.qtremors.arcile.core.storage.domain.QuickAccessPreferencesStore> { io.mockk.every { quickAccessItems } returns kotlinx.coroutines.flow.flowOf(emptyList()) }
        val viewModel = HomeViewModel(repository.volumeRepository, repository.storageAnalyticsRepository, repository.searchRepository, HomeFakeStorageClassificationStore(), quickAccessRepo)

        viewModel.loadHomeData(HomeRefreshMode.INITIAL)
        advanceUntilIdle()
        assertEquals(0, repository.invalidateAnalyticsCacheCalls)

        viewModel.loadHomeData(HomeRefreshMode.MANUAL)
        advanceUntilIdle()

        assertEquals(1, repository.invalidateAnalyticsCacheCalls)
        assertTrue(repository.requestedCategoryScopes.contains(StorageScope.AllStorage))
    }

    @Test
    fun `utility preference controls which utilities are visible on home`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeStorageRepositoryBundle()
        val quickAccessRepo = io.mockk.mockk<dev.qtremors.arcile.core.storage.domain.QuickAccessPreferencesStore> { io.mockk.every { quickAccessItems } returns kotlinx.coroutines.flow.flowOf(emptyList()) }
        val utilityStore = HomeFakeUtilityPreferencesStore()
        val viewModel = HomeViewModel(
            repository.volumeRepository,
            repository.storageAnalyticsRepository,
            repository.searchRepository,
            HomeFakeStorageClassificationStore(),
            quickAccessRepo,
            utilityStore
        )

        advanceUntilIdle()
        assertTrue("trash" in viewModel.state.value.homeUtilityIds)
        assertTrue("cleaner" in viewModel.state.value.homeUtilityIds)

        utilityStore.setHomeUtilityIds(listOf("trash"))
        advanceUntilIdle()

        assertEquals(listOf("trash"), viewModel.state.value.homeUtilityIds)
    }

    @Test
    fun `display state keeps only today recent files for home carousel limit`() {
        val older = homeFile("older.txt").copy(lastModified = 1L)
        val newer = homeFile("newer.txt").copy(lastModified = 20_000L)
        val state = HomeState(
            recentFiles = kotlinx.collections.immutable.persistentListOf(older, newer),
            todayStart = 10_000L
        ).withUpdatedDisplayState()

        assertEquals(listOf("newer.txt"), state.displayState.todayRecentFiles.map { it.name })
    }
}

private class HomeFakeStorageClassificationStore(
    private val setFailure: Throwable? = null
) : StorageClassificationStore {
    override fun observeClassifications() = MutableStateFlow<Map<String, StorageClassification>>(emptyMap()).asStateFlow()

    override suspend fun getClassification(storageKey: String): StorageClassification? = null

    override suspend fun setClassification(
        storageKey: String,
        kind: StorageKind,
        lastSeenName: String?,
        lastSeenPath: String?
    ) {
        if (setFailure != null) throw setFailure
    }

    override suspend fun resetClassification(storageKey: String) = Unit
}

private class HomeFakeUtilityPreferencesStore : UtilityPreferencesStore {
    private val ids = MutableStateFlow(listOf("trash", "cleaner"))

    override val homeUtilityIds = ids.asStateFlow()

    override suspend fun setHomeUtilityIds(ids: List<String>) {
        this.ids.value = ids
    }
}

private fun homeVolume(
    id: String,
    storageKey: String,
    name: String,
    path: String,
    kind: StorageKind,
    isPrimary: Boolean,
    isRemovable: Boolean
) = testVolume(
    id = id,
    storageKey = storageKey,
    name = name,
    path = path,
    totalBytes = 2_000L,
    freeBytes = 500L,
    isPrimary = isPrimary,
    isRemovable = isRemovable,
    kind = kind
)

private fun homeFile(name: String) = testFile(
    name = name,
    path = "/storage/emulated/0/$name",
    size = 10L,
    lastModified = 20L
)
