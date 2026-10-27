package dev.qtremors.arcile.feature.onlyfiles

import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.core.vault.domain.VaultBatchResult
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultItemOutcome
import dev.qtremors.arcile.core.vault.domain.VaultItemResult
import dev.qtremors.arcile.core.vault.domain.VaultOperationRecovery
import dev.qtremors.arcile.core.vault.domain.VaultRecoveryKind
import dev.qtremors.arcile.core.vault.domain.VaultRecoveryQueue
import dev.qtremors.arcile.core.vault.domain.VaultRecoveryResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnlyFilesRecoveryControllerTest {
    @Test
    fun `opening and discarding shows the other queues without resuming them`() = runTest {
        val queue = queue()
        val other = queue.copy(id = "other")
        val state = mounted(queue)
        val service = mockk<VaultOperationRecovery>()
        coEvery { service.list(queue.vaultId) } returns Result.success(listOf(queue, other))
        coEvery { service.discard(queue) } returns Result.success(Unit)
        val controller = OnlyFilesRecoveryController(service, state, this, {})

        controller.open()
        assertTrue(state.value.recovery!!.loading)
        advanceUntilIdle()
        assertEquals(listOf(queue, other), state.value.recovery?.queues)
        controller.discard(queue)
        advanceUntilIdle()

        assertEquals(listOf(other), state.value.recovery?.queues)
        assertFalse(state.value.recovery!!.loading)
        coVerify(exactly = 0) { service.resume(any(), any(), any()) }
    }

    @Test
    fun `a closed mount cannot publish late recovery results`() = runTest {
        val queue = queue()
        val state = mounted(queue)
        val finish = CompletableDeferred<Unit>()
        val service = mockk<VaultOperationRecovery>()
        coEvery { service.list(queue.vaultId) } returns Result.success(listOf(queue))
        coEvery { service.resume(queue, any(), any()) } coAnswers {
            finish.await()
            assertTrue(thirdArg<dev.qtremors.arcile.core.vault.domain.VaultCancellationSignal>().isCancelled())
            Result.success(transferred())
        }
        var reloads = 0
        val controller = OnlyFilesRecoveryController(service, state, this, { reloads++ })
        controller.open()
        advanceUntilIdle()
        controller.resume(queue)
        runCurrent()
        assertTrue(state.value.busy)

        controller.cancel()
        controller.close()
        state.update { it.copy(selectedVaultId = null, mountGeneration = 2) }
        finish.complete(Unit)
        advanceUntilIdle()

        assertFalse(state.value.busy)
        assertNull(state.value.recovery)
        assertNull(state.value.message)
        assertEquals(0, reloads)
    }

    @Test
    fun `locked or uncertain-only queues are never submitted for resume`() = runTest {
        val queue = queue()
        val locked = queue.copy(lockedSourceVaults = setOf(VaultId.of("locked")))
        val uncertain = queue.copy(id = "uncertain", hasUncertainItem = true, remainingItems = 0)
        val state = mounted(queue)
        val service = mockk<VaultOperationRecovery>()
        coEvery { service.list(queue.vaultId) } returns Result.success(listOf(locked, uncertain))
        val controller = OnlyFilesRecoveryController(service, state, this, {})
        controller.open()
        advanceUntilIdle()

        controller.resume(locked)
        controller.resume(uncertain)
        advanceUntilIdle()

        assertFalse(state.value.busy)
        assertEquals(listOf(locked, uncertain), state.value.recovery?.queues)
        coVerify(exactly = 0) { service.resume(any(), any(), any()) }
    }

    @Test
    fun `resumed transfer reports its results and refreshes the active listing`() = runTest {
        val queue = queue()
        val state = mounted(queue)
        val service = mockk<VaultOperationRecovery>()
        coEvery { service.list(queue.vaultId) } returns Result.success(listOf(queue))
        coEvery { service.resume(queue, any(), any()) } returns Result.success(transferred())
        var reloads = 0
        val controller = OnlyFilesRecoveryController(service, state, this, { reloads++ })
        controller.open()
        advanceUntilIdle()

        controller.resume(queue)
        controller.resume(queue)
        advanceUntilIdle()

        coVerify(exactly = 1) { service.resume(queue, any(), any()) }
        assertEquals(UiText.PluralResource(R.plurals.onlyfiles_recovered_items, 1, listOf(1)), state.value.operationFeedback?.message)
        assertNull(state.value.recovery)
        assertNull(state.value.transferMountGeneration)
        assertFalse(state.value.busy)
        assertEquals(1, reloads)
    }

    private fun queue() = VaultRecoveryQueue(
        "queue", VaultId.of("vault"), VaultRecoveryKind.PASTE, 0L, 0, 1,
        hasUncertainItem = false, wasMove = false, lockedSourceVaults = emptySet()
    )

    private fun mounted(queue: VaultRecoveryQueue) =
        MutableStateFlow(OnlyFilesUiState(selectedVaultId = queue.vaultId, mountGeneration = 1))

    private fun transferred() = VaultRecoveryResult.Transferred(VaultBatchResult(listOf(
        VaultItemResult("source", "File", VaultItemOutcome.COMPLETED)
    )))
}
