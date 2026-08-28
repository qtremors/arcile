package dev.qtremors.arcile.feature.onlyfiles

import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultLocationKind
import dev.qtremors.arcile.core.vault.domain.VaultSummary
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlyFilesVaultSessionStateTest {
    private val vaultId = VaultId.of("vault")

    @Test
    fun `live session state replaces a stale locked summary`() {
        val current = listOf(vault(isUnlocked = false)).withCurrentUnlockState(setOf(vaultId))

        assertTrue(current.single().isUnlocked)
    }

    @Test
    fun `live session state replaces a stale unlocked summary after expiry`() {
        val current = listOf(vault(isUnlocked = true)).withCurrentUnlockState(emptySet())

        assertFalse(current.single().isUnlocked)
    }

    @Test
    fun `pending prompt disappears once its vault is selected or unlocked`() {
        val locked = vault(isUnlocked = false)

        assertNull(pendingUnlockVault(vaultId, vaultId, listOf(locked)))
        assertNull(pendingUnlockVault(vaultId, null, listOf(locked.copy(isUnlocked = true))))
    }

    private fun vault(isUnlocked: Boolean) = VaultSummary(
        id = vaultId,
        name = "Vault",
        locationKind = VaultLocationKind.APP_PRIVATE,
        createdAtMillis = 0L,
        isUnlocked = isUnlocked,
        isAvailable = true
    )
}
