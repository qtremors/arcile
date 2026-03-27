package dev.qtremors.arcile.presentation.recentfiles

import android.content.IntentSender
import androidx.lifecycle.SavedStateHandle
import dev.qtremors.arcile.domain.CategoryStorage
import dev.qtremors.arcile.domain.ConflictResolution
import dev.qtremors.arcile.domain.FileConflict
import dev.qtremors.arcile.domain.FileModel
import dev.qtremors.arcile.domain.FileRepository
import dev.qtremors.arcile.domain.NativeConfirmationRequiredException
import dev.qtremors.arcile.domain.SearchFilters
import dev.qtremors.arcile.domain.StorageInfo
import dev.qtremors.arcile.domain.StorageKind
import dev.qtremors.arcile.domain.StorageScope
import dev.qtremors.arcile.domain.StorageVolume
import dev.qtremors.arcile.domain.TrashMetadata
import dev.qtremors.arcile.testutil.MainDispatcherRule
import dev.qtremors.arcile.testutil.FakeFileRepository
import dev.qtremors.arcile.testutil.testFile
import dev.qtremors.arcile.testutil.testVolume
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecentFilesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `search query filters recent files after debounce and ignores case`() = runTest(mainDispatcherRule.dispatcher) {
        val files = listOf(
            recentFile("Holiday.jpg"),
            recentFile("notes.txt"),
            recentFile("holiday-plan.pdf")
        )
        val repository = FakeFileRepository(
            initialRecentFilesByScope = mapOf(StorageScope.AllStorage to files)
        )
        val viewModel = RecentFilesViewModel(repository, SavedStateHandle())

        advanceUntilIdle()

        viewModel.updateSearchQuery("HOLIDAY")
        advanceTimeBy(299)
        assertFalse(viewModel.state.value.isSearching)
        advanceTimeBy(1)
        advanceUntilIdle()

        assertEquals(listOf("Holiday.jpg", "holiday-plan.pdf"), viewModel.state.value.searchResults.map { it.name })
        assertFalse(viewModel.state.value.isSearching)
    }

    @Test
    fun `blank search query clears recent file search state immediately`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeFileRepository(
            initialRecentFilesByScope = mapOf(StorageScope.AllStorage to listOf(recentFile("Holiday.jpg")))
        )
        val viewModel = RecentFilesViewModel(repository, SavedStateHandle())

        advanceUntilIdle()
        viewModel.updateSearchQuery("holiday")
        advanceTimeBy(300)
        advanceUntilIdle()

        viewModel.updateSearchQuery("")

        assertEquals(emptyList<FileModel>(), viewModel.state.value.searchResults)
        assertFalse(viewModel.state.value.isSearching)
    }

    @Test
    fun `pull to refresh reloads files and resets refresh flag`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeFileRepository(
            initialRecentFilesByScope = mapOf(StorageScope.AllStorage to listOf(recentFile("Holiday.jpg")))
        )
        val viewModel = RecentFilesViewModel(repository, SavedStateHandle())

        advanceUntilIdle()
        viewModel.loadRecentFiles(pullToRefresh = true)
        advanceUntilIdle()

        assertEquals(2, repository.requestedRecentScopes.size)
        assertEquals(StorageScope.AllStorage, repository.requestedRecentScopes.last())
        assertFalse(viewModel.state.value.isPullToRefreshing)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `requestDeleteSelected shows trash confirmation for trash-capable volume`() = runTest(mainDispatcherRule.dispatcher) {
        val internal = recentVolume("primary", "/storage/emulated/0", StorageKind.INTERNAL)
        val repository = FakeFileRepository(
            volumes = listOf(internal),
            initialRecentFilesByScope = mapOf(
                StorageScope.AllStorage to listOf(recentFile("Holiday.jpg", "/storage/emulated/0/Holiday.jpg"))
            )
        )
        val viewModel = RecentFilesViewModel(repository, SavedStateHandle())

        advanceUntilIdle()
        viewModel.toggleSelection("/storage/emulated/0/Holiday.jpg")
        viewModel.requestDeleteSelected()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.showTrashConfirmation)
        assertFalse(viewModel.state.value.showPermanentDeleteConfirmation)
        assertFalse(viewModel.state.value.showMixedDeleteExplanation)
    }

    @Test
    fun `moveSelectedToTrash surfaces native confirmation request`() = runTest(mainDispatcherRule.dispatcher) {
        val internal = recentVolume("primary", "/storage/emulated/0", StorageKind.INTERNAL)
        val repository = FakeFileRepository(
            volumes = listOf(internal),
            initialRecentFilesByScope = mapOf(
                StorageScope.AllStorage to listOf(recentFile("Holiday.jpg", "/storage/emulated/0/Holiday.jpg"))
            )
        ).apply {
            moveToTrashResultProvider = { Result.failure(NativeConfirmationRequiredException(fakeIntentSender())) }
        }
        val viewModel = RecentFilesViewModel(repository, SavedStateHandle())

        advanceUntilIdle()
        viewModel.toggleSelection("/storage/emulated/0/Holiday.jpg")
        viewModel.moveSelectedToTrash()
        advanceUntilIdle()

        assertEquals(RecentNativeAction.TRASH, viewModel.state.value.pendingNativeAction)
        assertFalse(viewModel.state.value.isLoading)
    }
}

private fun recentFile(name: String, path: String = "/storage/emulated/0/$name") = testFile(name = name, path = path)

private fun recentVolume(id: String, path: String, kind: StorageKind) = testVolume(
    id = id,
    storageKey = id,
    name = id,
    path = path,
    totalBytes = 100L,
    freeBytes = 20L,
    isPrimary = kind == StorageKind.INTERNAL,
    isRemovable = kind != StorageKind.INTERNAL,
    kind = kind
)

private fun fakeIntentSender(): IntentSender {
    return mockk()
}
