package dev.qtremors.arcile.feature.onlyfiles

import dev.qtremors.arcile.core.storage.domain.BuiltInFileOpen
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import dev.qtremors.arcile.core.vault.domain.VaultNodeRef
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Queue removals affect this playback session and survive additional listing pages. */
internal class OnlyFilesAudioQueue(nodes: List<VaultNodeMetadata>) {
    private val removed = hashSetOf<String>()
    private val vaultId = nodes.firstOrNull()?.ref?.vaultId
    private val mutableNodes = MutableStateFlow<List<VaultNodeMetadata>>(emptyList())
    val nodes = mutableNodes.asStateFlow()
    val refs = ConcurrentHashMap<String, VaultNodeRef>()

    init {
        merge(nodes)
    }

    fun merge(incoming: List<VaultNodeMetadata>) {
        val known = mutableNodes.value.associateBy { it.ref.nodeId.value }.toMutableMap()
        for (node in incoming) {
            if (node.ref.vaultId == vaultId && node.builtInOpen() == BuiltInFileOpen.AUDIO && node.ref.nodeId.value !in removed) {
                known[node.ref.nodeId.value] = node
                refs[node.ref.nodeId.value] = node.ref
            }
        }
        mutableNodes.value = known.values.toList()
    }

    fun move(reference: String, offset: Int) {
        val list = mutableNodes.value.toMutableList()
        val index = list.indexOfFirst { it.toSharedFileModel().reference == reference }
        val target = index + offset
        if (index !in list.indices || target !in list.indices) return
        list.add(target, list.removeAt(index))
        mutableNodes.value = list
    }

    fun remove(reference: String) {
        val target = mutableNodes.value.firstOrNull { it.toSharedFileModel().reference == reference } ?: return
        removed += target.ref.nodeId.value
        mutableNodes.value = mutableNodes.value.filterNot { it.ref == target.ref }
        refs.remove(target.ref.nodeId.value)
    }

    fun clear() {
        removed += mutableNodes.value.map { it.ref.nodeId.value }
        mutableNodes.value = emptyList()
        refs.clear()
    }
}
