package dev.qtremors.arcile.core.vault.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import dev.qtremors.arcile.core.storage.domain.StorageVolume
import dev.qtremors.arcile.core.storage.domain.VolumeRepository
import dev.qtremors.arcile.core.vault.crypto.FileVaultDirectory
import dev.qtremors.arcile.core.vault.crypto.VaultCryptography
import dev.qtremors.arcile.core.vault.crypto.VaultDirectoryManifestCodec
import dev.qtremors.arcile.core.vault.crypto.VaultHeaderCodec
import dev.qtremors.arcile.core.vault.crypto.VaultKeyDomain
import dev.qtremors.arcile.core.vault.crypto.VaultManifestEntry
import dev.qtremors.arcile.core.vault.domain.DirectoryId
import dev.qtremors.arcile.core.vault.domain.NodeId
import dev.qtremors.arcile.core.vault.domain.VaultCreationRequest
import dev.qtremors.arcile.core.vault.domain.VaultLocation
import dev.qtremors.arcile.core.vault.domain.VaultNodeKind
import dev.qtremors.arcile.core.vault.domain.VaultObjectId
import dev.qtremors.arcile.core.vault.domain.VaultSearchQuery
import dev.qtremors.arcile.core.vault.domain.VaultSortDirection
import dev.qtremors.arcile.core.vault.domain.VaultSortField
import java.io.File
import java.nio.ByteBuffer
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class VaultSearchPagingTest {
    @Test
    fun `deep windows preserve every sort order without duplicates or missing matches`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = Files.createTempDirectory("arcile-ranked-search").toFile()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val repository = repository(context, directory, scope, dispatcher)
        val vault = repository.createAppPrivateVault("Search paging", "password".toCharArray()).getOrThrow()
        try {
            val access = FileVaultDirectory(File(context.noBackupFilesDir, "$ROOT_DIRECTORY/${vault.value}"))
            val header = VaultHeaderCodec().open(access, "password".toCharArray()).getOrThrow()
            val key = VaultCryptography.deriveDomainKey(header.masterKey, vault, VaultKeyDomain.ROOT_DIRECTORY)
            val entries = (0 until 97).map { index ->
                VaultManifestEntry(
                    NodeId.of("node-$index"), "item-${index.toString().padStart(3, '0')}.txt",
                    VaultNodeKind.FILE, 1L, (index % 5).toLong(), (index % 7).toLong(),
                    if (index % 2 == 0) "text/plain" else "application/octet-stream",
                    VaultObjectId.fromRandomBytes(ByteBuffer.allocate(32).putInt(index).array()),
                    null, VaultCryptography.randomBytes(32)
                )
            }
            try {
                val codec = VaultDirectoryManifestCodec()
                codec.publish(access, codec.prepare(vault, DirectoryId.Root, key, 1L, entries))
            } finally {
                key.fill(0)
                header.masterKey.fill(0)
                entries.forEach { it.protectedKey.fill(0) }
            }
            for (field in VaultSortField.entries) for (direction in VaultSortDirection.entries) {
                val query = VaultSearchQuery("item-", true, sortField = field, direction = direction)
                val expected = repository.search(vault, DirectoryId.Root, query).getOrThrow()
                    .items.map { it.metadata.ref.nodeId }
                var token: String? = null
                val actual = mutableListOf<NodeId>()
                do {
                    val page = repository.search(
                        vault, DirectoryId.Root, query.copy(pageSize = 3, pageToken = token, reusePageWindow = true)
                    ).getOrThrow()
                    actual += page.items.map { it.metadata.ref.nodeId }
                    token = page.nextPageToken
                    assertTrue(actual.size <= expected.size)
                } while (token != null)
                assertEquals("$field $direction", expected, actual)
            }
        } finally {
            repository.deletePermanently(vault, "Search paging").getOrThrow()
            scope.cancel()
            directory.deleteRecursively()
        }
    }

    @Test
    fun `portable pages reuse ranking and detect a descendant changed by another repository`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = Files.createTempDirectory("arcile-portable-search").toFile()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val repository = repository(context, directory.parentFile, scope, dispatcher)
        val vault = repository.create(VaultCreationRequest(
            "Portable search", "password".toCharArray(),
            VaultLocation.Portable("test", directory.name), weakPasswordConfirmed = true
        )).getOrThrow()
        var other: DefaultVaultRepository? = null
        try {
            val folder = repository.createDirectory(vault, DirectoryId.Root, "Nested").getOrThrow().ref.directoryId!!
            repeat(9) { repository.createEmptyFile(vault, folder, "item-$it.txt").getOrThrow() }
            var visits = 0
            val query = VaultSearchQuery("item-", true, pageSize = 2, reusePageWindow = true) {
                visits++
                true
            }
            val first = repository.search(vault, DirectoryId.Root, query).getOrThrow()
            var token = first.nextPageToken
            repeat(3) {
                token = repository.search(vault, DirectoryId.Root, query.copy(pageToken = token)).getOrThrow().nextPageToken
            }
            assertEquals(9, visits)
            val tail = repository.search(vault, DirectoryId.Root, query.copy(pageToken = token)).getOrThrow()
            assertEquals(listOf("item-8.txt"), tail.items.map { it.metadata.name })
            assertEquals(18, visits)

            repository.search(vault, DirectoryId.Root, query).getOrThrow()
            other = repository(context, directory.parentFile, scope, dispatcher)
            other.unlock(vault, "password".toCharArray()).getOrThrow()
            other.createEmptyFile(vault, folder, "item-00.txt").getOrThrow()
            val changed = repository.search(vault, DirectoryId.Root, query.copy(pageToken = first.nextPageToken)).getOrThrow()

            assertFalse(first.generation == changed.generation)
            assertEquals(listOf("item-1.txt", "item-2.txt"), changed.items.map { it.metadata.name })
            assertEquals(37, visits)
            repository.lockAll()
            assertTrue(repository.search(vault, DirectoryId.Root, query).isFailure)
        } finally {
            other?.lockAll()
            repository.removeRegistration(vault).getOrThrow()
            scope.cancel()
            directory.deleteRecursively()
        }
    }

    @Test
    fun `portable file count paging invalidates when only a counted descendant changes`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = Files.createTempDirectory("arcile-count-search").toFile()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val repository = repository(context, directory.parentFile, scope, dispatcher)
        val vault = repository.create(VaultCreationRequest(
            "Count search", "password".toCharArray(), VaultLocation.Portable("test", directory.name),
            weakPasswordConfirmed = true
        )).getOrThrow()
        var other: DefaultVaultRepository? = null
        try {
            val folders = (0..4).map { index ->
                val folder = repository.createDirectory(vault, DirectoryId.Root, "group-$index").getOrThrow().ref.directoryId!!
                repeat(index + 1) { repository.createEmptyFile(vault, folder, "file-$it").getOrThrow() }
                folder
            }
            var visits = 0
            val query = VaultSearchQuery(
                "group-", recursive = false, pageSize = 1, reusePageWindow = true,
                sortField = VaultSortField.FILE_COUNT, direction = VaultSortDirection.DESCENDING
            ) {
                visits++
                true
            }
            val first = repository.search(vault, DirectoryId.Root, query).getOrThrow()
            assertEquals("group-4", first.items.single().metadata.name)
            repository.search(vault, DirectoryId.Root, query.copy(pageToken = first.nextPageToken)).getOrThrow()
            assertEquals(5, visits)
            other = repository(context, directory.parentFile, scope, dispatcher)
            other.unlock(vault, "password".toCharArray()).getOrThrow()
            repeat(6) { other.createEmptyFile(vault, folders.first(), "new-$it").getOrThrow() }

            val changed = repository.search(vault, DirectoryId.Root, query.copy(pageToken = first.nextPageToken)).getOrThrow()

            assertTrue(first.generation != changed.generation)
            assertEquals("group-4", changed.items.single().metadata.name)
            assertEquals(10, visits)
        } finally {
            other?.lockAll()
            repository.removeRegistration(vault).getOrThrow()
            scope.cancel()
            directory.deleteRecursively()
        }
    }

    @Test
    fun `portable root proofs include both slots and refuse missing or replaced bytes`() = runTest {
        val directory = Files.createTempDirectory("arcile-search-versions").toFile()
        try {
            val access = FileVaultDirectory(directory)
            val id = DirectoryId.of("nested")
            val first = VaultDirectoryManifestCodec.rootSlot(id, 0L)
            val second = VaultDirectoryManifestCodec.rootSlot(id, 1L)
            access.writeAtomic(first, byteArrayOf(1, 2, 3))
            access.writeAtomic(second, byteArrayOf(4, 5, 6))
            val versions = VaultSearchVersions()
            versions.capture(access, id)
            assertTrue(versions.matches(access))
            access.writeAtomic(second, byteArrayOf(4, 5, 7))
            assertFalse(versions.matches(access))
            access.writeAtomic(second, byteArrayOf(4, 5, 6))
            assertTrue(versions.matches(access))
            access.delete(first)
            assertFalse(versions.matches(access))
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun repository(
        context: Context, root: File, scope: CoroutineScope, dispatcher: kotlinx.coroutines.CoroutineDispatcher
    ): DefaultVaultRepository {
        val volume = StorageVolume("test", "test", "Test", root.path, root.totalSpace, root.freeSpace, true, false)
        val volumes = object : VolumeRepository {
            override fun observeStorageVolumes() = MutableStateFlow(listOf(volume))
            override suspend fun getStorageVolumes() = Result.success(listOf(volume))
            override suspend fun getVolumeForPath(path: String) = Result.success(volume)
            override fun getStandardFolders(): Map<String, String?> = emptyMap()
        }
        return DefaultVaultRepository(context, ArcileDispatchers(dispatcher, dispatcher, dispatcher, dispatcher), scope,
            VaultPortableLocationResolver(volumes))
    }
}
