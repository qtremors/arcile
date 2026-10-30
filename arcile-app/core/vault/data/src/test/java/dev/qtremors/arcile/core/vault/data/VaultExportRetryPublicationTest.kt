package dev.qtremors.arcile.core.vault.data

import dev.qtremors.arcile.core.vault.crypto.FileVaultDirectory
import dev.qtremors.arcile.core.vault.domain.*
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class VaultExportRetryPublicationTest {
    @Test
    fun `published child is removed from the recovered folder checkpoint without another export`() = fixture { f ->
        f.interrupt { progress -> f.publish(f.children[0], progress) }
        f.resume { run ->
            val pending = run.pendingRetries().single().second.exportRetry as PendingVaultExport
            assertEquals(listOf("B.txt"), pending.deferred.map { it.node.name })
            assertFalse(pending.move)
        }
        assertEquals("new vault bytes", File(f.output, "A.txt").readText())
        assertTrue(f.output.listFiles().orEmpty().none { it.name.endsWith(".tmp") })
    }

    @Test
    fun `unfinished child staging is removed and the original retry remains pending`() = fixture { f ->
        lateinit var stage: File
        f.interrupt { progress ->
            stage = f.stage()
            progress.prepare(VaultRetryPublication.export(f.children[0], f.receipt(stage, "A.txt")))
            Files.newOutputStream(stage.toPath(), StandardOpenOption.WRITE).use { it.write("partial plaintext".encodeToByteArray()) }
        }
        f.resume { run -> assertEquals(2, requireNotNull(run.pendingRetries().single().second.exportRetry).remainingCount) }
        assertFalse(stage.exists())
        assertFalse(File(f.output, "A.txt").exists())
    }

    @Test
    fun `interrupted child replacement restores the original backup without overwriting anything`() = fixture { f ->
        val target = File(f.output, "A.txt").apply { writeText("regular original") }
        lateinit var stage: File
        lateinit var backup: File
        f.interrupt { progress ->
            stage = f.stage()
            backup = File(f.output, ".arcile-backup-${UUID.randomUUID()}.tmp")
            val receipt = f.receipt(stage, target.name).copy(backup = backup.path,
                originalIdentity = ExportEntryIdentity.read(target, owned = false), ready = true)
            progress.prepare(VaultRetryPublication.export(f.children[0], receipt))
            Files.move(target.toPath(), backup.toPath())
        }
        f.resume { run -> assertEquals(2, requireNotNull(run.pendingRetries().single().second.exportRetry).remainingCount) }
        assertEquals("regular original", target.readText())
        assertFalse(backup.exists())
        assertFalse(stage.exists())
    }

    @Test
    fun `uncertain child never overwrites a newer target and leaves the known backup for review`() = fixture { f ->
        val target = File(f.output, "A.txt").apply { writeText("regular original") }
        lateinit var stage: File
        lateinit var backup: File
        f.interrupt { progress ->
            stage = f.stage()
            backup = File(f.output, ".arcile-backup-${UUID.randomUUID()}.tmp")
            progress.prepare(VaultRetryPublication.export(f.children[0], f.receipt(stage, target.name).copy(
                backup = backup.path, originalIdentity = ExportEntryIdentity.read(target, owned = false), ready = true)))
            Files.move(target.toPath(), backup.toPath())
            target.writeText("new regular file")
        }
        val queue = VaultOperationJournal(f.queues).list(f.session, setOf(f.vault)).single()
        assertTrue(queue.hasUncertainItem)
        assertEquals(listOf(0), queue.uncertainItemIndices)
        assertEquals(0, queue.remainingItems)
        assertEquals("new regular file", target.readText())
        assertEquals("regular original", backup.readText())
        assertFalse(stage.exists())
    }

    @Test
    fun `replacing the output root blocks a child even when its stage is moved into the new root`() = fixture { f ->
        lateinit var stage: File
        f.interrupt { progress ->
            stage = f.stage()
            progress.prepare(VaultRetryPublication.export(f.children[0], f.receipt(stage, "A.txt")))
        }
        val previous = File(f.output.parentFile, "Previous")
        Files.move(f.output.toPath(), previous.toPath())
        assertTrue(f.output.mkdir())
        Files.move(File(previous, stage.name).toPath(), stage.toPath())
        val queue = VaultOperationJournal(f.queues).list(f.session, setOf(f.vault)).single()
        assertTrue(queue.hasUncertainItem)
        assertTrue(stage.exists())
    }

    @Test
    fun `partial child folders retain nested deferred paths and folder identities after publication`() = fixture { f ->
        val first = f.children[0]
        val folderNode = first.node.copy(kind = VaultNodeKind.DIRECTORY, sizeBytes = 0,
            ref = first.node.ref.copy(directoryId = DirectoryId.of("nested-source")))
        val folder = DeferredExportChild(folderNode, emptyList())
        val partial = (f.partial.exportRetry as PendingVaultExport).copy(deferred = listOf(folder, f.children[1]))
        f.resetCheckpoint(partial)
        lateinit var publishedIdentity: ExportFolderIdentity
        f.interrupt { progress ->
            val stage = File(f.output, ".arcile-${UUID.randomUUID()}.tmp").apply { mkdir() }
            publishedIdentity = ExportFolderIdentity.read(stage)
            val deferred = f.node("Nested.txt", "nested-file", requireNotNull(folder.node.ref.directoryId))
            val snapshot = PendingVaultExport(folderNode, File(f.output, folderNode.name), false,
                listOf(DeferredExportChild(deferred, emptyList())), emptyMap(), emptyList(),
                mapOf(emptyList<String>() to publishedIdentity))
            val checkpoint = requireNotNull(VaultRetryCheckpoint.from(VaultItemResult(folderNode.ref.backendIdentity,
                folderNode.name, VaultItemOutcome.PARTIAL, exportRetry = snapshot)))
            val receipt = f.receipt(stage, folderNode.name).copy(ready = true, partial = true, checkpoint = checkpoint)
            progress.prepare(VaultRetryPublication.export(folder, receipt))
            Files.move(stage.toPath(), File(f.output, folderNode.name).toPath())
        }
        f.resume { run ->
            val retry = run.pendingRetries().single().second.exportRetry as PendingVaultExport
            val nested = retry.deferred.single { it.node.name == "Nested.txt" }
            assertEquals(listOf(folderNode.name), nested.parent)
            assertEquals(publishedIdentity, retry.folders[listOf(folderNode.name)])
            assertEquals(setOf("Nested.txt", "B.txt"), retry.deferred.map { it.node.name }.toSet())
        }
    }

    @Test
    fun `receipt for a different deferred source is rejected before writing plaintext`() = fixture { f ->
        val stage = f.stage()
        f.interrupt { progress ->
            val unknown = f.children[0].copy(node = f.node("Unknown.txt", "unknown"))
            assertTrue(runCatching {
                progress.prepare(VaultRetryPublication.export(unknown, f.receipt(stage, unknown.node.name)))
            }.isFailure)
        }
        assertFalse(f.queues.walkTopDown().any { ".step-" in it.name })
        assertEquals("", stage.readText())
    }

    @Test
    fun `receipt outside the selected child parent cannot clean an unrelated stage`() = fixture { f ->
        val unrelated = File(f.output.parentFile, ".arcile-${UUID.randomUUID()}.tmp").apply { writeText("keep") }
        f.interrupt { progress ->
            val receipt = f.receipt(f.stage(), "A.txt").copy(staging = unrelated.path)
            assertTrue(runCatching { progress.prepare(VaultRetryPublication.export(f.children[0], receipt)) }.isFailure)
        }
        assertEquals("keep", unrelated.readText())
    }

    @Test
    fun `settled child stays settled after its destination is edited by the user`() = fixture { f ->
        f.interrupt { progress ->
            f.publish(f.children[0], progress)
            progress.published()
        }
        File(f.output, "A.txt").writeText("user edit after completion")
        f.resume { run ->
            val retry = run.pendingRetries().single().second.exportRetry as PendingVaultExport
            assertEquals(listOf("B.txt"), retry.deferred.map { it.node.name })
        }
        assertEquals("user edit after completion", File(f.output, "A.txt").readText())
    }

    private class Fixture(val root: File) {
        val output = File(root, "exports/Private folder").canonicalFile.apply { mkdirs() }
        val queues = File(root, "queues")
        val vault = VaultId.of("vault")
        val session = VaultSessionRecord(vault, FileVaultDirectory(File(root, "vault")), ByteArray(32) { 3 })
        private val source = VaultNodeMetadata(VaultNodeRef(vault, NodeId.of("source"), DirectoryId.Root,
            VaultNodeCapabilities(), DirectoryId.of("source-dir")), "Private folder", VaultNodeKind.DIRECTORY, 0, 0, 1)
        val children = listOf("A", "B").map { DeferredExportChild(node("$it.txt", it), emptyList()) }
        val plan = VaultQueuePlan(VaultRecoveryKind.EXPORT, output.parentFile.path, true,
            nodes = listOf(QueuedVaultNode.from(source.ref)),
            localDestinationIdentity = ExportFolderIdentity.read(output.parentFile), publicationReceipts = true)
        val partial = VaultItemResult(source.ref.backendIdentity, source.name, VaultItemOutcome.PARTIAL,
            exportRetry = PendingVaultExport(source, output, true, children, emptyMap(), emptyList(),
                mapOf(emptyList<String>() to ExportFolderIdentity.read(output))))
        var id = ""

        init { resetCheckpoint(partial.exportRetry as PendingVaultExport) }

        fun node(name: String, id: String, parent: DirectoryId = DirectoryId.of("source-dir")) =
            VaultNodeMetadata(VaultNodeRef(vault, NodeId.of(id), parent, VaultNodeCapabilities()), name, VaultNodeKind.FILE, 15, 0, 1)

        fun resetCheckpoint(retry: PendingVaultExport) {
            val journal = VaultOperationJournal(queues)
            journal.list(session, setOf(vault)).forEach { journal.discard(session, it.id) }
            journal.start(session, plan).use { run -> run.beginItem(0); run.finishItem(0, partial.copy(exportRetry = retry)) }
            id = journal.list(session, setOf(vault)).single().id
        }

        fun stage() = File(output, ".arcile-${UUID.randomUUID()}.tmp").apply { writeText("") }
        fun receipt(stage: File, name: String) = VaultExportPublicationReceipt(stage.path, File(output, name).path,
            ExportFolderIdentity.read(output), ExportEntryIdentity.read(stage))

        fun publish(child: DeferredExportChild, progress: VaultRetryProgress) {
            val stage = stage()
            val receipt = receipt(stage, child.node.name)
            progress.prepare(VaultRetryPublication.export(child, receipt))
            Files.newOutputStream(stage.toPath(), StandardOpenOption.WRITE).use { it.write("new vault bytes".encodeToByteArray()) }
            progress.prepare(VaultRetryPublication.export(child, receipt.copy(ready = true)))
            Files.move(stage.toPath(), File(output, child.node.name).toPath())
        }

        fun interrupt(test: (VaultRetryProgress) -> Unit) {
            VaultOperationJournal(queues).resume(session, id, VaultRecoveryKind.EXPORT).use { run -> test(run.retryProgress(0)) }
        }

        fun resume(test: (VaultQueueRun) -> Unit) {
            val journal = VaultOperationJournal(queues)
            val queue = journal.list(session, setOf(vault)).single()
            assertFalse(queue.hasUncertainItem)
            journal.resume(session, queue.id, VaultRecoveryKind.EXPORT).use(test)
        }
    }

    private fun fixture(test: (Fixture) -> Unit) {
        val root = Files.createTempDirectory("arcile-export-child-publication-").toFile()
        val f = Fixture(root)
        try { test(f) } finally { f.session.destroy(); root.deleteRecursively() }
    }
}
