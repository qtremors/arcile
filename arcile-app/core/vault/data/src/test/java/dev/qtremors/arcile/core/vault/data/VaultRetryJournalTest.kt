package dev.qtremors.arcile.core.vault.data

import dev.qtremors.arcile.core.vault.crypto.FileVaultDirectory
import dev.qtremors.arcile.core.vault.domain.*
import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class VaultRetryJournalTest {
    @Test
    fun `partial folder paste remains recoverable after every top-level item has settled`() = fixture { f ->
        val journal = VaultOperationJournal(f.root)
        val partial = f.merge()
        journal.start(f.session, f.pastePlan()).use {
            it.beginItem(0)
            it.finishItem(0, partial)
            it.finishQueue()
        }
        val restarted = VaultOperationJournal(f.root)
        val queue = restarted.list(f.session, setOf(f.vault)).single()
        assertEquals(1, queue.settledItems)
        assertEquals(1, queue.remainingItems)
        assertFalse(queue.hasUncertainItem)
        restarted.resume(f.session, queue.id, VaultRecoveryKind.PASTE).use { run ->
            assertEquals(1, run.startIndex)
            val retry = run.pendingRetries().single().second.mergeRetry as PendingVaultMerge
            assertEquals((partial.mergeRetry as PendingVaultMerge).children, retry.children)
            assertFalse(retry.move)
            assertTrue(retry.cleanupPaths.isEmpty())
        }
    }

    @Test
    fun `saved export preserves proofs and exact published folder while revoking move authority`() = fixture { f ->
        val partial = f.export()
        VaultOperationJournal(f.root).start(f.session, f.exportPlan()).use {
            it.beginItem(0)
            it.finishItem(0, partial)
            it.finishQueue()
        }
        val restarted = VaultOperationJournal(f.root)
        val queue = restarted.list(f.session, setOf(f.vault)).single()
        restarted.resume(f.session, queue.id, VaultRecoveryKind.EXPORT).use { run ->
            val original = partial.exportRetry as PendingVaultExport
            val restored = run.pendingRetries().single().second.exportRetry as PendingVaultExport
            assertEquals(original.outputRoot, restored.outputRoot)
            assertEquals(original.deferred, restored.deferred)
            assertEquals(original.generations, restored.generations)
            assertEquals(original.files, restored.files)
            assertEquals(original.folders, restored.folders)
            assertFalse(restored.move)
        }
    }

    @Test
    fun `checkpoint written before a lost cursor update reconciles the published partial item`() = fixture { f ->
        val journal = VaultOperationJournal(f.root)
        lateinit var oldCursor: ByteArray
        journal.start(f.session, f.pastePlan()).use { run ->
            run.beginItem(0)
            oldCursor = f.root.walkTopDown().single { it.extension == "cursor" }.readBytes()
            run.finishItem(0, f.merge())
        }
        // Simulate death after the retry proof was atomically written but before cursor publication.
        f.root.walkTopDown().single { it.extension == "cursor" }.writeBytes(oldCursor)
        oldCursor.fill(0)
        val restarted = VaultOperationJournal(f.root)
        val queue = restarted.list(f.session, setOf(f.vault)).single()
        assertFalse(queue.hasUncertainItem)
        assertEquals(1, queue.remainingItems)
        restarted.resume(f.session, queue.id, VaultRecoveryKind.PASTE).use {
            assertEquals(1, it.startIndex)
            assertEquals(1, it.pendingRetries().count())
        }
    }

    @Test
    fun `finishing a retried folder removes its checkpoint and completed queue`() = fixture { f ->
        VaultOperationJournal(f.root).start(f.session, f.pastePlan()).use {
            it.beginItem(0); it.finishItem(0, f.merge()); it.finishQueue()
        }
        val journal = VaultOperationJournal(f.root)
        val queue = journal.list(f.session, setOf(f.vault)).single()
        journal.resume(f.session, queue.id, VaultRecoveryKind.PASTE).use { run ->
            val saved = run.pendingRetries().single()
            run.finishRetry(saved.first, saved.second.copy(outcome = VaultItemOutcome.COMPLETED, mergeRetry = null))
            run.finishQueue()
        }
        assertTrue(VaultOperationJournal(f.root).list(f.session, setOf(f.vault)).isEmpty())
        assertTrue(f.root.walkTopDown().filter(File::isFile).none { ".retry-" in it.name })
    }

    @Test
    fun `an updated partial retry replaces its proof and stays available after another restart`() = fixture { f ->
        VaultOperationJournal(f.root).start(f.session, f.pastePlan()).use {
            it.beginItem(0); it.finishItem(0, f.merge()); it.finishQueue()
        }
        val journal = VaultOperationJournal(f.root)
        val queue = journal.list(f.session, setOf(f.vault)).single()
        journal.resume(f.session, queue.id, VaultRecoveryKind.PASTE).use { run ->
            val (index, saved) = run.pendingRetries().single()
            val retry = saved.mergeRetry as PendingVaultMerge
            val changed = retry.children.single().copy(sourceName = "Still pending.txt")
            run.finishRetry(index, saved.copy(mergeRetry = retry.copy(children = listOf(changed))))
            run.finishQueue()
        }
        val next = VaultOperationJournal(f.root)
        val remaining = next.list(f.session, setOf(f.vault)).single()
        next.resume(f.session, remaining.id, VaultRecoveryKind.PASTE).use {
            assertEquals("Still pending.txt", (it.pendingRetries().single().second.mergeRetry as PendingVaultMerge).children.single().sourceName)
        }
    }

    @Test
    fun `partial checkpoint and later unstarted selection survive in the same queue`() = fixture { f ->
        val other = f.source.ref.copy(nodeId = NodeId.of("later"))
        val plan = f.pastePlan().copy(nodes = listOf(QueuedVaultNode.from(f.source.ref), QueuedVaultNode.from(other)))
        VaultOperationJournal(f.root).start(f.session, plan).use {
            it.beginItem(0); it.finishItem(0, f.merge())
        }
        val journal = VaultOperationJournal(f.root)
        val queue = journal.list(f.session, setOf(f.vault)).single()
        assertEquals(2, queue.remainingItems)
        journal.resume(f.session, queue.id, VaultRecoveryKind.PASTE).use {
            assertEquals(listOf(other), it.plan.nodes.drop(it.startIndex).map(QueuedVaultNode::ref))
            assertEquals(1, it.pendingRetries().count())
        }
    }

    @Test
    fun `retry documents expose neither private paths nor display names on disk`() = fixture { f ->
        VaultOperationJournal(f.root).start(f.session, f.exportPlan()).use {
            it.beginItem(0); it.finishItem(0, f.export()); it.finishQueue()
        }
        f.root.walkTopDown().filter(File::isFile).forEach { file ->
            val raw = file.readBytes().decodeToString()
            assertFalse(raw.contains("Private root"))
            assertFalse(raw.contains("Remaining.txt"))
            assertFalse(raw.contains(f.output.path))
        }
    }

    @Test
    fun `tampered retry proof cannot authorize a continuation or rewind the item`() = fixture { f ->
        VaultOperationJournal(f.root).start(f.session, f.pastePlan()).use {
            it.beginItem(0); it.finishItem(0, f.merge()); it.finishQueue()
        }
        val checkpoint = f.root.walkTopDown().single { ".retry-" in it.name }
        checkpoint.writeBytes(checkpoint.readBytes().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() })
        val journal = VaultOperationJournal(f.root)
        val queue = journal.list(f.session, setOf(f.vault)).single()
        assertNull(queue.kind)
        assertTrue(runCatching { journal.resume(f.session, queue.id, VaultRecoveryKind.PASTE) }.isFailure)
        journal.discard(f.session, queue.id)
        assertFalse(checkpoint.exists())
    }

    @Test
    fun `a checkpoint from another queue fails authentication even for the same source`() = fixture { f ->
        val journal = VaultOperationJournal(f.root)
        repeat(2) {
            journal.start(f.session, f.pastePlan()).use {
                it.beginItem(0); it.finishItem(0, f.merge()); it.finishQueue()
            }
        }
        val proofs = f.root.walkTopDown().filter { ".retry-" in it.name }.toList()
        proofs.first().copyTo(proofs.last(), overwrite = true)
        val queues = VaultOperationJournal(f.root).list(f.session, setOf(f.vault))
        assertEquals(1, queues.count { it.kind == null })
        assertEquals(1, queues.count { it.kind == VaultRecoveryKind.PASTE })
    }

    @Test
    fun `a checkpoint for the wrong source or destination is rejected before saving`() = fixture { f ->
        val original = f.merge().mergeRetry as PendingVaultMerge
        val invalid = listOf(
            original.copy(source = original.source.copy(nodeId = NodeId.of("wrong"))),
            original.copy(destinationDirectory = DirectoryId.of("wrong")),
            original.copy(destinationVault = VaultId.of("wrong"))
        )
        invalid.forEach { retry ->
            val journal = VaultOperationJournal(f.root)
            journal.start(f.session, f.pastePlan()).use {
                it.beginItem(0)
                assertTrue(runCatching { it.finishItem(0, f.merge().copy(mergeRetry = retry)) }.isFailure)
            }
        }
        assertTrue(f.root.walkTopDown().filter(File::isFile).none { ".retry-" in it.name })
    }

    @Test
    fun `export path traversal cannot escape the authenticated destination`() = fixture { f ->
        val original = f.export().exportRetry as PendingVaultExport
        val outside = original.copy(outputRoot = File(f.output.parentFile, "../outside"))
        val nested = original.copy(deferred = listOf(original.deferred.single().copy(parent = listOf(".."))))
        for (retry in listOf(outside, nested)) {
            val journal = VaultOperationJournal(f.root)
            journal.start(f.session, f.exportPlan()).use {
                it.beginItem(0)
                assertTrue(runCatching { it.finishItem(0, f.export().copy(exportRetry = retry)) }.isFailure)
            }
        }
    }

    @Test
    fun `checkpoint serialization limit retains an uncertain item instead of writing an unbounded proof`() = fixture { f ->
        val retry = f.export().exportRetry as PendingVaultExport
        val longName = "p".repeat(250)
        val child = retry.deferred.single().copy(node = retry.deferred.single().node.copy(name = longName, mimeType = "application/x-" + "p".repeat(200)))
        val oversized = retry.copy(deferred = List(9_998) { child })
        val checkpoint = requireNotNull(VaultRetryCheckpoint.from(f.export().copy(exportRetry = oversized)))
        val encoded = kotlinx.serialization.json.Json.encodeToString(checkpoint).encodeToByteArray()
        assertTrue(encoded.size > 4 * 1024 * 1024)
        encoded.fill(0)
        val journal = VaultOperationJournal(f.root)
        journal.start(f.session, f.exportPlan()).use {
            it.beginItem(0)
            assertTrue(runCatching { it.finishItem(0, f.export().copy(exportRetry = oversized)) }.isFailure)
        }
        val queue = VaultOperationJournal(f.root).list(f.session, setOf(f.vault)).single()
        assertTrue(queue.hasUncertainItem)
        assertTrue(f.root.walkTopDown().filter(File::isFile).none { ".retry-" in it.name })
    }

    private class Fixture(val root: File, val session: VaultSessionRecord, val output: File) {
        val vault = session.id
        val source = VaultNodeMetadata(
            VaultNodeRef(vault, NodeId.of("source"), DirectoryId.Root, VaultNodeCapabilities(), DirectoryId.of("source-dir")),
            "Private root", VaultNodeKind.DIRECTORY, 0, 0, 1
        )
        val destination = DirectoryId.of("destination")
        private val targetFolder = VaultNodeRef(vault, NodeId.of("published"), destination, VaultNodeCapabilities(), DirectoryId.of("published-dir"))
        private val child = VaultNodeMetadata(
            VaultNodeRef(vault, NodeId.of("child"), requireNotNull(source.ref.directoryId), VaultNodeCapabilities()),
            "Remaining.txt", VaultNodeKind.FILE, 10, 0, 1
        )
        fun pastePlan() = VaultQueuePlan(VaultRecoveryKind.PASTE, destination.value, true, nodes = listOf(QueuedVaultNode.from(source.ref)))
        fun exportPlan() = VaultQueuePlan(VaultRecoveryKind.EXPORT, output.parentFile.path, true,
            nodes = listOf(QueuedVaultNode.from(source.ref)), localDestinationIdentity = ExportFolderIdentity.read(output.parentFile))
        fun merge() = VaultItemResult(source.ref.backendIdentity, source.name, VaultItemOutcome.PARTIAL, mergeRetry = PendingVaultMerge(
            source.ref, vault, destination, true, source.name,
            listOf(DeferredVaultChild(listOf(source.ref), listOf(targetFolder), child.ref, child.revision, child.name)),
            setOf(listOf(source.ref))
        ))
        fun export() = VaultItemResult(source.ref.backendIdentity, source.name, VaultItemOutcome.PARTIAL, exportRetry = PendingVaultExport(
            source, output, true, listOf(DeferredExportChild(child, emptyList())),
            mapOf(requireNotNull(source.ref.directoryId) to 1L), listOf(PublishedExportFile(listOf("Done.txt"), 5, "a".repeat(64))),
            mapOf(emptyList<String>() to ExportFolderIdentity.read(output))
        ))
    }

    private fun fixture(test: (Fixture) -> Unit) {
        val root = Files.createTempDirectory("arcile-retry-journal-").toFile()
        val session = VaultSessionRecord(VaultId.of("vault"), FileVaultDirectory(File(root, "vault")), ByteArray(32) { 3 })
        val output = File(root, "exports/Private root").apply { mkdirs() }
        try { test(Fixture(File(root, "queues"), session, output)) }
        finally { session.destroy(); root.deleteRecursively() }
    }
}
