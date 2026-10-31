package dev.qtremors.arcile.core.vault.data

import dev.qtremors.arcile.core.vault.crypto.VaultManifestEntry
import dev.qtremors.arcile.core.vault.domain.DirectoryId
import dev.qtremors.arcile.core.vault.domain.VaultFailure

/** Directory IDs, rather than cached display paths, establish containment. */
internal fun vaultSubtreeDirectoryIds(
    session: VaultSessionRecord,
    root: VaultManifestEntry,
    limit: Int = Int.MAX_VALUE,
    checkActive: () -> Unit = {}
): Set<DirectoryId> {
    val ids = mutableSetOf<DirectoryId>()
    val pending = ArrayDeque<Pair<DirectoryId, ByteArray>>()
    pending += requireNotNull(root.childDirectoryId) to root.protectedKey.copyOf()
    try {
        while (pending.isNotEmpty()) {
            checkActive()
            val (id, key) = pending.removeFirst()
            try {
                if (!ids.add(id)) continue
                if (ids.size > limit) throw VaultFailure.Unavailable("The folder exceeds the Undo verification limit")
                val snapshot = session.readDirectory(id, key)
                try {
                    for (entry in snapshot.entries) {
                        val child = entry.childDirectoryId ?: continue
                        if (ids.size.toLong() + pending.size + 1 > limit) {
                            throw VaultFailure.Unavailable("The folder exceeds the Undo verification limit")
                        }
                        pending += child to entry.protectedKey.copyOf()
                    }
                } finally { snapshot.clearProtectedKeys() }
            } finally { key.fill(0) }
        }
        return ids
    } finally {
        while (pending.isNotEmpty()) pending.removeFirst().second.fill(0)
    }
}
