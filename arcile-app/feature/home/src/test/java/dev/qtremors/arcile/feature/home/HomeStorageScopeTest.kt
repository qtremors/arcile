package dev.qtremors.arcile.feature.home

import dev.qtremors.arcile.core.storage.domain.CategoryStorage
import dev.qtremors.arcile.core.storage.domain.QuickAccessPreferencesStore
import dev.qtremors.arcile.core.storage.domain.StorageClassification
import dev.qtremors.arcile.core.storage.domain.StorageClassificationStore
import dev.qtremors.arcile.core.storage.domain.StorageKind
import dev.qtremors.arcile.core.storage.domain.StorageScope
import dev.qtremors.arcile.core.storage.domain.StorageVolume
import dev.qtremors.arcile.testutil.FakeStorageRepositoryBundle
import dev.qtremors.arcile.testutil.testVolume
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeStorageScopeTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loads indexed volume category scopes when multiple volumes are present`() = runTest(dispatcher) {
        val internal = volume("primary", "Internal", "/storage/emulated/0")
        val sd = volume(
            "sd",
            "SD Card",
            "/storage/1234-5678",
            removable = true,
            kind = StorageKind.SD_CARD
        )
        val repository = FakeStorageRepositoryBundle(
            volumes = listOf(internal, sd),
            initialCategorySizesByScope = mapOf(
                StorageScope.AllStorage to listOf(CategoryStorage("Images", 10L, setOf("jpg"))),
                StorageScope.Volume("primary") to listOf(CategoryStorage("Images", 7L, setOf("jpg"))),
                StorageScope.Volume("sd") to listOf(CategoryStorage("Images", 3L, setOf("jpg")))
            )
        )

        val viewModel = createViewModel(repository, FakeStorageClassificationStore())
        advanceUntilIdle()

        assertTrue(StorageScope.AllStorage in repository.requestedStorageInfoScopes)
        assertTrue(StorageScope.AllStorage in repository.requestedCategoryScopes)
        assertTrue(StorageScope.Volume("primary") in repository.requestedCategoryScopes)
        assertTrue(StorageScope.Volume("sd") in repository.requestedCategoryScopes)
        assertEquals(2, viewModel.state.value.categoryStoragesByVolume.size)
        assertEquals(
            listOf(CategoryStorage("Images", 7L, setOf("jpg"))),
            viewModel.state.value.categoryStoragesByVolume["primary"]
        )
        assertEquals(
            listOf(CategoryStorage("Images", 3L, setOf("jpg"))),
            viewModel.state.value.categoryStoragesByVolume["sd"]
        )

        viewModel.loadDashboardCategoryBreakdown()
        advanceUntilIdle()
        assertEquals(2, viewModel.state.value.categoryStoragesByVolume.size)
    }

    @Test
    fun `defers per-volume category scopes for one storage volume`() = runTest(dispatcher) {
        val repository = FakeStorageRepositoryBundle(
            volumes = listOf(volume("primary", "Internal", "/storage/emulated/0")),
            initialCategorySizesByScope = mapOf(
                StorageScope.AllStorage to listOf(CategoryStorage("Images", 10L, setOf("jpg"))),
                StorageScope.Volume("primary") to listOf(CategoryStorage("Images", 7L, setOf("jpg")))
            )
        )

        val viewModel = createViewModel(repository, FakeStorageClassificationStore())
        advanceUntilIdle()

        assertTrue(StorageScope.AllStorage in repository.requestedStorageInfoScopes)
        assertTrue(StorageScope.AllStorage in repository.requestedCategoryScopes)
        assertTrue(repository.requestedCategoryScopes.none { it is StorageScope.Volume })
        assertEquals(
            listOf(CategoryStorage("Images", 10L, setOf("jpg"))),
            viewModel.state.value.categoryStoragesByVolume["primary"]
        )

        viewModel.loadDashboardCategoryBreakdown()
        advanceUntilIdle()
        assertTrue(repository.requestedCategoryScopes.none { it is StorageScope.Volume })
        assertEquals(1, viewModel.state.value.categoryStoragesByVolume.size)
    }

    @Test
    fun `classification persists last seen removable volume metadata`() = runTest(dispatcher) {
        val otg = volume(
            "otg",
            "USB Drive",
            "/storage/ABCD-1234",
            removable = true,
            kind = StorageKind.EXTERNAL_UNCLASSIFIED
        )
        val store = RecordingStorageClassificationStore()
        val viewModel = createViewModel(FakeStorageRepositoryBundle(volumes = listOf(otg)), store)
        advanceUntilIdle()

        viewModel.setVolumeClassification(otg.storageKey, StorageKind.OTG)
        advanceUntilIdle()

        assertEquals(otg.storageKey, store.lastStorageKey)
        assertEquals(StorageKind.OTG, store.lastKind)
        assertEquals("USB Drive", store.lastSeenName)
        assertEquals("/storage/ABCD-1234", store.lastSeenPath)
    }

    @Test
    fun `classification optimistically removes the volume prompt`() = runTest(dispatcher) {
        val otg = volume(
            "otg",
            "USB Drive",
            "/storage/ABCD-1234",
            removable = true,
            kind = StorageKind.EXTERNAL_UNCLASSIFIED
        )
        val viewModel = createViewModel(
            FakeStorageRepositoryBundle(volumes = listOf(otg)),
            RecordingStorageClassificationStore()
        )
        advanceUntilIdle()

        assertTrue(viewModel.state.value.showClassificationPrompt)
        viewModel.setVolumeClassification(otg.storageKey, StorageKind.OTG)

        assertFalse(viewModel.state.value.showClassificationPrompt)
        assertTrue(viewModel.state.value.unclassifiedVolumes.isEmpty())
        advanceUntilIdle()
        assertFalse(viewModel.state.value.showClassificationPrompt)
    }

    private fun createViewModel(
        repository: FakeStorageRepositoryBundle,
        classifications: StorageClassificationStore
    ): HomeViewModel {
        val quickAccess = mockk<QuickAccessPreferencesStore> {
            every { quickAccessItems } returns flowOf(emptyList())
        }
        return HomeViewModel(
            repository.volumeRepository,
            repository.storageAnalyticsRepository,
            repository.searchRepository,
            classifications,
            quickAccess
        )
    }

    private fun volume(
        id: String,
        name: String,
        path: String,
        removable: Boolean = false,
        kind: StorageKind = if (removable) {
            StorageKind.EXTERNAL_UNCLASSIFIED
        } else {
            StorageKind.INTERNAL
        }
    ): StorageVolume = testVolume(
        id = id,
        storageKey = id,
        name = name,
        path = path,
        totalBytes = 100L,
        freeBytes = 40L,
        isPrimary = !removable,
        isRemovable = removable,
        kind = kind,
        isUserClassified = removable
    )
}

private class FakeStorageClassificationStore : StorageClassificationStore {
    override fun observeClassifications(): Flow<Map<String, StorageClassification>> =
        MutableStateFlow<Map<String, StorageClassification>>(emptyMap()).asStateFlow()

    override suspend fun getClassification(storageKey: String): StorageClassification? = null

    override suspend fun setClassification(
        storageKey: String,
        kind: StorageKind,
        lastSeenName: String?,
        lastSeenPath: String?
    ) = Unit

    override suspend fun resetClassification(storageKey: String) = Unit
}

private class RecordingStorageClassificationStore : StorageClassificationStore {
    var lastStorageKey: String? = null
    var lastKind: StorageKind? = null
    var lastSeenName: String? = null
    var lastSeenPath: String? = null

    override fun observeClassifications(): Flow<Map<String, StorageClassification>> =
        MutableStateFlow<Map<String, StorageClassification>>(emptyMap()).asStateFlow()

    override suspend fun getClassification(storageKey: String): StorageClassification? = null

    override suspend fun setClassification(
        storageKey: String,
        kind: StorageKind,
        lastSeenName: String?,
        lastSeenPath: String?
    ) {
        lastStorageKey = storageKey
        lastKind = kind
        this.lastSeenName = lastSeenName
        this.lastSeenPath = lastSeenPath
    }

    override suspend fun resetClassification(storageKey: String) = Unit
}
