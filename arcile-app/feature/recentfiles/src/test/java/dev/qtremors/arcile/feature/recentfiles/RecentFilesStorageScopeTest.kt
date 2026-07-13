package dev.qtremors.arcile.feature.recentfiles

import androidx.lifecycle.SavedStateHandle
import dev.qtremors.arcile.core.storage.domain.StorageScope
import dev.qtremors.arcile.testutil.FakeBulkFileOperationCoordinator
import dev.qtremors.arcile.testutil.FakeFilePreferencesStore
import dev.qtremors.arcile.testutil.FakeStorageRepositoryBundle
import dev.qtremors.arcile.testutil.testFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecentFilesStorageScopeTest {
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
    fun `queries the selected volume`() = runTest(dispatcher) {
        val repository = FakeStorageRepositoryBundle(
            initialRecentFilesByScope = mapOf(
                StorageScope.Volume("sd") to listOf(
                    testFile("clip.mp4", "/storage/1234-5678/Movies/clip.mp4")
                )
            )
        )

        val viewModel = createViewModel(repository, "sd")
        advanceUntilIdle()

        assertTrue(StorageScope.Volume("sd") in repository.requestedRecentScopes)
        assertEquals(
            listOf("/storage/1234-5678/Movies/clip.mp4"),
            viewModel.state.value.recentFiles.map { it.absolutePath }
        )
    }

    @Test
    fun `blank volume id queries all storage`() = runTest(dispatcher) {
        val repository = FakeStorageRepositoryBundle(
            initialRecentFilesByScope = mapOf(
                StorageScope.AllStorage to listOf(
                    testFile("note.txt", "/storage/emulated/0/Download/note.txt")
                )
            )
        )

        val viewModel = createViewModel(repository, "")
        advanceUntilIdle()

        assertTrue(StorageScope.AllStorage in repository.requestedRecentScopes)
        assertEquals(1, viewModel.state.value.recentFiles.size)
    }

    private fun createViewModel(
        repository: FakeStorageRepositoryBundle,
        volumeId: String
    ) = RecentFilesViewModel(
        volumeRepository = repository.volumeRepository,
        storageAnalyticsRepository = repository.storageAnalyticsRepository,
        fileBrowserRepository = repository.fileBrowserRepository,
        searchRepository = repository.searchRepository,
        browserPreferencesRepository = FakeFilePreferencesStore(),
        bulkFileOperationCoordinator = FakeBulkFileOperationCoordinator(),
        savedStateHandle = SavedStateHandle(mapOf("volumeId" to volumeId))
    )
}
