package dev.qtremors.arcile.core.vault.data

import dev.qtremors.arcile.core.vault.crypto.*
import dev.qtremors.arcile.core.vault.domain.*
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class VaultPublicationRecoveryTest {
    @Test
    fun `copy published before a lost cursor update is settled without being copied again`() = runTest {
        val f = Fixture()
        try {
            VaultOperationJournal(f.journalRoot).start(f.session, f.plan()).use { run ->
                run.beginItem(0)
                val result = f.engine.copyOne(f.session, f.source, f.session, f.destination,
                    VaultConflictResolver { VaultConflictDecision.REPLACE }, neverCancelled,
                    onPrepared = { run.preparePublication(0, it) })
                assertEquals(VaultItemOutcome.COMPLETED, result.outcome)
                // The process ended before finishItem, after the destination commit completed.
            }
            f.session.destroy()
            f.withFreshSession { session ->
                assertTrue(VaultOperationJournal(f.journalRoot).list(session, setOf(session.id)).isEmpty())
                assertEquals(listOf("Source.txt"), f.names(session, f.destination))
                assertTrue(f.names(session, DirectoryId.Root).contains("Source.txt"))
            }
        } finally { f.close() }
    }

    @Test
    fun `atomic same-vault move publication settles an interrupted item without another source deletion`() = runTest {
        val f = Fixture()
        try {
            VaultOperationJournal(f.journalRoot).start(f.session, f.plan(move = true)).use { run ->
                run.beginItem(0)
                val result = f.engine.moveOneWithinVault(f.session, f.source, f.destination,
                    VaultConflictResolver { VaultConflictDecision.REPLACE }, neverCancelled) {
                    run.preparePublication(0, it)
                }
                assertEquals(VaultItemOutcome.COMPLETED, result.outcome)
            }
            f.session.destroy()
            f.withFreshSession { session ->
                assertTrue(VaultOperationJournal(f.journalRoot).list(session, setOf(session.id)).isEmpty())
                assertEquals(listOf("Source.txt"), f.names(session, f.destination))
                assertFalse(f.names(session, DirectoryId.Root).contains("Source.txt"))
            }
        } finally { f.close() }
    }

    @Test
    fun `prepared but unpublished copy is eligible for a safe retry after restart`() = runTest {
        val f = Fixture()
        try {
            VaultOperationJournal(f.journalRoot).start(f.session, f.plan()).use { run ->
                run.beginItem(0)
                val failed = runCatching {
                    f.engine.copyOne(f.session, f.source, f.session, f.destination,
                        VaultConflictResolver { VaultConflictDecision.REPLACE }, neverCancelled,
                        onPrepared = {
                            run.preparePublication(0, it)
                            throw CancellationException("Stopped before commit")
                        })
                }
                assertTrue(failed.exceptionOrNull() is CancellationException)
            }
            f.session.destroy()
            val session = f.freshSession()
            try {
                val journal = VaultOperationJournal(f.journalRoot)
                val queue = journal.list(session, setOf(session.id)).single()
                assertFalse(queue.hasUncertainItem)
                assertEquals(1, queue.remainingItems)
                journal.resume(session, queue.id, VaultRecoveryKind.PASTE).use { run ->
                    assertEquals(0, run.startIndex)
                    run.beginItem(0)
                    val result = f.engine.copyOne(session, f.source, session, f.destination,
                        VaultConflictResolver { VaultConflictDecision.REPLACE }, neverCancelled,
                        onPrepared = { run.preparePublication(0, it) })
                    run.finishItem(0, result)
                    run.finishQueue()
                }
                assertEquals(listOf("Source.txt"), f.names(session, f.destination))
                assertTrue(journal.list(session, setOf(session.id)).isEmpty())
            } finally { session.destroy() }
        } finally { f.close() }
    }

    @Test
    fun `a changed destination after preparation remains uncertain instead of being overwritten`() = runTest {
        val f = Fixture()
        try {
            VaultOperationJournal(f.journalRoot).start(f.session, f.plan()).use { run ->
                run.beginItem(0)
                val expected = SavedVaultNode(QueuedVaultNode(f.vault.value, "planned", f.destination.value, null),
                    "Source.txt", VaultNodeKind.FILE, 4, 0, 1)
                run.preparePublication(0, VaultPublicationReceipt(listOf(expected), 0))
                f.publishDestination(1, emptyList()) // Another authenticated mutation superseded the original parent.
            }
            f.withFreshSession { session ->
                val queue = VaultOperationJournal(f.journalRoot).list(session, setOf(session.id)).single()
                assertTrue(queue.hasUncertainItem)
                assertEquals(0, queue.remainingItems)
            }
        } finally { f.close() }
    }

    @Test
    fun `tampered publication receipt cannot settle or rewind a source selection`() = runTest {
        val f = Fixture()
        try {
            VaultOperationJournal(f.journalRoot).start(f.session, f.plan()).use { run ->
                run.beginItem(0)
                val expected = SavedVaultNode(QueuedVaultNode(f.vault.value, "planned", f.destination.value, null),
                    "Source.txt", VaultNodeKind.FILE, 4, 0, 1)
                run.preparePublication(0, VaultPublicationReceipt(listOf(expected), 0))
            }
            val receipt = f.journalRoot.walkTopDown().single { ".receipt-" in it.name }
            receipt.writeBytes(receipt.readBytes().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() })
            f.withFreshSession { session ->
                assertNull(VaultOperationJournal(f.journalRoot).list(session, setOf(session.id)).single().kind)
            }
            assertTrue(f.names(f.session, DirectoryId.Root).contains("Source.txt"))
        } finally { f.close() }
    }

    private class Fixture {
        val root = Files.createTempDirectory("arcile-publication-recovery-").toFile()
        val journalRoot = File(root, "queues")
        val vault = VaultId.of("vault")
        val destination = DirectoryId.of("destination")
        private val access = FileVaultDirectory(File(root, "vault").apply { mkdirs() })
        val session = VaultSessionRecord(vault, access, ByteArray(32) { 3 })
        private val codec = VaultDirectoryManifestCodec()
        private val fileCodec = VaultFileCodec()
        private val destinationKey = ByteArray(32) { 5 }
        val engine = VaultTransferEngine(codec, fileCodec, VaultTransactionManager(codec))
        val source = VaultNodeRef(vault, NodeId.of("source"), DirectoryId.Root, VaultNodeCapabilities())

        init {
            val contentKey = ByteArray(32) { 4 }
            val objectId = VaultObjectId.of("a".repeat(64))
            fileCodec.writeObject(access, objectId.shardedPath(), vault, objectId, 1, contentKey, "data".byteInputStream())
            val sourceEntry = VaultManifestEntry(source.nodeId, "Source.txt", VaultNodeKind.FILE, 1, 0, 4,
                null, objectId, null, contentKey)
            val folder = VaultManifestEntry(NodeId.of("folder"), "Destination", VaultNodeKind.DIRECTORY,
                1, 0, 0, null, null, destination, destinationKey.copyOf())
            val resolved = session.root()
            try { codec.publish(access, codec.prepare(vault, DirectoryId.Root, resolved.key, 0, listOf(sourceEntry, folder))) }
            finally { resolved.key.fill(0); contentKey.fill(0); folder.protectedKey.fill(0) }
            publishDestination(0, emptyList())
        }

        fun plan(move: Boolean = false) = VaultQueuePlan(VaultRecoveryKind.PASTE, destination.value, move,
            nodes = listOf(QueuedVaultNode.from(source)))
        fun publishDestination(generation: Long, entries: List<VaultManifestEntry>) {
            codec.publish(access, codec.prepare(vault, destination, destinationKey, generation, entries))
        }
        fun freshSession() = VaultSessionRecord(vault, access, ByteArray(32) { 3 })
        fun withFreshSession(test: (VaultSessionRecord) -> Unit) {
            val fresh = freshSession()
            try { test(fresh) } finally { fresh.destroy() }
        }
        fun names(session: VaultSessionRecord, directory: DirectoryId): List<String> {
            val resolved = session.resolveDirectory(directory)
            try {
                val snapshot = session.readDirectory(resolved)
                try { return snapshot.entries.map { it.name } }
                finally { snapshot.clearProtectedKeys() }
            } finally { resolved.key.fill(0) }
        }
        fun close() { session.destroy(); destinationKey.fill(0); root.deleteRecursively() }
    }

    private companion object { val neverCancelled = VaultCancellationSignal { false } }
}
