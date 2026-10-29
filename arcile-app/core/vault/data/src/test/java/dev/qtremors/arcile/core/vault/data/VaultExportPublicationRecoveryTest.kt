package dev.qtremors.arcile.core.vault.data

import dev.qtremors.arcile.core.vault.crypto.FileVaultDirectory
import dev.qtremors.arcile.core.vault.domain.*
import java.io.File
import java.nio.file.Files
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class VaultExportPublicationRecoveryTest {
    @Test
    fun `incomplete plaintext stage is removed and its selection is eligible for a safe copy retry`() = fixture { f ->
        val receipt = f.receipt()
        f.interrupt(receipt) {
            Files.newOutputStream(f.stage.toPath(), java.nio.file.StandardOpenOption.WRITE).use {
                it.write("private incomplete bytes".encodeToByteArray())
            }
            assertTrue("An existing staging stream retains its ownership identity", receipt.stagedIdentity.matches(f.stage))
        }
        val queue = f.queues().single()
        assertFalse(queue.hasUncertainItem)
        assertEquals(1, queue.remainingItems)
        assertFalse(f.stage.exists())
        assertFalse(f.target.exists())
    }

    @Test
    fun `verified published file settles the item without exporting another copy`() = fixture { f ->
        f.stage.writeText("published bytes")
        val receipt = f.receipt().copy(ready = true)
        f.interrupt(receipt) { Files.move(f.stage.toPath(), f.target.toPath()) }
        assertTrue(f.queues().isEmpty())
        assertEquals("published bytes", f.target.readText())
    }

    @Test
    fun `interruption after backup rename restores the original and removes only the owned stage`() = fixture { f ->
        f.target.writeText("original local bytes")
        f.stage.writeText("new staged bytes")
        val receipt = f.receipt(replacing = true).copy(ready = true)
        f.interrupt(receipt) { Files.move(f.target.toPath(), f.backup.toPath()) }
        val queue = f.queues().single()
        assertFalse(queue.hasUncertainItem)
        assertEquals("original local bytes", f.target.readText())
        assertFalse(f.stage.exists())
        assertFalse(f.backup.exists())
    }

    @Test
    fun `published replacement cleans its recorded backup and preserves the new target`() = fixture { f ->
        f.target.writeText("old local bytes")
        f.stage.writeText("verified export bytes")
        val receipt = f.receipt(replacing = true).copy(ready = true)
        f.interrupt(receipt) {
            Files.move(f.target.toPath(), f.backup.toPath())
            Files.move(f.stage.toPath(), f.target.toPath())
        }
        assertTrue(f.queues().isEmpty())
        assertEquals("verified export bytes", f.target.readText())
        assertFalse(f.backup.exists())
    }

    @Test
    fun `a newer destination is never overwritten by automatic backup restoration`() = fixture { f ->
        f.target.writeText("old local bytes")
        val receipt = f.receipt(replacing = true).copy(ready = true)
        f.interrupt(receipt) {
            Files.move(f.target.toPath(), f.backup.toPath())
            f.target.writeText("new user bytes")
        }
        assertTrue(f.queues().single().hasUncertainItem)
        assertEquals("new user bytes", f.target.readText())
        assertEquals("old local bytes", f.backup.readText())
        assertFalse(f.stage.exists())
    }

    @Test
    fun `replaced staging identity is retained for review rather than deleting another file`() = fixture { f ->
        val receipt = f.receipt()
        val held = File(f.output, "held-stage")
        f.interrupt(receipt) {
            Files.move(f.stage.toPath(), held.toPath())
            f.stage.writeText("unrelated new file")
        }
        assertTrue(f.queues().single().hasUncertainItem)
        assertEquals("unrelated new file", f.stage.readText())
        assertTrue(held.exists())
    }

    @Test
    fun `replaced export parent cannot redirect cleanup into another directory`() = fixture { f ->
        val receipt = f.receipt()
        f.interrupt(receipt) {}
        val held = File(f.root, "previous-output")
        Files.move(f.output.toPath(), held.toPath())
        assertTrue(f.output.mkdir())
        f.stage.writeText("unrelated new directory contents")
        assertTrue(f.queues().single().hasUncertainItem)
        assertEquals("unrelated new directory contents", f.stage.readText())
        assertTrue(File(held, f.stage.name).exists())
    }

    @Test
    fun `a failed cleanup cannot settle and discard the only recovery receipt`() = fixture { f ->
        val journal = VaultOperationJournal(f.journalRoot)
        journal.start(f.session, f.plan).use { run ->
            run.beginItem(0)
            run.prepareExportPublication(0, f.receipt())
            Files.move(f.stage.toPath(), File(f.output, "held-stage").toPath())
            f.stage.writeText("unrelated new file")
            assertFalse(run.finishItem(0, VaultItemResult("vault:source", "Source.txt", VaultItemOutcome.FAILED)))
            run.finishQueue()
        }
        assertTrue(f.queues().single().hasUncertainItem)
        assertEquals("unrelated new file", f.stage.readText())
        assertTrue(f.journalRoot.walkTopDown().any { ".export-" in it.name })
    }

    @Test
    fun `missing prepared receipt stays uncertain instead of treating a publication as unstarted`() = fixture { f ->
        f.interrupt(f.receipt()) {}
        assertTrue(f.journalRoot.walkTopDown().single { ".export-" in it.name }.delete())
        assertTrue(f.queues().single().hasUncertainItem)
        assertTrue(f.stage.exists())
    }

    @Test
    fun `new producer stopped before preparing any publication can retry without skipping the item`() = fixture { f ->
        VaultOperationJournal(f.journalRoot).start(f.session, f.plan).use { it.beginItem(0) }
        val queue = f.queues().single()
        assertFalse(queue.hasUncertainItem)
        assertEquals(1, queue.remainingItems)
    }

    @Test
    fun `receipt paths cannot escape the authenticated export destination`() = fixture { f ->
        val victim = File(f.root, "untouched.txt").also { it.writeText("user bytes") }
        val receipt = f.receipt().copy(staging = victim.path)
        VaultOperationJournal(f.journalRoot).start(f.session, f.plan).use { run ->
            run.beginItem(0)
            assertTrue(runCatching { run.prepareExportPublication(0, receipt) }.isFailure)
        }
        assertEquals("user bytes", victim.readText())
    }

    @Test
    fun `private export paths are encrypted in the recovery receipt`() = fixture { f ->
        f.interrupt(f.receipt()) {}
        f.journalRoot.walkTopDown().filter(File::isFile).forEach {
            val bytes = it.readBytes().decodeToString()
            assertFalse(bytes.contains(f.stage.path))
            assertFalse(bytes.contains(f.target.path))
        }
    }

    private class Fixture(val root: File) {
        val output = File(root, "output").canonicalFile.apply { mkdirs() }
        val journalRoot = File(root, "queues")
        val vault = VaultId.of("vault")
        val session = VaultSessionRecord(vault, FileVaultDirectory(File(root, "vault")), ByteArray(32) { 3 })
        val stage = File(output, ".arcile-${UUID.randomUUID()}.tmp").also { it.writeText("") }
        val backup = File(output, ".arcile-backup-${UUID.randomUUID()}.tmp")
        val target = File(output, "Private exported document.txt")
        private val parentIdentity = ExportFolderIdentity.read(output)
        val plan = VaultQueuePlan(VaultRecoveryKind.EXPORT, output.path, true,
            nodes = listOf(QueuedVaultNode(vault.value, "source", DirectoryId.Root.value, null)),
            localDestinationIdentity = parentIdentity, publicationReceipts = true)
        fun receipt(replacing: Boolean = false) = VaultExportPublicationReceipt(
            stage.path, target.path, parentIdentity, ExportEntryIdentity.read(stage),
            backup.path.takeIf { replacing }, if (replacing) ExportEntryIdentity.read(target, owned = false) else null
        )
        fun interrupt(receipt: VaultExportPublicationReceipt, action: () -> Unit) {
            VaultOperationJournal(journalRoot).start(session, plan).use {
                it.beginItem(0)
                it.prepareExportPublication(0, receipt)
                action()
            }
        }
        fun queues() = VaultOperationJournal(journalRoot).list(session, setOf(vault))
    }

    private fun fixture(test: (Fixture) -> Unit) {
        val root = Files.createTempDirectory("arcile-export-publication-").toFile()
        val f = Fixture(root)
        try { test(f) } finally { f.session.destroy(); root.deleteRecursively() }
    }
}
