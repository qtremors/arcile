package dev.qtremors.arcile.feature.browser.delegate

import dev.qtremors.arcile.core.runtime.R
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.FileConflict
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.FileRepository
import dev.qtremors.arcile.core.storage.domain.ClipboardOperation
import dev.qtremors.arcile.core.storage.domain.ClipboardState
import dev.qtremors.arcile.core.ui.UiText
import dev.qtremors.arcile.feature.browser.BrowserState
import dev.qtremors.arcile.core.operation.BulkFileOperationCoordinator
import dev.qtremors.arcile.core.operation.BulkFileOperationType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class ClipboardDelegateTest {

    private lateinit var state: MutableStateFlow<BrowserState>
    private lateinit var repository: FileRepository
    private lateinit var bulkFileOperationCoordinator: BulkFileOperationCoordinator
    private lateinit var delegate: ClipboardDelegate
    private lateinit var testScope: TestScope
    private var refreshActionCalled = false

    @Before
    fun setup() {
        testScope = TestScope(UnconfinedTestDispatcher())
        state = MutableStateFlow(BrowserState())
        repository = mockk(relaxed = true)
        bulkFileOperationCoordinator = mockk(relaxed = true)
        refreshActionCalled = false
        delegate = ClipboardDelegate(
            state = state,
            viewModelScope = testScope,
            clipboardRepository = repository,
            bulkFileOperationCoordinator = bulkFileOperationCoordinator,
            refreshAction = { refreshActionCalled = true }
        )
    }

    @Test
    fun `copySelectedToClipboard updates state correctly`() {
        val file = FileModel("test.txt", "/test.txt", 100L, 0L, false, "txt", false)
        state.value = state.value.copy(
            files = listOf(file).toPersistentList(),
            selectedFiles = setOf("/test.txt").toPersistentSet()
        )

        delegate.copySelectedToClipboard()

        val clipboardState = state.value.clipboardState
        assertNotNull(clipboardState)
        assertEquals(ClipboardOperation.COPY, clipboardState?.operation)
        assertEquals(1, clipboardState?.files?.size)
        assertEquals("/test.txt", clipboardState?.files?.first()?.absolutePath)
        assertTrue(state.value.selectedFiles.isEmpty())
        assertEquals(0L, state.value.selectedFilesTotalSize)
    }

    @Test
    fun `cutSelectedToClipboard updates state correctly`() {
        val file = FileModel("test.txt", "/test.txt", 100L, 0L, false, "txt", false)
        state.value = state.value.copy(
            files = listOf(file).toPersistentList(),
            selectedFiles = setOf("/test.txt").toPersistentSet()
        )

        delegate.cutSelectedToClipboard()

        val clipboardState = state.value.clipboardState
        assertNotNull(clipboardState)
        assertEquals(ClipboardOperation.CUT, clipboardState?.operation)
        assertEquals(1, clipboardState?.files?.size)
        assertEquals("/test.txt", clipboardState?.files?.first()?.absolutePath)
        assertTrue(state.value.selectedFiles.isEmpty())
    }

    @Test
    fun `cancelClipboard clears state and cancels active operations`() {
        state.value = state.value.copy(
            clipboardState = ClipboardState(ClipboardOperation.COPY, listOf(mockk()))
        )

        delegate.cancelClipboard()

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

        delegate.removeFromClipboard("/1.txt")
        assertEquals(1, state.value.clipboardState?.files?.size)
        assertEquals("/2.txt", state.value.clipboardState?.files?.first()?.absolutePath)

        delegate.removeFromClipboard("/2.txt")
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

        delegate.pasteFromClipboard()

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

        delegate.pasteFromClipboard()

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
    fun `dismissConflictDialog clears dialog state`() {
        val file = FileModel("test.txt", "/test.txt", 0L, 0L, false, "txt", false)
        val existing = FileModel("test.txt", "/dest/test.txt", 0L, 0L, false, "txt", false)
        state.value = state.value.copy(
            showConflictDialog = true,
            pasteConflicts = listOf(FileConflict("/test.txt", file, existing)).toPersistentList()
        )

        delegate.dismissConflictDialog()

        assertFalse(state.value.showConflictDialog)
        assertTrue(state.value.pasteConflicts.isEmpty())
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

        delegate.pasteFromClipboard()

        assertFalse(state.value.isLoading)
        assertEquals(UiText.StringResource(R.string.error_operation_already_running), state.value.error)
    }
}
