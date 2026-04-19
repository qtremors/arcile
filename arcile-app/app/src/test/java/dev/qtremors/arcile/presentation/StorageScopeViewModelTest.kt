package dev.qtremors.arcile.presentation

import androidx.lifecycle.SavedStateHandle
import dev.qtremors.arcile.data.StorageClassification
import dev.qtremors.arcile.data.StorageClassificationStore
import dev.qtremors.arcile.domain.CategoryStorage
import dev.qtremors.arcile.domain.ConflictResolution
import dev.qtremors.arcile.domain.FileConflict
import dev.qtremors.arcile.domain.FileModel
import dev.qtremors.arcile.domain.FileRepository
import dev.qtremors.arcile.domain.SearchFilters
import dev.qtremors.arcile.domain.StorageInfo
import dev.qtremors.arcile.domain.StorageKind
import dev.qtremors.arcile.domain.StorageScope
import dev.qtremors.arcile.domain.StorageVolume
import dev.qtremors.arcile.domain.TrashMetadata
import dev.qtremors.arcile.domain.DestinationRequiredException
import dev.qtremors.arcile.presentation.home.HomeViewModel
import dev.qtremors.arcile.presentation.recentfiles.RecentFilesViewModel
import dev.qtremors.arcile.presentation.trash.TrashViewModel
import dev.qtremors.arcile.testutil.FakeFileRepository
import dev.qtremors.arcile.testutil.testFile
import dev.qtremors.arcile.testutil.testVolume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StorageScopeViewModelTest {

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
    fun `home view model loads global and indexed per-volume storage scopes`() = runTest(dispatcher) {
        val internal = volume(id = "primary", name = "Internal", path = "/storage/emulated/0")
        val sd = volume(id = "sd", name = "SD Card", path = "/storage/1234-5678", removable = true)
        val repository = FakeFileRepository(
            volumes = listOf(internal, sd),
            initialCategorySizesByScope = mapOf(
                StorageScope.AllStorage to listOf(CategoryStorage("Images", 10L, setOf("jpg"))),
                StorageScope.Volume("primary") to listOf(CategoryStorage("Images", 7L, setOf("jpg"))),
                StorageScope.Volume("sd") to listOf(CategoryStorage("Images", 3L, setOf("jpg")))
            )
        )

        val quickAccessRepo = io.mockk.mockk<dev.qtremors.arcile.data.QuickAccessPreferencesRepository> { io.mockk.every { quickAccessItems } returns kotlinx.coroutines.flow.flowOf(emptyList()) }
        val viewModel = HomeViewModel(repository, FakeStorageClassificationStore(), quickAccessRepo)
        advanceUntilIdle()

        assertTrue(repository.requestedStorageInfoScopes.contains(StorageScope.AllStorage))
        assertTrue(repository.requestedCategoryScopes.contains(StorageScope.AllStorage))
        assertTrue(repository.requestedCategoryScopes.contains(StorageScope.Volume("primary")))
        assertTrue(repository.requestedCategoryScopes.none { it == StorageScope.Volume("sd") })
        assertEquals(1, viewModel.state.value.categoryStoragesByVolume.size)
    }

    @Test
    fun `recent files view model scopes queries to selected volume`() = runTest(dispatcher) {
        val repository = FakeFileRepository(
            initialRecentFilesByScope = mapOf(
                StorageScope.Volume("sd") to listOf(file("clip.mp4", "/storage/1234-5678/Movies/clip.mp4"))
            )
        )

        val viewModel = RecentFilesViewModel(
            repository = repository,
            savedStateHandle = SavedStateHandle(mapOf("volumeId" to "sd"))
        )
        advanceUntilIdle()

        assertTrue(repository.requestedRecentScopes.contains(StorageScope.Volume("sd")))
        assertEquals(listOf("/storage/1234-5678/Movies/clip.mp4"), viewModel.state.value.recentFiles.map { it.absolutePath })
    }

    @Test
    fun `recent files view model treats blank volume id as all storage`() = runTest(dispatcher) {
        val repository = FakeFileRepository(
            initialRecentFilesByScope = mapOf(
                StorageScope.AllStorage to listOf(file("note.txt", "/storage/emulated/0/Download/note.txt"))
            )
        )

        val viewModel = RecentFilesViewModel(
            repository = repository,
            savedStateHandle = SavedStateHandle(mapOf("volumeId" to ""))
        )
        advanceUntilIdle()

        assertTrue(repository.requestedRecentScopes.contains(StorageScope.AllStorage))
        assertEquals(1, viewModel.state.value.recentFiles.size)
    }

    @Test
    fun `home view model persists last seen metadata for unindexed removable volume classification`() = runTest(dispatcher) {
        val otg = volume(
            id = "otg",
            name = "USB Drive",
            path = "/storage/ABCD-1234",
            removable = true,
            kind = StorageKind.EXTERNAL_UNCLASSIFIED
        )
        val store = RecordingStorageClassificationStore()
        val repository = FakeFileRepository(volumes = listOf(otg))

        val quickAccessRepo = io.mockk.mockk<dev.qtremors.arcile.data.QuickAccessPreferencesRepository> { io.mockk.every { quickAccessItems } returns kotlinx.coroutines.flow.flowOf(emptyList()) }
        val viewModel = HomeViewModel(repository, store, quickAccessRepo)
        advanceUntilIdle()

        viewModel.setVolumeClassification(otg.storageKey, StorageKind.OTG)
        advanceUntilIdle()

        assertEquals(otg.storageKey, store.lastStorageKey)
        assertEquals(StorageKind.OTG, store.lastKind)
        assertEquals("USB Drive", store.lastSeenName)
        assertEquals("/storage/ABCD-1234", store.lastSeenPath)
    }

    @Test
    fun `home view model performs optimistic update when classifying volumes`() = runTest(dispatcher) {
        val otg = volume(
            id = "otg",
            name = "USB Drive",
            path = "/storage/ABCD-1234",
            removable = true,
            kind = StorageKind.EXTERNAL_UNCLASSIFIED
        )
        val store = RecordingStorageClassificationStore()
        val repository = FakeFileRepository(volumes = listOf(otg))

        val quickAccessRepo = io.mockk.mockk<dev.qtremors.arcile.data.QuickAccessPreferencesRepository> { io.mockk.every { quickAccessItems } returns kotlinx.coroutines.flow.flowOf(emptyList()) }
        val viewModel = HomeViewModel(repository, store, quickAccessRepo)
        advanceUntilIdle()

        // Initially shown
        assertTrue(viewModel.state.value.showClassificationPrompt)
        assertEquals(1, viewModel.state.value.unclassifiedVolumes.size)

        // Classify - this should trigger optimistic update
        viewModel.setVolumeClassification(otg.storageKey, StorageKind.OTG)
        
        // CHECK IMMEDIATELY (without advanceUntilIdle)
        assertFalse("Prompt should be hidden immediately via optimistic update", viewModel.state.value.showClassificationPrompt)
        assertTrue("Unclassified list should be empty immediately", viewModel.state.value.unclassifiedVolumes.isEmpty())

        advanceUntilIdle()
        // Still hidden after background work
        assertFalse(viewModel.state.value.showClassificationPrompt)
    }

    @Test
    fun `trash view model shows destination picker when restore needs alternate volume`() = runTest(dispatcher) {
        val internal = volume(id = "primary", name = "Internal", path = "/storage/emulated/0")
        val repository = FakeFileRepository(volumes = listOf(internal)).apply {
            restoreFromTrashResultProvider = { _, _ ->
                Result.failure(DestinationRequiredException(listOf("trash-1")))
            }
        }
        val viewModel = TrashViewModel(repository)

        advanceUntilIdle()
        viewModel.toggleSelection("trash-1")
        viewModel.restoreSelectedTrash()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.showDestinationPicker)
        assertNull(viewModel.state.value.error)
        assertFalse(viewModel.state.value.isLoading)
    }

    private fun volume(
        id: String,
        name: String,
        path: String,
        removable: Boolean = false,
        kind: StorageKind = if (removable) StorageKind.EXTERNAL_UNCLASSIFIED else StorageKind.INTERNAL
    ) = testVolume(
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

    private fun file(name: String, path: String) = testFile(name = name, path = path)
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
