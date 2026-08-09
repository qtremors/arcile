package dev.qtremors.arcile.core.ui.category

import dev.qtremors.arcile.core.operation.BulkFileOperationEvent
import dev.qtremors.arcile.core.operation.BulkFileOperationRequest
import dev.qtremors.arcile.core.operation.BulkFileOperationType
import dev.qtremors.arcile.core.storage.domain.ArchivePathResolver
import dev.qtremors.arcile.core.storage.domain.FileBrowserRepository
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.FileMutationRepository
import dev.qtremors.arcile.core.storage.domain.VolumeRepository
import dev.qtremors.arcile.testutil.FakeBulkFileOperationCoordinator
import dev.qtremors.arcile.testutil.FakeClipboardRepository
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CategoryFileActionControllerTest {
    @Test
    fun `foreign completed deletion reconciles files without presenting foreign feedback`() = runTest {
        val deletedFile = file("/storage/emulated/0/Download/deleted.pdf")
        val retainedFile = file("/storage/emulated/0/Download/retained.pdf")
        val state = MutableStateFlow(TestState(files = listOf(deletedFile, retainedFile)))
        var reloadCount = 0
        var feedbackCount = 0
        val controller = CategoryFileActionController(
            scope = this,
            state = state,
            clipboardRepository = FakeClipboardRepository(),
            fileBrowserRepository = mockk<FileBrowserRepository>(relaxed = true),
            fileMutationRepository = mockk<FileMutationRepository>(relaxed = true),
            volumeRepository = mockk<VolumeRepository>(relaxed = true),
            archivePathResolver = mockk<ArchivePathResolver>(relaxed = true),
            operationCoordinator = FakeBulkFileOperationCoordinator(),
            operationOwnerId = "documents-owner",
            files = TestState::files,
            selectedPaths = TestState::selection,
            actionState = TestState::actions,
            withSelection = { current, selection -> current.copy(selection = selection) },
            withActions = { current, actions -> current.copy(actions = actions) },
            withoutPaths = { current, paths ->
                current.copy(files = current.files.filterNot { it.absolutePath in paths })
            },
            onOperationFeedback = { feedbackCount += 1 },
            reload = { reloadCount += 1 }
        )
        val request = BulkFileOperationRequest(
            operationId = "browser-delete",
            type = BulkFileOperationType.DELETE,
            sourcePaths = listOf(deletedFile.absolutePath),
            presentationOwnerId = "browser-owner"
        )

        controller.handleOperationEvent(BulkFileOperationEvent.Completed(request))

        assertEquals(listOf(retainedFile), state.value.files)
        assertEquals(1, reloadCount)
        assertEquals(0, feedbackCount)
        assertNull(state.value.actions.activeOperation)
    }

    private data class TestState(
        val files: List<FileModel>,
        val selection: Set<String> = emptySet(),
        val actions: CategoryFileActionState = CategoryFileActionState()
    )

    private fun file(path: String) = FileModel(
        name = path.substringAfterLast('/'),
        absolutePath = path,
        extension = path.substringAfterLast('.', "")
    )
}
