package dev.qtremors.arcile.feature.onlyfiles

import dev.qtremors.arcile.core.vault.domain.VaultExternalAccessManager
import dev.qtremors.arcile.core.vault.domain.VaultExternalGrant
import dev.qtremors.arcile.core.vault.domain.VaultId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads and revokes external grants off the main thread, only while [stillCurrent] holds for the open mount. */
internal class OnlyFilesExternalGrants(private val manager: VaultExternalAccessManager) {
    suspend fun active(vault: VaultId, stillCurrent: () -> Boolean): List<VaultExternalGrant> {
        if (!stillCurrent()) return emptyList()
        val grants = withContext(Dispatchers.IO) { manager.activeGrants(vault) }
        return grants.takeIf { stillCurrent() }.orEmpty()
    }

    suspend fun revoke(vault: VaultId, token: String, stillCurrent: () -> Boolean) {
        withContext(Dispatchers.IO) {
            if (stillCurrent() && manager.activeGrants(vault).any { it.token == token }) manager.revoke(token)
        }
    }
}
