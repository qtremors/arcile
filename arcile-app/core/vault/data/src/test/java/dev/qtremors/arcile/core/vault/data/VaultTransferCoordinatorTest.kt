package dev.qtremors.arcile.core.vault.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import dev.qtremors.arcile.core.storage.domain.StorageVolume
import dev.qtremors.arcile.core.storage.domain.VolumeRepository
import dev.qtremors.arcile.core.vault.domain.VaultCancellationSignal
import dev.qtremors.arcile.core.vault.domain.VaultConflictDecision
import dev.qtremors.arcile.core.vault.domain.VaultConflict
import dev.qtremors.arcile.core.vault.domain.VaultConflictResolver
import dev.qtremors.arcile.core.vault.domain.VaultItemOutcome
import dev.qtremors.arcile.core.vault.domain.VaultListOptions
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class VaultTransferCoordinatorTest {
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
    fun `same vault transfers implement every root conflict policy atomically`() = runTest {
        val (repository, scope) = repository()
        val vault = repository.createAppPrivateVault("Transfers", "password".toCharArray()).getOrThrow()
        val sourceParent = repository.createDirectory(vault, VaultSessionRecord.ROOT_DIRECTORY_ID, "Source").getOrThrow()
        val destinationParent = repository.createDirectory(vault, VaultSessionRecord.ROOT_DIRECTORY_ID, "Destination").getOrThrow()
        val sourceId = requireNotNull(sourceParent.ref.directoryId)
        val destinationId = requireNotNull(destinationParent.ref.directoryId)
        val sourceFile = repository.createEmptyFile(vault, sourceId, "note.txt", "text/plain").getOrThrow()

        val first = repository.copyWithinVault(
            listOf(sourceFile.ref), destinationId, decision(VaultConflictDecision.REPLACE), neverCancelled
        )
        assertEquals(VaultItemOutcome.COMPLETED, first.items.single().outcome)
        val keepBoth = repository.copyWithinVault(
            listOf(sourceFile.ref), destinationId, decision(VaultConflictDecision.KEEP_BOTH), neverCancelled
        )
        assertEquals(VaultItemOutcome.COMPLETED, keepBoth.items.single().outcome)
        assertEquals(
            listOf("note (1).txt", "note.txt"),
            names(repository, vault, destinationId)
        )
        val skipped = repository.copyWithinVault(
            listOf(sourceFile.ref), destinationId, decision(VaultConflictDecision.SKIP), neverCancelled
        )
        assertEquals(VaultItemOutcome.SKIPPED, skipped.items.single().outcome)
        repository.copyWithinVault(
            listOf(sourceFile.ref), destinationId, decision(VaultConflictDecision.REPLACE), neverCancelled
        )
        assertEquals(listOf("note (1).txt", "note.txt"), names(repository, vault, destinationId))

        val moved = repository.moveWithinVault(
            listOf(sourceFile.ref), destinationId, decision(VaultConflictDecision.KEEP_BOTH), neverCancelled
        )
        assertEquals(VaultItemOutcome.COMPLETED, moved.items.single().outcome)
        assertTrue(names(repository, vault, sourceId).isEmpty())
        assertEquals(3, names(repository, vault, destinationId).size)
        scope.cancel()
    }

    @Test
    fun `known paste conflicts are reviewed together before any selected item is written`() = runTest {
        val (repository, scope) = repository()
        try {
            val vault = repository.createAppPrivateVault("Review", "password".toCharArray()).getOrThrow()
            val root = VaultSessionRecord.ROOT_DIRECTORY_ID
            val from = repository.createDirectory(vault, root, "Source").getOrThrow().ref.directoryId!!
            val to = repository.createDirectory(vault, root, "Destination").getOrThrow().ref.directoryId!!
            val incoming = listOf("plain.txt", "one.txt", "two.txt").map {
                repository.createEmptyFile(vault, from, it).getOrThrow()
            }
            repository.createEmptyFile(vault, to, "ONE.txt").getOrThrow()
            repository.createEmptyFile(vault, to, "two.txt").getOrThrow()
            var reviews = 0
            val resolver = object : VaultConflictResolver {
                override suspend fun review(conflicts: List<VaultConflict>): Map<VaultConflict, VaultConflictDecision> {
                    reviews++
                    assertEquals(listOf("one.txt", "two.txt"), conflicts.map(VaultConflict::sourceName))
                    assertEquals(listOf("ONE.txt", "two.txt"), conflicts.map(VaultConflict::destinationName))
                    assertEquals(listOf("ONE.txt", "two.txt"), names(repository, vault, to))
                    assertEquals(2, conflicts.map(VaultConflict::sourceIdentity).distinct().size)
                    return mapOf(conflicts[0] to VaultConflictDecision.KEEP_BOTH, conflicts[1] to VaultConflictDecision.SKIP)
                }

                override suspend fun decide(conflict: VaultConflict): VaultConflictDecision =
                    error("A reviewed conflict should not prompt twice")
            }

            val result = repository.copyWithinVault(incoming.map { it.ref }, to, resolver, neverCancelled)

            assertEquals(1, reviews)
            assertEquals(listOf(VaultItemOutcome.COMPLETED, VaultItemOutcome.COMPLETED, VaultItemOutcome.SKIPPED),
                result.items.map { it.outcome })
            assertEquals(setOf("ONE.txt", "one (1).txt", "plain.txt", "two.txt"), names(repository, vault, to).toSet())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `cancelling during paste review leaves both the source and the destination unchanged`() = runTest {
        val (repository, scope) = repository()
        try {
            val vault = repository.createAppPrivateVault("Cancel", "password".toCharArray()).getOrThrow()
            val root = VaultSessionRecord.ROOT_DIRECTORY_ID
            val from = repository.createDirectory(vault, root, "Source").getOrThrow().ref.directoryId!!
            val to = repository.createDirectory(vault, root, "Destination").getOrThrow().ref.directoryId!!
            val collision = repository.createEmptyFile(vault, from, "collision.txt").getOrThrow()
            val other = repository.createEmptyFile(vault, from, "other.txt").getOrThrow()
            val original = repository.createEmptyFile(vault, to, "collision.txt").getOrThrow()
            var cancelled = false
            val resolver = object : VaultConflictResolver {
                override suspend fun review(conflicts: List<VaultConflict>): Map<VaultConflict, VaultConflictDecision> {
                    cancelled = true
                    return conflicts.associateWith { VaultConflictDecision.REPLACE }
                }

                override suspend fun decide(conflict: VaultConflict): VaultConflictDecision = error("Transfer is cancelled")
            }

            val result = repository.moveWithinVault(
                listOf(other.ref, collision.ref), to, resolver, VaultCancellationSignal { cancelled }
            )

            assertEquals(2, result.rolledBack.size)
            assertEquals(listOf("collision.txt", "other.txt"), names(repository, vault, from))
            assertEquals(listOf(original.ref), repository.listDirectory(vault, to).getOrThrow().items.map { it.ref })
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `directory merge preserves both trees and rejects descendant moves`() = runTest {
        val (repository, scope) = repository()
        val vault = repository.createAppPrivateVault("Merge", "password".toCharArray()).getOrThrow()
        val sourceParent = repository.createDirectory(vault, VaultSessionRecord.ROOT_DIRECTORY_ID, "Source").getOrThrow()
        val destinationParent = repository.createDirectory(vault, VaultSessionRecord.ROOT_DIRECTORY_ID, "Destination").getOrThrow()
        val sourceFolder = repository.createDirectory(vault, requireNotNull(sourceParent.ref.directoryId), "Folder").getOrThrow()
        val destinationFolder = repository.createDirectory(vault, requireNotNull(destinationParent.ref.directoryId), "Folder").getOrThrow()
        repository.createEmptyFile(vault, requireNotNull(sourceFolder.ref.directoryId), "from-source", null).getOrThrow()
        repository.createEmptyFile(vault, requireNotNull(destinationFolder.ref.directoryId), "from-destination", null).getOrThrow()

        val merged = repository.copyWithinVault(
            listOf(sourceFolder.ref),
            requireNotNull(destinationParent.ref.directoryId),
            decision(VaultConflictDecision.MERGE_DIRECTORIES),
            neverCancelled
        )
        assertEquals(VaultItemOutcome.COMPLETED, merged.items.single().outcome)
        val mergedFolder = repository.listDirectory(
            vault, requireNotNull(destinationParent.ref.directoryId), VaultListOptions()
        ).getOrThrow().items.single { it.name == "Folder" }
        assertEquals(
            listOf("from-destination", "from-source"),
            names(repository, vault, requireNotNull(mergedFolder.ref.directoryId))
        )

        val child = repository.createDirectory(vault, requireNotNull(sourceFolder.ref.directoryId), "Child").getOrThrow()
        val invalid = repository.moveWithinVault(
            listOf(sourceFolder.ref), requireNotNull(child.ref.directoryId),
            decision(VaultConflictDecision.REPLACE), neverCancelled
        )
        assertEquals(VaultItemOutcome.FAILED, invalid.items.single().outcome)
        scope.cancel()
    }

    @Test
    fun `cross vault move deletes source only after verified destination commit`() = runTest {
        val (repository, scope) = repository()
        val sourceVault = repository.createAppPrivateVault("Source", "password".toCharArray()).getOrThrow()
        val destinationVault = repository.createAppPrivateVault("Destination", "password".toCharArray()).getOrThrow()
        val copied = repository.createEmptyFile(
            sourceVault, VaultSessionRecord.ROOT_DIRECTORY_ID, "copied.bin", "application/octet-stream"
        ).getOrThrow()
        val moved = repository.createEmptyFile(
            sourceVault, VaultSessionRecord.ROOT_DIRECTORY_ID, "moved.bin", "application/octet-stream"
        ).getOrThrow()

        repository.transferAcrossVaults(
            listOf(copied.ref), destinationVault, VaultSessionRecord.ROOT_DIRECTORY_ID,
            move = false, decision(VaultConflictDecision.REPLACE), neverCancelled
        )
        assertEquals(listOf("copied.bin", "moved.bin"), names(repository, sourceVault, VaultSessionRecord.ROOT_DIRECTORY_ID))
        repository.transferAcrossVaults(
            listOf(moved.ref), destinationVault, VaultSessionRecord.ROOT_DIRECTORY_ID,
            move = true, decision(VaultConflictDecision.REPLACE), neverCancelled
        )
        assertEquals(listOf("copied.bin"), names(repository, sourceVault, VaultSessionRecord.ROOT_DIRECTORY_ID))
        assertEquals(listOf("copied.bin", "moved.bin"), names(repository, destinationVault, VaultSessionRecord.ROOT_DIRECTORY_ID))

        val cancelled = repository.transferAcrossVaults(
            listOf(copied.ref), destinationVault, VaultSessionRecord.ROOT_DIRECTORY_ID,
            move = true, decision(VaultConflictDecision.REPLACE), VaultCancellationSignal { true }
        )
        assertEquals(VaultItemOutcome.ROLLED_BACK, cancelled.items.single().outcome)
        assertEquals(listOf("copied.bin"), names(repository, sourceVault, VaultSessionRecord.ROOT_DIRECTORY_ID))
        scope.cancel()
    }

    private fun repository(): Pair<DefaultVaultRepository, CoroutineScope> {
        val dispatcher = UnconfinedTestDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val volumeRepository = object : VolumeRepository {
            private val root = context.cacheDir
            private val volume = StorageVolume(
                "test", "test", "Test", root.path, root.totalSpace, root.freeSpace, true, false
            )
            override fun observeStorageVolumes() = MutableStateFlow(listOf(volume))
            override suspend fun getStorageVolumes() = Result.success(listOf(volume))
            override suspend fun getVolumeForPath(path: String) = Result.success(volume)
            override fun getStandardFolders(): Map<String, String?> = emptyMap()
        }
        return DefaultVaultRepository(
            context,
            ArcileDispatchers(dispatcher, dispatcher, dispatcher, dispatcher),
            scope,
            VaultPortableLocationResolver(volumeRepository)
        ) to scope
    }

    private suspend fun names(
        repository: DefaultVaultRepository,
        vaultId: dev.qtremors.arcile.core.vault.domain.VaultId,
        directoryId: dev.qtremors.arcile.core.vault.domain.DirectoryId
    ) = repository.listDirectory(vaultId, directoryId, VaultListOptions()).getOrThrow().items.map { it.name }

    private fun decision(value: VaultConflictDecision) = VaultConflictResolver { value }

    private companion object {
        val neverCancelled = VaultCancellationSignal { false }
    }
}
