package dev.qtremors.arcile.feature.browser.delegate

import dev.qtremors.arcile.core.operation.BulkFileOperationCoordinator
import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.core.storage.domain.FileBrowserRepository
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.FileMutationRepository
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import dev.qtremors.arcile.core.storage.domain.VolumeRepository
import dev.qtremors.arcile.feature.browser.BrowserUndoAction
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BrowserMutationControllerNodeRenameTest {
    private lateinit var scope: TestScope
    private lateinit var mutationRepository: FileMutationRepository
    private lateinit var context: BrowserMutationContext
    private lateinit var controller: BrowserMutationController
    private var selectionCleared = false
    private var error: UiText? = null
    private var completion: BrowserUndoAction? = null

    @Before
    fun setup() {
        scope = TestScope()
        mutationRepository = mockk(relaxed = true)
        context = BrowserMutationContext(
            currentPath = "/system",
            isVolumeRootScreen = false,
            isArchive = false,
            selectedPaths = emptyList(),
            currentNodeRef = rootNode("/system"),
            files = emptyList()
        )
        selectionCleared = false
        error = null
        completion = null
        controller = BrowserMutationController(
            initialState = BrowserDeleteWorkflowState(),
            scope = scope,
            fileBrowserRepository = mockk<FileBrowserRepository>(relaxed = true),
            fileMutationRepository = mutationRepository,
            volumeRepository = mockk<VolumeRepository>(relaxed = true),
            operationCoordinator = mockk<BulkFileOperationCoordinator>(relaxed = true),
            contextProvider = { context },
            clearSelection = { selectionCleared = true },
            onBusyChange = {},
            onError = { error = it },
            onMutationCompleted = { _, undo -> completion = undo }
        )
    }

    @Test
    fun `single rename sends the selected privileged node`() = scope.runTest {
        val original = rootNode("/system/build.prop")
        val renamed = rootNode("/system/build.conf")
        val file = model(original)
        context = context.copy(
            selectedPaths = listOf(file.absolutePath),
            files = listOf(file)
        )
        coEvery { mutationRepository.renameNode(original, "build.conf") } returns
            Result.success(model(renamed))

        controller.rename(file.absolutePath, "build.conf")
        advanceUntilIdle()

        coVerify(exactly = 1) { mutationRepository.renameNode(original, "build.conf") }
        coVerify(exactly = 0) { mutationRepository.renameFile(any(), any()) }
        assertTrue(selectionCleared)
        val undo = completion as BrowserUndoAction.Rename
        assertEquals(original, undo.originalNode)
        assertEquals(renamed, undo.renamedNode)
        assertEquals("/system/build.prop", undo.originalPath)
        assertEquals("/system/build.conf", undo.renamedPath)
        assertNull(error)
    }

    @Test
    fun `single rename retains path fallback when listing metadata is unavailable`() = scope.runTest {
        context = context.copy(
            selectedPaths = listOf("/system/build.prop"),
            files = emptyList()
        )
        coEvery { mutationRepository.renameFile("/system/build.prop", "build.conf") } returns
            Result.success(
                FileModel(
                    name = "build.conf",
                    absolutePath = "/system/build.conf"
                )
            )

        controller.rename("/system/build.prop", "build.conf")
        advanceUntilIdle()

        coVerify(exactly = 1) {
            mutationRepository.renameFile("/system/build.prop", "build.conf")
        }
        val undo = completion as BrowserUndoAction.Rename
        assertNull(undo.originalNode)
        assertNull(undo.renamedNode)
    }

    @Test
    fun `batch rename preserves every source backend in request and undo`() = scope.runTest {
        val rootOriginal = rootNode("/system/a.conf")
        val shizukuOriginal = shizukuNode("/data/b.conf")
        val rootRenamed = rootNode("/system/one.conf")
        val shizukuRenamed = shizukuNode("/data/two.conf")
        val renames = listOf(
            model(rootOriginal) to "one.conf",
            model(shizukuOriginal) to "two.conf"
        )
        coEvery {
            mutationRepository.batchRenameNodes(
                listOf(rootOriginal to "one.conf", shizukuOriginal to "two.conf")
            )
        } returns Result.success(
            listOf(rootOriginal to rootRenamed, shizukuOriginal to shizukuRenamed)
        )

        controller.batchRename(renames)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            mutationRepository.batchRenameNodes(
                listOf(rootOriginal to "one.conf", shizukuOriginal to "two.conf")
            )
        }
        coVerify(exactly = 0) { mutationRepository.batchRenameFiles(any()) }
        assertTrue(selectionCleared)
        val undo = completion as BrowserUndoAction.BatchRename
        assertEquals(listOf(rootOriginal, shizukuOriginal), undo.entries.map { it.originalNode })
        assertEquals(listOf(rootRenamed, shizukuRenamed), undo.entries.map { it.renamedNode })
        assertEquals(listOf("/system/a.conf", "/data/b.conf"), undo.entries.map { it.originalPath })
        assertEquals(listOf("/system/one.conf", "/data/two.conf"), undo.entries.map { it.renamedPath })
        assertNull(error)
    }

    @Test
    fun `batch failure keeps selection and reports backend error`() = scope.runTest {
        val original = rootNode("/system/a.conf")
        val failure = IllegalStateException("Root service disconnected")
        coEvery { mutationRepository.batchRenameNodes(listOf(original to "one.conf")) } returns
            Result.failure(failure)

        controller.batchRename(listOf(model(original) to "one.conf"))
        advanceUntilIdle()

        assertFalse(selectionCleared)
        assertNull(completion)
        assertEquals(UiText.Dynamic("Root service disconnected"), error)
    }

    @Test
    fun `archive context blocks single and batch rename`() = scope.runTest {
        val original = rootNode("/system/a.conf")
        context = context.copy(isArchive = true, files = listOf(model(original)))

        controller.rename("/system/a.conf", "one.conf")
        controller.batchRename(listOf(model(original) to "one.conf"))
        advanceUntilIdle()

        coVerify(exactly = 0) { mutationRepository.renameNode(any(), any()) }
        coVerify(exactly = 0) { mutationRepository.renameFile(any(), any()) }
        coVerify(exactly = 0) { mutationRepository.batchRenameNodes(any()) }
        assertNull(completion)
        assertFalse(selectionCleared)
    }

    @Test
    fun `empty batch is ignored without clearing selection`() = scope.runTest {
        controller.batchRename(emptyList())
        advanceUntilIdle()

        coVerify(exactly = 0) { mutationRepository.batchRenameNodes(any()) }
        assertFalse(selectionCleared)
        assertNull(completion)
    }

    private fun rootNode(path: String): StorageNodeRef = StorageNodeRef.root(
        displayPath = path,
        remoteCanonicalIdentity = path
    )

    private fun shizukuNode(path: String): StorageNodeRef = StorageNodeRef.shizuku(
        displayPath = path,
        remoteCanonicalIdentity = path
    )

    private fun model(node: StorageNodeRef): FileModel = FileModel(
        name = node.displayPath.absolutePath.substringAfterLast('/'),
        absolutePath = node.displayPath.absolutePath,
        isDirectory = false,
        extension = node.displayPath.absolutePath.substringAfterLast('.', ""),
        nodeRef = node
    )
}
