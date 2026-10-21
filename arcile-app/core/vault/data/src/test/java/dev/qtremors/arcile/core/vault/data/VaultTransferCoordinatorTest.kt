package dev.qtremors.arcile.core.vault.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import dev.qtremors.arcile.core.storage.domain.TrashRepository
import dev.qtremors.arcile.core.storage.domain.StorageVolume
import dev.qtremors.arcile.core.storage.domain.VolumeRepository
import dev.qtremors.arcile.core.vault.domain.VaultCancellationSignal
import dev.qtremors.arcile.core.vault.domain.VaultConflictDecision
import dev.qtremors.arcile.core.vault.domain.VaultConflict
import dev.qtremors.arcile.core.vault.domain.VaultConflictResolver
import dev.qtremors.arcile.core.vault.domain.VaultFailure
import dev.qtremors.arcile.core.vault.domain.VaultItemOutcome
import dev.qtremors.arcile.core.vault.domain.VaultListOptions
import io.mockk.mockk
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun `copy retry skips already copied files and leaves the source intact`() = runTest {
        val (repository, scope) = repository()
        try {
            val vault = repository.createAppPrivateVault("Retry copy", "password".toCharArray()).getOrThrow()
            val root = VaultSessionRecord.ROOT_DIRECTORY_ID
            val from = repository.createDirectory(vault, root, "From").getOrThrow().ref.directoryId!!
            val to = repository.createDirectory(vault, root, "To").getOrThrow().ref.directoryId!!
            val source = repository.createDirectory(vault, from, "Folder").getOrThrow()
            val sourceId = source.ref.directoryId!!
            val target = repository.createDirectory(vault, to, "Folder").getOrThrow().ref.directoryId!!
            repository.createEmptyFile(vault, sourceId, "already.txt").getOrThrow()
            repository.createEmptyFile(vault, sourceId, "collision.txt").getOrThrow()
            repository.createEmptyFile(vault, target, "collision.txt").getOrThrow()

            val first = repository.copyWithinVault(listOf(source.ref), to, mergeFoldersSkipFiles, neverCancelled)
            val partial = first.partial.single()
            val retry = requireNotNull(partial.mergeRetry)
            assertEquals(1, retry.remainingCount)
            val targetId = repository.listDirectory(vault, to).getOrThrow().items.single().ref.directoryId!!
            val copied = repository.listDirectory(vault, targetId).getOrThrow().items.single { it.name == "already.txt" }

            val cancelled = repository.resumeMerge(retry, decision(VaultConflictDecision.REPLACE), VaultCancellationSignal { true })
            assertEquals(VaultItemOutcome.PARTIAL, cancelled.outcome)
            assertEquals(retry, cancelled.mergeRetry)

            val result = repository.resumeMerge(retry, VaultConflictResolver { conflict ->
                assertEquals("collision.txt", conflict.sourceName)
                VaultConflictDecision.KEEP_BOTH
            }, neverCancelled)

            assertEquals(VaultItemOutcome.COMPLETED, result.outcome)
            assertNull(result.mergeRetry)
            val final = repository.listDirectory(vault, targetId).getOrThrow().items
            assertEquals(setOf("already.txt", "collision.txt", "collision (1).txt"), final.map { it.name }.toSet())
            assertEquals(copied, final.single { it.name == "already.txt" })
            assertEquals(listOf("already.txt", "collision.txt"), names(repository, vault, sourceId))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `cross vault move retry prunes empty source folders and preserves unrelated siblings`() = runTest {
        val (repository, scope) = repository()
        try {
            val sourceVault = repository.createAppPrivateVault("Retry source", "password".toCharArray()).getOrThrow()
            val targetVault = repository.createAppPrivateVault("Retry target", "password".toCharArray()).getOrThrow()
            val root = VaultSessionRecord.ROOT_DIRECTORY_ID
            val source = repository.createDirectory(sourceVault, root, "Folder").getOrThrow()
            val sourceId = source.ref.directoryId!!
            val targetId = repository.createDirectory(targetVault, root, "Folder").getOrThrow().ref.directoryId!!
            val sourceNested = repository.createDirectory(sourceVault, sourceId, "Nested").getOrThrow().ref.directoryId!!
            val targetNested = repository.createDirectory(targetVault, targetId, "Nested").getOrThrow().ref.directoryId!!
            repository.createEmptyFile(sourceVault, sourceId, "moved.txt").getOrThrow()
            repository.createEmptyFile(sourceVault, sourceNested, "collision.txt").getOrThrow()
            repository.createEmptyFile(targetVault, targetNested, "collision.txt").getOrThrow()

            val first = repository.transferAcrossVaults(
                listOf(source.ref), targetVault, root, move = true, mergeFoldersSkipFiles, neverCancelled
            )
            val retry = requireNotNull(first.partial.single().mergeRetry)
            assertEquals(listOf("Nested"), names(repository, sourceVault, sourceId))
            val newFile = repository.createEmptyFile(sourceVault, sourceId, "created later.txt").getOrThrow()

            val result = repository.resumeMerge(retry, decision(VaultConflictDecision.REPLACE), neverCancelled)

            assertEquals(VaultItemOutcome.COMPLETED, result.outcome)
            assertEquals(setOf(sourceNested), result.removedFolders)
            assertEquals(listOf(newFile), repository.listDirectory(sourceVault, sourceId).getOrThrow().items)
            val target = repository.listDirectory(targetVault, root).getOrThrow().items.single().ref.directoryId!!
            assertEquals(listOf("Nested", "moved.txt"), names(repository, targetVault, target))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `move retry refuses a replacement destination folder`() = runTest {
        val (repository, scope) = repository()
        try {
            val vault = repository.createAppPrivateVault("Retry destination", "password".toCharArray()).getOrThrow()
            val root = VaultSessionRecord.ROOT_DIRECTORY_ID
            val from = repository.createDirectory(vault, root, "From").getOrThrow().ref.directoryId!!
            val to = repository.createDirectory(vault, root, "To").getOrThrow().ref.directoryId!!
            val source = repository.createDirectory(vault, from, "Folder").getOrThrow()
            val target = repository.createDirectory(vault, to, "Folder").getOrThrow()
            repository.createEmptyFile(vault, source.ref.directoryId!!, "conflict").getOrThrow()
            repository.createEmptyFile(vault, target.ref.directoryId!!, "conflict").getOrThrow()
            val first = repository.moveWithinVault(listOf(source.ref), to, mergeFoldersSkipFiles, neverCancelled)
            val retry = requireNotNull(first.partial.single().mergeRetry)
            val published = repository.listDirectory(vault, to).getOrThrow().items.single()
            repository.deletePermanently(published.ref).getOrThrow()
            val replacement = repository.createDirectory(vault, to, "Folder").getOrThrow()

            val result = repository.resumeMerge(retry, decision(VaultConflictDecision.REPLACE), neverCancelled)

            assertEquals(VaultItemOutcome.PARTIAL, result.outcome)
            assertTrue(result.failure is VaultFailure.NodeNotFound)
            assertEquals(listOf("conflict"), names(repository, vault, source.ref.directoryId!!))
            assertTrue(names(repository, vault, replacement.ref.directoryId!!).isEmpty())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `retry can narrow a skipped subtree again and reserve sibling names during keep both`() = runTest {
        val (repository, scope) = repository()
        try {
            val vault = repository.createAppPrivateVault("Nested retry", "password".toCharArray()).getOrThrow()
            val root = VaultSessionRecord.ROOT_DIRECTORY_ID
            val from = repository.createDirectory(vault, root, "From").getOrThrow().ref.directoryId!!
            val to = repository.createDirectory(vault, root, "To").getOrThrow().ref.directoryId!!
            val source = repository.createDirectory(vault, from, "Folder").getOrThrow()
            val target = repository.createDirectory(vault, to, "Folder").getOrThrow()
            val sourceInner = repository.createDirectory(vault, source.ref.directoryId!!, "Inner").getOrThrow().ref.directoryId!!
            val targetInner = repository.createDirectory(vault, target.ref.directoryId!!, "Inner").getOrThrow().ref.directoryId!!
            for (name in listOf("dup.txt", "name.txt", "name (2).txt")) {
                repository.createEmptyFile(vault, sourceInner, name).getOrThrow()
            }
            for (name in listOf("dup.txt", "name.txt", "name (1).txt")) {
                repository.createEmptyFile(vault, targetInner, name).getOrThrow()
            }
            val first = repository.moveWithinVault(listOf(source.ref), to, VaultConflictResolver {
                if (it.sourceName == "Folder") VaultConflictDecision.MERGE_DIRECTORIES else VaultConflictDecision.SKIP
            }, neverCancelled)
            val retry = requireNotNull(first.partial.single().mergeRetry)

            val second = repository.resumeMerge(retry, VaultConflictResolver {
                when (it.sourceName) {
                    "Inner" -> VaultConflictDecision.MERGE_DIRECTORIES
                    "name.txt" -> VaultConflictDecision.KEEP_BOTH
                    "dup.txt" -> VaultConflictDecision.SKIP
                    else -> error("Unexpected conflict for ${it.sourceName}")
                }
            }, neverCancelled)
            val narrower = requireNotNull(second.mergeRetry)
            assertEquals(1, narrower.remainingCount)
            assertEquals(listOf("dup.txt"), names(repository, vault, sourceInner))
            val outputRoot = repository.listDirectory(vault, to).getOrThrow().items.single().ref.directoryId!!
            val outputInner = repository.listDirectory(vault, outputRoot).getOrThrow().items.single().ref.directoryId!!
            val published = repository.listDirectory(vault, outputInner).getOrThrow().items.filterNot { it.name == "dup.txt" }
            assertEquals(setOf("name.txt", "name (1).txt", "name (2).txt", "name (3).txt"), published.map { it.name }.toSet())

            val final = repository.resumeMerge(narrower, VaultConflictResolver {
                assertEquals("dup.txt", it.sourceName)
                VaultConflictDecision.REPLACE
            }, neverCancelled)

            assertEquals(VaultItemOutcome.COMPLETED, final.outcome)
            assertEquals(setOf(source.ref.directoryId!!, sourceInner), final.removedFolders)
            assertTrue(names(repository, vault, from).isEmpty())
            assertEquals(published, repository.listDirectory(vault, outputInner).getOrThrow().items.filterNot { it.name == "dup.txt" })
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `move out retry verifies earlier copies before deleting the vault original`() = runTest {
        val (repository, scope) = repository()
        val destination = Files.createTempDirectory("arcile-resume-export").toFile()
        try {
            val vault = repository.createAppPrivateVault("Export retry", "password".toCharArray()).getOrThrow()
            val root = VaultSessionRecord.ROOT_DIRECTORY_ID
            val folder = repository.createDirectory(vault, root, "Folder").getOrThrow()
            val folderId = folder.ref.directoryId!!
            val original = repository.createEmptyFile(vault, folderId, "already.txt").getOrThrow()
            repository.replaceFileContents(original.ref, original.revision, "original".encodeToByteArray()).getOrThrow()
            repository.createEmptyFile(vault, folderId, "collision.txt").getOrThrow()
            val output = File(destination, "Folder").also { assertTrue(it.mkdir()) }
            val collision = File(output, "collision.txt").also { it.writeText("local file") }
            val coordinator = DefaultVaultBoundaryTransferCoordinator(repository, mockk<TrashRepository>())
            val reservation = coordinator.prepareExport(listOf(folder.ref)).getOrThrow()

            val initial = coordinator.exportToDestination(
                reservation, destination.absolutePath, move = true, mergeFoldersSkipFiles, neverCancelled
            )
            val retry = requireNotNull(initial.partial.single().exportRetry)
            assertEquals(1, retry.remainingCount)
            val copied = File(output, "already.txt")
            assertEquals("original", copied.readText())
            copied.writeText("tampered")

            val blocked = coordinator.resumeExport(retry, decision(VaultConflictDecision.KEEP_BOTH), neverCancelled)
            assertEquals(VaultItemOutcome.PARTIAL, blocked.outcome)
            assertTrue(blocked.failure is VaultFailure.IntegrityFailed)
            assertEquals(listOf("Folder"), names(repository, vault, root))
            assertEquals("local file", collision.readText())

            copied.writeText("original")
            val resumed = coordinator.resumeExport(requireNotNull(blocked.exportRetry), VaultConflictResolver { conflict ->
                assertEquals("collision.txt", conflict.sourceName)
                VaultConflictDecision.KEEP_BOTH
            }, neverCancelled)

            assertEquals(VaultItemOutcome.COMPLETED, resumed.outcome)
            assertNull(resumed.exportRetry)
            assertTrue(names(repository, vault, root).isEmpty())
            assertEquals("original", copied.readText())
            assertEquals("local file", collision.readText())
            assertTrue(File(output, "collision (1).txt").isFile)
        } finally {
            scope.cancel()
            destination.deleteRecursively()
        }
    }

    @Test
    fun `copy out retry refuses an unrelated replacement directory and can resume once restored`() = runTest {
        val (repository, scope) = repository()
        val destination = Files.createTempDirectory("arcile-resume-copy-out").toFile()
        try {
            val vault = repository.createAppPrivateVault("Copy out retry", "password".toCharArray()).getOrThrow()
            val root = VaultSessionRecord.ROOT_DIRECTORY_ID
            val folder = repository.createDirectory(vault, root, "Folder").getOrThrow()
            val inner = repository.createDirectory(vault, folder.ref.directoryId!!, "Inner").getOrThrow().ref.directoryId!!
            repository.createEmptyFile(vault, inner, "collision.txt").getOrThrow()
            val output = File(destination, "Folder/Inner").also { assertTrue(it.mkdirs()) }
            File(output, "collision.txt").writeText("local file")
            val coordinator = DefaultVaultBoundaryTransferCoordinator(repository, mockk<TrashRepository>())
            val reservation = coordinator.prepareExport(listOf(folder.ref)).getOrThrow()
            val initial = coordinator.exportToDestination(
                reservation, destination.absolutePath, move = false, mergeFoldersSkipFiles, neverCancelled
            )
            val retry = requireNotNull(initial.partial.single().exportRetry)
            val renamed = File(destination, "Original")
            Files.move(output.toPath(), renamed.toPath())
            assertTrue(output.mkdir())

            val blocked = coordinator.resumeExport(retry, decision(VaultConflictDecision.REPLACE), neverCancelled)
            assertEquals(VaultItemOutcome.PARTIAL, blocked.outcome)
            assertTrue(blocked.failure is VaultFailure.ConcurrentMutation)
            assertTrue(output.listFiles().orEmpty().isEmpty())
            assertTrue(output.delete())
            Files.move(renamed.toPath(), output.toPath())

            val resumed = coordinator.resumeExport(requireNotNull(blocked.exportRetry), decision(VaultConflictDecision.KEEP_BOTH), neverCancelled)
            assertEquals(VaultItemOutcome.COMPLETED, resumed.outcome)
            assertEquals("local file", File(output, "collision.txt").readText())
            assertTrue(File(output, "collision (1).txt").isFile)
            assertEquals(listOf("collision.txt"), names(repository, vault, inner))
        } finally {
            scope.cancel()
            destination.deleteRecursively()
        }
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
        val mergeFoldersSkipFiles = VaultConflictResolver {
            if (it.sourceIsDirectory && it.destinationIsDirectory) VaultConflictDecision.MERGE_DIRECTORIES
            else VaultConflictDecision.SKIP
        }
    }
}
