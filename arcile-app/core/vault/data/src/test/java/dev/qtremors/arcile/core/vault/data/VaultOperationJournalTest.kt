package dev.qtremors.arcile.core.vault.data

import dev.qtremors.arcile.core.vault.crypto.FileVaultDirectory
import dev.qtremors.arcile.core.vault.domain.DirectoryId
import dev.qtremors.arcile.core.vault.domain.VaultFailure
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultRecoveryKind
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultOperationJournalTest {
    @Test
    fun `reopened journal resumes after the uncertain item and removes the completed queue`() = withJournal { root, session ->
        val journal = VaultOperationJournal(root)
        val plan = importPlan("settled", "uncertain", "ready", move = true)
        journal.start(session, plan).use { run ->
            run.beginItem(0)
            run.finishItem(0)
            run.beginItem(1)
            assertTrue(journal.list(session, setOf(session.id)).isEmpty())
        }
        val restarted = VaultOperationJournal(root)
        val queue = restarted.list(session, setOf(session.id)).single()
        assertEquals(1, queue.settledItems)
        assertEquals(1, queue.remainingItems)
        assertTrue(queue.hasUncertainItem)
        assertTrue(queue.wasMove)

        restarted.resume(session, queue.id, VaultRecoveryKind.IMPORT).use { resumed ->
            assertEquals(2, resumed.startIndex)
            assertEquals(listOf("ready"), resumed.plan.localSources.drop(resumed.startIndex))
            assertTrue(runCatching { restarted.discard(session, queue.id) }.exceptionOrNull() is VaultFailure.OperationInProgress)
            resumed.beginItem(2)
            resumed.finishItem(2)
            resumed.finishQueue()
        }
        assertTrue(VaultOperationJournal(root).list(session, setOf(session.id)).isEmpty())
    }

    @Test
    fun `missing or tampered cursor is never interpreted as an unstarted queue`() = withJournal { root, session ->
        val journal = VaultOperationJournal(root)
        journal.start(session, importPlan("sensitive path")).close()
        val cursor = root.walkTopDown().single { it.extension == "cursor" }
        cursor.writeBytes(cursor.readBytes().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() })
        val restarted = VaultOperationJournal(root)
        val queue = restarted.list(session, setOf(session.id)).single()
        assertNull(queue.kind)
        assertEquals(0, queue.remainingItems)
        assertTrue(runCatching { restarted.resume(session, queue.id, VaultRecoveryKind.IMPORT) }.isFailure)
        assertTrue(cursor.delete())
        assertNull(restarted.list(session, setOf(session.id)).single().kind)
        restarted.discard(session, queue.id)
        assertTrue(restarted.list(session, setOf(session.id)).isEmpty())
    }

    @Test
    fun `a cursor from another queue cannot rewind an interrupted item`() = withJournal { root, session ->
        val journal = VaultOperationJournal(root)
        journal.start(session, importPlan("first", "second")).use { it.beginItem(0) }
        val first = journal.list(session, setOf(session.id)).single()
        journal.start(session, importPlan("another", "queue")).close()
        val second = journal.list(session, setOf(session.id)).single { it.id != first.id }
        val firstCursor = root.walkTopDown().single { it.name == "${first.id}.cursor" }
        val secondCursor = root.walkTopDown().single { it.name == "${second.id}.cursor" }

        secondCursor.copyTo(firstCursor, overwrite = true)

        val queues = VaultOperationJournal(root).list(session, setOf(session.id))
        assertNull(queues.single { it.id == first.id }.kind)
        assertEquals(2, queues.single { it.id == second.id }.remainingItems)
        assertTrue(runCatching { journal.resume(session, first.id, VaultRecoveryKind.IMPORT) }.isFailure)
    }

    @Test
    fun `plan contains no plaintext paths and cannot be opened with another master key`() = withJournal { root, session ->
        val sensitive = "/storage/emulated/0/Private project/sample.pdf"
        VaultOperationJournal(root).start(session, importPlan(sensitive)).close()
        assertFalse(root.walkTopDown().filter(File::isFile).any { it.readBytes().decodeToString().contains(sensitive) })
        val wrongKey = VaultSessionRecord(session.id, session.directory, ByteArray(32) { 7 })
        try {
            val queue = VaultOperationJournal(root).list(wrongKey, setOf(wrongKey.id)).single()
            assertNull(queue.kind)
            assertEquals(0, queue.remainingItems)
        } finally {
            wrongKey.destroy()
        }
        assertEquals(VaultRecoveryKind.IMPORT, VaultOperationJournal(root).list(session, setOf(session.id)).single().kind)
    }

    @Test
    fun `queue with only an uncertain item stays visible for manual review`() = withJournal { root, session ->
        val journal = VaultOperationJournal(root)
        journal.start(session, importPlan("uncertain")).use { it.beginItem(0) }
        val queue = journal.list(session, setOf(session.id)).single()
        assertTrue(queue.hasUncertainItem)
        assertEquals(0, queue.remainingItems)
        assertTrue(runCatching { journal.resume(session, queue.id, VaultRecoveryKind.IMPORT) }.isFailure)
        journal.discard(session, queue.id)
        assertTrue(journal.list(session, setOf(session.id)).isEmpty())
    }

    @Test
    fun `resuming and stopping before starting another item never makes the uncertain item eligible again`() =
        withJournal { root, session ->
            VaultOperationJournal(root).start(session, importPlan("uncertain", "ready")).use { it.beginItem(0) }
            val journal = VaultOperationJournal(root)
            val queue = journal.list(session, setOf(session.id)).single()
            journal.resume(session, queue.id, VaultRecoveryKind.IMPORT).close()
            val next = VaultOperationJournal(root).list(session, setOf(session.id)).single()
            assertEquals(queue.id, next.id)
            assertFalse(next.hasUncertainItem)
            assertEquals(1, next.remainingItems)
            VaultOperationJournal(root).resume(session, next.id, VaultRecoveryKind.IMPORT).use {
                assertEquals(1, it.startIndex)
            }
        }

    private fun importPlan(vararg paths: String, move: Boolean = false) =
        VaultQueuePlan(VaultRecoveryKind.IMPORT, DirectoryId.Root.value, move, localSources = paths.toList())

    private fun withJournal(test: (File, VaultSessionRecord) -> Unit) {
        val root = Files.createTempDirectory("arcile-operation-journal").toFile()
        val session = VaultSessionRecord(VaultId.of("vault"), FileVaultDirectory(File(root, "vault")), ByteArray(32) { 3 })
        try {
            test(File(root, "queues"), session)
        } finally {
            session.destroy()
            root.deleteRecursively()
        }
    }
}
