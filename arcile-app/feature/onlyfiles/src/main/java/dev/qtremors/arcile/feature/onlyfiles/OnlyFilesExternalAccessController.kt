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
        isCurrentMount: () -> Boolean,
        onReady: (List<VaultExternalGrant>) -> Unit,
        onFailure: () -> Unit = {}
    ) {
        val files = nodes.filterNot(VaultNodeMetadata::isDirectory)
        if (files.isEmpty()) return onFailure()
        scope.launch {
            val issued = mutableListOf<VaultExternalGrant>()
            var delivered = false
            try {
                for (node in files) {
                    if (!isCurrentMount()) return@launch
                    val result = if (plaintextFallback) {
                        manager.issuePlaintextFallback(node.ref)
                    } else {
                        manager.issue(node.ref)
                    }
                    val grant = result.getOrElse { error ->
                        if (isCurrentMount()) showError(error)
                        return@launch
                    }
                    issued += grant
                }
                if (isCurrentMount()) {
                    onReady(issued)
                    delivered = true
                }
            } finally {
                if (!delivered) {
                    revoke(issued)
                    onFailure()
                }
            }
        }
    }

    fun revoke(grants: List<VaultExternalGrant>) {
        grants.forEach { manager.revoke(it.token) }
    }
}
