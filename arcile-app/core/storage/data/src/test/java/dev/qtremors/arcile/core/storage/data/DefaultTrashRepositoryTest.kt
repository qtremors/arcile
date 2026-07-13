package dev.qtremors.arcile.core.storage.data

import android.app.PendingIntent
import android.app.RecoverableSecurityException
import android.app.RemoteAction
import android.content.IntentSender
import dev.qtremors.arcile.core.runtime.NativeStorageAuthorizationGateway
import dev.qtremors.arcile.core.storage.data.manager.TrashManager
import dev.qtremors.arcile.core.storage.domain.StorageAuthorizationOperation
import dev.qtremors.arcile.core.storage.domain.StorageMutationResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DefaultTrashRepositoryTest {

    @Test
    fun `move to trash delegates progress-capable operation unchanged`() = runTest {
        val manager = mockk<TrashManager>()
        coEvery { manager.moveToTrash(listOf("/a"), any()) } returns Result.success(Unit)
        val repository = DefaultTrashRepository(manager, NativeStorageAuthorizationGateway())

        val result = repository.moveToTrash(listOf("/a"))

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { manager.moveToTrash(listOf("/a"), null) }
    }

    @Test
    fun `successful authorized mutations complete without registering native action`() = runTest {
        val manager = mockk<TrashManager>()
        val gateway = NativeStorageAuthorizationGateway()
        coEvery { manager.restoreFromTrash(any(), any()) } returns Result.success(Unit)
        val repository = DefaultTrashRepository(manager, gateway)

        assertSame(
            StorageMutationResult.Completed,
            repository.restoreFromTrash(listOf("trash-1"), null)
        )
    }

    @Test
    fun `nonrecoverable failure remains a typed failure`() = runTest {
        val failure = SecurityException("denied")
        val manager = mockk<TrashManager>()
        coEvery { manager.emptyTrash() } returns Result.failure(failure)
        val repository = DefaultTrashRepository(manager, NativeStorageAuthorizationGateway())

        val result = repository.emptyTrash()

        assertTrue(result is StorageMutationResult.Failed)
        assertSame(failure, (result as StorageMutationResult.Failed).error)
    }

    @Test
    fun `cancellation propagates instead of becoming a mutation failure`() = runTest {
        val manager = mockk<TrashManager>()
        coEvery { manager.deletePermanentlyFromTrash(any()) } returns
            Result.failure(CancellationException("cancelled"))
        val repository = DefaultTrashRepository(manager, NativeStorageAuthorizationGateway())

        var cancellation: CancellationException? = null
        try {
            repository.deletePermanentlyFromTrash(listOf("trash-1"))
        } catch (error: CancellationException) {
            cancellation = error
        }

        assertEquals("cancelled", cancellation?.message)
    }

    @Test
    fun `recoverable restore failure registers typed native authorization`() = runTest {
        assertRecoverableAuthorization(StorageAuthorizationOperation.RESTORE_TRASH) { manager, error ->
            coEvery { manager.restoreFromTrash(any(), any()) } returns Result.failure(error)
            DefaultTrashRepository(manager, gateway).restoreFromTrash(listOf("trash-1"), null)
        }
    }

    @Test
    fun `recoverable empty failure registers typed native authorization`() = runTest {
        assertRecoverableAuthorization(StorageAuthorizationOperation.EMPTY_TRASH) { manager, error ->
            coEvery { manager.emptyTrash() } returns Result.failure(error)
            DefaultTrashRepository(manager, gateway).emptyTrash()
        }
    }

    @Test
    fun `recoverable permanent delete failure registers typed native authorization`() = runTest {
        assertRecoverableAuthorization(StorageAuthorizationOperation.DELETE_TRASH) { manager, error ->
            coEvery { manager.deletePermanentlyFromTrash(any()) } returns Result.failure(error)
            DefaultTrashRepository(manager, gateway)
                .deletePermanentlyFromTrash(listOf("trash-1"))
        }
    }

    private lateinit var gateway: NativeStorageAuthorizationGateway

    private suspend fun assertRecoverableAuthorization(
        operation: StorageAuthorizationOperation,
        invoke: suspend (TrashManager, RecoverableSecurityException) -> StorageMutationResult
    ) {
        val sender = mockk<IntentSender>()
        val pendingIntent = mockk<PendingIntent>()
        every { pendingIntent.intentSender } returns sender
        val remoteAction = mockk<RemoteAction>()
        every { remoteAction.actionIntent } returns pendingIntent
        val error = RecoverableSecurityException(
            SecurityException("denied"),
            "Authorization required",
            remoteAction
        )
        val manager = mockk<TrashManager>()
        gateway = NativeStorageAuthorizationGateway()

        val result = invoke(manager, error)

        assertTrue(result is StorageMutationResult.AuthorizationRequired)
        val requirement = (result as StorageMutationResult.AuthorizationRequired).requirement
        assertEquals(operation, requirement.operation)
        assertSame(sender, gateway.resolve(requirement))
        gateway.complete(requirement)
        assertNull(gateway.resolve(requirement))
    }
}
