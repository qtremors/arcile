package dev.qtremors.arcile.core.vault.data

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import dev.qtremors.arcile.core.vault.crypto.VaultHeaderCodec
import dev.qtremors.arcile.core.vault.domain.*
import io.mockk.*
import java.io.File
import java.util.concurrent.Executors
import javax.crypto.Cipher
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class VaultBiometricDispatchTest {
    @Test fun `enrollment and authenticated recovery run on IO and lock clears the published session`() = runBlocking {
        fixture { repository, id, secret, store ->
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            var enrolled: ByteArray? = null
            var unlocked: ByteArray? = null
            every { store.prepareEnrollment(any()) } answers { assertIo(); cipher }
            every { store.prepareUnlock(any()) } answers { assertIo(); cipher }
            every { store.finishEnrollment(any(), cipher, any()) } answers {
                assertIo(); enrolled = thirdArg(); Unit
            }
            every { store.finishUnlock(any(), cipher) } answers {
                assertIo(); secret.copyOf().also { unlocked = it }
            }
            val password = "test password".toCharArray()
            val enroll = repository.prepareBiometricEnrollment(id, password).getOrThrow()
            assertTrue(password.all { it == '\u0000' })
            enroll.completeAfterAuthentication().getOrThrow()
            assertTrue(requireNotNull(enrolled).all { it == 0.toByte() })
            repository.lock(id)
            val challenge = repository.prepareBiometricUnlock(id).getOrThrow()
            challenge.completeAfterAuthentication().getOrThrow()
            assertTrue(id in repository.unlockedVaultIds.value)
            assertTrue(repository.list(id).isSuccess)
            repository.lockAll()
            assertFalse(id in repository.unlockedVaultIds.value)
            assertTrue(requireNotNull(unlocked).all { it == 0.toByte() })
        }
    }

    @Test fun `background locking invalidates pending authentication before decryption`() = runBlocking {
        fixture { repository, id, _, store ->
            repository.lock(id)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            every { store.prepareUnlock(any()) } returns cipher
            val challenge = repository.prepareBiometricUnlock(id).getOrThrow()
            repository.lockAll()
            assertTrue(challenge.completeAfterAuthentication().exceptionOrNull() is VaultFailure.Locked)
            verify(exactly = 0) { store.finishUnlock(any(), any()) }
            assertFalse(id in repository.unlockedVaultIds.value)
        }
    }

    @Test fun `cancelled biometric completion propagates cancellation and clears its secret`() = runBlocking {
        fixture { repository, id, secret, store ->
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            var ownedSecret: ByteArray? = null
            every { store.prepareEnrollment(any()) } returns cipher
            every { store.finishEnrollment(any(), cipher, any()) } answers {
                ownedSecret = thirdArg(); throw CancellationException("cancel authentication")
            }
            val challenge = repository.prepareBiometricEnrollment(id, "test password".toCharArray()).getOrThrow()
            assertTrue(runCatching { challenge.completeAfterAuthentication() }.exceptionOrNull() is CancellationException)
            assertTrue(challenge.isClosed)
            assertTrue(requireNotNull(ownedSecret).all { it == 0.toByte() })
            repository.lock(id)
            val completionJob = Job()
            var decrypted: ByteArray? = null
            every { store.prepareUnlock(any()) } returns cipher
            every { store.finishUnlock(any(), cipher) } answers {
                completionJob.cancel()
                secret.copyOf().also { decrypted = it }
            }
            val unlock = repository.prepareBiometricUnlock(id).getOrThrow()
            assertTrue(runCatching {
                withContext(completionJob) { unlock.completeAfterAuthentication() }
            }.exceptionOrNull() is CancellationException)
            assertFalse(id in repository.unlockedVaultIds.value)
            assertTrue(requireNotNull(decrypted).all { it == 0.toByte() })
        }
    }

    @Test fun `cancellation before IO preparation clears the supplied password`() = runTest {
        val root = java.nio.file.Files.createTempDirectory("biometric-cancel").toFile()
        val context = testContext(root)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        try {
            val repository = DefaultVaultRepository(context, ArcileDispatchers(dispatcher, dispatcher, dispatcher, dispatcher),
                scope, mockk(relaxed = true))
            val password = "pending password".toCharArray()
            val job = async(start = CoroutineStart.UNDISPATCHED) {
                repository.prepareBiometricEnrollment(VaultId.random(), password)
            }
            job.cancelAndJoin()
            assertTrue(password.all { it == '\u0000' })
        } finally { scope.cancel(); root.deleteRecursively() }
    }

    private suspend fun fixture(block: suspend (DefaultVaultRepository, VaultId, ByteArray, VaultBiometricStore) -> Unit) {
        val root = java.nio.file.Files.createTempDirectory("biometric-dispatch").toFile()
        val context = testContext(root)
        val dispatcher = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "vault-io-test") }.asCoroutineDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)

        var secret: ByteArray? = null
        try {
            val repository = DefaultVaultRepository(context, ArcileDispatchers(dispatcher, dispatcher, Dispatchers.Unconfined, dispatcher),
                scope, mockk(relaxed = true))
            val id = repository.createAppPrivateVault("Test", "test password".toCharArray()).getOrThrow()
            val directory = File(root, ROOT_DIRECTORY).listFiles().orEmpty().single { it.isDirectory && !it.name.startsWith(".") }
            secret = withContext(dispatcher) { VaultHeaderCodec().open(directory, "test password".toCharArray()).getOrThrow().masterKey }
            val field = VaultRepositoryFoundation::class.java.getDeclaredField("biometricStore").apply { isAccessible = true }
            // Exercise the repository's own store without requiring Android Keystore.
            val store = spyk(field.get(repository) as VaultBiometricStore)
            field.set(repository, store)
            block(repository, id, secret, store)
            repository.lockAll()
        } finally {
            secret?.fill(0)
            scope.cancel()
            dispatcher.close()

            root.deleteRecursively()
        }
    }

    private fun testContext(root: File) = object : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
        override fun getNoBackupFilesDir(): File = root
    }
    private fun assertIo() = assertEquals("vault-io-test", Thread.currentThread().name.substringBefore(" @coroutine#"))
}
