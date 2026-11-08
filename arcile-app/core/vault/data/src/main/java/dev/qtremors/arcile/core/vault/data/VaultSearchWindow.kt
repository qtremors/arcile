package dev.qtremors.arcile.core.vault.data

import dev.qtremors.arcile.core.vault.domain.DirectoryId
import dev.qtremors.arcile.core.vault.domain.VaultPage
import dev.qtremors.arcile.core.vault.domain.VaultSearchHit
import dev.qtremors.arcile.core.vault.domain.VaultSearchQuery

/** A bounded slice of ranked results retained only by the live interactive vault session. */
internal class VaultSearchWindow(
    val directoryId: DirectoryId,
    val query: VaultSearchQuery,
    val publicationGeneration: Long,
    private val startOffset: Int,
    private val ranked: VaultPage<VaultSearchHit>,
    val versions: VaultSearchVersions? = null,
    val anchorFileCount: Long? = null
) {
    fun nextAnchor(offset: Int): VaultSearchHit? =
        ranked.items.lastOrNull()?.takeIf {
            ranked.nextPageToken != null && offset.toLong() == startOffset.toLong() + ranked.items.size
        }

    fun page(offset: Int): VaultPage<VaultSearchHit>? {
        val local = offset - startOffset
        if (local !in ranked.items.indices) return null
        val moreAfterWindow = ranked.nextPageToken != null
        val requestedEnd = local + query.pageSize
        if (requestedEnd > ranked.items.size && moreAfterWindow) return null
        val end = requestedEnd.coerceAtMost(ranked.items.size)
        return VaultPage(
            ranked.items.subList(local, end).toList(),
            (startOffset + end).takeIf { end < ranked.items.size || moreAfterWindow }?.toString(),
            ranked.generation
        )
    }
}
