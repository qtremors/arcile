package dev.qtremors.arcile.core.vault.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import dev.qtremors.arcile.core.ui.externalfile.ExternalFileAccessHelper
import dev.qtremors.arcile.core.ui.externalfile.ExternalFileAccessProvider
import dev.qtremors.arcile.core.storage.domain.StorageVolume
import dev.qtremors.arcile.core.storage.domain.VolumeRepository
import dev.qtremors.arcile.core.vault.domain.VaultFailure
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowStatFs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class VaultExternalAccessManagerTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        File(context.noBackupFilesDir, ROOT_DIRECTORY).deleteRecursively()
        ExternalFileAccessHelper.clearPrivatePlaintextFallbacks(context)
    }

    @After
    fun tearDown() {
        File(context.noBackupFilesDir, ROOT_DIRECTORY).deleteRecursively()
        ExternalFileAccessHelper.clearPrivatePlaintextFallbacks(context)
    }

    @Test
    fun `opaque bounded grant survives interactive lock and revocation closes access`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val root = context.cacheDir
        val volume = StorageVolume("test", "test", "Test", root.path, root.totalSpace, root.freeSpace, true, false)
        val volumes = object : VolumeRepository {
            override fun observeStorageVolumes() = MutableStateFlow(listOf(volume))
            override suspend fun getStorageVolumes() = Result.success(listOf(volume))
            override suspend fun getVolumeForPath(path: String) = Result.success(volume)
            override fun getStandardFolders(): Map<String, String?> = emptyMap()
        }
        val repository = DefaultVaultRepository(
            context,
            ArcileDispatchers(dispatcher, dispatcher, dispatcher, dispatcher),
            scope,
            VaultPortableLocationResolver(volumes)
        )
        val vaultId = repository.createAppPrivateVault("External", "password".toCharArray()).getOrThrow()
        val file = repository.createEmptyFile(
            vaultId, VaultSessionRecord.ROOT_DIRECTORY_ID, "private-name.txt", "text/plain"
        ).getOrThrow()
        val manager = DefaultVaultExternalAccessManager(context, repository, scope)
        val grant = manager.issue(file.ref, 60_000L).getOrThrow()
        val expiring = manager.issue(file.ref, DefaultVaultExternalAccessManager.MAX_LIFETIME_MILLIS).getOrThrow()
        assertFalse(grant.contentUri.contains("private-name"))
        assertEquals(64, grant.token.length)

        repository.lock(vaultId)
        manager.openGrantedContent(grant.token, consumerUid = 1001).getOrThrow().reader.use {
            assertEquals(0L, it.sizeBytes)
        }
        assertTrue(grant in manager.activeGrants())
        assertTrue(manager.revoke(grant.token))
        assertTrue(manager.openGrantedContent(grant.token, consumerUid = 1001).exceptionOrNull() is VaultFailure.ExternalGrantExpired)
        assertEquals(listOf(expiring), manager.activeGrants())

        assertEquals(expiring, manager.describe(expiring.token).getOrThrow())
        val reader = manager.openGrantedContent(expiring.token, consumerUid = 2001).getOrThrow().reader
        assertTrue(
            manager.openGrantedContent(expiring.token, consumerUid = 2002).exceptionOrNull()
                is VaultFailure.ExternalGrantConsumerMismatch
        )
        reader.close()
        advanceTimeBy(DefaultVaultExternalAccessManager.REVOKE_AFTER_CLOSE_MILLIS - 1)
        runCurrent()
        assertEquals(listOf(expiring), manager.activeGrants())
        advanceTimeBy(1)
        runCurrent()
        assertTrue(manager.activeGrants().isEmpty())
        assertTrue(
            manager.issue(file.ref, DefaultVaultExternalAccessManager.MAX_LIFETIME_MILLIS + 1).isFailure
        )
        scope.cancel()
    }

    @Test
    fun `confirmed plaintext fallback stays private and revocation deletes it`() = runTest {
        ShadowStatFs.registerStats(context.cacheDir.absolutePath, 100_000, 100_000, 100_000)
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val root = context.cacheDir
        val volume = StorageVolume("test-fallback", "test-fallback", "Test", root.path, root.totalSpace, root.freeSpace, true, false)
        val volumes = object : VolumeRepository {
            override fun observeStorageVolumes() = MutableStateFlow(listOf(volume))
            override suspend fun getStorageVolumes() = Result.success(listOf(volume))
            override suspend fun getVolumeForPath(path: String) = Result.success(volume)
            override fun getStandardFolders(): Map<String, String?> = emptyMap()
        }
        val repository = DefaultVaultRepository(
            context,
            ArcileDispatchers(dispatcher, dispatcher, dispatcher, dispatcher),
            scope,
            VaultPortableLocationResolver(volumes)
        )
        val vaultId = repository.createAppPrivateVault("Fallback", "password".toCharArray()).getOrThrow()
        val file = repository.createEmptyFile(
            vaultId,
            VaultSessionRecord.ROOT_DIRECTORY_ID,
            "compatibility.txt",
            "text/plain"
        ).getOrThrow()
        val manager = DefaultVaultExternalAccessManager(context, repository, scope)

        val fallback = manager.issuePlaintextFallback(file.ref, 60_000L).getOrThrow()
        val uri = android.net.Uri.parse(fallback.contentUri)
        val staged = File(
            ExternalFileAccessProvider.stagingRoot(context),
            uri.pathSegments.joinToString(File.separator)
        )
        assertEquals(ExternalFileAccessProvider.authority(context), uri.authority)
        assertTrue(staged.isFile)
        assertTrue(staged.canonicalPath.startsWith(context.cacheDir.canonicalPath + File.separator))
        assertEquals(listOf(fallback), manager.activeGrants())

        repository.lock(vaultId)
        assertTrue(manager.revoke(fallback.token))
        assertFalse(staged.exists())
        assertTrue(manager.activeGrants().isEmpty())
        scope.cancel()
    }
}
