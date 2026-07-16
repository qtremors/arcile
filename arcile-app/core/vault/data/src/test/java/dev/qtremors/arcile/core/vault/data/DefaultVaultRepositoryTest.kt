package dev.qtremors.arcile.core.vault.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import dev.qtremors.arcile.core.vault.domain.VaultFailure
import dev.qtremors.arcile.core.vault.domain.VaultPath
import dev.qtremors.arcile.core.vault.domain.VaultHealthMode
import dev.qtremors.arcile.core.vault.domain.VaultListOptions
import dev.qtremors.arcile.core.vault.domain.VaultSearchQuery
import dev.qtremors.arcile.core.vault.domain.VaultObjectId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultVaultRepositoryTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        File(context.noBackupFilesDir, ROOT_DIRECTORY).deleteRecursively()
    }

    @After
    fun tearDown() {
        File(context.noBackupFilesDir, ROOT_DIRECTORY).deleteRecursively()
    }

    @Test
    fun `vault lifecycle remains encrypted independent and lock safe`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val repository = DefaultVaultRepository(
            context,
            ArcileDispatchers(dispatcher, dispatcher, dispatcher, dispatcher),
            scope
        )

        val firstPassword = "first password".toCharArray()
        val first = repository.createAppPrivateVault("First", firstPassword).getOrThrow()
        assertTrue(firstPassword.all { it == '\u0000' })
        val second = repository.createAppPrivateVault("Second", "second password".toCharArray()).getOrThrow()
        assertEquals(2, repository.vaults.value.size)
        assertTrue(first in repository.unlockedVaultIds.value)
        assertTrue(second in repository.unlockedVaultIds.value)

        val folder = repository.createDirectory(first, VaultPath.Root, "Photos").getOrThrow()
        val renamed = repository.rename(first, folder.path, "Private photos").getOrThrow()
        assertEquals("Private photos", renamed.name)

        val lease = repository.holdSession(first).getOrThrow()
        repository.lock(first)
        assertFalse(first in repository.unlockedVaultIds.value)
        assertTrue(repository.list(first).exceptionOrNull() is VaultFailure.Locked)
        lease.close()

        val wrongPassword = "wrong".toCharArray()
        assertTrue(repository.unlock(first, wrongPassword).exceptionOrNull() is VaultFailure.AuthenticationFailed)
        assertTrue(wrongPassword.all { it == '\u0000' })
        repository.unlock(first, "first password".toCharArray()).getOrThrow()
        assertEquals(listOf("Private photos"), repository.list(first).getOrThrow().map { it.name })
        repository.delete(first, renamed.path).getOrThrow()
        assertTrue(repository.list(first).getOrThrow().isEmpty())

        repository.lockAll()
        assertTrue(repository.unlockedVaultIds.value.isEmpty())
        scope.cancel()
    }

    @Test
    fun `app private vaults with the same visible label remain independent`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val repository = DefaultVaultRepository(
            context,
            ArcileDispatchers(dispatcher, dispatcher, dispatcher, dispatcher),
            scope
        )
        val first = repository.createAppPrivateVault("Private", "one".toCharArray()).getOrThrow()
        val second = repository.createAppPrivateVault("private", "two".toCharArray()).getOrThrow()
        assertEquals(2, repository.vaults.value.size)
        assertTrue(first != second)
        scope.cancel()
    }

    @Test
    fun `stable id file system and health checks cover encrypted objects`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val repository = DefaultVaultRepository(
            context,
            ArcileDispatchers(dispatcher, dispatcher, dispatcher, dispatcher),
            scope
        )
        val vaultId = repository.createAppPrivateVault("Stable", "password".toCharArray()).getOrThrow()
        val folder = repository.createDirectory(vaultId, VaultSessionRecord.ROOT_DIRECTORY_ID, "Folder").getOrThrow()
        val folderId = requireNotNull(folder.ref.directoryId)
        val empty = repository.createEmptyFile(vaultId, folderId, "empty.txt", "text/plain").getOrThrow()

        assertEquals(listOf("Folder"), repository.listDirectory(vaultId, VaultSessionRecord.ROOT_DIRECTORY_ID, VaultListOptions()).getOrThrow().items.map { it.name })
        assertEquals(listOf("empty.txt"), repository.listDirectory(vaultId, folderId, VaultListOptions()).getOrThrow().items.map { it.name })
        assertEquals("empty.txt", repository.metadata(empty.ref).getOrThrow().name)
        repository.openReader(empty.ref).getOrThrow().use { assertEquals(0L, it.sizeBytes) }
        val search = repository.search(
            vaultId,
            VaultSessionRecord.ROOT_DIRECTORY_ID,
            VaultSearchQuery("empty", recursive = true)
        ).getOrThrow()
        assertEquals(listOf("empty.txt"), search.items.map { it.metadata.name })

        assertTrue(repository.verify(vaultId, VaultHealthMode.QUICK).getOrThrow().isHealthy)
        assertTrue(repository.verify(vaultId, VaultHealthMode.FULL).getOrThrow().isHealthy)

        val orphan = VaultObjectId.of("f".repeat(64))
        val orphanFile = File(context.noBackupFilesDir, "$ROOT_DIRECTORY/${vaultId.value}/${orphan.shardedPath()}")
        orphanFile.parentFile?.mkdirs()
        orphanFile.writeBytes(byteArrayOf(1, 2, 3))
        val damaged = repository.verify(vaultId, VaultHealthMode.QUICK).getOrThrow()
        assertTrue(orphan in damaged.orphanObjectIds)
        assertEquals(1, repository.cleanupOrphans(vaultId, setOf(orphan)).getOrThrow())
        assertFalse(orphanFile.exists())
        scope.cancel()
    }
}
