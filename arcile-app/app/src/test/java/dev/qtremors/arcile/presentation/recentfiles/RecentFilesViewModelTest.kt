package dev.qtremors.arcile.presentation.recentfiles

import android.content.IntentSender
import androidx.lifecycle.SavedStateHandle
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
import dev.qtremors.arcile.testutil.MainDispatcherRule
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecentFilesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `search query filters recent files after debounce and ignores case`() = runTest(mainDispatcherRule.dispatcher) {
        val files = listOf(
            recentFile("Holiday.jpg"),
            recentFile("notes.txt"),
            recentFile("holiday-plan.pdf")
        )
        val repository = RecentFakeFileRepository(recentFiles = files)
        val viewModel = RecentFilesViewModel(repository, SavedStateHandle())

        advanceUntilIdle()

        viewModel.updateSearchQuery("HOLIDAY")
        advanceTimeBy(299)
        assertFalse(viewModel.state.value.isSearching)
        advanceTimeBy(1)
        advanceUntilIdle()

        assertEquals(listOf("Holiday.jpg", "holiday-plan.pdf"), viewModel.state.value.searchResults.map { it.name })
        assertFalse(viewModel.state.value.isSearching)
    }

    @Test
    fun `blank search query clears recent file search state immediately`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = RecentFakeFileRepository(recentFiles = listOf(recentFile("Holiday.jpg")))
        val viewModel = RecentFilesViewModel(repository, SavedStateHandle())

        advanceUntilIdle()
        viewModel.updateSearchQuery("holiday")
        advanceTimeBy(300)
        advanceUntilIdle()

        viewModel.updateSearchQuery("")

        assertEquals(emptyList<FileModel>(), viewModel.state.value.searchResults)
        assertFalse(viewModel.state.value.isSearching)
    }

    @Test
    fun `pull to refresh reloads files and resets refresh flag`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = RecentFakeFileRepository(recentFiles = listOf(recentFile("Holiday.jpg")))
        val viewModel = RecentFilesViewModel(repository, SavedStateHandle())

        advanceUntilIdle()
        viewModel.loadRecentFiles(pullToRefresh = true)
        advanceUntilIdle()

        assertEquals(2, repository.requestedScopes.size)
        assertEquals(StorageScope.AllStorage, repository.requestedScopes.last())
        assertFalse(viewModel.state.value.isPullToRefreshing)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `requestDeleteSelected shows trash confirmation for trash-capable volume`() = runTest(mainDispatcherRule.dispatcher) {
        val internal = recentVolume("primary", "/storage/emulated/0", StorageKind.INTERNAL)
        val repository = RecentFakeFileRepository(
            recentFiles = listOf(recentFile("Holiday.jpg", "/storage/emulated/0/Holiday.jpg")),
            volumes = listOf(internal)
        )
        val viewModel = RecentFilesViewModel(repository, SavedStateHandle())

        advanceUntilIdle()
        viewModel.toggleSelection("/storage/emulated/0/Holiday.jpg")
        viewModel.requestDeleteSelected()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.showTrashConfirmation)
        assertFalse(viewModel.state.value.showPermanentDeleteConfirmation)
        assertFalse(viewModel.state.value.showMixedDeleteExplanation)
    }

    @Test
    fun `moveSelectedToTrash surfaces native confirmation request`() = runTest(mainDispatcherRule.dispatcher) {
        val internal = recentVolume("primary", "/storage/emulated/0", StorageKind.INTERNAL)
        val repository = RecentFakeFileRepository(
            recentFiles = listOf(recentFile("Holiday.jpg", "/storage/emulated/0/Holiday.jpg")),
            volumes = listOf(internal),
            moveToTrashResult = Result.failure(NativeConfirmationRequiredException(fakeIntentSender()))
        )
        val viewModel = RecentFilesViewModel(repository, SavedStateHandle())

        advanceUntilIdle()
        viewModel.toggleSelection("/storage/emulated/0/Holiday.jpg")
        viewModel.moveSelectedToTrash()
        advanceUntilIdle()

        assertEquals(RecentNativeAction.TRASH, viewModel.state.value.pendingNativeAction)
        assertFalse(viewModel.state.value.isLoading)
    }
}

private class RecentFakeFileRepository(
    private val recentFiles: List<FileModel>,
    volumes: List<StorageVolume> = emptyList(),
    private val moveToTrashResult: Result<Unit> = Result.success(Unit)
) : FileRepository {

    private val observedVolumes = MutableSharedFlow<List<StorageVolume>>(replay = 1).apply {
        tryEmit(volumes)
    }
    private val volumeList = volumes

    val requestedScopes = mutableListOf<StorageScope>()

    override suspend fun listFiles(path: String): Result<List<FileModel>> = Result.failure(NotImplementedError())
    override suspend fun createDirectory(parentPath: String, name: String): Result<FileModel> = Result.failure(NotImplementedError())
    override suspend fun createFile(parentPath: String, name: String): Result<FileModel> = Result.failure(NotImplementedError())
    override suspend fun deleteFile(path: String): Result<Unit> = Result.failure(NotImplementedError())
    override suspend fun deletePermanently(paths: List<String>): Result<Unit> = Result.failure(NotImplementedError())
    override suspend fun renameFile(path: String, newName: String): Result<FileModel> = Result.failure(NotImplementedError())
    override fun observeStorageVolumes(): Flow<List<StorageVolume>> = observedVolumes
    override suspend fun getStorageVolumes(): Result<List<StorageVolume>> = Result.success(volumeList)
    override suspend fun getVolumeForPath(path: String): Result<StorageVolume> {
        val volume = volumeList.sortedByDescending { it.path.length }
            .firstOrNull { path == it.path || path.startsWith(it.path + "/") }
        return volume?.let { Result.success(it) } ?: Result.failure(IllegalArgumentException("No volume for path"))
    }
    override fun getStandardFolders(): Map<String, String?> = emptyMap()
    override suspend fun getRecentFiles(scope: StorageScope, limit: Int, offset: Int, minTimestamp: Long): Result<List<FileModel>> {
        requestedScopes += scope
        return Result.success(recentFiles)
    }
    override suspend fun getStorageInfo(scope: StorageScope): Result<StorageInfo> = Result.failure(NotImplementedError())
    override suspend fun getCategoryStorageSizes(scope: StorageScope): Result<List<CategoryStorage>> = Result.failure(NotImplementedError())
    override suspend fun getFilesByCategory(scope: StorageScope, categoryName: String): Result<List<FileModel>> = Result.failure(NotImplementedError())
    override suspend fun searchFiles(query: String, scope: StorageScope, filters: SearchFilters?): Result<List<FileModel>> = Result.failure(NotImplementedError())
    override suspend fun detectCopyConflicts(sourcePaths: List<String>, destinationPath: String): Result<List<FileConflict>> = Result.failure(NotImplementedError())
    override suspend fun copyFiles(sourcePaths: List<String>, destinationPath: String, resolutions: Map<String, ConflictResolution>, onProgress: ((dev.qtremors.arcile.presentation.operations.BulkFileOperationProgress) -> Unit)?): Result<Unit> = Result.failure(NotImplementedError())
    override suspend fun moveFiles(sourcePaths: List<String>, destinationPath: String, resolutions: Map<String, ConflictResolution>, onProgress: ((dev.qtremors.arcile.presentation.operations.BulkFileOperationProgress) -> Unit)?): Result<Unit> = Result.failure(NotImplementedError())
    override suspend fun moveToTrash(paths: List<String>): Result<Unit> = moveToTrashResult
    override suspend fun restoreFromTrash(trashIds: List<String>, destinationPath: String?): Result<Unit> = Result.failure(NotImplementedError())
    override suspend fun emptyTrash(): Result<Unit> = Result.failure(NotImplementedError())
    override suspend fun getTrashFiles(): Result<List<TrashMetadata>> = Result.failure(NotImplementedError())
    override suspend fun deletePermanentlyFromTrash(trashIds: List<String>): Result<Unit> = Result.failure(NotImplementedError())
}

private fun recentFile(name: String, path: String = "/storage/emulated/0/$name") = FileModel(
    name = name,
    absolutePath = path,
    size = 1L,
    lastModified = 1L,
    extension = name.substringAfterLast('.', ""),
    isHidden = false
)

private fun recentVolume(id: String, path: String, kind: StorageKind) = StorageVolume(
    id = id,
    storageKey = id,
    name = id,
    path = path,
    totalBytes = 100L,
    freeBytes = 20L,
    isPrimary = kind == StorageKind.INTERNAL,
    isRemovable = kind != StorageKind.INTERNAL,
    kind = kind
)

private fun fakeIntentSender(): IntentSender {
    return mockk()
}
