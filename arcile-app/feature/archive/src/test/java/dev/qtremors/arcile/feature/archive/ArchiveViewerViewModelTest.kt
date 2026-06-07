package dev.qtremors.arcile.feature.archive

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import dev.qtremors.arcile.core.operation.BulkFileOperationType
import dev.qtremors.arcile.core.storage.domain.ArchiveEntryModel
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.core.storage.domain.ArchiveNameEncoding
import dev.qtremors.arcile.core.storage.domain.ArchiveSummary
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.FileConflict
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.testutil.FakeBulkFileOperationCoordinator
import dev.qtremors.arcile.testutil.FakeStorageRepositoryBundle
import dev.qtremors.arcile.testutil.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ArchiveViewerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `extract prompts for conflicts before starting operation`() = runTest(mainDispatcherRule.dispatcher) {
        val archivePath = "/storage/emulated/0/Download/demo.zip"
        val repository = FakeStorageRepositoryBundle().also {
            it.archiveRepository.apply {
            archiveEntriesResultProvider = { _, _, _ -> Result.success(listOf(entry("same.txt"))) }
            archiveMetadataResultProvider = { _, _, _ -> Result.success(summary(archivePath)) }
            detectArchiveConflictsResultProvider = { _, _, _, _, _ ->
                Result.success(listOf(conflict("same.txt")))
            }
            }
        }
        val coordinator = FakeBulkFileOperationCoordinator()
        val viewModel = viewModel(archivePath, repository, coordinator)
        advanceUntilIdle()

        viewModel.extractAll()
        advanceUntilIdle()

        assertEquals(listOf("same.txt"), viewModel.state.value.pendingConflicts.map { it.sourcePath })
        assertTrue(coordinator.startedRequests.isEmpty())

        viewModel.setConflictResolution("same.txt", ConflictResolution.REPLACE)
        viewModel.confirmConflictResolutions()
        advanceUntilIdle()

        val request = coordinator.startedRequests.single()
        assertEquals(BulkFileOperationType.EXTRACT_ARCHIVE, request.type)
        assertEquals(mapOf("same.txt" to ConflictResolution.REPLACE), request.resolutions)
    }

    @Test
    fun `selected encoding is used for load and extraction request`() = runTest(mainDispatcherRule.dispatcher) {
        val archivePath = "/storage/emulated/0/Download/legacy.zip"
        val requestedEncodings = mutableListOf<ArchiveNameEncoding>()
        val repository = FakeStorageRepositoryBundle().also {
            it.archiveRepository.apply {
            archiveEntriesResultProvider = { _, _, encoding ->
                requestedEncodings += encoding
                Result.success(listOf(entry("legacy.txt")))
            }
            archiveMetadataResultProvider = { _, _, encoding ->
                requestedEncodings += encoding
                Result.success(summary(archivePath))
            }
            detectArchiveConflictsResultProvider = { _, _, _, _, encoding ->
                requestedEncodings += encoding
                Result.success(emptyList())
            }
            }
        }
        val coordinator = FakeBulkFileOperationCoordinator()
        val viewModel = viewModel(archivePath, repository, coordinator)
        advanceUntilIdle()

        viewModel.selectNameEncoding(ArchiveNameEncoding.WINDOWS_1252)
        advanceUntilIdle()
        viewModel.extractCurrentFolder()
        advanceUntilIdle()

        assertTrue(requestedEncodings.contains(ArchiveNameEncoding.WINDOWS_1252))
        assertEquals(ArchiveNameEncoding.WINDOWS_1252, coordinator.startedRequests.single().archiveNameEncoding)
    }

    private fun viewModel(
        archivePath: String,
        repository: FakeStorageRepositoryBundle,
        coordinator: FakeBulkFileOperationCoordinator
    ): ArchiveViewerViewModel =
        ArchiveViewerViewModel(
            savedStateHandle = SavedStateHandle(mapOf("archivePath" to archivePath)),
            repository = repository.archiveRepository,
            bulkFileOperationCoordinator = coordinator,
            context = mockk<Context> {
                every { getString(any()) } returns "Fallback error"
            }
        )

    private fun entry(path: String): ArchiveEntryModel =
        ArchiveEntryModel(
            name = path.substringAfterLast('/'),
            path = path,
            size = 1L,
            compressedSize = 1L,
            lastModified = 1L,
            isDirectory = false
        )

    private fun summary(path: String): ArchiveSummary =
        ArchiveSummary(
            archivePath = path,
            format = ArchiveFormat.ZIP,
            archiveSize = 1L,
            totalUncompressedSize = 1L,
            fileCount = 1,
            folderCount = 0,
            newestModifiedAt = 1L,
            oldestModifiedAt = 1L,
            hasUnreadableEntries = false
        )

    private fun conflict(path: String): FileConflict =
        FileConflict(
            sourcePath = path,
            sourceFile = FileModel(path, path, size = 1L, lastModified = 2L),
            existingFile = FileModel(path, "/storage/emulated/0/Download/demo/$path", size = 1L, lastModified = 1L)
        )
}
