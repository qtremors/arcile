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
import dev.qtremors.arcile.core.vault.domain.VaultPath
import dev.qtremors.arcile.core.vault.domain.VaultRecoveryKind
import io.mockk.mockk
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CancellationException
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

    @Test
    fun `recovered cross vault move copies only untouched items after both vaults unlock`() = runTest {
        val (before, beforeScope) = repository()
        val (after, afterScope) = try {
            val source = before.createAppPrivateVault("Interrupted source", "password".toCharArray()).getOrThrow()
            val target = before.createAppPrivateVault("Interrupted target", "password".toCharArray()).getOrThrow()
            val root = VaultSessionRecord.ROOT_DIRECTORY_ID
            val sourceNodes = listOf("uncertain.txt", "remaining.txt").map {
                before.createEmptyFile(source, root, it).getOrThrow()
            }
            val sentinel = before.createEmptyFile(target, root, "sentinel.txt").getOrThrow()
            val session = before.createBoundarySession(listOf(sentinel.ref))
            try {
                before.operationJournal.start(session, VaultQueuePlan(
                    VaultRecoveryKind.PASTE, root.value, move = true, nodes = sourceNodes.map { QueuedVaultNode.from(it.ref) }
                )).use { it.beginItem(0) }
            } finally {
                session.destroy()
            }
            before.lockAll()
            val (restarted, scope) = repository()
            try {
                assertTrue(restarted.listInterruptedOperations(target).isFailure)
                restarted.unlock(target, "password".toCharArray()).getOrThrow()
                val blocked = restarted.listInterruptedOperations(target).getOrThrow().single()
                assertEquals(setOf(source), blocked.lockedSourceVaults)
                restarted.unlock(source, "password".toCharArray()).getOrThrow()
                val ready = restarted.listInterruptedOperations(target).getOrThrow().single()
                assertTrue(ready.lockedSourceVaults.isEmpty())
                val plan = restarted.interruptedPlan(ready).getOrThrow()

                val result = restarted.resumeInterruptedPaste(ready, plan, decision(VaultConflictDecision.SKIP), neverCancelled)

                assertEquals(1, result.completed.size)
                assertEquals(listOf("remaining.txt", "sentinel.txt"), names(restarted, target, root))
                assertEquals(listOf("remaining.txt", "uncertain.txt"), names(restarted, source, root))
                assertTrue(restarted.listInterruptedOperations(target).getOrThrow().isEmpty())
            } catch (error: Throwable) {
                scope.cancel()
                throw error
            }
            restarted to scope
        } finally {
            beforeScope.cancel()
        }
        after.lockAll()
        afterScope.cancel()
    }

    @Test
    fun `recovered import uses the recorded directory identity and preserves local originals`() = runTest {
        val (repository, scope) = repository()
        val local = Files.createTempDirectory("arcile-recovery-import").toFile()
        try {
            val vault = repository.createAppPrivateVault("Interrupted import", "password".toCharArray()).getOrThrow()
            val root = VaultSessionRecord.ROOT_DIRECTORY_ID
            val folder = repository.createDirectory(vault, root, "Original name").getOrThrow()
            val id = folder.ref.directoryId!!
            val uncertain = File(local, "uncertain.txt").also { it.writeText("might have been imported") }
            val ready = File(local, "remaining.txt").also { it.writeText("still local") }
            val session = repository.createBoundarySession(listOf(folder.ref))
            try {
                repository.operationJournal.start(session, VaultQueuePlan(
                    VaultRecoveryKind.IMPORT, id.value, move = true, localSources = listOf(uncertain.path, ready.path)
                )).use { it.beginItem(0) }
            } finally {
                session.destroy()
            }
            repository.rename(folder.ref, "Renamed").getOrThrow()
            val queue = repository.listInterruptedOperations(vault).getOrThrow().single()
            val token = requireNotNull(repository.reserveImport(vault))

            val result = repository.importUris(token, VaultPath.Root, emptyList(), recoveryId = queue.id) { _, _, _, _, _ -> }
                .getOrThrow()

            assertEquals(1, result.completed.size)
            assertEquals(listOf("remaining.txt"), names(repository, vault, id))
            assertTrue(uncertain.isFile)
            assertEquals("still local", ready.readText())
            assertTrue(repository.listInterruptedOperations(vault).getOrThrow().isEmpty())
        } finally {
            scope.cancel()
            local.deleteRecursively()
        }
    }

    @Test
    fun `import interruption preserves the queue while an explicit cancellation discards it`() = runTest {
        val (repository, scope) = repository()
        val local = Files.createTempDirectory("arcile-recovery-stop-import").toFile()
        try {
            val vault = repository.createAppPrivateVault("Stopped import", "password".toCharArray()).getOrThrow()
            val sources = listOf("uncertain.txt", "remaining.txt").map { name ->
                File(local, name).also { it.writeText(name) }.path
            }
            val interruptedToken = requireNotNull(repository.reserveImport(vault))
            val interruption = runCatching {
                repository.importUris(interruptedToken, VaultPath.Root, sources) { _, _, _, _, _ ->
                    throw CancellationException("Service stopped")
                }
            }
            assertTrue(interruption.exceptionOrNull() is CancellationException)
            val pending = repository.listInterruptedOperations(vault).getOrThrow().single()
            assertTrue(!pending.hasUncertainItem)
            assertEquals(2, pending.remainingItems)
            repository.discardInterruptedOperation(pending).getOrThrow()

            val cancelledToken = requireNotNull(repository.reserveImport(vault))
            val cancelled = runCatching {
                repository.importUris(cancelledToken, VaultPath.Root, sources) { _, _, _, _, _ ->
                    throw UserCancelledVaultImport()
                }
            }
            assertTrue(cancelled.exceptionOrNull() is UserCancelledVaultImport)
            assertTrue(repository.listInterruptedOperations(vault).getOrThrow().isEmpty())
            assertTrue(sources.all { File(it).isFile })
        } finally {
            scope.cancel()
            local.deleteRecursively()
        }
    }

    @Test
    fun `interrupted export retries an item stopped before its publication receipt and preserves settled output`() = runTest {
        val (repository, scope) = repository()
        val output = Files.createTempDirectory("arcile-recovery-export-stop").toFile()
        try {
            val vault = repository.createAppPrivateVault("Export interruption", "password".toCharArray()).getOrThrow()
            val root = VaultSessionRecord.ROOT_DIRECTORY_ID
            val nodes = listOf("finished.txt", "conflict.txt", "remaining.txt").map {
                repository.createEmptyFile(vault, root, it).getOrThrow()
            }
            val collision = File(output, "conflict.txt").also { it.writeText("local original") }
            val coordinator = DefaultVaultBoundaryTransferCoordinator(repository, mockk<TrashRepository>())
            val reservation = coordinator.prepareExport(nodes.map { it.ref }).getOrThrow()
            val stop = object : VaultConflictResolver {
                override suspend fun review(conflicts: List<VaultConflict>): Map<VaultConflict, VaultConflictDecision> =
                    emptyMap()

                override suspend fun decide(conflict: VaultConflict): VaultConflictDecision {
                    assertEquals("conflict.txt", conflict.sourceName)
                    throw CancellationException("Process stopped during the conflict")
                }
            }

            val interrupted = runCatching {
                coordinator.exportToDestination(reservation, output.path, move = false, stop, neverCancelled)
            }
            assertTrue(interrupted.exceptionOrNull() is CancellationException)
            val queue = repository.listInterruptedOperations(vault).getOrThrow().single()
            assertEquals(1, queue.settledItems)
            assertEquals(2, queue.remainingItems)
            assertTrue(!queue.hasUncertainItem)
            val finished = File(output, "finished.txt")
            assertTrue(finished.isFile)
            finished.writeText("existing export kept")

            val result = coordinator.resumeInterruptedExport(
                queue, repository.interruptedPlan(queue).getOrThrow(), decision(VaultConflictDecision.REPLACE), neverCancelled
            )

            assertEquals(listOf("conflict.txt", "remaining.txt"), result.completed.map { it.displayName })
            assertEquals("existing export kept", finished.readText())
            assertEquals("", collision.readText())
            assertTrue(File(output, "remaining.txt").isFile)
            assertTrue(repository.listInterruptedOperations(vault).getOrThrow().isEmpty())
        } finally {
            scope.cancel()
            output.deleteRecursively()
        }
    }

    @Test
    fun `recovered export refuses replacement of the recorded destination and retains originals`() = runTest {
        val (repository, scope) = repository()
        val local = Files.createTempDirectory("arcile-recovery-export").toFile()
        try {
            val vault = repository.createAppPrivateVault("Interrupted export", "password".toCharArray()).getOrThrow()
            val root = VaultSessionRecord.ROOT_DIRECTORY_ID
            val nodes = listOf("settled.txt", "uncertain.txt", "remaining.txt").map {
                repository.createEmptyFile(vault, root, it).getOrThrow()
            }
            val output = File(local, "Output").canonicalFile.also { assertTrue(it.mkdir()) }
            val plan = VaultQueuePlan(
                VaultRecoveryKind.EXPORT, output.path, move = true, nodes = nodes.map { QueuedVaultNode.from(it.ref) },
                localDestinationIdentity = ExportFolderIdentity.read(output)
            )
            val session = repository.createBoundarySession(nodes.map { it.ref })
            try {
                repository.operationJournal.start(session, plan).use {
                    it.beginItem(0)
                    it.finishItem(0)
                    it.beginItem(1)
                }
            } finally {
                session.destroy()
            }
            val coordinator = DefaultVaultBoundaryTransferCoordinator(repository, mockk<TrashRepository>())
            val queue = repository.listInterruptedOperations(vault).getOrThrow().single()
            val previous = File(local, "Previous")
            Files.move(output.toPath(), previous.toPath())
            assertTrue(output.mkdir())

            val blocked = runCatching {
                coordinator.resumeInterruptedExport(queue, plan, decision(VaultConflictDecision.SKIP), neverCancelled)
            }
            assertTrue(blocked.isFailure)
            assertTrue(output.listFiles().orEmpty().isEmpty())
            assertEquals(queue, repository.listInterruptedOperations(vault).getOrThrow().single())
            assertTrue(output.delete())
            Files.move(previous.toPath(), output.toPath())
            File(output, "settled.txt").writeText("already exported")

            val result = coordinator.resumeInterruptedExport(queue, plan, decision(VaultConflictDecision.REPLACE), neverCancelled)

            assertEquals(1, result.completed.size)
            assertEquals("already exported", File(output, "settled.txt").readText())
            assertTrue(File(output, "remaining.txt").isFile)
            assertTrue(!File(output, "uncertain.txt").exists())
            assertEquals(listOf("remaining.txt", "settled.txt", "uncertain.txt"), names(repository, vault, root))
            assertTrue(repository.listInterruptedOperations(vault).getOrThrow().isEmpty())
        } finally {
            scope.cancel()
            local.deleteRecursively()
        }
    }

    @Test
    fun `published partial folder merge resumes only skipped children after repository restart`() = runTest {
        val (repository, scope) = repository()
        val vault = repository.createAppPrivateVault("Checkpointed merge", "password".toCharArray()).getOrThrow()
        val root = VaultSessionRecord.ROOT_DIRECTORY_ID
        val sourceParent = repository.createDirectory(vault, root, "Source").getOrThrow()
        val targetParent = repository.createDirectory(vault, root, "Target").getOrThrow()
        val source = repository.createDirectory(vault, requireNotNull(sourceParent.ref.directoryId), "Folder").getOrThrow()
        val target = repository.createDirectory(vault, requireNotNull(targetParent.ref.directoryId), "Folder").getOrThrow()
        val sourceDirectory = requireNotNull(source.ref.directoryId)
        val targetParentDirectory = requireNotNull(targetParent.ref.directoryId)
        repository.createEmptyFile(vault, sourceDirectory, "Shared.txt").getOrThrow()
        repository.createEmptyFile(vault, sourceDirectory, "Fresh.txt").getOrThrow()
        repository.createEmptyFile(vault, requireNotNull(target.ref.directoryId), "Shared.txt").getOrThrow()
        val initial = repository.moveWithinVault(listOf(source.ref), targetParentDirectory, mergeFoldersSkipFiles, neverCancelled)
        assertEquals(1, initial.partial.size)
        assertEquals(listOf("Shared.txt"), names(repository, vault, sourceDirectory))
        repository.lockAll()
        scope.cancel()

        val (restarted, nextScope) = repository()
        try {
            restarted.unlock(vault, "password".toCharArray()).getOrThrow()
            val queue = restarted.listInterruptedOperations(vault).getOrThrow().single()
            assertEquals(1, queue.remainingItems)
            assertTrue(queue.wasMove)
            val result = restarted.resumeInterruptedPaste(queue, restarted.interruptedPlan(queue).getOrThrow(),
                decision(VaultConflictDecision.KEEP_BOTH), neverCancelled)
            assertEquals(1, result.completed.size)
            val published = restarted.listDirectory(vault, targetParentDirectory).getOrThrow().items.single()
            assertEquals(3, names(restarted, vault, requireNotNull(published.ref.directoryId)).size)
            assertEquals(1, names(restarted, vault, requireNotNull(published.ref.directoryId)).count { it == "Fresh.txt" })
            assertEquals(listOf("Shared.txt"), names(restarted, vault, sourceDirectory))
            assertTrue(restarted.listInterruptedOperations(vault).getOrThrow().isEmpty())
        } finally { restarted.lockAll(); nextScope.cancel() }
    }

    @Test
    fun `partial export resumes into its published folder after repository restart without recopying completed children`() = runTest {
        val (repository, scope) = repository()
        val local = Files.createTempDirectory("arcile-persisted-export-").toFile()
        try {
            val vault = repository.createAppPrivateVault("Checkpointed export", "password".toCharArray()).getOrThrow()
            val root = repository.createDirectory(vault, VaultSessionRecord.ROOT_DIRECTORY_ID, "Folder").getOrThrow()
            val directory = requireNotNull(root.ref.directoryId)
            val shared = repository.createEmptyFile(vault, directory, "Shared.txt").getOrThrow()
            val fresh = repository.createEmptyFile(vault, directory, "Fresh.txt").getOrThrow()
            repository.replaceFileContents(shared.ref, shared.revision, "from vault".encodeToByteArray()).getOrThrow()
            repository.replaceFileContents(fresh.ref, fresh.revision, "new data".encodeToByteArray()).getOrThrow()
            val output = File(local, "Output").canonicalFile.also { assertTrue(it.mkdir()) }
            val existing = File(output, "Folder").also { assertTrue(it.mkdir()) }
            File(existing, "Shared.txt").writeText("from regular storage")
            val coordinator = DefaultVaultBoundaryTransferCoordinator(repository, mockk<TrashRepository>())
            val initial = coordinator.exportToDestination(coordinator.prepareExport(listOf(root.ref)).getOrThrow(),
                output.path, false, mergeFoldersSkipFiles, neverCancelled)
            assertEquals(1, initial.partial.size)
            assertEquals("new data", File(output, "Folder/Fresh.txt").readText())
            repository.lockAll()
            scope.cancel()

            val (restarted, nextScope) = repository()
            try {
                restarted.unlock(vault, "password".toCharArray()).getOrThrow()
                val queue = restarted.listInterruptedOperations(vault).getOrThrow().single()
                val resumedCoordinator = DefaultVaultBoundaryTransferCoordinator(restarted, mockk<TrashRepository>())
                val result = resumedCoordinator.resumeInterruptedExport(queue, restarted.interruptedPlan(queue).getOrThrow(),
                    decision(VaultConflictDecision.REPLACE), neverCancelled)
                assertEquals(1, result.completed.size)
                assertEquals("from vault", File(output, "Folder/Shared.txt").readText())
                assertEquals("new data", File(output, "Folder/Fresh.txt").readText())
                assertEquals(2, File(output, "Folder").listFiles().orEmpty().size)
                assertEquals(listOf("Fresh.txt", "Shared.txt"), names(restarted, vault, directory))
                assertTrue(restarted.listInterruptedOperations(vault).getOrThrow().isEmpty())
            } finally { restarted.lockAll(); nextScope.cancel() }
        } finally { scope.cancel(); local.deleteRecursively() }
    }

    @Test
    fun `same-session move retry journals its first child before interruption and restart`() = runTest {
        val (repository, scope) = repository()
        try {
            val vault = repository.createAppPrivateVault("Resumed move", "password".toCharArray()).getOrThrow()
            val root = VaultSessionRecord.ROOT_DIRECTORY_ID
            val destination = repository.createDirectory(vault, root, "Destination").getOrThrow()
            val destinationId = requireNotNull(destination.ref.directoryId)
            val source = repository.createDirectory(vault, root, "Folder").getOrThrow()
            val sourceId = requireNotNull(source.ref.directoryId)
            val target = repository.createDirectory(vault, destinationId, "Folder").getOrThrow()
            val targetId = requireNotNull(target.ref.directoryId)
            for (name in listOf("A.txt", "B.txt")) {
                repository.createEmptyFile(vault, sourceId, name).getOrThrow()
                repository.createEmptyFile(vault, targetId, name).getOrThrow()
            }
            val initial = repository.moveWithinVault(listOf(source.ref), destinationId, mergeFoldersSkipFiles, neverCancelled)
            val retry = initial.partial.single().mergeRetry as PendingVaultMerge
            assertTrue(retry.origin != null)
            val publishedTargetId = retry.children.first().destination
            val first = retry.children.first().sourceName
            val remaining = retry.children.last().sourceName
            val stop = object : VaultConflictResolver {
                override suspend fun review(conflicts: List<VaultConflict>) = emptyMap<VaultConflict, VaultConflictDecision>()
                override suspend fun decide(conflict: VaultConflict): VaultConflictDecision {
                    if (conflict.sourceName != first) throw CancellationException("Process stopped after the first child")
                    return VaultConflictDecision.REPLACE
                }
            }
            assertTrue(runCatching { repository.resumeMerge(retry, stop, neverCancelled) }.exceptionOrNull() is CancellationException)
            assertEquals(listOf(remaining), names(repository, vault, sourceId))
            repository.lockAll()
            scope.cancel()

            val (restarted, nextScope) = repository()
            try {
                restarted.unlock(vault, "password".toCharArray()).getOrThrow()
                assertTrue(runCatching {
                    restarted.resumeMerge(retry, decision(VaultConflictDecision.REPLACE), neverCancelled)
                }.isFailure)
                val queue = restarted.listInterruptedOperations(vault).getOrThrow().single()
                assertTrue(!queue.hasUncertainItem)
                val conflicts = mutableListOf<String>()
                val result = restarted.resumeInterruptedPaste(queue, restarted.interruptedPlan(queue).getOrThrow(),
                    VaultConflictResolver { conflicts += it.sourceName; VaultConflictDecision.REPLACE }, neverCancelled)
                assertEquals(1, result.completed.size)
                assertEquals(listOf(remaining), conflicts)
                assertEquals(listOf("A.txt", "B.txt"), names(restarted, vault, publishedTargetId))
                // Recovery copies the final child. Only the earlier, explicitly resumed move deleted its source.
                assertEquals(listOf(remaining), names(restarted, vault, sourceId))
                assertTrue(restarted.listInterruptedOperations(vault).getOrThrow().isEmpty())
            } finally { restarted.lockAll(); nextScope.cancel() }
        } finally { scope.cancel() }
    }

    @Test
    fun `same-session export retry keeps its completed child across restart and preserves a user edit`() = runTest {
        val (repository, scope) = repository()
        val local = Files.createTempDirectory("arcile-resumed-export-stop-").toFile()
        try {
            val vault = repository.createAppPrivateVault("Resumed export", "password".toCharArray()).getOrThrow()
            val source = repository.createDirectory(vault, VaultSessionRecord.ROOT_DIRECTORY_ID, "Folder").getOrThrow()
            val sourceId = requireNotNull(source.ref.directoryId)
            val output = File(local, "Output").canonicalFile.apply { mkdir() }
            val folder = File(output, "Folder").apply { mkdir() }
            for (name in listOf("A.txt", "B.txt")) {
                val node = repository.createEmptyFile(vault, sourceId, name).getOrThrow()
                repository.replaceFileContents(node.ref, node.revision, "vault $name".encodeToByteArray()).getOrThrow()
                File(folder, name).writeText("regular $name")
            }
            val coordinator = DefaultVaultBoundaryTransferCoordinator(repository, mockk<TrashRepository>())
            val initial = coordinator.exportToDestination(coordinator.prepareExport(listOf(source.ref)).getOrThrow(),
                output.path, true, mergeFoldersSkipFiles, neverCancelled)
            val retry = initial.partial.single().exportRetry as PendingVaultExport
            assertTrue(retry.origin != null)
            val first = retry.deferred.first().node.name
            val remaining = retry.deferred.last().node.name
            val stop = object : VaultConflictResolver {
                override suspend fun review(conflicts: List<VaultConflict>) = emptyMap<VaultConflict, VaultConflictDecision>()
                override suspend fun decide(conflict: VaultConflict): VaultConflictDecision {
                    if (conflict.sourceName != first) throw CancellationException("Process stopped after the first export")
                    return VaultConflictDecision.REPLACE
                }
            }
            assertTrue(runCatching { coordinator.resumeExport(retry, stop, neverCancelled) }.exceptionOrNull() is CancellationException)
            assertEquals("vault $first", File(folder, first).readText())
            File(folder, first).writeText("user edit after the completed export")
            repository.lockAll()
            scope.cancel()

            val (restarted, nextScope) = repository()
            try {
                restarted.unlock(vault, "password".toCharArray()).getOrThrow()
                val queue = restarted.listInterruptedOperations(vault).getOrThrow().single()
                val resumed = DefaultVaultBoundaryTransferCoordinator(restarted, mockk<TrashRepository>())
                assertTrue(runCatching {
                    resumed.resumeExport(retry, decision(VaultConflictDecision.REPLACE), neverCancelled)
                }.isFailure)
                val conflicts = mutableListOf<String>()
                val result = resumed.resumeInterruptedExport(queue, restarted.interruptedPlan(queue).getOrThrow(),
                    VaultConflictResolver { conflicts += it.sourceName; VaultConflictDecision.REPLACE }, neverCancelled)
                assertEquals(1, result.completed.size)
                assertEquals(listOf(remaining), conflicts)
                assertEquals("user edit after the completed export", File(folder, first).readText())
                assertEquals("vault $remaining", File(folder, remaining).readText())
                assertEquals(listOf("A.txt", "B.txt"), names(restarted, vault, sourceId))
                assertEquals(2, folder.listFiles().orEmpty().size)
                assertTrue(restarted.listInterruptedOperations(vault).getOrThrow().isEmpty())
            } finally { restarted.lockAll(); nextScope.cancel() }
        } finally { scope.cancel(); local.deleteRecursively() }
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
