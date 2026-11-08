package dev.qtremors.arcile.feature.onlyfiles

import dev.qtremors.arcile.core.vault.domain.DirectoryId
import dev.qtremors.arcile.core.vault.domain.NodeId
import dev.qtremors.arcile.core.vault.domain.VaultExternalAccessManager
import dev.qtremors.arcile.core.vault.domain.VaultExternalGrant
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultNodeCapabilities
import dev.qtremors.arcile.core.vault.domain.VaultNodeKind
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import dev.qtremors.arcile.core.vault.domain.VaultNodeRef
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnlyFilesExternalAccessControllerTest {
    @Test
    fun `scoped handoff delivers grants without creating compatibility copies`() = runTest {
        val file = node("first.txt")
        val grant = grant(file)
        val manager = mockk<VaultExternalAccessManager> {
            every { issue(file.ref, any()) } returns Result.success(grant)
        }
        val controller = OnlyFilesExternalAccessController(manager, this) { throw it }
        val delivered = mutableListOf<VaultExternalGrant>()

        controller.issue(listOf(file), plaintextFallback = false, isCurrentMount = { true }, delivered::addAll)
        advanceUntilIdle()

        assertEquals(listOf(grant), delivered)
        verify(exactly = 0) { manager.revoke(any()) }
        io.mockk.coVerify(exactly = 0) { manager.issuePlaintextFallback(any(), any()) }
    }

    @Test
    fun `a compatibility grant issued after lock is revoked before launching the chooser`() = runTest {
        val file = node("first.txt")
        val grant = grant(file)
        val pending = CompletableDeferred<Result<VaultExternalGrant>>()
        val manager = mockk<VaultExternalAccessManager> {
            coEvery { issuePlaintextFallback(file.ref, any()) } coAnswers { pending.await() }
            every { revoke(grant.token) } returns true
        }
        val controller = OnlyFilesExternalAccessController(manager, this) { throw it }
        var mounted = true

        controller.issue(listOf(file), plaintextFallback = true, isCurrentMount = { mounted }, onReady = {
            error("A locked mount must never launch a chooser")
        })
        runCurrent()
        mounted = false
        pending.complete(Result.success(grant))
        advanceUntilIdle()

        verify(exactly = 1) { manager.revoke(grant.token) }
    }

    @Test
    fun `partial multi-file handoff revokes grants already issued on failure`() = runTest {
        val first = node("first.txt")
        val second = node("second.txt")
        val firstGrant = grant(first)
        val failure = IllegalStateException("Grant unavailable")
        val manager = mockk<VaultExternalAccessManager> {
            every { issue(first.ref, any()) } returns Result.success(firstGrant)
            every { issue(second.ref, any()) } returns Result.failure(failure)
            every { revoke(firstGrant.token) } returns true
        }
        val errors = mutableListOf<Throwable>()
        var failed = false
        val controller = OnlyFilesExternalAccessController(manager, this) { errors += it }

        controller.issue(listOf(first, second), plaintextFallback = false, isCurrentMount = { true }, onReady = {
            error("A partial handoff must never launch a chooser")
        }, onFailure = { failed = true })
        advanceUntilIdle()

        assertEquals(listOf(failure), errors)
        assertTrue(failed)
        verify(exactly = 1) { manager.revoke(firstGrant.token) }
    }

    private fun node(name: String) = VaultNodeMetadata(
        VaultNodeRef(VaultId.of("vault"), NodeId.of(name), DirectoryId.Root, VaultNodeCapabilities()),
        name, VaultNodeKind.FILE, 1L, 0L, 1L, mimeType = "text/plain"
    )

    private fun grant(file: VaultNodeMetadata) = VaultExternalGrant(
        token = file.ref.nodeId.value,
        contentUri = "content://vault/grant/${file.ref.nodeId.value}",
        displayName = file.name,
        mimeType = requireNotNull(file.mimeType),
        sizeBytes = file.sizeBytes,
        expiresAtMillis = 100L
    )
}
