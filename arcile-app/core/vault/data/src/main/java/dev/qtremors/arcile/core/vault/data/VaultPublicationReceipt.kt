package dev.qtremors.arcile.core.vault.data

import dev.qtremors.arcile.core.vault.domain.*
import kotlinx.serialization.Serializable

internal enum class VaultPublicationState { PUBLISHED, UNPUBLISHED, UNCERTAIN }

/** Written before the atomic manifest commit. Contains no protected directory/content keys. */
@Serializable
internal data class VaultPublicationReceipt(
    val nodes: List<SavedVaultNode>,
    val parentGeneration: Long,
    val checkpoint: VaultRetryCheckpoint? = null
) {
    fun validate(plan: VaultQueuePlan, index: Int, owner: VaultId) {
        require(plan.kind == VaultRecoveryKind.IMPORT || plan.kind == VaultRecoveryKind.PASTE)
        require(index in 0 until plan.size && parentGeneration >= 0)
        require(nodes.isNotEmpty() && nodes.size <= MAX_EXPORT_RETRY_RECORDS)
        val destination = DirectoryId.of(plan.destination)
        nodes.forEach {
            val metadata = it.restore()
            require(metadata.ref.vaultId == owner && metadata.ref.parentId == destination)
        }
        require(nodes.map { it.ref.node }.distinct().size == nodes.size)
        checkpoint?.validate(plan, index, owner)
    }

    fun inspect(session: VaultSessionRecord): VaultPublicationState = try {
        val parent = session.resolveDirectory(nodes.first().ref.ref().parentId)
        try {
            val snapshot = session.readDirectory(parent)
            try {
                val byId = snapshot.entries.associateBy { it.nodeId }
                val matches = nodes.all { saved ->
                    val expected = saved.restore()
                    byId[expected.ref.nodeId]?.toMetadata(session.id, parent.id) == expected
                }
                when {
                    matches -> VaultPublicationState.PUBLISHED
                    snapshot.generation == parentGeneration && nodes.none { it.ref.ref().nodeId in byId } -> VaultPublicationState.UNPUBLISHED
                    else -> VaultPublicationState.UNCERTAIN
                }
            } finally { snapshot.clearProtectedKeys() }
        } finally { parent.key.fill(0) }
    } catch (_: Exception) {
        VaultPublicationState.UNCERTAIN
    }
}
