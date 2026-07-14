package dev.qtremors.arcile.core.vault.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import dev.qtremors.arcile.core.vault.domain.VaultFailure
import dev.qtremors.arcile.core.vault.domain.VaultPath
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
    fun `creation never overwrites an existing vault location`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val repository = DefaultVaultRepository(
            context,
            ArcileDispatchers(dispatcher, dispatcher, dispatcher, dispatcher),
            scope
        )
        repository.createAppPrivateVault("Private", "one".toCharArray()).getOrThrow()
        val failure = repository.createAppPrivateVault("private", "two".toCharArray()).exceptionOrNull()
        assertTrue(failure is VaultFailure.NameConflict)
        assertEquals(1, repository.vaults.value.size)
        scope.cancel()
    }
}
