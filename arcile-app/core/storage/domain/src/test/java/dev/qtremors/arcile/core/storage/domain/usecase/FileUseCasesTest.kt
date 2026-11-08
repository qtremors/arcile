package dev.qtremors.arcile.core.storage.domain.usecase

import dev.qtremors.arcile.testutil.FakeStorageRepositoryBundle
import dev.qtremors.arcile.testutil.testVolume
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FileUseCasesTest {

    @Test
    fun `ObserveStorageVolumesUseCase exposes repository volume stream`() = runTest {
        val initialVolume = testVolume("primary", "/storage/emulated/0")
        val repository = FakeStorageRepositoryBundle(volumes = listOf(initialVolume))
        val useCase = ObserveStorageVolumesUseCase(repository.volumeRepository)

        assertEquals(listOf(initialVolume), useCase().first())
    }
}
