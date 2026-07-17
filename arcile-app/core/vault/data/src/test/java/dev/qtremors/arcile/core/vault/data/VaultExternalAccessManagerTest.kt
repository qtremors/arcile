package dev.qtremors.arcile.core.vault.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class VaultExternalAccessManagerTest {
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
        val manager = DefaultVaultExternalAccessManager(context, repository)
        val grant = manager.issue(file.ref, 60_000L).getOrThrow()
        assertFalse(grant.contentUri.contains("private-name"))
        assertEquals(64, grant.token.length)

        repository.lock(vaultId)
        manager.openGrantedContent(grant.token).getOrThrow().reader.use {
            assertEquals(0L, it.sizeBytes)
        }
        assertEquals(listOf(grant), manager.activeGrants())
        assertTrue(manager.revoke(grant.token))
        assertTrue(manager.openGrantedContent(grant.token).exceptionOrNull() is VaultFailure.ExternalGrantExpired)
        assertTrue(manager.activeGrants().isEmpty())
        scope.cancel()
    }
}
