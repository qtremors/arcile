package dev.qtremors.arcile.core.vault.data

import dev.qtremors.arcile.core.vault.domain.VaultFailure

/** Writes small deltas per child and compacts the folder checkpoint once per attempt/restart. */
internal class VaultRetryProgress(
    private val storage: Storage,
    private val validate: (VaultRetryPublication, VaultRetryCheckpoint) -> Unit,
    private val inspect: (VaultRetryPublication, VaultRetryCheckpoint) -> VaultPublicationState
) {
    interface Storage {
        fun checkpoint(): VaultRetryCheckpoint
        fun saveCheckpoint(value: VaultRetryCheckpoint)
        fun count(): Int?
        fun saveCount(value: Int)
        fun readStep(sequence: Int): VaultRetryPublication
        fun writeStep(sequence: Int, value: VaultRetryPublication)
        fun removeSteps(through: Int)
    }

    private var checkpoint: VaultRetryCheckpoint? = null
    private var count = 0
    private var pending: VaultRetryPublication? = null
    private var ready = false

    /** Uncertain publication blocks this root only. Safe roots/unstarted selections can continue. */
    fun recover(): Boolean {
        var current = storage.checkpoint()
        val savedThrough = current.completedSteps
        val savedCount = storage.count()
        require(!current.stepJournal || savedCount != null) { "The folder retry cursor is missing" }
        count = savedCount ?: 0
        require(count in current.completedSteps..MAX_STEPS)
        var resolvedThrough = current.completedSteps
        var safe = true
        for (sequence in current.completedSteps + 1..count) {
            val step = storage.readStep(sequence)
            validate(step, current)
            val state = if (step.settled) VaultPublicationState.PUBLISHED else {
                // An unacknowledged publication must be the final step in its attempt.
                require(sequence == count)
                inspect(step, current)
            }
            if (state == VaultPublicationState.UNCERTAIN) {
                pending = step
                safe = false
                break
            }
            if (state == VaultPublicationState.PUBLISHED) current = step.apply(current)
            resolvedThrough = sequence
        }
        current = current.copy(completedSteps = resolvedThrough)
        if (resolvedThrough != savedThrough) storage.saveCheckpoint(current)
        // The synced checkpoint carries its sequence, so a crash while removing old deltas is safe.
        storage.removeSteps(resolvedThrough)
        checkpoint = current
        ready = safe
        if (safe) pending = null
        return safe
    }

    fun prepare(publication: VaultRetryPublication) {
        check(ready) { "The folder publication requires review" }
        var current = requireNotNull(checkpoint) { "Recover the folder checkpoint before publishing" }
        validate(publication, current)
        if (!current.stepJournal) {
            storage.saveCount(count)
            current = current.copy(stepJournal = true)
            storage.saveCheckpoint(current)
            checkpoint = current
        }
        val active = pending
        if (active != null) {
            require(active.sameChild(publication) && !active.settled)
            storage.writeStep(count, publication)
        } else {
            require(count < MAX_STEPS) { "The folder retry exceeds the operation limit" }
            val sequence = count + 1
            storage.writeStep(sequence, publication)
            storage.saveCount(sequence)
            count = sequence
        }
        pending = publication
    }

    /** Called only after the child operation has returned its committed result. */
    fun published() {
        check(ready) { "The folder publication requires review" }
        val step = pending ?: return // A skipped conflict does not publish anything.
        val current = requireNotNull(checkpoint)
        storage.writeStep(count, step.copy(settled = true))
        checkpoint = step.apply(current)
        pending = null
    }

    fun finish(): VaultRetryCheckpoint {
        if (!recover()) throw VaultFailure.ConcurrentMutation()
        return requireNotNull(checkpoint)
    }

    companion object {
        const val MAX_STEPS = 10_000
    }
}
