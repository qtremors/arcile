package dev.qtremors.arcile.feature.onlyfiles

import dev.qtremors.arcile.core.ui.video.VideoPlaybackItem
import dev.qtremors.arcile.core.ui.video.VideoPlaybackSourceUpdate
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import dev.qtremors.arcile.core.vault.domain.VaultNodeRef
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Retains loaded pages while a listing refresh replaces the visible first page. */
internal class OnlyFilesVideoQueue(
    nodes: List<VaultNodeMetadata>,
    private val item: (VaultNodeMetadata) -> VideoPlaybackItem
) {
    private val nodesByReference = linkedMapOf<String, VaultNodeMetadata>()
    private val vaultId = nodes.firstOrNull()?.ref?.vaultId
    private val removedIds = hashSetOf<String>()
    private val itemsByReference = linkedMapOf<String, VideoPlaybackItem>()
    val refsByOpaqueId = ConcurrentHashMap<String, VaultNodeRef>()
    private val updates = MutableStateFlow(VideoPlaybackSourceUpdate(emptyList(), emptyList()))
    val sourceUpdates = updates.asStateFlow()
    val size: Int get() = nodesByReference.size

    init {
        merge(nodes)
    }

    fun node(reference: String): VaultNodeMetadata? = nodesByReference[reference]

    fun merge(nodes: List<VaultNodeMetadata>) {
        for (node in nodes) {
            if (!node.isViewableVideo() || node.ref.vaultId != vaultId || node.ref.nodeId.value in removedIds) continue
            val reference = node.toSharedFileModel().reference
            if (nodesByReference[reference] == node) continue
            nodesByReference[reference] = node
            itemsByReference[reference] = item(node)
            refsByOpaqueId[node.ref.nodeId.value] = node.ref
        }
        publish()
    }

    fun remove(nodeIds: Set<String>) {
        removedIds += nodeIds
        val references = nodesByReference.filterValues { it.ref.nodeId.value in nodeIds }.keys.toList()
        for (reference in references) {
            val node = nodesByReference.remove(reference) ?: continue
            itemsByReference.remove(reference)
            refsByOpaqueId.remove(node.ref.nodeId.value)
        }
        publish()
    }

    fun overlay(visible: Boolean) {
        updates.value = updates.value.copy(overlayVisible = visible)
    }

    private fun publish() {
        updates.value = VideoPlaybackSourceUpdate(
            itemsByReference.values.toList(), nodesByReference.values.map(VaultNodeMetadata::toSharedFileModel),
            overlayVisible = updates.value.overlayVisible
        )
    }
}
