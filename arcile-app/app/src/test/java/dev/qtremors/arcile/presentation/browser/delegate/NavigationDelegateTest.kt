package dev.qtremors.arcile.presentation.browser.delegate

import androidx.lifecycle.SavedStateHandle
import dev.qtremors.arcile.data.BrowserPreferencesStore
import dev.qtremors.arcile.domain.FileModel
import dev.qtremors.arcile.domain.FileRepository
import dev.qtremors.arcile.domain.SearchFilters
import dev.qtremors.arcile.domain.StorageScope
import dev.qtremors.arcile.presentation.browser.BrowserState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

import kotlinx.coroutines.test.advanceUntilIdle

import dev.qtremors.arcile.testutil.FakeFileRepository

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class NavigationDelegateTest {

    private lateinit var testScope: TestScope
    private lateinit var repository: FakeFileRepository
    private lateinit var browserPreferencesRepository: BrowserPreferencesStore
    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var state: MutableStateFlow<BrowserState>
    private lateinit var delegate: NavigationDelegate
    private var onClearSearchCalled = false

    @Before
    fun setup() {
        testScope = TestScope(UnconfinedTestDispatcher())
        
        val testVolume = dev.qtremors.arcile.domain.StorageVolume(
            id = "vol1",
            storageKey = "vol1",
            name = "Internal",
            path = "/storage/emulated/0",
            totalBytes = 1000L,
            freeBytes = 500L,
            isPrimary = true,
            isRemovable = false,
            kind = dev.qtremors.arcile.domain.StorageKind.INTERNAL
        )

        repository = FakeFileRepository(volumes = listOf(testVolume))
        
        browserPreferencesRepository = mockk(relaxed = true) {
            every { preferencesFlow } returns kotlinx.coroutines.flow.flowOf(dev.qtremors.arcile.domain.BrowserPreferences())
            coEvery { updateLastOpenedLocation(any(), any()) } returns Unit
        }
        savedStateHandle = mockk(relaxed = true)
        
        state = MutableStateFlow(BrowserState(storageVolumes = listOf(testVolume)))
        onClearSearchCalled = false

        delegate = NavigationDelegate(
            state = state,
            viewModelScope = testScope,
            repository = repository,
            browserPreferencesRepository = browserPreferencesRepository,
            savedStateHandle = savedStateHandle,
            onClearSearch = { onClearSearchCalled = true }
        )
    }

    @Test
    fun `openVolumeRoots clears path and category state`() = testScope.runTest {
        state.value = state.value.copy(
            currentPath = "/storage/emulated/0/some/path",
            activeCategoryName = "Images",
            isCategoryScreen = true
        )

        delegate.openVolumeRoots()
        advanceUntilIdle()

        assertTrue(state.value.isVolumeRootScreen)
        assertFalse(state.value.isCategoryScreen)
        assertEquals("", state.value.currentPath)
        assertEquals("", state.value.activeCategoryName)
        verify { savedStateHandle.set("isVolumeRootScreen", true) }
        verify { savedStateHandle.set("isCategoryScreen", false) }
    }

    @Test
    fun `openFileBrowser from explicit root entry does not overwrite last opened location`() = testScope.runTest {
        repository.filesByPath = mapOf("/storage/emulated/0" to emptyList())

        delegate.openFileBrowser(restorePersistentLocation = false)
        advanceUntilIdle()

        assertEquals("/storage/emulated/0", state.value.currentPath)
        coVerify(exactly = 0) { browserPreferencesRepository.updateLastOpenedLocation(any(), any()) }
    }

    @Test
    fun `openFileBrowser restores persisted location for swipe entry`() = testScope.runTest {
        every { browserPreferencesRepository.preferencesFlow } returns kotlinx.coroutines.flow.flowOf(
            dev.qtremors.arcile.domain.BrowserPreferences(
                lastOpenedPath = "/storage/emulated/0/Documents",
                lastOpenedVolumeId = "vol1"
            )
        )
        repository.filesByPath = mapOf("/storage/emulated/0/Documents" to emptyList())

        delegate.openFileBrowser(restorePersistentLocation = true)
        advanceUntilIdle()

        assertEquals("/storage/emulated/0/Documents", state.value.currentPath)
        coVerify { browserPreferencesRepository.updateLastOpenedLocation("/storage/emulated/0/Documents", "vol1") }
    }

    @Test
    fun `navigateBack returns false when history is empty`() = testScope.runTest {
        state.value = state.value.copy(
            currentPath = "/storage/emulated/0/some/path"
        )
        // history is empty by default because we just instantiated the delegate

        val result = delegate.navigateBack()
        advanceUntilIdle()

        assertFalse(result)
    }

    @Test
    fun `navigateToSpecificFolder saves history, then navigateBack pops history and loads directory`() = testScope.runTest {
        state.value = state.value.copy(currentPath = "/storage/emulated/0/parent")
        repository.filesByPath = mapOf(
            "/storage/emulated/0/parent/child" to emptyList(),
            "/storage/emulated/0/parent" to listOf(FileModel("child", "/storage/emulated/0/parent/child", 0L, 0L, true, "", false))
        )

        // 1. Navigate forward (pushes "/storage/emulated/0/parent" to history)
        delegate.navigateToSpecificFolder("/storage/emulated/0/parent/child")
        advanceUntilIdle()

        assertEquals("/storage/emulated/0/parent/child", state.value.currentPath)
        verify { savedStateHandle.set("currentPath", "/storage/emulated/0/parent/child") }

        // 3. Navigate back (pops "/storage/emulated/0")
        val result = delegate.navigateBack()
        advanceUntilIdle()

        assertTrue(result)
        assertEquals("/storage/emulated/0", state.value.currentPath)
        
        // 4. Try navigating back again, should fail since history is empty now
        assertFalse(delegate.navigateBack())
    }

    @Test
    fun `navigateToCategory updates state and loads category files`() = testScope.runTest {
        state.value = state.value.copy(currentPath = "/storage/emulated/0/Music")
        val files = listOf(FileModel(
            name = "test",
            absolutePath = "/test",
            size = 0L,
            lastModified = 0L,
            isDirectory = false,
            extension = "",
            isHidden = false
        ))
        repository.filesByCategory = mapOf("Images" to files)

        delegate.navigateToCategory("Images", "vol1")
        advanceUntilIdle()

        assertTrue(state.value.isCategoryScreen)
        assertEquals("Images", state.value.activeCategoryName)
        assertEquals("", state.value.currentPath)
        assertEquals("vol1", state.value.currentVolumeId)
        assertFalse(state.value.isLoading)
        assertEquals(files, state.value.files)
    }

    @Test
    fun `navigateToFolder clears previous files while loading new directory`() = testScope.runTest {
        val previousFiles = listOf(FileModel("old", "/storage/emulated/0/old", 0L, 0L, false, "", false))
        val loadedFiles = listOf(FileModel("new", "/storage/emulated/0/new", 0L, 0L, false, "", false))
        val deferredFiles = CompletableDeferred<Result<List<FileModel>>>()
        repository.listFilesResultProvider = { deferredFiles.await() }
        state.value = state.value.copy(
            currentPath = "/storage/emulated/0",
            currentVolumeId = "vol1",
            files = previousFiles
        )

        delegate.navigateToFolder("/storage/emulated/0/Downloads")

        assertTrue(state.value.isLoading)
        assertEquals(emptyList<FileModel>(), state.value.files)

        deferredFiles.complete(Result.success(loadedFiles))
        advanceUntilIdle()

        assertFalse(state.value.isLoading)
        assertEquals(loadedFiles, state.value.files)
    }

    @Test
    fun `navigateToCategory clears previous files while loading category`() = testScope.runTest {
        val previousFiles = listOf(FileModel("old", "/storage/emulated/0/old", 0L, 0L, false, "", false))
        val loadedFiles = listOf(FileModel("image", "/storage/emulated/0/image.jpg", 0L, 0L, false, "jpg", false))
        val deferredFiles = CompletableDeferred<Result<List<FileModel>>>()
        repository.filesByCategoryResultProvider = { _, _ -> deferredFiles.await() }
        state.value = state.value.copy(
            currentPath = "/storage/emulated/0",
            currentVolumeId = "vol1",
            files = previousFiles
        )

        delegate.navigateToCategory("Images", "vol1")

        assertTrue(state.value.isLoading)
        assertEquals(emptyList<FileModel>(), state.value.files)

        deferredFiles.complete(Result.success(loadedFiles))
        advanceUntilIdle()

        assertFalse(state.value.isLoading)
        assertEquals(loadedFiles, state.value.files)
    }
}
