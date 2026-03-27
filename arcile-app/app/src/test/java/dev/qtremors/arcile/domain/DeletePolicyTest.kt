package dev.qtremors.arcile.domain

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DeletePolicyTest {

    @Test
    fun `returns trash when all selected files are on trash-enabled storage`() = runTest {
        val repository = FakeDeletePolicyRepository(
            mapOf(
                "/storage/emulated/0/file.txt" to volume("primary", "/storage/emulated/0", StorageKind.INTERNAL),
                "/storage/1234-5678/movie.mp4" to volume("sd", "/storage/1234-5678", StorageKind.SD_CARD)
            )
        )

        val result = evaluateDeletePolicy(
            listOf("/storage/emulated/0/file.txt", "/storage/1234-5678/movie.mp4"),
            repository
        )

        assertEquals(DeletePolicyResult.Trash, result)
    }

    @Test
    fun `returns permanent delete when all selected files are on temporary storage`() = runTest {
        val repository = FakeDeletePolicyRepository(
            mapOf(
                "/storage/ABCD-1234/file.txt" to volume("otg", "/storage/ABCD-1234", StorageKind.OTG)
            )
        )

        val result = evaluateDeletePolicy(listOf("/storage/ABCD-1234/file.txt"), repository)

        assertEquals(DeletePolicyResult.PermanentDelete, result)
    }

    @Test
    fun `returns mixed selection when selection spans permanent and temporary storage`() = runTest {
        val repository = FakeDeletePolicyRepository(
            mapOf(
                "/storage/emulated/0/file.txt" to volume("primary", "/storage/emulated/0", StorageKind.INTERNAL),
                "/storage/ABCD-1234/file.txt" to volume("otg", "/storage/ABCD-1234", StorageKind.EXTERNAL_UNCLASSIFIED)
            )
        )

        val result = evaluateDeletePolicy(
            listOf("/storage/emulated/0/file.txt", "/storage/ABCD-1234/file.txt"),
            repository
        )

        assertEquals(DeletePolicyResult.MixedSelection, result)
    }

    private fun volume(id: String, path: String, kind: StorageKind) = StorageVolume(
        id = id,
        storageKey = id,
        name = id,
        path = path,
        totalBytes = 100L,
        freeBytes = 50L,
        isPrimary = kind == StorageKind.INTERNAL,
        isRemovable = kind != StorageKind.INTERNAL,
        kind = kind,
        isUserClassified = kind != StorageKind.INTERNAL
    )
}

private class FakeDeletePolicyRepository(
    private val volumesByPath: Map<String, StorageVolume>
) : FileRepository {
    override suspend fun getVolumeForPath(path: String): Result<StorageVolume> =
        volumesByPath[path]?.let { Result.success(it) }
            ?: Result.failure(IllegalArgumentException("No volume for path"))

    override fun getStandardFolders(): Map<String, String?> = emptyMap()

    override suspend fun listFiles(path: String): Result<List<FileModel>> = Result.failure(NotImplementedError())
    override suspend fun createDirectory(parentPath: String, name: String): Result<FileModel> = Result.failure(NotImplementedError())
    override suspend fun createFile(parentPath: String, name: String): Result<FileModel> = Result.failure(NotImplementedError())
    override suspend fun deleteFile(path: String): Result<Unit> = Result.failure(NotImplementedError())
    override suspend fun deletePermanently(paths: List<String>): Result<Unit> = Result.failure(NotImplementedError())
    override suspend fun renameFile(path: String, newName: String): Result<FileModel> = Result.failure(NotImplementedError())
    override fun observeStorageVolumes() = kotlinx.coroutines.flow.emptyFlow<List<StorageVolume>>()
    override suspend fun getStorageVolumes(): Result<List<StorageVolume>> = Result.failure(NotImplementedError())
    override suspend fun getRecentFiles(scope: StorageScope, limit: Int, offset: Int, minTimestamp: Long): Result<List<FileModel>> = Result.failure(NotImplementedError())
    override suspend fun getStorageInfo(scope: StorageScope): Result<StorageInfo> = Result.failure(NotImplementedError())
    override suspend fun getCategoryStorageSizes(scope: StorageScope): Result<List<CategoryStorage>> = Result.failure(NotImplementedError())
    override suspend fun getFilesByCategory(scope: StorageScope, categoryName: String): Result<List<FileModel>> = Result.failure(NotImplementedError())
    override suspend fun searchFiles(query: String, scope: StorageScope, filters: SearchFilters?): Result<List<FileModel>> = Result.failure(NotImplementedError())
    override suspend fun detectCopyConflicts(sourcePaths: List<String>, destinationPath: String): Result<List<FileConflict>> = Result.failure(NotImplementedError())
    override suspend fun copyFiles(sourcePaths: List<String>, destinationPath: String, resolutions: Map<String, ConflictResolution>, onProgress: ((dev.qtremors.arcile.presentation.operations.BulkFileOperationProgress) -> Unit)?): Result<Unit> = Result.failure(NotImplementedError())
    override suspend fun moveFiles(sourcePaths: List<String>, destinationPath: String, resolutions: Map<String, ConflictResolution>, onProgress: ((dev.qtremors.arcile.presentation.operations.BulkFileOperationProgress) -> Unit)?): Result<Unit> = Result.failure(NotImplementedError())
    override suspend fun moveToTrash(paths: List<String>): Result<Unit> = Result.failure(NotImplementedError())
    override suspend fun restoreFromTrash(trashIds: List<String>, destinationPath: String?): Result<Unit> = Result.failure(NotImplementedError())
    override suspend fun emptyTrash(): Result<Unit> = Result.failure(NotImplementedError())
    override suspend fun getTrashFiles(): Result<List<TrashMetadata>> = Result.failure(NotImplementedError())
    override suspend fun deletePermanentlyFromTrash(trashIds: List<String>): Result<Unit> = Result.failure(NotImplementedError())
}
