package dev.qtremors.arcile.feature.trash

import dev.qtremors.arcile.core.storage.domain.DestinationRequiredException
import dev.qtremors.arcile.testutil.FakeStorageRepositoryBundle
import dev.qtremors.arcile.testutil.testVolume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrashRestoreScopeTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `restore requests an alternate destination when original volume is unavailable`() =
        runTest(dispatcher) {
            val internal = testVolume(
                id = "primary",
                storageKey = "primary",
                name = "Internal",
                path = "/storage/emulated/0",
                totalBytes = 100L,
                freeBytes = 40L,
                isPrimary = true,
                isRemovable = false
            )
            val repository = FakeStorageRepositoryBundle(volumes = listOf(internal)).apply {
                restoreFromTrashResultProvider = { _, _ ->
                    Result.failure(DestinationRequiredException(listOf("trash-1")))
                }
            }
            val viewModel = TrashViewModel(
                repository.trashRepository,
                repository.volumeRepository
            )

            advanceUntilIdle()
            viewModel.toggleSelection("trash-1")
            viewModel.restoreSelectedTrash()
            advanceUntilIdle()

            assertTrue(viewModel.state.value.showDestinationPicker)
            assertNull(viewModel.state.value.error)
            assertFalse(viewModel.state.value.isLoading)
        }
}
