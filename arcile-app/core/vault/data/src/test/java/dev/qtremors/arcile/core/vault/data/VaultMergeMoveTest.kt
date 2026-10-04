package dev.qtremors.arcile.core.vault.data

import dev.qtremors.arcile.core.vault.crypto.FileVaultDirectory
import dev.qtremors.arcile.core.vault.crypto.VaultCryptography
import dev.qtremors.arcile.core.vault.crypto.VaultDirectoryManifestCodec
import dev.qtremors.arcile.core.vault.crypto.VaultFileCodec
import dev.qtremors.arcile.core.vault.crypto.VaultManifestEntry
import dev.qtremors.arcile.core.vault.domain.*
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultMergeMoveTest {
    private val root = Files.createTempDirectory("vault-merge-move").toFile()
    private val manifests = VaultDirectoryManifestCodec()
    private val files = VaultFileCodec()
    private val transactions = VaultTransactionManager(manifests)
    private val engine = VaultTransferEngine(manifests, files, transactions)
    private val sessions = mutableListOf<VaultSessionRecord>()
    private val neverCancelled = VaultCancellationSignal { false }
    private val mergeAndSkip = VaultConflictResolver {
        if (it.sourceIsDirectory && it.destinationIsDirectory) VaultConflictDecision.MERGE_DIRECTORIES
        else VaultConflictDecision.SKIP
    }

    @After
    fun tearDown() {
        sessions.forEach(VaultSessionRecord::destroy)
        root.deleteRecursively()
    }

    @Test
    fun `within vault merge move preserves nested skipped bytes and moves other children once`() = runTest {
        exerciseMergeMove(acrossVaults = false)
    }

    @Test
    fun `cross vault merge move preserves nested skipped bytes and moves other children once`() = runTest {
        exerciseMergeMove(acrossVaults = true)
    }

    private suspend fun exerciseMergeMove(acrossVaults: Boolean) {
        val source = session("source")
        val destination = if (acrossVaults) session("destination") else source
        val sourceFolder = directory(source, "Folder", listOf(
            file(source, "conflict.txt", "source root bytes"),
            file(source, "moved.txt", "moved root bytes"),
            directory(source, "Nested", listOf(
                file(source, "conflict.txt", "source nested bytes"),
                file(source, "moved.txt", "moved nested bytes")
            )),
            directory(source, "SkippedFolder", listOf(file(source, "private.txt", "skipped subtree"))),
            directory(source, "MovedFolder", listOf(file(source, "child.txt", "whole moved subtree")))
        ))
        val destinationFolder = directory(destination, "Folder", listOf(
            file(destination, "conflict.txt", "destination root bytes"),
            directory(destination, "Nested", listOf(file(destination, "conflict.txt", "destination nested bytes"))),
            directory(destination, "SkippedFolder", listOf(file(destination, "private.txt", "destination subtree")))
        ))
        val sourceParent = directory(source, "Source", listOf(sourceFolder))
        val destinationParent = directory(destination, "Destination", listOf(destinationFolder))
        if (acrossVaults) {
            publishRoot(source, listOf(sourceParent))
            publishRoot(destination, listOf(destinationParent))
        } else publishRoot(source, listOf(sourceParent, destinationParent))
        val ref = VaultNodeRef(source.id, sourceFolder.nodeId, requireNotNull(sourceParent.childDirectoryId), VaultNodeCapabilities())
        val conflicts = VaultConflictResolver {
            if (it.sourceName == "SkippedFolder") VaultConflictDecision.SKIP else mergeAndSkip.decide(it)
        }
        val result = if (acrossVaults) engine.copyOne(
            source, ref, destination, requireNotNull(destinationParent.childDirectoryId), conflicts, neverCancelled,
            moveSource = true
        ) else engine.moveOneWithinVault(
            source, ref, requireNotNull(destinationParent.childDirectoryId), conflicts, neverCancelled
        )
        assertEquals(VaultItemOutcome.PARTIAL, result.outcome)
        assertEquals(setOf("conflict.txt", "Nested", "SkippedFolder"), names(source, "Source/Folder"))
        assertEquals(setOf("conflict.txt"), names(source, "Source/Folder/Nested"))
        assertEquals("source root bytes", read(source, "Source/Folder/conflict.txt"))
        assertEquals("source nested bytes", read(source, "Source/Folder/Nested/conflict.txt"))
        assertEquals("skipped subtree", read(source, "Source/Folder/SkippedFolder/private.txt"))
        assertEquals("destination root bytes", read(destination, "Destination/Folder/conflict.txt"))
        assertEquals("destination nested bytes", read(destination, "Destination/Folder/Nested/conflict.txt"))
        assertEquals("destination subtree", read(destination, "Destination/Folder/SkippedFolder/private.txt"))
        assertEquals("moved root bytes", read(destination, "Destination/Folder/moved.txt"))
        assertEquals("moved nested bytes", read(destination, "Destination/Folder/Nested/moved.txt"))
        assertEquals("whole moved subtree", read(destination, "Destination/Folder/MovedFolder/child.txt"))

        // Resolve using fresh sessions, without relying on cached keys or in-memory state.
        val reopenedSource = reopen(source)
        val reopenedDestination = if (acrossVaults) reopen(destination) else reopenedSource
        assertEquals("source nested bytes", read(reopenedSource, "Source/Folder/Nested/conflict.txt"))
        assertEquals("moved nested bytes", read(reopenedDestination, "Destination/Folder/Nested/moved.txt"))
        assertFalse(transactions.hasPendingCommit(source.directory))
        assertFalse(transactions.hasPendingCommit(destination.directory))
    }

    @Test
    fun `merge move without skips removes original tree and preserves both destination branches`() = runTest {
        val source = session("complete")
        val folder = directory(source, "Folder", listOf(file(source, "new.txt", "new")))
        val parent = directory(source, "Source", listOf(folder))
        val target = directory(source, "Destination", listOf(
            directory(source, "Folder", listOf(file(source, "old.txt", "old")))
        ))
        publishRoot(source, listOf(parent, target))
        val result = engine.moveOneWithinVault(
            source, VaultNodeRef(source.id, folder.nodeId, requireNotNull(parent.childDirectoryId), VaultNodeCapabilities()),
            requireNotNull(target.childDirectoryId), mergeAndSkip, neverCancelled
        )
        assertEquals(VaultItemOutcome.COMPLETED, result.outcome)
        assertTrue(names(source, "Source").isEmpty())
        assertEquals("new", read(source, "Destination/Folder/new.txt"))
        assertEquals("old", read(source, "Destination/Folder/old.txt"))
    }

    @Test
    fun `interrupted within vault merge recovers skipped source and committed destination together`() = runTest {
        val source = session("interrupted")
        val folder = directory(source, "Folder", listOf(
            file(source, "conflict.txt", "keep source"), file(source, "moved.txt", "move me")
        ))
        val parent = directory(source, "Source", listOf(folder))
        val target = directory(source, "Destination", listOf(
            directory(source, "Folder", listOf(file(source, "conflict.txt", "keep destination")))
        ))
        publishRoot(source, listOf(parent, target))
        val failingTransactions = VaultTransactionManager(manifests, stageObserver = {
            if (it == VaultTransactionStage.COMMIT_MARKER_SYNCED) throw IllegalStateException("Injected process death")
        })
        try {
            VaultTransferEngine(manifests, files, failingTransactions).moveOneWithinVault(
                source, VaultNodeRef(source.id, folder.nodeId, requireNotNull(parent.childDirectoryId), VaultNodeCapabilities()),
                requireNotNull(target.childDirectoryId), mergeAndSkip, neverCancelled
            )
            throw AssertionError("Expected interruption")
        } catch (error: IllegalStateException) {
            assertEquals("Injected process death", error.message)
        }
        transactions.recover(source.directory, source.id, source.masterSecret)
        val reopened = reopen(source)
        assertEquals(setOf("conflict.txt"), names(reopened, "Source/Folder"))
        assertEquals("keep source", read(reopened, "Source/Folder/conflict.txt"))
        assertEquals("keep destination", read(reopened, "Destination/Folder/conflict.txt"))
        assertEquals("move me", read(reopened, "Destination/Folder/moved.txt"))
    }

    @Test
    fun `oversized partial merge fails before durable commit and preserves both original trees`() = runTest {
        val session = session("oversized")
        fun tree(depth: Int, source: Boolean): VaultManifestEntry {
            val children = mutableListOf(file(session, "conflict.txt", if (source) "source" else "destination"))
            if (source) children += file(session, "moved.txt", "still original")
            if (depth > 1) children += tree(depth - 1, source)
            return directory(session, if (depth == 16) "Folder" else "Nested", children)
        }
        val folder = tree(16, source = true)
        val parent = directory(session, "Source", listOf(folder))
        val target = directory(session, "Destination", listOf(tree(16, source = false)))
        publishRoot(session, listOf(parent, target))
        try {
            engine.moveOneWithinVault(
                session, VaultNodeRef(session.id, folder.nodeId, requireNotNull(parent.childDirectoryId), VaultNodeCapabilities()),
                requireNotNull(target.childDirectoryId), mergeAndSkip, neverCancelled
            )
            throw AssertionError("Expected transaction limit rejection")
        } catch (_: IllegalArgumentException) {
            assertFalse(transactions.hasPendingCommit(session.directory))
        }
        val reopened = reopen(session)
        assertEquals("source", read(reopened, "Source/Folder/conflict.txt"))
        assertEquals("still original", read(reopened, "Source/Folder/moved.txt"))
        assertEquals("destination", read(reopened, "Destination/Folder/conflict.txt"))
        assertEquals(setOf("conflict.txt", "Nested"), names(reopened, "Destination/Folder"))
        val nested = List(15) { "Nested" }.joinToString("/")
        assertEquals("source", read(reopened, "Source/Folder/$nested/conflict.txt"))
        assertEquals("still original", read(reopened, "Source/Folder/$nested/moved.txt"))
        assertEquals("destination", read(reopened, "Destination/Folder/$nested/conflict.txt"))
    }

    private fun session(name: String): VaultSessionRecord {
        val session = VaultSessionRecord(VaultId.of(name), FileVaultDirectory(File(root, name).apply { mkdirs() }), VaultCryptography.randomBytes(32))
        sessions += session
        val directory = session.root()
        try {
            manifests.createRoot(session.directory, session.id, directory.id, directory.key)
        } finally { directory.key.fill(0) }
        return session
    }

    private fun reopen(session: VaultSessionRecord) = VaultSessionRecord(
        session.id, session.directory, session.masterSecret.copyOf()
    ).also { sessions += it }

    private fun file(session: VaultSessionRecord, name: String, text: String): VaultManifestEntry {
        val key = VaultCryptography.randomBytes(32)
        val objectId = VaultObjectId.fromRandomBytes(VaultCryptography.randomBytes(32))
        val bytes = text.encodeToByteArray()
        files.writeObject(session.directory, objectId.shardedPath(), session.id, objectId, 1L, key, ByteArrayInputStream(bytes))
        return VaultManifestEntry(NodeId.random(), name, VaultNodeKind.FILE, 1L, 1L, bytes.size.toLong(), "text/plain", objectId, null, key)
    }

    private fun directory(session: VaultSessionRecord, name: String, children: List<VaultManifestEntry>): VaultManifestEntry {
        val id = DirectoryId.random()
        val key = VaultCryptography.randomBytes(32)
        manifests.publish(session.directory, manifests.prepare(session.id, id, key, 0L, children))
        children.forEach { it.protectedKey.fill(0) }
        return VaultManifestEntry(NodeId.random(), name, VaultNodeKind.DIRECTORY, 1L, 1L, 0L, null, null, id, key)
    }

    private fun publishRoot(session: VaultSessionRecord, children: List<VaultManifestEntry>) {
        val directory = session.root()
        try {
            manifests.publish(session.directory, manifests.prepare(session.id, directory.id, directory.key, 1L, children))
        } finally { directory.key.fill(0) }
        children.forEach { it.protectedKey.fill(0) }
    }

    private fun names(session: VaultSessionRecord, path: String): Set<String> {
        val directory = session.resolveDirectory(VaultPath.of(path))
        try {
            val snapshot = session.readDirectory(directory)
            try { return snapshot.entries.map { it.name }.toSet() }
            finally { snapshot.clearProtectedKeys() }
        } finally { directory.key.fill(0) }
    }

    private fun read(session: VaultSessionRecord, path: String): String {
        val resolved = session.resolveEntry(VaultPath.of(path))
        try {
            val entry = resolved.entry
            val objectId = requireNotNull(entry.objectId)
            files.openObject(session.directory, objectId.shardedPath(), session.id, objectId, entry.revision, entry.protectedKey).use {
                val bytes = ByteArray(it.sizeBytes.toInt())
                assertEquals(bytes.size, it.readAt(0L, bytes, 0, bytes.size))
                return bytes.decodeToString()
            }
        } finally {
            resolved.entry.protectedKey.fill(0)
            resolved.parent.key.fill(0)
        }
    }
}
