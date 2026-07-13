package dev.qtremors.arcile.feature.browser.delegate

import androidx.lifecycle.SavedStateHandle
import dev.qtremors.arcile.core.storage.domain.BrowserLocationPreferences
import dev.qtremors.arcile.core.storage.domain.BrowserLocationPreferencesStore
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.SearchFilters
import dev.qtremors.arcile.core.storage.domain.StorageBrowserLocation
import dev.qtremors.arcile.core.storage.domain.StorageScope
import dev.qtremors.arcile.feature.browser.BrowserNavigationState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.collections.immutable.toPersistentList
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
class BrowserNavigationControllerTest {

    private lateinit var testScope: TestScope
    private lateinit var repository: FakeStorageRepositoryBundle
    private lateinit var browserPreferencesRepository: BrowserLocationPreferencesStore
    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var delegate: BrowserNavigationController

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
            every { locationPreferencesFlow } returns kotlinx.coroutines.flow.flowOf(BrowserLocationPreferences())
            coEvery { updateLastOpenedLocation(any(), any()) } returns Unit
        }
        savedStateHandle = mockk(relaxed = true)
        
        delegate = BrowserNavigationController(
            initialState = BrowserNavigationState().withValues(
                storageVolumes = listOf(testVolume).toPersistentList()
            ),
            viewModelScope = testScope,
            fileBrowserRepository = repository.fileBrowserRepository,
            archiveRepository = repository.archiveRepository,
            searchRepository = repository.searchRepository,
            browserPreferencesRepository = browserPreferencesRepository,
            savedStateHandle = savedStateHandle,
            onLocationChanged = {}
        )
    }

    @Test
    fun `openVolumeRoots clears path and category state`() = testScope.runTest {
        delegate.state.value = delegate.state.value.withValues(
            currentPath = "/storage/emulated/0/some/path",
            activeCategoryName = "Images",
            isCategoryScreen = true
        )

        delegate.openVolumeRoots()
        advanceUntilIdle()

        assertTrue(delegate.state.value.isVolumeRootScreen)
        assertFalse(delegate.state.value.isCategoryScreen)
        assertEquals("", delegate.state.value.currentPath)
        assertEquals("", delegate.state.value.activeCategoryName)
        verify { savedStateHandle.set("isVolumeRootScreen", true) }
        verify { savedStateHandle.set("isCategoryScreen", false) }
    }

    @Test
    fun `openFileBrowser from explicit root entry does not overwrite last opened location`() = testScope.runTest {
        repository.filesByPath = mapOf("/storage/emulated/0" to emptyList())

        delegate.openFileBrowser(restorePersistentLocation = false)
        advanceUntilIdle()

        assertEquals("/storage/emulated/0", delegate.state.value.currentPath)
        coVerify(exactly = 0) { browserPreferencesRepository.updateLastOpenedLocation(any(), any()) }
    }

    @Test
    fun `openFileBrowser restores persisted location for swipe entry`() = testScope.runTest {
        every { browserPreferencesRepository.locationPreferencesFlow } returns kotlinx.coroutines.flow.flowOf(
            BrowserLocationPreferences(
                lastOpenedPath = "/storage/emulated/0/Documents",
                lastOpenedVolumeId = "vol1"
            )
        )
        repository.filesByPath = mapOf("/storage/emulated/0/Documents" to emptyList())

        delegate.openFileBrowser(restorePersistentLocation = true)
        advanceUntilIdle()

        assertEquals("/storage/emulated/0/Documents", delegate.state.value.currentPath)
        coVerify { browserPreferencesRepository.updateLastOpenedLocation("/storage/emulated/0/Documents", "vol1") }
    }

    @Test
    fun `openFileBrowser same-folder restore preserves folder history after viewer return`() = testScope.runTest {
        every { browserPreferencesRepository.locationPreferencesFlow } returns kotlinx.coroutines.flow.flowOf(
            BrowserLocationPreferences(
                lastOpenedPath = "/storage/emulated/0/Pictures/Trip",
                lastOpenedVolumeId = "vol1"
            )
        )
        repository.filesByPath = mapOf(
            "/storage/emulated/0/Pictures" to listOf(
                FileModel("Trip", "/storage/emulated/0/Pictures/Trip", 0L, 0L, true, "", false)
            ),
            "/storage/emulated/0/Pictures/Trip" to listOf(
                FileModel("photo.jpg", "/storage/emulated/0/Pictures/Trip/photo.jpg", 0L, 0L, false, "jpg", false)
            )
        )

        delegate.navigateToSpecificFolder("/storage/emulated/0/Pictures", seedInitialPathHistory = false)
        advanceUntilIdle()
        delegate.navigateToFolder("/storage/emulated/0/Pictures/Trip")
        advanceUntilIdle()

        delegate.openFileBrowser(restorePersistentLocation = true)
        advanceUntilIdle()

        assertTrue(delegate.navigateBack(allowVolumeRootFallback = false))
        advanceUntilIdle()
        assertEquals("/storage/emulated/0/Pictures", delegate.state.value.currentPath)
    }

    @Test
    fun `navigateBack returns false when history is empty`() = testScope.runTest {
        delegate.state.value = delegate.state.value.withValues(
            currentPath = "/storage/emulated/0"
        )
        // history is empty by default because we just instantiated the delegate

        val result = delegate.navigateBack()
        advanceUntilIdle()

        assertFalse(result)
    }

    @Test
    fun `navigateBack falls back to parent folder when history is empty in main browser`() = testScope.runTest {
        repository.filesByPath = mapOf("/storage/emulated/0/Pictures" to emptyList())
        delegate.navigateToSpecificFolder(
            "/storage/emulated/0/Pictures/Images",
            seedInitialPathHistory = false
        )
        advanceUntilIdle()

        val result = delegate.navigateBack()
        advanceUntilIdle()

        assertTrue(result)
        assertEquals("/storage/emulated/0/Pictures", delegate.state.value.currentPath)
    }

    @Test
    fun `navigateBack returns false from category screen so app back stack can handle it`() = testScope.runTest {
        delegate.state.value = delegate.state.value.withValues(
            isCategoryScreen = true,
            activeCategoryName = "Images",
            currentPath = "",
            currentVolumeId = "vol1"
        )

        val result = delegate.navigateBack()
        advanceUntilIdle()

        assertFalse(result)
        assertTrue(delegate.state.value.isCategoryScreen)
        assertEquals("Images", delegate.state.value.activeCategoryName)
    }

    @Test
    fun `navigateToSpecificFolder saves history, then navigateBack pops history and loads directory`() = testScope.runTest {
        delegate.state.value = delegate.state.value.withValues(currentPath = "/storage/emulated/0/parent")
        repository.filesByPath = mapOf(
            "/storage/emulated/0/parent/child" to emptyList(),
            "/storage/emulated/0/parent" to listOf(FileModel("child", "/storage/emulated/0/parent/child", 0L, 0L, true, "", false))
        )

        // 1. Navigate forward (pushes "/storage/emulated/0/parent" to history)
        delegate.navigateToSpecificFolder("/storage/emulated/0/parent/child")
        advanceUntilIdle()

        assertEquals("/storage/emulated/0/parent/child", delegate.state.value.currentPath)
        verify { savedStateHandle.set("currentPath", "/storage/emulated/0/parent/child") }

        // 3. Navigate back (pops "/storage/emulated/0")
        val result = delegate.navigateBack()
        advanceUntilIdle()

        assertTrue(result)
        assertEquals("/storage/emulated/0", delegate.state.value.currentPath)
        
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

        assertEquals("/storage/emulated/0/Download", delegate.state.value.currentPath)
        assertFalse(delegate.navigateBack(allowVolumeRootFallback = false))
    }

    @Test
    fun `navigateBack can skip volume roots so app route stack handles external origins`() = testScope.runTest {
        val sdCard = dev.qtremors.arcile.core.storage.domain.StorageVolume(
            id = "sdcard",
            storageKey = "sdcard",
            name = "SD Card",
            path = "/storage/1234-5678",
            totalBytes = 1000L,
            freeBytes = 500L,
            isPrimary = false,
            isRemovable = true,
            kind = dev.qtremors.arcile.core.storage.domain.StorageKind.SD_CARD
        )
        delegate.state.value = delegate.state.value.withValues(
            storageVolumes = (delegate.state.value.storageVolumes + sdCard).toPersistentList(),
            currentPath = "/storage/emulated/0/Download",
            isVolumeRootScreen = false
        )

        assertFalse(delegate.navigateBack(allowVolumeRootFallback = false))
        advanceUntilIdle()

        assertEquals("/storage/emulated/0/Download", delegate.state.value.currentPath)
        assertFalse(delegate.state.value.isVolumeRootScreen)
    }

    @Test
    fun `navigateToCategory updates state and loads category files`() = testScope.runTest {
        delegate.state.value = delegate.state.value.withValues(currentPath = "/storage/emulated/0/Music")
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

        assertTrue(delegate.state.value.isCategoryScreen)
        assertEquals("Images", delegate.state.value.activeCategoryName)
        assertEquals("", delegate.state.value.currentPath)
        assertEquals("vol1", delegate.state.value.currentVolumeId)
        assertFalse(delegate.state.value.isLoading)
        assertEquals(files, delegate.state.value.files)
    }

    @Test
    fun `restoreLocationFromState restores archive path and entry prefix`() {
        every { savedStateHandle.get<Boolean>("isVolumeRootScreen") } returns null
        every { savedStateHandle.get<String>("currentPath") } returns null
        every { savedStateHandle.get<String>("currentVolumeId") } returns null
        every { savedStateHandle.get<Boolean>("isCategoryScreen") } returns null
        every { savedStateHandle.get<String>("activeCategoryName") } returns null
        every { savedStateHandle.get<String>("archivePath") } returns "/storage/emulated/0/Download/archive.zip"
        every { savedStateHandle.get<String>("archiveEntryPrefix") } returns "folder/subfolder"
        every { savedStateHandle.get<Array<String>>("pathHistory") } returns arrayOf("dir:/storage/emulated/0/Download")

        val location = delegate.restoreLocationFromState()

        assertEquals(
            StorageBrowserLocation.Archive(
                archivePath = "/storage/emulated/0/Download/archive.zip",
                entryPrefix = "folder/subfolder"
            ),
            location
        )
    }

    @Test
    fun `navigateToFolder clears previous files while loading new directory`() = testScope.runTest {
        val previousFiles = listOf(FileModel("old", "/storage/emulated/0/old", 0L, 0L, false, "", false))
        val loadedFiles = listOf(FileModel("new", "/storage/emulated/0/new", 0L, 0L, false, "", false))
        val deferredFiles = CompletableDeferred<Result<List<FileModel>>>()
        repository.listFilesResultProvider = { deferredFiles.await() }
        delegate.state.value = delegate.state.value.withValues(
            currentPath = "/storage/emulated/0",
            currentVolumeId = "vol1",
            files = previousFiles.toPersistentList()
        )

        delegate.navigateToFolder("/storage/emulated/0/Downloads")

        assertTrue(delegate.state.value.isLoading)
        assertEquals(emptyList<FileModel>(), delegate.state.value.files)

        deferredFiles.complete(Result.success(loadedFiles))
        advanceUntilIdle()

        assertFalse(delegate.state.value.isLoading)
        assertEquals(loadedFiles, delegate.state.value.files)
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
        delegate.state.value = delegate.state.value.withValues(currentPath = "/storage/emulated/0", currentVolumeId = "vol1")

        delegate.navigateToFolder("/storage/emulated/0/Slow")
        delegate.navigateToFolder("/storage/emulated/0/Fast")
        secondListing.complete(Result.success(secondFiles))
        advanceUntilIdle()

        firstListing.complete(Result.success(firstFiles))
        advanceUntilIdle()

        assertEquals("/storage/emulated/0/Fast", delegate.state.value.currentPath)
        assertFalse(delegate.state.value.isLoading)
        assertEquals(secondFiles, delegate.state.value.files)
        verify { savedStateHandle.set("currentPath", "/storage/emulated/0/Fast") }
    }

    @Test
    fun `navigateToCategory clears previous files while loading category`() = testScope.runTest {
        val previousFiles = listOf(FileModel("old", "/storage/emulated/0/old", 0L, 0L, false, "", false))
        val loadedFiles = listOf(FileModel("image", "/storage/emulated/0/image.jpg", 0L, 0L, false, "jpg", false))
        val deferredFiles = CompletableDeferred<Result<List<FileModel>>>()
        repository.filesByCategoryResultProvider = { _, _ -> deferredFiles.await() }
        delegate.state.value = delegate.state.value.withValues(
            currentPath = "/storage/emulated/0",
            currentVolumeId = "vol1",
            files = previousFiles.toPersistentList()
        )

        delegate.navigateToCategory("Images", "vol1")

        assertTrue(delegate.state.value.isLoading)
        assertEquals(emptyList<FileModel>(), delegate.state.value.files)

        deferredFiles.complete(Result.success(loadedFiles))
        advanceUntilIdle()

        assertFalse(delegate.state.value.isLoading)
        assertEquals(loadedFiles, delegate.state.value.files)
    }
}
