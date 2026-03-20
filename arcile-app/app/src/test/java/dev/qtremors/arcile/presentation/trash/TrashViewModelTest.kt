package dev.qtremors.arcile.presentation.trash

import android.content.IntentSender
import dev.qtremors.arcile.domain.CategoryStorage
import dev.qtremors.arcile.domain.ConflictResolution
import dev.qtremors.arcile.domain.FileConflict
import dev.qtremors.arcile.domain.FileModel
import dev.qtremors.arcile.domain.FileRepository
import dev.qtremors.arcile.domain.NativeConfirmationRequiredException
import dev.qtremors.arcile.domain.SearchFilters
import dev.qtremors.arcile.domain.StorageInfo
import dev.qtremors.arcile.domain.StorageKind
import dev.qtremors.arcile.domain.StorageMountState
import dev.qtremors.arcile.domain.StorageScope
import dev.qtremors.arcile.domain.StorageVolume
import dev.qtremors.arcile.domain.TrashMetadata
import dev.qtremors.arcile.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrashViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `loadTrashFiles drops stale selections that no longer exist`() = runTest(mainDispatcherRule.dispatcher) {
        val items = listOf(trashItem("keep-1", "keep.txt"))
        val repository = TrashFakeFileRepository(trashFiles = items)
        val viewModel = TrashViewModel(repository)

        advanceUntilIdle()
        viewModel.toggleSelection("keep-1")
        viewModel.toggleSelection("missing")

        viewModel.loadTrashFiles()
        advanceUntilIdle()

        assertEquals(setOf("keep-1"), viewModel.state.value.selectedFiles)
    }

    @Test
    fun `updateSearchQuery filters trash items after debounce and clears on blank`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = TrashFakeFileRepository(
            trashFiles = listOf(
                trashItem("1", "Photo.jpg"),
                trashItem("2", "notes.txt"),
                trashItem("3", "photo-backup.png")
            )
        )
        val viewModel = TrashViewModel(repository)

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
        val repository = TrashFakeFileRepository(
            trashFiles = listOf(trashItem("1", "Photo.jpg")),
            restoreToDestinationResult = Result.failure(NativeConfirmationRequiredException(fakeIntentSender()))
        )
        val viewModel = TrashViewModel(repository)

        advanceUntilIdle()
        viewModel.restoreToDestination(listOf("1"), "/storage/emulated/0/Download")
        advanceUntilIdle()

        assertEquals(NativeAction.RESTORE_TO_DESTINATION, viewModel.state.value.pendingNativeAction)
        assertEquals("/storage/emulated/0/Download", viewModel.state.value.pendingDestinationPath)
        assertEquals(listOf("1"), viewModel.state.value.pendingRestoreIds)
        assertNull(viewModel.state.value.error)
        assertFalse(viewModel.state.value.isLoading)
    }
}

private class TrashFakeFileRepository(
    private val trashFiles: List<TrashMetadata>,
    private val restoreToDestinationResult: Result<Unit> = Result.success(Unit)
) : FileRepository {

    private val observedVolumes = MutableSharedFlow<List<StorageVolume>>(replay = 1).apply {
        tryEmit(
            listOf(
                StorageVolume(
                    id = "primary",
                    storageKey = "primary",
                    name = "Internal",
                    path = "/storage/emulated/0",
                    totalBytes = 100L,
                    freeBytes = 20L,
                    isPrimary = true,
                    isRemovable = false,
                    mountState = StorageMountState.MOUNTED,
                    kind = StorageKind.INTERNAL
                )
            )
        )
    }

    override suspend fun listFiles(path: String): Result<List<FileModel>> = Result.failure(NotImplementedError())
    override suspend fun createDirectory(parentPath: String, name: String): Result<FileModel> = Result.failure(NotImplementedError())
    override suspend fun createFile(parentPath: String, name: String): Result<FileModel> = Result.failure(NotImplementedError())
    override suspend fun deleteFile(path: String): Result<Unit> = Result.failure(NotImplementedError())
    override suspend fun deletePermanently(paths: List<String>): Result<Unit> = Result.failure(NotImplementedError())
    override suspend fun renameFile(path: String, newName: String): Result<FileModel> = Result.failure(NotImplementedError())
    override fun observeStorageVolumes(): Flow<List<StorageVolume>> = observedVolumes
    override suspend fun getStorageVolumes(): Result<List<StorageVolume>> = Result.success(observedVolumes.replayCache.lastOrNull().orEmpty())
    override suspend fun getVolumeForPath(path: String): Result<StorageVolume> = Result.failure(NotImplementedError())
    override fun getStandardFolders(): Map<String, String?> = emptyMap()
    override suspend fun getRecentFiles(scope: StorageScope, limit: Int, minTimestamp: Long): Result<List<FileModel>> = Result.failure(NotImplementedError())
    override suspend fun getStorageInfo(scope: StorageScope): Result<StorageInfo> = Result.failure(NotImplementedError())
    override suspend fun getCategoryStorageSizes(scope: StorageScope): Result<List<CategoryStorage>> = Result.failure(NotImplementedError())
    override suspend fun getFilesByCategory(scope: StorageScope, categoryName: String): Result<List<FileModel>> = Result.failure(NotImplementedError())
    override suspend fun searchFiles(query: String, scope: StorageScope, filters: SearchFilters?): Result<List<FileModel>> = Result.failure(NotImplementedError())
    override suspend fun detectCopyConflicts(sourcePaths: List<String>, destinationPath: String): Result<List<FileConflict>> = Result.failure(NotImplementedError())
    override suspend fun copyFiles(sourcePaths: List<String>, destinationPath: String, resolutions: Map<String, ConflictResolution>): Result<Unit> = Result.failure(NotImplementedError())
    override suspend fun moveFiles(sourcePaths: List<String>, destinationPath: String, resolutions: Map<String, ConflictResolution>): Result<Unit> = Result.failure(NotImplementedError())
    override suspend fun moveToTrash(paths: List<String>): Result<Unit> = Result.failure(NotImplementedError())
    override suspend fun restoreFromTrash(trashIds: List<String>, destinationPath: String?): Result<Unit> {
        return if (destinationPath != null) restoreToDestinationResult else Result.success(Unit)
    }
    override suspend fun emptyTrash(): Result<Unit> = Result.failure(NotImplementedError())
    override suspend fun getTrashFiles(): Result<List<TrashMetadata>> = Result.success(trashFiles)
    override suspend fun deletePermanentlyFromTrash(trashIds: List<String>): Result<Unit> = Result.failure(NotImplementedError())
}

private fun trashItem(id: String, name: String) = TrashMetadata(
    id = id,
    originalPath = "/storage/emulated/0/$name",
    deletionTime = 1L,
    fileModel = FileModel(
        name = name,
        absolutePath = "/trash/$name",
        size = 1L,
        lastModified = 1L,
        extension = name.substringAfterLast('.', ""),
        isHidden = false
    ),
    sourceVolumeId = "primary",
    sourceStorageKind = StorageKind.INTERNAL
)

private fun fakeIntentSender(): IntentSender {
    val field = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe")
    field.isAccessible = true
    val unsafe = field.get(null)
    val allocateInstance = unsafe.javaClass.getMethod("allocateInstance", Class::class.java)
    return allocateInstance.invoke(unsafe, IntentSender::class.java) as IntentSender
}
