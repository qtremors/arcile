package dev.qtremors.arcile.feature.onlyfiles

import dev.qtremors.arcile.core.vault.domain.VaultExternalAccessManager
import dev.qtremors.arcile.core.vault.domain.VaultExternalGrant
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class OnlyFilesExternalAccessController(
    private val manager: VaultExternalAccessManager,
    private val scope: CoroutineScope,
    private val showError: (Throwable) -> Unit
) {
    fun issue(
        nodes: List<VaultNodeMetadata>,
        plaintextFallback: Boolean,
        onReady: (List<VaultExternalGrant>) -> Unit
    ) {
        val files = nodes.filterNot(VaultNodeMetadata::isDirectory)
        if (files.isEmpty()) return
        scope.launch {
            val issued = mutableListOf<VaultExternalGrant>()
            for (node in files) {
                val result = if (plaintextFallback) {
                    manager.issuePlaintextFallback(node.ref)
                } else {
                    manager.issue(node.ref)
                }
                val grant = result.getOrElse { error ->
                    issued.forEach { manager.revoke(it.token) }
                    showError(error)
                    return@launch
                }
                issued += grant
            }
            onReady(issued)
        }
    }

    fun revoke(grants: List<VaultExternalGrant>) {
        grants.forEach { manager.revoke(it.token) }
    }
}
