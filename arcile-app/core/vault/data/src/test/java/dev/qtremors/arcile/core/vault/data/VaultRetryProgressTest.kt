package dev.qtremors.arcile.core.vault.data

import dev.qtremors.arcile.core.vault.domain.*
import org.junit.Assert.*
import org.junit.Test

class VaultRetryProgressTest {
    @Test
    fun `large retry writes small child records and compacts the root once per attempt`() {
        val f = Fixture(200)
        val progress = f.open()
        for (child in f.children) {
            progress.prepare(f.publication(child))
            progress.published()
        }
        val completed = progress.finish()
        assertTrue(requireNotNull(completed.merge).children.isEmpty())
        assertEquals(200, completed.completedSteps)
        assertEquals(2, f.storage.checkpointWrites) // Initialization promise, then compaction.
        assertEquals(400, f.storage.stepWrites) // Prepared, then acknowledged per child.
        assertTrue(f.storage.steps.isEmpty())
    }

    @Test
    fun `crash after compaction but before old deltas are removed cannot apply them twice`() {
        val f = Fixture(2)
        val progress = f.open()
        progress.prepare(f.publication(f.children[0]))
        progress.published()
        f.storage.failRemoval = true
        assertTrue(runCatching { progress.finish() }.isFailure)
        assertEquals(1, f.storage.saved.completedSteps)
        assertEquals(1, requireNotNull(f.storage.saved.merge).children.size)
        f.storage.failRemoval = false
        // Even a corrupted obsolete delta is not read after its checkpoint is durable.
        f.storage.steps[1] = f.publication(f.children[1]).copy(mergeChild = null)
        val restarted = f.open()
        val saved = restarted.finish()
        assertEquals(listOf(f.children[1]), requireNotNull(saved.merge).children)
        assertTrue(f.storage.steps.isEmpty())
    }

    @Test
    fun `crash before syncing the count leaves an unpublished orphan that can be overwritten`() {
        val f = Fixture(2)
        val progress = f.open()
        f.storage.failCountAt = 1
        assertTrue(runCatching { progress.prepare(f.publication(f.children[0])) }.isFailure)
        assertEquals(0, f.storage.savedCount)
        assertEquals(1, f.storage.steps.size)
        f.storage.failCountAt = null
        val restarted = f.open()
        restarted.prepare(f.publication(f.children[1]))
        restarted.published()
        assertEquals(listOf(f.children[0]), requireNotNull(restarted.finish().merge).children)
    }

    @Test
    fun `uncertain publication is kept intact and cannot be replaced by another child`() {
        val f = Fixture(2)
        f.open().prepare(f.publication(f.children[0]))
        f.state = VaultPublicationState.UNCERTAIN
        val restarted = f.newProgress()
        assertFalse(restarted.recover())
        assertTrue(runCatching { restarted.prepare(f.publication(f.children[1])) }.isFailure)
        assertTrue(runCatching { restarted.prepare(f.publication(f.children[0])) }.isFailure)
        assertTrue(runCatching { restarted.published() }.isFailure)
        assertTrue(runCatching { restarted.finish() }.isFailure)
        assertEquals(2, requireNotNull(f.storage.saved.merge).children.size)
        assertEquals(1, f.storage.steps.size)
    }

    @Test
    fun `unpublished final child is retained after earlier acknowledged children are compacted`() {
        val f = Fixture(3)
        val progress = f.open()
        progress.prepare(f.publication(f.children[0]))
        progress.published()
        progress.prepare(f.publication(f.children[1]))
        f.state = VaultPublicationState.UNPUBLISHED
        val restarted = f.open()
        val current = restarted.finish()
        assertEquals(f.children.drop(1), requireNotNull(current.merge).children)
        assertEquals(2, current.completedSteps)
        assertEquals(2, f.storage.savedCount)
        assertTrue(f.storage.steps.isEmpty())
    }

    @Test
    fun `new attempt continues the durable sequence after an unpublished child`() {
        val f = Fixture(2)
        f.open().prepare(f.publication(f.children[0]))
        f.state = VaultPublicationState.UNPUBLISHED
        val next = f.open()
        next.prepare(f.publication(f.children[0]))
        assertEquals(2, f.storage.savedCount)
        next.published()
        assertEquals(listOf(f.children[1]), requireNotNull(next.finish().merge).children)
    }

    @Test
    fun `missing counter after initialization blocks replay`() {
        val f = Fixture(1)
        f.open().prepare(f.publication(f.children[0]))
        f.storage.savedCount = null
        assertTrue(runCatching { f.newProgress().recover() }.isFailure)
        assertEquals(1, requireNotNull(f.storage.saved.merge).children.size)
    }

    @Test
    fun `missing promised step blocks replay rather than silently dropping progress`() {
        val f = Fixture(2)
        val progress = f.open()
        progress.prepare(f.publication(f.children[0]))
        progress.published()
        progress.prepare(f.publication(f.children[1]))
        f.storage.steps.remove(1)
        assertTrue(runCatching { f.newProgress().recover() }.isFailure)
        assertEquals(2, requireNotNull(f.storage.saved.merge).children.size)
    }

    @Test
    fun `out of bounds counters fail before reading arbitrary steps`() {
        for (count in listOf(-1, VaultRetryProgress.MAX_STEPS + 1)) {
            val f = Fixture(1)
            f.storage.savedCount = count
            assertTrue(runCatching { f.newProgress().recover() }.isFailure)
            assertEquals(0, f.storage.stepReads)
        }
    }

    @Test
    fun `an unacknowledged nonfinal step is rejected as inconsistent history`() {
        val f = Fixture(2)
        f.open().prepare(f.publication(f.children[0]))
        f.storage.savedCount = 2
        f.storage.steps[2] = f.publication(f.children[1])
        assertTrue(runCatching { f.newProgress().recover() }.isFailure)
    }

    @Test
    fun `checkpoint write failure preserves acknowledged deltas for the next restart`() {
        val f = Fixture(2)
        val progress = f.open()
        progress.prepare(f.publication(f.children[0]))
        progress.published()
        f.storage.failCheckpoint = true
        assertTrue(runCatching { progress.finish() }.isFailure)
        assertEquals(0, f.storage.saved.completedSteps)
        assertTrue(f.storage.steps[1]?.settled == true)
        f.storage.failCheckpoint = false
        assertEquals(listOf(f.children[1]), requireNotNull(f.open().finish().merge).children)
    }

    private class MemoryStorage(overrideCheckpoint: VaultRetryCheckpoint) : VaultRetryProgress.Storage {
        var saved = overrideCheckpoint
        var savedCount: Int? = null
        val steps = mutableMapOf<Int, VaultRetryPublication>()
        var failRemoval = false
        var failCheckpoint = false
        var failCountAt: Int? = null
        var checkpointWrites = 0
        var stepWrites = 0
        var stepReads = 0
        override fun checkpoint() = saved
        override fun saveCheckpoint(value: VaultRetryCheckpoint) {
            check(!failCheckpoint) { "Checkpoint sync failed" }
            saved = value
            checkpointWrites++
        }
        override fun count() = savedCount
        override fun saveCount(value: Int) {
            check(failCountAt != value) { "Cursor sync failed" }
            savedCount = value
        }
        override fun readStep(sequence: Int): VaultRetryPublication {
            stepReads++
            return requireNotNull(steps[sequence])
        }
        override fun writeStep(sequence: Int, value: VaultRetryPublication) {
            steps[sequence] = value
            stepWrites++
        }
        override fun removeSteps(through: Int) {
            check(!failRemoval) { "Delta cleanup interrupted" }
            steps.keys.removeAll { it <= through }
        }
    }

    private class Fixture(count: Int) {
        val vault = VaultId.of("vault")
        private val source = QueuedVaultNode(vault.value, "source", DirectoryId.Root.value, "source-dir")
        private val destination = QueuedVaultNode(vault.value, "destination", DirectoryId.Root.value, "destination-dir")
        val children = (0 until count).map { index -> SavedDeferredVaultChild(listOf(source), listOf(destination),
            QueuedVaultNode(vault.value, "child-$index", "source-dir", null), 1, "Child $index.txt") }
        private val plan = VaultQueuePlan(VaultRecoveryKind.PASTE, DirectoryId.Root.value, true, nodes = listOf(source))
        val storage = MemoryStorage(VaultRetryCheckpoint(merge = SavedVaultMerge(source, vault.value,
            DirectoryId.Root.value, "Source", children)))
        var state = VaultPublicationState.PUBLISHED

        fun publication(child: SavedDeferredVaultChild) = VaultRetryPublication(mergeChild = child,
            vault = VaultPublicationReceipt(listOf(SavedVaultNode(
                QueuedVaultNode(vault.value, "published-${child.source.node}", "destination-dir", null),
                child.name, VaultNodeKind.FILE, 1, 0, 1)), 0))

        fun newProgress() = VaultRetryProgress(storage,
            validate = { step, checkpoint -> step.validate(checkpoint, plan, 0, vault) }, inspect = { _, _ -> state })
        fun open() = newProgress().also { assertTrue(it.recover()) }
    }
}
