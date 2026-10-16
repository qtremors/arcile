package dev.qtremors.arcile.core.vault.data

import dev.qtremors.arcile.core.vault.crypto.VaultDirectorySnapshot
import dev.qtremors.arcile.core.vault.crypto.VaultManifestEntry
import dev.qtremors.arcile.core.vault.domain.VaultCancellationSignal
import dev.qtremors.arcile.core.vault.domain.VaultConflict
import dev.qtremors.arcile.core.vault.domain.VaultConflictDecision
import dev.qtremors.arcile.core.vault.domain.VaultConflictResolver
import dev.qtremors.arcile.core.vault.domain.VaultFailure
import dev.qtremors.arcile.core.vault.domain.VaultItemOutcome
import dev.qtremors.arcile.core.vault.domain.VaultItemResult
import dev.qtremors.arcile.core.vault.domain.VaultName
import dev.qtremors.arcile.core.vault.domain.VaultNodeKind
import dev.qtremors.arcile.core.vault.domain.VaultNodeRef
import dev.qtremors.arcile.core.vault.domain.VaultSeekableReader
import java.io.InputStream

internal data class ResolvedTransferEntry(
    val parent: ResolvedVaultDirectory,
    val snapshot: VaultDirectorySnapshot,
    val entry: VaultManifestEntry
) : AutoCloseable {
    override fun close() {
        entry.protectedKey.fill(0)
        snapshot.clearProtectedKeys()
        parent.key.fill(0)
    }
}

internal class ReaderInputStream(
    private val reader: VaultSeekableReader,
    private val cancellation: VaultCancellationSignal
) : InputStream() {
    private var position = 0L
    private val one = ByteArray(1)
    override fun read(): Int = if (read(one, 0, 1) == 1) one[0].toInt() and 0xff else -1
    override fun read(target: ByteArray, offset: Int, length: Int): Int {
        cancellation.throwIfCancelled()
        if (position >= reader.sizeBytes) return -1
        val count = reader.readAt(position, target, offset, length)
        if (count > 0) position += count
        return count
    }
}

internal fun VaultCancellationSignal.throwIfCancelled() {
    if (isCancelled()) throw VaultFailure.Cancelled()
}

internal fun findByName(entries: List<VaultManifestEntry>, name: String): VaultManifestEntry? {
    val key = VaultName.of(name).comparisonKey
    return entries.firstOrNull { VaultName.of(it.name).comparisonKey == key }
}

internal fun VaultManifestEntry.conflictWith(source: VaultManifestEntry) = VaultConflict(
    sourceName = source.name,
    destinationName = name,
    sourceIsDirectory = source.kind == VaultNodeKind.DIRECTORY,
    destinationIsDirectory = kind == VaultNodeKind.DIRECTORY,
    sourceIdentity = "${source.nodeId.value}:${source.revision}",
    destinationIdentity = "${nodeId.value}:$revision",
    sourceSize = source.sizeBytes,
    destinationSize = sizeBytes,
    sourceModifiedAt = source.modifiedAtMillis,
    destinationModifiedAt = modifiedAtMillis
)

internal suspend fun VaultConflictResolver.reviewed(
    conflicts: List<VaultConflict>,
    cancellation: VaultCancellationSignal
): VaultConflictResolver {
    val choices = if (conflicts.isEmpty() || cancellation.isCancelled()) emptyMap() else review(conflicts)
    return VaultConflictResolver { conflict ->
        cancellation.throwIfCancelled()
        val choice = choices[conflict] ?: decide(conflict)
        cancellation.throwIfCancelled()
        if (choice == VaultConflictDecision.MERGE_DIRECTORIES && !conflict.canMerge) {
            throw VaultFailure.NameConflict(conflict.destinationName)
        }
        choice
    }
}

internal fun uniqueName(name: String, entries: List<VaultManifestEntry>): String {
    val names = entries.mapTo(mutableSetOf()) { VaultName.of(it.name).comparisonKey }
    val dot = name.lastIndexOf('.').takeIf { it > 0 }
    val stem = if (dot == null) name else name.substring(0, dot)
    val extension = if (dot == null) "" else name.substring(dot)
    for (index in 1..10_000) {
        val candidate = "$stem ($index)$extension"
        if (VaultName.of(candidate).comparisonKey !in names) return candidate
    }
    throw VaultFailure.NameConflict(name)
}

internal fun result(ref: VaultNodeRef, entry: VaultManifestEntry, outcome: VaultItemOutcome) = VaultItemResult(
    sourceIdentity = "${ref.vaultId.value}:${ref.nodeId.value}",
    displayName = entry.name,
    outcome = outcome
)
