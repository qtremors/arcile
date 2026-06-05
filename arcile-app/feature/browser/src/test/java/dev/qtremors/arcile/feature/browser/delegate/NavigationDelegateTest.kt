package dev.qtremors.arcile.feature.browser.delegate

import androidx.lifecycle.SavedStateHandle
import dev.qtremors.arcile.core.storage.domain.BrowserPreferencesStore
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.FileRepository
import dev.qtremors.arcile.core.storage.domain.SearchFilters
import dev.qtremors.arcile.core.storage.domain.StorageScope
import dev.qtremors.arcile.feature.browser.BrowserState
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
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

import kotlinx.coroutines.test.advanceUntilIdle

import dev.qtremors.arcile.testutil.FakeStorageRepositoryBundle

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class NavigationDelegateTest {

    private lateinit var testScope: TestScope
    private lateinit var repository: FakeStorageRepositoryBundle
    private lateinit var browserPreferencesRepository: BrowserPreferencesStore
    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var state: MutableStateFlow<BrowserState>
    private lateinit var delegate: NavigationDelegate
    private var onClearSearchCalled = false

    @Before
    fun setup() {
        testScope = TestScope(UnconfinedTestDispatcher())
        
        val testVolume = dev.qtremors.arcile.core.storage.domain.StorageVolume(
            id = "vol1",
            storageKey = "vol1",
            name = "Internal",
            path = "/storage/emulated/0",
            totalBytes = 1000L,
            freeBytes = 500L,
            isPrimary = true,
            isRemovable = false,
            kind = dev.qtremors.arcile.core.storage.domain.StorageKind.INTERNAL
        )

        repository = FakeStorageRepositoryBundle(volumes = listOf(testVolume))
        
        browserPreferencesRepository = mockk(relaxed = true) {
            every { preferencesFlow } returns kotlinx.coroutines.flow.flowOf(dev.qtremors.arcile.core.storage.domain.BrowserPreferences())
            coEvery { updateLastOpenedLocation(any(), any()) } returns Unit
        }
        savedStateHandle = mockk(relaxed = true)
        
        state = MutableStateFlow(BrowserState(storageVolumes = listOf(testVolume).toPersistentList()))
        onClearSearchCalled = false

        delegate = NavigationDelegate(
            state = state,
            viewModelScope = testScope,
            fileBrowserRepository = repository.fileBrowserRepository,
            searchRepository = repository.searchRepository,
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
            dev.qtremors.arcile.core.storage.domain.BrowserPreferences(
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
    fun `navigateBack clears selection before navigating folder history`() = testScope.runTest {
        repository.filesByPath = mapOf(
            "/storage/emulated/0/parent/child" to emptyList(),
            "/storage/emulated/0" to listOf(FileModel("parent", "/storage/emulated/0/parent", 0L, 0L, true, "", false))
        )

        delegate.navigateToSpecificFolder("/storage/emulated/0/parent/child")
        advanceUntilIdle()
        state.update {
            it.copy(
                selectedFiles = setOf("/storage/emulated/0/parent/child/file.txt").toPersistentSet(),
                selectedFilesTotalSize = 123L
            )
        }

        val result = delegate.navigateBack()
        advanceUntilIdle()

        assertTrue(result)
        assertEquals("/storage/emulated/0/parent/child", state.value.currentPath)
        assertTrue(state.value.selectedFiles.isEmpty())
        assertEquals(0L, state.value.selectedFilesTotalSize)
    }

    @Test
    fun `navigateBack returns false from category screen so app back stack can handle it`() = testScope.runTest {
        state.value = state.value.copy(
            isCategoryScreen = true,
            activeCategoryName = "Images",
            currentPath = "",
            currentVolumeId = "vol1"
        )

        val result = delegate.navigateBack()
        advanceUntilIdle()

        assertFalse(result)
        assertTrue(state.value.isCategoryScreen)
        assertEquals("Images", state.value.activeCategoryName)
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
    fun `navigateToSpecificFolder can skip initial root history for external app origins`() = testScope.runTest {
        repository.filesByPath = mapOf("/storage/emulated/0/Download" to emptyList())

        delegate.navigateToSpecificFolder(
            path = "/storage/emulated/0/Download",
            seedInitialPathHistory = false
        )
        advanceUntilIdle()

        assertEquals("/storage/emulated/0/Download", state.value.currentPath)
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
            files = previousFiles.toPersistentList()
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
    fun `newer directory navigation prevents stale listing from updating state`() = testScope.runTest {
        val firstFiles = listOf(FileModel("old", "/storage/emulated/0/Slow/old.txt", 0L, 0L, false, "txt", false))
        val secondFiles = listOf(FileModel("new", "/storage/emulated/0/Fast/new.txt", 0L, 0L, false, "txt", false))
        val firstListing = CompletableDeferred<Result<List<FileModel>>>()
        val secondListing = CompletableDeferred<Result<List<FileModel>>>()
        repository.listFilesResultProvider = { path ->
            when (path) {
                "/storage/emulated/0/Slow" -> firstListing.await()
                "/storage/emulated/0/Fast" -> secondListing.await()
                else -> Result.success(emptyList())
            }
        }
        state.value = state.value.copy(currentPath = "/storage/emulated/0", currentVolumeId = "vol1")

        delegate.navigateToFolder("/storage/emulated/0/Slow")
        delegate.navigateToFolder("/storage/emulated/0/Fast")
        secondListing.complete(Result.success(secondFiles))
        advanceUntilIdle()

        firstListing.complete(Result.success(firstFiles))
        advanceUntilIdle()

        assertEquals("/storage/emulated/0/Fast", state.value.currentPath)
        assertFalse(state.value.isLoading)
        assertEquals(secondFiles, state.value.files)
        verify { savedStateHandle.set("currentPath", "/storage/emulated/0/Fast") }
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
            files = previousFiles.toPersistentList()
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
