package dev.qtremors.arcile.feature.trash

import dev.qtremors.arcile.core.storage.domain.CategoryStorage
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.FileConflict
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.StorageAuthorizationOperation
import dev.qtremors.arcile.core.storage.domain.StorageAuthorizationRequirement
import dev.qtremors.arcile.core.storage.domain.StorageMutationResult
import dev.qtremors.arcile.core.storage.domain.SearchFilters
import dev.qtremors.arcile.core.storage.domain.StorageInfo
import dev.qtremors.arcile.core.storage.domain.StorageKind
import dev.qtremors.arcile.core.storage.domain.StorageScope
import dev.qtremors.arcile.core.storage.domain.StorageVolume
import dev.qtremors.arcile.core.storage.domain.TrashMetadata
import dev.qtremors.arcile.core.storage.domain.TrashRestoreStatus
import dev.qtremors.arcile.testutil.MainDispatcherRule
import dev.qtremors.arcile.testutil.FakeStorageRepositoryBundle
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
        val repository = FakeStorageRepositoryBundle().apply {
            trashFilesResult = Result.success(items)
        }
        val viewModel = TrashViewModel(
            trashRepository = repository.trashRepository,
            volumeRepository = repository.volumeRepository
        )

        advanceUntilIdle()
        viewModel.toggleSelection("keep-1")
        viewModel.toggleSelection("missing")

        viewModel.loadTrashFiles()
        advanceUntilIdle()

        assertEquals(setOf("keep-1"), viewModel.state.value.selectedFiles)
    }

    @Test
    fun `updateSearchQuery filters trash items after debounce and clears on blank`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeStorageRepositoryBundle().apply {
            trashFilesResult = Result.success(listOf(
                trashItem("1", "Photo.jpg"),
                trashItem("2", "notes.txt"),
                trashItem("3", "photo-backup.png")
            ))
        }
        val viewModel = TrashViewModel(
            trashRepository = repository.trashRepository,
            volumeRepository = repository.volumeRepository
        )

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
        val requirement = StorageAuthorizationRequirement(
            requestId = "restore-request",
            operation = StorageAuthorizationOperation.RESTORE_TRASH
        )
        val repository = FakeStorageRepositoryBundle().apply {
            trashFilesResult = Result.success(listOf(trashItem("1", "Photo.jpg")))
            restoreFromTrashMutationResultProvider = { _, destinationPath ->
                if (destinationPath != null) {
                    StorageMutationResult.AuthorizationRequired(requirement)
                } else {
                    StorageMutationResult.Completed
                }
            }
        }
        val viewModel = TrashViewModel(
            trashRepository = repository.trashRepository,
            volumeRepository = repository.volumeRepository
        )

        advanceUntilIdle()
        viewModel.restoreToDestination(listOf("1"), "/storage/emulated/0/Download")
        advanceUntilIdle()

        assertEquals(
            TrashAuthorizationAction.RESTORE_TO_DESTINATION,
            viewModel.state.value.pendingAuthorizationAction
        )
        assertEquals(requirement, viewModel.state.value.pendingAuthorization)
        assertEquals("/storage/emulated/0/Download", viewModel.state.value.pendingDestinationPath)
        assertEquals(listOf("1"), viewModel.state.value.pendingRestoreIds)
        assertNull(viewModel.state.value.error)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `stale authorization result cannot consume current request`() = runTest(mainDispatcherRule.dispatcher) {
        val requirement = restoreRequirement("current-request")
        var restoreCalls = 0
        val repository = authorizationRepository(requirement) { restoreCalls += 1 }
        val viewModel = TrashViewModel(repository.trashRepository, repository.volumeRepository)

        advanceUntilIdle()
        viewModel.restoreToDestination(listOf("1"), "/storage/emulated/0/Download")
        advanceUntilIdle()
        viewModel.handleAuthorizationResult("stale-request", confirmed = true)
        advanceUntilIdle()

        assertEquals(requirement, viewModel.state.value.pendingAuthorization)
        assertEquals(1, restoreCalls)
    }

    @Test
    fun `denied authorization clears retry context without repeating mutation`() = runTest(mainDispatcherRule.dispatcher) {
        val requirement = restoreRequirement("denied-request")
        var restoreCalls = 0
        val repository = authorizationRepository(requirement) { restoreCalls += 1 }
        val viewModel = TrashViewModel(repository.trashRepository, repository.volumeRepository)

        advanceUntilIdle()
        viewModel.restoreToDestination(listOf("1"), "/storage/emulated/0/Download")
        advanceUntilIdle()
        viewModel.handleAuthorizationResult(requirement.requestId, confirmed = false)

        assertNull(viewModel.state.value.pendingAuthorization)
        assertNull(viewModel.state.value.pendingAuthorizationAction)
        assertNull(viewModel.state.value.pendingDestinationPath)
        assertTrue(viewModel.state.value.pendingRestoreIds.isEmpty())
        assertEquals(1, restoreCalls)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `confirmed authorization retries stored mutation exactly once`() = runTest(mainDispatcherRule.dispatcher) {
        val requirement = restoreRequirement("confirmed-request")
        var restoreCalls = 0
        val repository = FakeStorageRepositoryBundle().apply {
            trashFilesResult = Result.success(listOf(trashItem("1", "Photo.jpg")))
            restoreFromTrashMutationResultProvider = { _, destinationPath ->
                if (destinationPath == null) {
                    StorageMutationResult.Completed
                } else if (++restoreCalls == 1) {
                    StorageMutationResult.AuthorizationRequired(requirement)
                } else {
                    StorageMutationResult.Completed
                }
            }
        }
        val viewModel = TrashViewModel(repository.trashRepository, repository.volumeRepository)

        advanceUntilIdle()
        viewModel.restoreToDestination(listOf("1"), "/storage/emulated/0/Download")
        advanceUntilIdle()
        viewModel.handleAuthorizationResult(requirement.requestId, confirmed = true)
        advanceUntilIdle()

        assertEquals(2, restoreCalls)
        assertNull(viewModel.state.value.pendingAuthorization)
        assertNull(viewModel.state.value.error)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `unavailable authorization clears context and reports operation error`() = runTest(mainDispatcherRule.dispatcher) {
        val requirement = restoreRequirement("expired-request")
        val repository = authorizationRepository(requirement) {}
        val viewModel = TrashViewModel(repository.trashRepository, repository.volumeRepository)

        advanceUntilIdle()
        viewModel.restoreToDestination(listOf("1"), "/storage/emulated/0/Download")
        advanceUntilIdle()
        viewModel.handleAuthorizationUnavailable(requirement.requestId)

        assertNull(viewModel.state.value.pendingAuthorization)
        assertNull(viewModel.state.value.pendingAuthorizationAction)
        assertEquals(
            dev.qtremors.arcile.core.presentation.UiText.StringResource(
                dev.qtremors.arcile.core.ui.R.string.error_restore_files_failed
            ),
            viewModel.state.value.error
        )
        assertFalse(viewModel.state.value.isLoading)
    }

    private fun restoreRequirement(requestId: String) = StorageAuthorizationRequirement(
        requestId = requestId,
        operation = StorageAuthorizationOperation.RESTORE_TRASH
    )

    private fun authorizationRepository(
        requirement: StorageAuthorizationRequirement,
        onRestore: () -> Unit
    ) = FakeStorageRepositoryBundle().apply {
        trashFilesResult = Result.success(listOf(trashItem("1", "Photo.jpg")))
        restoreFromTrashMutationResultProvider = { _, destinationPath ->
            if (destinationPath != null) {
                onRestore()
                StorageMutationResult.AuthorizationRequired(requirement)
            } else {
                StorageMutationResult.Completed
            }
        }
    }

    @Test
    fun `restoreSelectedTrash shows destination picker when DestinationRequiredException is thrown`() = runTest(mainDispatcherRule.dispatcher) {
        val trashIds = listOf("1")
        val repository = FakeStorageRepositoryBundle().apply {
            trashFilesResult = Result.success(listOf(trashItem("1", "Photo.jpg")))
            restoreFromTrashResultProvider = { _, destinationPath ->
                if (destinationPath == null) Result.failure(dev.qtremors.arcile.core.storage.domain.DestinationRequiredException(trashIds))
                else Result.success(Unit)
            }
        }
        val viewModel = TrashViewModel(
            trashRepository = repository.trashRepository,
            volumeRepository = repository.volumeRepository
        )

        advanceUntilIdle()
        viewModel.toggleSelection("1")
        viewModel.restoreSelectedTrash()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.showDestinationPicker)
        assertEquals(trashIds, viewModel.state.value.selectedTrashIdsForDestination)
        assertFalse(viewModel.state.value.isLoading)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `restoreTrashItem restores only the requested item`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeStorageRepositoryBundle().apply {
            trashFilesResult = Result.success(
                listOf(
                    trashItem("1", "Photo.jpg"),
                    trashItem("2", "Notes.txt")
                )
            )
        }
        val viewModel = TrashViewModel(
            trashRepository = repository.trashRepository,
            volumeRepository = repository.volumeRepository
        )

        advanceUntilIdle()
        viewModel.toggleSelection("2")
        viewModel.restoreTrashItem("1")
        advanceUntilIdle()

        assertEquals(listOf("1"), repository.restoreFromTrashRequests.single().trashIds)
        assertEquals(setOf("2"), viewModel.state.value.selectedFiles)
    }

    @Test
    fun `restoreTrashItem sends recovered item to destination picker`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeStorageRepositoryBundle().apply {
            trashFilesResult = Result.success(
                listOf(trashItem("1", "Recovered Item (1)", TrashRestoreStatus.RECOVERED_ITEM))
            )
        }
        val viewModel = TrashViewModel(
            trashRepository = repository.trashRepository,
            volumeRepository = repository.volumeRepository
        )

        advanceUntilIdle()
        viewModel.restoreTrashItem("1")

        assertTrue(viewModel.state.value.showDestinationPicker)
        assertEquals(listOf("1"), viewModel.state.value.selectedTrashIdsForDestination)
        assertTrue(repository.restoreFromTrashRequests.isEmpty())
    }

    @Test
    fun `filter exposes restore status groups`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeStorageRepositoryBundle().apply {
            trashFilesResult = Result.success(
                listOf(
                    trashItem("1", "ready.txt", TrashRestoreStatus.ORIGINAL_AVAILABLE),
                    trashItem("2", "conflict.txt", TrashRestoreStatus.ORIGINAL_CONFLICT_RENAME),
                    trashItem("3", "missing.txt", TrashRestoreStatus.DESTINATION_REQUIRED),
                    trashItem("4", "recovered", TrashRestoreStatus.RECOVERED_ITEM)
                )
            )
        }
        val viewModel = TrashViewModel(
            trashRepository = repository.trashRepository,
            volumeRepository = repository.volumeRepository
        )

        advanceUntilIdle()
        viewModel.updateFilter(TrashFilter.CAN_RESTORE)
        assertEquals(listOf("ready.txt", "conflict.txt"), viewModel.state.value.visibleTrashFiles.map { it.fileModel.name })

        viewModel.updateFilter(TrashFilter.NEEDS_DESTINATION)
        assertEquals(listOf("missing.txt"), viewModel.state.value.visibleTrashFiles.map { it.fileModel.name })

        viewModel.updateFilter(TrashFilter.RECOVERED)
        assertEquals(listOf("recovered"), viewModel.state.value.visibleTrashFiles.map { it.fileModel.name })
    }

    @Test
    fun `sort reorders visible trash files`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeStorageRepositoryBundle().apply {
            trashFilesResult = Result.success(
                listOf(
                    trashItem("1", "b.txt").copy(deletionTime = 2L, fileModel = testFile(name = "b.txt", path = "/trash/b.txt", size = 10L)),
                    trashItem("2", "a.txt").copy(deletionTime = 1L, fileModel = testFile(name = "a.txt", path = "/trash/a.txt", size = 20L))
                )
            )
        }
        val viewModel = TrashViewModel(
            trashRepository = repository.trashRepository,
            volumeRepository = repository.volumeRepository
        )

        advanceUntilIdle()
        viewModel.updateSortOption(TrashSortOption.NAME_ASC)
        assertEquals(listOf("a.txt", "b.txt"), viewModel.state.value.visibleTrashFiles.map { it.fileModel.name })

        viewModel.updateSortOption(TrashSortOption.SIZE_LARGEST)
        assertEquals(listOf("a.txt", "b.txt"), viewModel.state.value.visibleTrashFiles.map { it.fileModel.name })
    }

    @Test
    fun `restoreSelectedTrash opens destination picker with full selection when any item needs destination`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeStorageRepositoryBundle().apply {
            trashFilesResult = Result.success(
                listOf(
                    trashItem("1", "Recovered Item (1)", TrashRestoreStatus.RECOVERED_ITEM),
                    trashItem("2", "ready.txt", TrashRestoreStatus.ORIGINAL_AVAILABLE)
                )
            )
        }
        val viewModel = TrashViewModel(
            trashRepository = repository.trashRepository,
            volumeRepository = repository.volumeRepository
        )

        advanceUntilIdle()
        viewModel.toggleSelection("1")
        viewModel.toggleSelection("2")
        viewModel.restoreSelectedTrash()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.showDestinationPicker)
        assertEquals(listOf("1", "2"), viewModel.state.value.selectedTrashIdsForDestination)
        assertTrue(repository.restoreFromTrashRequests.isEmpty())
    }

    @Test
    fun `openPropertiesForSelection exposes trash-specific properties`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeStorageRepositoryBundle().apply {
            trashFilesResult = Result.success(listOf(trashItem("1", "Photo.jpg")))
        }
        val viewModel = TrashViewModel(
            trashRepository = repository.trashRepository,
            volumeRepository = repository.volumeRepository
        )

        advanceUntilIdle()
        viewModel.toggleSelection("1")
        viewModel.openPropertiesForSelection()

        assertTrue(viewModel.state.value.isPropertiesVisible)
        assertEquals("Photo.jpg", viewModel.state.value.properties?.title)
        assertTrue(viewModel.state.value.properties?.rows?.any { it.first == "Trash payload" } == true)
    }
}

private fun trashItem(
    id: String,
    name: String,
    restoreStatus: TrashRestoreStatus = TrashRestoreStatus.ORIGINAL_AVAILABLE
) = TrashMetadata(
    id = id,
    originalPath = "/storage/emulated/0/$name",
    deletionTime = 1L,
    fileModel = testFile(name = name, path = "/trash/$name"),
    sourceVolumeId = "primary",
    sourceStorageKind = StorageKind.INTERNAL,
    restoreStatus = restoreStatus
)
