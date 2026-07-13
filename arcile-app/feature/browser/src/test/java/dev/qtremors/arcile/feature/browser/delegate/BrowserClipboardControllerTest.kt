package dev.qtremors.arcile.feature.browser.delegate

import dev.qtremors.arcile.core.runtime.R
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.FileConflict
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.ClipboardRepository
import dev.qtremors.arcile.core.storage.domain.ClipboardOperation
import dev.qtremors.arcile.core.storage.domain.ClipboardState
import dev.qtremors.arcile.core.storage.domain.FolderStats
import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.core.presentation.ClipboardController
import dev.qtremors.arcile.feature.browser.BrowserArchiveContext
import dev.qtremors.arcile.core.operation.BulkFileOperationCoordinator
import dev.qtremors.arcile.core.operation.BulkFileOperationType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BrowserClipboardControllerTest {

    private lateinit var state: MutableStateFlow<ClipboardFixtureState>
    private lateinit var repository: ClipboardRepository
    private lateinit var bulkFileOperationCoordinator: BulkFileOperationCoordinator
    private lateinit var delegate: BrowserClipboardController
    private lateinit var testScope: TestScope
    private var selectionCleared = false

    @Before
    fun setup() {
        testScope = TestScope(UnconfinedTestDispatcher())
        state = MutableStateFlow(ClipboardFixtureState())
        repository = mockk(relaxed = true)

        val dynamicClipboardState = object : kotlinx.coroutines.flow.StateFlow<ClipboardState?> {
            override val value: ClipboardState?
                get() = state.value.clipboardState
            override val replayCache: List<ClipboardState?>
                get() = listOf(state.value.clipboardState)
            override suspend fun collect(collector: kotlinx.coroutines.flow.FlowCollector<ClipboardState?>): Nothing {
                state.collect { collector.emit(it.clipboardState) }
            }
        }

        io.mockk.every { repository.clipboardState } returns dynamicClipboardState
        io.mockk.every { repository.setClipboardState(any()) } answers {
            val value = firstArg<ClipboardState?>()
            state.update { it.copy(clipboardState = value) }
        }
        io.mockk.every { repository.clearClipboardState() } answers {
            state.update { it.copy(clipboardState = null) }
        }

        bulkFileOperationCoordinator = mockk(relaxed = true)
        selectionCleared = false
        delegate = BrowserClipboardController(
            scope = testScope,
            clipboardRepository = repository,
            clipboardController = ClipboardController(repository),
            operationCoordinator = bulkFileOperationCoordinator,
            contextProvider = {
                val current = state.value
                BrowserClipboardContext(
                    archiveContext = current.archiveContext,
                    currentPath = current.currentPath,
                    clipboardState = current.clipboardState,
                    selectedPaths = current.selectedFiles,
                    files = current.files,
                    folderStats = current.folderStatsByPath
                )
            },
            clearSelection = {
                selectionCleared = true
                state.update {
                    it.copy(
                        selectedFiles = kotlinx.collections.immutable.persistentSetOf(),
                        selectedFilesTotalSize = 0L
                    )
                }
            },
            onConflicts = { conflicts ->
                state.update {
                    it.copy(
                        pasteConflicts = conflicts.toPersistentList(),
                        showConflictDialog = true
                    )
                }
            },
            onDismissConflicts = {
                state.update {
                    it.copy(
                        pasteConflicts = kotlinx.collections.immutable.persistentListOf(),
                        showConflictDialog = false
                    )
                }
            },
            onBusyChange = { busy -> state.update { it.copy(isLoading = busy) } },
            onError = { error -> state.update { it.copy(error = error) } }
        )
    }

    @Test
    fun `copySelectedToClipboard updates state correctly`() {
        val file = FileModel("test.txt", "/test.txt", 100L, 0L, false, "txt", false)
        state.value = state.value.copy(
            files = listOf(file).toPersistentList(),
            selectedFiles = setOf("/test.txt").toPersistentSet()
        )

        delegate.copySelected()

        val clipboardState = state.value.clipboardState
        assertNotNull(clipboardState)
        assertEquals(ClipboardOperation.COPY, clipboardState?.operation)
        assertEquals(1, clipboardState?.files?.size)
        assertEquals("/test.txt", clipboardState?.files?.first()?.absolutePath)
        assertTrue(state.value.selectedFiles.isEmpty())
        assertEquals(0L, state.value.selectedFilesTotalSize)
        assertTrue(selectionCleared)
    }

    @Test
    fun `cutSelectedToClipboard updates state correctly`() {
        val file = FileModel("test.txt", "/test.txt", 100L, 0L, false, "txt", false)
        state.value = state.value.copy(
            files = listOf(file).toPersistentList(),
            selectedFiles = setOf("/test.txt").toPersistentSet()
        )

        delegate.cutSelected()

        val clipboardState = state.value.clipboardState
        assertNotNull(clipboardState)
        assertEquals(ClipboardOperation.CUT, clipboardState?.operation)
        assertEquals(1, clipboardState?.files?.size)
        assertEquals("/test.txt", clipboardState?.files?.first()?.absolutePath)
        assertTrue(state.value.selectedFiles.isEmpty())
        assertTrue(selectionCleared)
    }

    @Test
    fun `cancelClipboard clears state and cancels active operations`() {
        state.value = state.value.copy(
            clipboardState = ClipboardState(ClipboardOperation.COPY, listOf(mockk()))
        )

        delegate.cancel()

        assertNull(state.value.clipboardState)
        coVerify(exactly = 1) { bulkFileOperationCoordinator.cancelActiveOperation() }
    }

    @Test
    fun `removeFromClipboard removes file and clears state if empty`() {
        val file1 = FileModel("1.txt", "/1.txt", 0L, 0L, false, "txt", false)
        val file2 = FileModel("2.txt", "/2.txt", 0L, 0L, false, "txt", false)
        state.value = state.value.copy(
            clipboardState = ClipboardState(ClipboardOperation.COPY, listOf(file1, file2))
        )

        delegate.remove("/1.txt")
        assertEquals(1, state.value.clipboardState?.files?.size)
        assertEquals("/2.txt", state.value.clipboardState?.files?.first()?.absolutePath)

        delegate.remove("/2.txt")
        assertNull(state.value.clipboardState)
    }

    @Test
    fun `pasteFromClipboard starts operation when no conflicts`() = testScope.runTest {
        val file = FileModel("test.txt", "/test.txt", 0L, 0L, false, "txt", false)
        state.value = state.value.copy(
            clipboardState = ClipboardState(ClipboardOperation.COPY, listOf(file)),
            currentPath = "/dest"
        )
        coEvery { repository.detectCopyConflicts(listOf("/test.txt"), "/dest") } returns Result.success(emptyList())
        coEvery { bulkFileOperationCoordinator.startOperation(any(), any(), any(), any()) } returns true

        delegate.paste()

        assertFalse(state.value.isLoading)
        assertNull(state.value.error)
        coVerify(exactly = 1) {
            bulkFileOperationCoordinator.startOperation(
                type = BulkFileOperationType.COPY,
                sourcePaths = listOf("/test.txt"),
                destinationPath = "/dest",
                resolutions = emptyMap()
            )
        }
    }

    @Test
    fun `pasteFromClipboard shows conflict dialog when conflicts exist`() = testScope.runTest {
        val file = FileModel("test.txt", "/test.txt", 0L, 0L, false, "txt", false)
        val existing = FileModel("test.txt", "/dest/test.txt", 0L, 0L, false, "txt", false)
        state.value = state.value.copy(
            clipboardState = ClipboardState(ClipboardOperation.COPY, listOf(file)),
            currentPath = "/dest"
        )
        val conflicts = listOf(FileConflict("/test.txt", file, existing))
        coEvery { repository.detectCopyConflicts(listOf("/test.txt"), "/dest") } returns Result.success(conflicts)

        delegate.paste()

        assertFalse(state.value.isLoading)
        assertTrue(state.value.showConflictDialog)
        assertEquals(conflicts, state.value.pasteConflicts)
        coVerify(exactly = 0) { bulkFileOperationCoordinator.startOperation(any(), any(), any(), any()) }
    }

    @Test
    fun `resolveConflicts starts operation with resolutions`() = testScope.runTest {
        val file = FileModel("test.txt", "/test.txt", 0L, 0L, false, "txt", false)
        val existing = FileModel("test.txt", "/dest/test.txt", 0L, 0L, false, "txt", false)
        state.value = state.value.copy(
            clipboardState = ClipboardState(ClipboardOperation.CUT, listOf(file)),
            currentPath = "/dest",
            showConflictDialog = true,
            pasteConflicts = listOf(FileConflict("/test.txt", file, existing)).toPersistentList()
        )
        coEvery { bulkFileOperationCoordinator.startOperation(any(), any(), any(), any()) } returns true

        val resolutions = mapOf("/test.txt" to ConflictResolution.REPLACE)
        delegate.resolveConflicts(resolutions)

        assertFalse(state.value.showConflictDialog)
        assertTrue(state.value.pasteConflicts.isEmpty())
        assertFalse(state.value.isLoading)

        coVerify(exactly = 1) {
            bulkFileOperationCoordinator.startOperation(
                type = BulkFileOperationType.MOVE,
                sourcePaths = listOf("/test.txt"),
                destinationPath = "/dest",
                resolutions = resolutions
            )
        }
    }

    @Test
    fun `pasteFromClipboard handles already running operation`() = testScope.runTest {
        val file = FileModel("test.txt", "/test.txt", 0L, 0L, false, "txt", false)
        state.value = state.value.copy(
            clipboardState = ClipboardState(ClipboardOperation.COPY, listOf(file)),
            currentPath = "/dest"
        )
        coEvery { repository.detectCopyConflicts(listOf("/test.txt"), "/dest") } returns Result.success(emptyList())
        coEvery { bulkFileOperationCoordinator.startOperation(any(), any(), any(), any()) } returns false

        delegate.paste()

        assertFalse(state.value.isLoading)
        assertEquals(UiText.StringResource(R.string.error_operation_already_running), state.value.error)
    }
}

private data class ClipboardFixtureState(
    val archiveContext: BrowserArchiveContext? = null,
    val currentPath: String = "",
    val clipboardState: ClipboardState? = null,
    val selectedFiles: Set<String> = emptySet(),
    val selectedFilesTotalSize: Long = 0L,
    val files: List<FileModel> = emptyList(),
    val folderStatsByPath: Map<String, FolderStats> = emptyMap(),
    val pasteConflicts: List<FileConflict> = emptyList(),
    val showConflictDialog: Boolean = false,
    val isLoading: Boolean = true,
    val error: UiText? = null
)
