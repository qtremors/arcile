package dev.qtremors.arcile.core.vault.data

import dev.qtremors.arcile.core.vault.crypto.*
import dev.qtremors.arcile.core.vault.domain.*
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class VaultRetryPublicationTest {
    @Test
    fun `restart recognizes a child committed before the retry acknowledged it`() = runTest {
        fixture { f ->
            f.beginRetry { run, progress ->
                f.copy(f.children.first(), progress)
                // Process death before progress.published() or finishRetry().
                assertEquals(2, (run.pendingRetries().single().second.mergeRetry as PendingVaultMerge).remainingCount)
            }
            f.withFreshSession { session ->
                val journal = VaultOperationJournal(f.queues)
                val queue = journal.list(session, setOf(f.vault)).single()
                assertFalse(queue.hasUncertainItem)
                journal.resume(session, queue.id, VaultRecoveryKind.PASTE).use { run ->
                    val pending = run.pendingRetries().single().second.mergeRetry as PendingVaultMerge
                    assertEquals(listOf("B.txt"), pending.children.map { it.sourceName })
                    assertFalse(pending.move)
                    assertTrue(pending.cleanupPaths.isEmpty())
                }
                assertEquals(listOf("A.txt"), f.names(session, f.targetDirectory))
                assertEquals(listOf("A.txt", "B.txt"), f.names(session, f.sourceDirectory))
            }
        }
    }

    @Test
    fun `restart retries a child prepared before its manifest publication`() = runTest {
        fixture { f ->
            f.beginRetry { _, progress ->
                val result = runCatching {
                    f.engine.copyOne(f.session, f.children.first().source, f.session, f.targetDirectory,
                        replace, neverCancelled, onPrepared = {
                            progress.prepare(VaultRetryPublication.merge(f.children.first(), it))
                            throw CancellationException("Process stopped before committing")
                        })
                }
                assertTrue(result.exceptionOrNull() is CancellationException)
            }
            f.withFreshSession { session ->
                val journal = VaultOperationJournal(f.queues)
                val queue = journal.list(session, setOf(f.vault)).single()
                assertFalse(queue.hasUncertainItem)
                journal.resume(session, queue.id, VaultRecoveryKind.PASTE).use {
                    assertEquals(2, (it.pendingRetries().single().second.mergeRetry as PendingVaultMerge).remainingCount)
                }
                assertTrue(f.names(session, f.targetDirectory).isEmpty())
            }
        }
    }

    @Test
    fun `acknowledged children stay settled when a later manifest generation changes`() = runTest {
        fixture { f ->
            f.beginRetry { _, progress ->
                f.copy(f.children[0], progress)
                progress.published()
                f.copy(f.children[1], progress)
                // The second publication changes the first receipt's parent generation.
            }
            f.withFreshSession { session ->
                val journal = VaultOperationJournal(f.queues)
                val queue = journal.list(session, setOf(f.vault)).single()
                journal.resume(session, queue.id, VaultRecoveryKind.PASTE).use { run ->
                    val result = run.pendingRetries().single().second
                    assertEquals(0, requireNotNull(result.mergeRetry).remainingCount)
                    run.retryProgress(0)
                    run.finishRetry(0, result.copy(outcome = VaultItemOutcome.COMPLETED, mergeRetry = null))
                    run.finishQueue()
                }
                assertTrue(journal.list(session, setOf(f.vault)).isEmpty())
                assertEquals(listOf("A.txt", "B.txt"), f.names(session, f.targetDirectory))
            }
        }
    }

    @Test
    fun `changed unacknowledged output blocks its root and reports its actual index`() = runTest {
        fixture { f ->
            f.beginRetry { _, progress -> f.copy(f.children[0], progress) }
            f.publishTarget(2, emptyList()) // Independent mutation after publication.
            f.withFreshSession { session ->
                val journal = VaultOperationJournal(f.queues)
                val queue = journal.list(session, setOf(f.vault)).single()
                assertTrue(queue.hasUncertainItem)
                assertEquals(listOf(0), queue.uncertainItemIndices)
                assertEquals(0, queue.remainingItems)
                assertTrue(runCatching { journal.resume(session, queue.id, VaultRecoveryKind.PASTE) }.isFailure)
                assertEquals(listOf("A.txt", "B.txt"), f.names(session, f.sourceDirectory))
            }
        }
    }

    @Test
    fun `missing prepared delta is unreadable rather than replayed`() = runTest {
        fixture { f ->
            f.beginRetry { _, progress -> f.copy(f.children[0], progress) }
            assertTrue(f.queues.walkTopDown().single { it.name.endsWith(".step-0-1") }.delete())
            f.assertUnreadable()
            assertEquals(listOf("A.txt"), f.names(f.session, f.targetDirectory))
        }
    }

    @Test
    fun `missing retry counter cannot masquerade as an older checkpoint`() = runTest {
        fixture { f ->
            f.beginRetry { _, progress -> f.copy(f.children[0], progress) }
            assertTrue(f.queues.walkTopDown().single { it.name.endsWith(".progress-0") }.delete())
            f.assertUnreadable()
        }
    }

    @Test
    fun `tampered child receipt cannot authorize checkpoint advancement`() = runTest {
        fixture { f ->
            f.beginRetry { _, progress -> f.copy(f.children[0], progress) }
            val step = f.queues.walkTopDown().single { ".step-" in it.name }
            val bytes = step.readBytes()
            bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
            step.writeBytes(bytes)
            bytes.fill(0)
            f.assertUnreadable()
        }
    }

    @Test
    fun `copying a child receipt into a different queue fails authentication`() = runTest {
        fixture { f ->
            f.beginRetry { _, progress -> f.copy(f.children[0], progress) }
            val first = f.queues.walkTopDown().single { ".step-" in it.name }
            val firstPlan = f.queues.walkTopDown().single { it.extension == "plan" }
            val journal = VaultOperationJournal(f.queues)
            journal.start(f.session, f.plan).use {
                it.beginItem(0)
                it.finishItem(0, f.partial)
            }
            val secondPlan = f.queues.walkTopDown().filter { it.extension == "plan" && it != firstPlan }.single()
            val target = File(secondPlan.parentFile, secondPlan.name.removeSuffix(".plan") + ".step-0-1")
            target.writeBytes(first.readBytes())
            // Orphaned, unpublished step files are ignored; a promised count binds their use.
            val count = f.queues.walkTopDown().single { it.name.endsWith(".progress-0") }
            File(secondPlan.parentFile, secondPlan.name.removeSuffix(".plan") + ".progress-0").writeBytes(count.readBytes())
            val queues = VaultOperationJournal(f.queues).list(f.session, setOf(f.vault))
            assertEquals(1, queues.count { it.kind == null })
            assertEquals(1, queues.count { it.kind == VaultRecoveryKind.PASTE })
        }
    }

    @Test
    fun `child receipts reject a destination outside the saved folder chain`() = runTest {
        fixture { f ->
            f.beginRetry { _, progress ->
                val child = f.children[0]
                val wrong = SavedVaultNode(QueuedVaultNode(f.vault.value, "wrong", DirectoryId.Root.value, null),
                    "A.txt", VaultNodeKind.FILE, 1, 0, 1)
                assertTrue(runCatching {
                    progress.prepare(VaultRetryPublication.merge(child, VaultPublicationReceipt(listOf(wrong), 0)))
                }.isFailure)
            }
            assertTrue(f.names(f.session, f.targetDirectory).isEmpty())
        }
    }

    @Test
    fun `child records contain no plaintext logical names or directory keys on disk`() = runTest {
        fixture { f ->
            f.beginRetry { _, progress -> f.copy(f.children[0], progress) }
            f.queues.walkTopDown().filter(File::isFile).forEach { file ->
                val bytes = file.readBytes()
                assertFalse(bytes.decodeToString().contains("A.txt"))
                assertFalse(bytes.decodeToString().contains("Private source"))
                assertFalse(bytes.toList().windowed(32).any { it.all { byte -> byte == 6.toByte() } })
                bytes.fill(0)
            }
        }
    }

    @Test
    fun `uncertain folder retry stays available while a safe unstarted selection can complete`() = runTest {
        fixture(includeUnstarted = true) { f ->
            f.beginRetry { _, progress -> f.copy(f.children[0], progress) }
            f.publishTarget(2, emptyList())
            f.withFreshSession { session ->
                val journal = VaultOperationJournal(f.queues)
                val queue = journal.list(session, setOf(f.vault)).single()
                assertEquals(1, queue.remainingItems)
                assertEquals(listOf(0), queue.uncertainItemIndices)
                journal.resume(session, queue.id, VaultRecoveryKind.PASTE).use { run ->
                    assertTrue(run.pendingRetries().none())
                    assertEquals(1, run.startIndex)
                    run.beginItem(1)
                }
                // Stopping the safe selection before its receipt returns it to the queue.
                val stopped = journal.list(session, setOf(f.vault)).single()
                assertEquals(1, stopped.remainingItems)
                assertEquals(listOf(0), stopped.uncertainItemIndices)
            }
            val journal = VaultOperationJournal(f.queues)
            val queue = journal.list(f.session, setOf(f.vault)).single()
            journal.resume(f.session, queue.id, VaultRecoveryKind.PASTE).use { run ->
                run.beginItem(1)
                val copied = f.engine.copyOne(f.session, f.children[1].source, f.session, DirectoryId.Root,
                    replace, neverCancelled, onPrepared = { run.preparePublication(1, it) })
                run.finishItem(1, copied)
                run.finishQueue()
            }
            val remaining = journal.list(f.session, setOf(f.vault)).single()
            assertEquals(0, remaining.remainingItems)
            assertEquals(listOf(0), remaining.uncertainItemIndices)
            assertEquals(listOf("A.txt", "B.txt"), f.names(f.session, f.sourceDirectory))
            assertTrue(f.names(f.session, DirectoryId.Root).contains("B.txt"))
        }
    }

    @Test
    fun `finishing a folder retry cannot discard other unstarted selections`() = runTest {
        fixture(includeUnstarted = true) { f ->
            val journal = VaultOperationJournal(f.queues)
            val queue = journal.list(f.session, setOf(f.vault)).single()
            journal.resume(f.session, queue.id, VaultRecoveryKind.PASTE).use { run ->
                val progress = run.retryProgress(0)
                for (child in f.children) { f.copy(child, progress); progress.published() }
                run.finishRetry(0, f.partial.copy(outcome = VaultItemOutcome.COMPLETED, mergeRetry = null))
                run.finishQueue()
            }
            val remaining = VaultOperationJournal(f.queues).list(f.session, setOf(f.vault)).single()
            assertEquals(1, remaining.remainingItems)
            assertEquals(1, remaining.settledItems)
            assertFalse(remaining.hasUncertainItem)
            journal.resume(f.session, queue.id, VaultRecoveryKind.PASTE).use { run ->
                assertTrue(run.pendingRetries().none())
                assertEquals(1, run.startIndex)
            }
        }
    }

    private class Fixture(includeUnstarted: Boolean) {
        val root = Files.createTempDirectory("arcile-child-publication-").toFile()
        val queues = File(root, "queues")
        val vault = VaultId.of("vault")
        private val access = FileVaultDirectory(File(root, "vault").apply { mkdirs() })
        val session = VaultSessionRecord(vault, access, ByteArray(32) { 3 })
        val sourceDirectory = DirectoryId.of("source-dir")
        val targetDirectory = DirectoryId.of("target-dir")
        private val codec = VaultDirectoryManifestCodec()
        private val fileCodec = VaultFileCodec()
        private val sourceKey = ByteArray(32) { 5 }
        private val targetKey = ByteArray(32) { 6 }
        val engine = VaultTransferEngine(codec, fileCodec, VaultTransactionManager(codec))
        private val source = VaultNodeRef(vault, NodeId.of("source"), DirectoryId.Root, VaultNodeCapabilities(), sourceDirectory)
        private val target = VaultNodeRef(vault, NodeId.of("target"), DirectoryId.Root, VaultNodeCapabilities(), targetDirectory)
        val children = listOf("A", "B").map { name ->
            DeferredVaultChild(listOf(source), listOf(target),
                VaultNodeRef(vault, NodeId.of("child-$name"), sourceDirectory, VaultNodeCapabilities()), 1, "$name.txt")
        }
        val plan = VaultQueuePlan(VaultRecoveryKind.PASTE, DirectoryId.Root.value, true,
            nodes = listOf(QueuedVaultNode.from(source)) +
                if (includeUnstarted) listOf(QueuedVaultNode.from(children[1].source)) else emptyList(), publicationReceipts = true)
        val partial = VaultItemResult(source.backendIdentity, "Private source", VaultItemOutcome.PARTIAL,
            mergeRetry = PendingVaultMerge(source, vault, DirectoryId.Root, true, "Private source", children, setOf(listOf(source))))

        init {
            val sourceEntry = VaultManifestEntry(source.nodeId, "Private source", VaultNodeKind.DIRECTORY,
                1, 0, 0, null, null, sourceDirectory, sourceKey.copyOf())
            val targetEntry = VaultManifestEntry(target.nodeId, "Published", VaultNodeKind.DIRECTORY,
                1, 0, 0, null, null, targetDirectory, targetKey.copyOf())
            val rootKey = session.root().key
            try { codec.publish(access, codec.prepare(vault, DirectoryId.Root, rootKey, 0, listOf(sourceEntry, targetEntry))) }
            finally { rootKey.fill(0); sourceEntry.protectedKey.fill(0); targetEntry.protectedKey.fill(0) }
            val entries = children.mapIndexed { index, child ->
                val key = ByteArray(32) { (index + 7).toByte() }
                val objectId = VaultObjectId.of((index + 1).toString().repeat(64))
                fileCodec.writeObject(access, objectId.shardedPath(), vault, objectId, 1, key, "data".byteInputStream())
                VaultManifestEntry(child.source.nodeId, child.sourceName, VaultNodeKind.FILE, 1, 0, 4, null, objectId, null, key)
            }
            try { codec.publish(access, codec.prepare(vault, sourceDirectory, sourceKey, 0, entries)) }
            finally { entries.forEach { it.protectedKey.fill(0) } }
            publishTarget(0, emptyList())
            VaultOperationJournal(queues).start(session, plan).use { run ->
                run.beginItem(0)
                run.finishItem(0, partial)
            }
        }

        suspend fun beginRetry(test: suspend (VaultQueueRun, VaultRetryProgress) -> Unit) {
            val journal = VaultOperationJournal(queues)
            val queue = journal.list(session, setOf(vault)).single()
            journal.resume(session, queue.id, VaultRecoveryKind.PASTE).use { run -> test(run, run.retryProgress(0)) }
        }

        suspend fun copy(child: DeferredVaultChild, progress: VaultRetryProgress) =
            engine.copyOne(session, child.source, session, targetDirectory, replace, neverCancelled,
                onPrepared = { progress.prepare(VaultRetryPublication.merge(child, it)) })

        fun publishTarget(generation: Long, entries: List<VaultManifestEntry>) =
            codec.publish(access, codec.prepare(vault, targetDirectory, targetKey, generation, entries))

        fun names(session: VaultSessionRecord, directory: DirectoryId): List<String> {
            val resolved = session.resolveDirectory(directory)
            try {
                val snapshot = session.readDirectory(resolved)
                try { return snapshot.entries.map { it.name }.sorted() }
                finally { snapshot.clearProtectedKeys() }
            } finally { resolved.key.fill(0) }
        }

        fun withFreshSession(test: (VaultSessionRecord) -> Unit) {
            val fresh = VaultSessionRecord(vault, access, ByteArray(32) { 3 })
            try { test(fresh) } finally { fresh.destroy() }
        }

        fun assertUnreadable() = withFreshSession { session ->
            val queue = VaultOperationJournal(queues).list(session, setOf(vault)).single()
            assertNull(queue.kind)
            assertEquals(0, queue.remainingItems)
        }

        fun close() { session.destroy(); sourceKey.fill(0); targetKey.fill(0); root.deleteRecursively() }
    }

    private suspend fun fixture(includeUnstarted: Boolean = false, test: suspend (Fixture) -> Unit) {
        val f = Fixture(includeUnstarted)
        try { test(f) } finally { f.close() }
    }

    private companion object {
        val neverCancelled = VaultCancellationSignal { false }
        val replace = VaultConflictResolver { VaultConflictDecision.REPLACE }
    }
}
