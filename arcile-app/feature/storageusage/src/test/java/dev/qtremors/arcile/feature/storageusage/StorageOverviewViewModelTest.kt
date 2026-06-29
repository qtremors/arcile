package dev.qtremors.arcile.feature.storageusage

import dev.qtremors.arcile.core.storage.domain.CategoryStorage
import dev.qtremors.arcile.core.storage.domain.StorageClassification
import dev.qtremors.arcile.core.storage.domain.StorageClassificationStore
import dev.qtremors.arcile.core.storage.domain.StorageKind
import dev.qtremors.arcile.core.storage.domain.StorageScope
import dev.qtremors.arcile.testutil.FakeStorageRepositoryBundle
import dev.qtremors.arcile.testutil.testVolume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StorageOverviewViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var classificationStore: RecordingClassificationStore

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        classificationStore = RecordingClassificationStore()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `load owns aggregate and per-volume dashboard state`() = runTest(dispatcher) {
        val primary = testVolume("primary", "/storage/emulated/0")
        val sdCard = testVolume("sd", "/storage/sd", kind = StorageKind.SD_CARD)
        val allCategories = listOf(CategoryStorage("Images", 30L, setOf("jpg")))
        val sdCategories = listOf(CategoryStorage("Audio", 20L, setOf("mp3")))
        val repositories = FakeStorageRepositoryBundle(
            volumes = listOf(primary, sdCard),
            initialCategorySizesByScope = mapOf(
                StorageScope.AllStorage to allCategories,
                StorageScope.Volume("primary") to allCategories,
                StorageScope.Volume("sd") to sdCategories
            )
        )
        val viewModel = StorageOverviewViewModel(
            repositories.volumeRepository,
            repositories.storageAnalyticsRepository,
            classificationStore
        )

        viewModel.load("sd")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals(listOf(primary, sdCard), state.indexedVolumes)
        assertEquals(allCategories, state.categoryStorages)
        assertEquals(sdCategories, state.categoryStoragesByVolume["sd"])
    }

    @Test
    fun `classification commands preserve volume metadata`() = runTest(dispatcher) {
        val sdCard = testVolume(
            id = "sd",
            path = "/storage/sd",
            name = "Camera card",
            storageKey = "disk:1",
            kind = StorageKind.EXTERNAL_UNCLASSIFIED
        )
        val repositories = FakeStorageRepositoryBundle(volumes = listOf(sdCard))
        val viewModel = StorageOverviewViewModel(
            repositories.volumeRepository,
            repositories.storageAnalyticsRepository,
            classificationStore
        )
        viewModel.load(null)
        advanceUntilIdle()

        viewModel.setVolumeClassification("disk:1", StorageKind.SD_CARD)
        advanceUntilIdle()

        assertEquals("disk:1", classificationStore.storageKey)
        assertEquals(StorageKind.SD_CARD, classificationStore.kind)
        assertEquals("Camera card", classificationStore.lastSeenName)
        assertEquals("/storage/sd", classificationStore.lastSeenPath)
    }
}

private class RecordingClassificationStore : StorageClassificationStore {
    var storageKey: String? = null
    var kind: StorageKind? = null
    var lastSeenName: String? = null
    var lastSeenPath: String? = null

    override fun observeClassifications(): Flow<Map<String, StorageClassification>> = flowOf(emptyMap())
    override suspend fun getClassification(storageKey: String): StorageClassification? = null

    override suspend fun setClassification(
        storageKey: String,
        kind: StorageKind,
        lastSeenName: String?,
        lastSeenPath: String?
    ) {
        this.storageKey = storageKey
        this.kind = kind
        this.lastSeenName = lastSeenName
        this.lastSeenPath = lastSeenPath
    }

    override suspend fun resetClassification(storageKey: String) = Unit
}
