package dev.qtremors.arcile.core.storage.domain

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import dev.qtremors.arcile.testutil.FakeStorageRepositoryBundle
import dev.qtremors.arcile.testutil.testVolume

class DeletePolicyTest {

    @Test
    fun `returns trash when all selected files are on trash-enabled storage`() = runTest {
        val repository = FakeStorageRepositoryBundle(
            volumes = listOf(
                volume("primary", "/storage/emulated/0", StorageKind.INTERNAL),
                volume("sd", "/storage/1234-5678", StorageKind.SD_CARD)
            )
        )

        val result = evaluateDeletePolicy(
            listOf("/storage/emulated/0/file.txt", "/storage/1234-5678/movie.mp4"),
            repository.volumeRepository
        )

        assertEquals(DeletePolicyResult.Trash, result)
    }

    @Test
    fun `returns permanent delete when all selected files are on temporary storage`() = runTest {
        val repository = FakeStorageRepositoryBundle(
            volumes = listOf(volume("otg", "/storage/ABCD-1234", StorageKind.OTG))
        )

        val result = evaluateDeletePolicy(listOf("/storage/ABCD-1234/file.txt"), repository.volumeRepository)

        assertEquals(DeletePolicyResult.PermanentDelete, result)
    }

    @Test
    fun `returns mixed selection when selection spans permanent and temporary storage`() = runTest {
        val repository = FakeStorageRepositoryBundle(
            volumes = listOf(
                volume("primary", "/storage/emulated/0", StorageKind.INTERNAL),
                volume("otg", "/storage/ABCD-1234", StorageKind.EXTERNAL_UNCLASSIFIED)
            )
        )

        val result = evaluateDeletePolicy(
            listOf("/storage/emulated/0/file.txt", "/storage/ABCD-1234/file.txt"),
            repository.volumeRepository
        )

        assertEquals(DeletePolicyResult.MixedSelection, result)
    }

    private fun volume(id: String, path: String, kind: StorageKind) = testVolume(
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
