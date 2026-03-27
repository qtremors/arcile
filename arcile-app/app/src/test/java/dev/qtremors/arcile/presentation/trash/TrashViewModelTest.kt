package dev.qtremors.arcile.presentation.trash

import android.content.IntentSender
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
import io.mockk.mockk
import dev.qtremors.arcile.testutil.MainDispatcherRule
import dev.qtremors.arcile.testutil.FakeFileRepository
import dev.qtremors.arcile.testutil.testFile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrashViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `loadTrashFiles drops stale selections that no longer exist`() = runTest(mainDispatcherRule.dispatcher) {
        val items = listOf(trashItem("keep-1", "keep.txt"))
        val repository = FakeFileRepository().apply {
            trashFilesResult = Result.success(items)
        }
        val viewModel = TrashViewModel(repository)

        advanceUntilIdle()
        viewModel.toggleSelection("keep-1")
        viewModel.toggleSelection("missing")

        viewModel.loadTrashFiles()
        advanceUntilIdle()

        assertEquals(setOf("keep-1"), viewModel.state.value.selectedFiles)
    }

    @Test
    fun `updateSearchQuery filters trash items after debounce and clears on blank`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeFileRepository().apply {
            trashFilesResult = Result.success(listOf(
                trashItem("1", "Photo.jpg"),
                trashItem("2", "notes.txt"),
                trashItem("3", "photo-backup.png")
            ))
        }
        val viewModel = TrashViewModel(repository)

        advanceUntilIdle()
        viewModel.updateSearchQuery("PHOTO")
        advanceTimeBy(300)
        advanceUntilIdle()

        assertEquals(listOf("Photo.jpg", "photo-backup.png"), viewModel.state.value.searchResults.map { it.fileModel.name })
        assertFalse(viewModel.state.value.isSearching)

        viewModel.updateSearchQuery("")

        assertEquals(emptyList<TrashMetadata>(), viewModel.state.value.searchResults)
        assertFalse(viewModel.state.value.isSearching)
    }

    @Test
    fun `restoreToDestination stores pending native confirmation context`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeFileRepository().apply {
            trashFilesResult = Result.success(listOf(trashItem("1", "Photo.jpg")))
            restoreFromTrashResultProvider = { _, destinationPath ->
                if (destinationPath != null) Result.failure(NativeConfirmationRequiredException(fakeIntentSender()))
                else Result.success(Unit)
            }
        }
        val viewModel = TrashViewModel(repository)

        advanceUntilIdle()
        viewModel.restoreToDestination(listOf("1"), "/storage/emulated/0/Download")
        advanceUntilIdle()

        assertEquals(NativeAction.RESTORE_TO_DESTINATION, viewModel.state.value.pendingNativeAction)
        assertEquals("/storage/emulated/0/Download", viewModel.state.value.pendingDestinationPath)
        assertEquals(listOf("1"), viewModel.state.value.pendingRestoreIds)
        assertNull(viewModel.state.value.error)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `restoreSelectedTrash shows destination picker when DestinationRequiredException is thrown`() = runTest(mainDispatcherRule.dispatcher) {
        val trashIds = listOf("1")
        val repository = FakeFileRepository().apply {
            trashFilesResult = Result.success(listOf(trashItem("1", "Photo.jpg")))
            restoreFromTrashResultProvider = { _, destinationPath ->
                if (destinationPath == null) Result.failure(dev.qtremors.arcile.domain.DestinationRequiredException(trashIds))
                else Result.success(Unit)
            }
        }
        val viewModel = TrashViewModel(repository)

        advanceUntilIdle()
        viewModel.toggleSelection("1")
        viewModel.restoreSelectedTrash()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.showDestinationPicker)
        assertEquals(trashIds, viewModel.state.value.selectedTrashIdsForDestination)
        assertFalse(viewModel.state.value.isLoading)
        assertNull(viewModel.state.value.error)
    }
}

private fun trashItem(id: String, name: String) = TrashMetadata(
    id = id,
    originalPath = "/storage/emulated/0/$name",
    deletionTime = 1L,
    fileModel = testFile(name = name, path = "/trash/$name"),
    sourceVolumeId = "primary",
    sourceStorageKind = StorageKind.INTERNAL
)

private fun fakeIntentSender(): IntentSender {
    return mockk(relaxed = true)
}
