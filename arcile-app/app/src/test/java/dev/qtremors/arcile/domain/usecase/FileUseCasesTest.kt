package dev.qtremors.arcile.domain.usecase

import dev.qtremors.arcile.domain.ConflictResolution
import dev.qtremors.arcile.testutil.FakeFileRepository
import dev.qtremors.arcile.testutil.testVolume
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileUseCasesTest {

    @Test
    fun `GetStorageVolumesUseCase exposes repository volume stream`() = runTest {
        val initialVolume = testVolume("primary", "/storage/emulated/0")
        val repository = FakeFileRepository(volumes = listOf(initialVolume))
        val useCase = GetStorageVolumesUseCase(repository)

        assertEquals(listOf(initialVolume), useCase().first())
    }

    @Test
    fun `MoveToTrashUseCase delegates paths to repository`() = runTest {
        val repository = FakeFileRepository()
        val useCase = MoveToTrashUseCase(repository)
        val paths = listOf("/storage/emulated/0/Download/a.txt", "/storage/emulated/0/Download/b.txt")

        val result = useCase(paths)

        assertTrue(result.isSuccess)
        assertEquals(listOf(paths), repository.moveToTrashRequests)
    }

    @Test
    fun `PasteFilesUseCase copies when isMove is false`() = runTest {
        val repository = FakeFileRepository()
        val useCase = PasteFilesUseCase(repository)
        val resolutions = mapOf("/source/a.txt" to ConflictResolution.KEEP_BOTH)

        val result = useCase(
            sourcePaths = listOf("/source/a.txt"),
            destinationPath = "/destination",
            isMove = false,
            resolutions = resolutions
        )

        assertTrue(result.isSuccess)
        assertEquals(1, repository.copyRequests.size)
        assertEquals(emptyList<FakeFileRepository.TransferRequest>(), repository.moveRequests)
        assertEquals(listOf("/source/a.txt"), repository.copyRequests.single().sourcePaths)
        assertEquals("/destination", repository.copyRequests.single().destinationPath)
        assertEquals(resolutions, repository.copyRequests.single().resolutions)
    }

    @Test
    fun `PasteFilesUseCase moves when isMove is true`() = runTest {
        val repository = FakeFileRepository()
        val useCase = PasteFilesUseCase(repository)
        val resolutions = mapOf("/source/a.txt" to ConflictResolution.REPLACE)

        val result = useCase(
            sourcePaths = listOf("/source/a.txt"),
            destinationPath = "/destination",
            isMove = true,
            resolutions = resolutions
        )

        assertTrue(result.isSuccess)
        assertEquals(1, repository.moveRequests.size)
        assertEquals(emptyList<FakeFileRepository.TransferRequest>(), repository.copyRequests)
        assertEquals(listOf("/source/a.txt"), repository.moveRequests.single().sourcePaths)
        assertEquals("/destination", repository.moveRequests.single().destinationPath)
        assertEquals(resolutions, repository.moveRequests.single().resolutions)
    }
}
