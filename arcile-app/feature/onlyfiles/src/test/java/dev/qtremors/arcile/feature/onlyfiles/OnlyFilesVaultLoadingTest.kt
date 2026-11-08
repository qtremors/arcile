package dev.qtremors.arcile.feature.onlyfiles

import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultLocationKind
import dev.qtremors.arcile.core.vault.domain.VaultSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlyFilesVaultLoadingTest {
    private val scanned = listOf(vault("a"), vault("b"))

    @Test
    fun `an empty list before the first scan means still loading`() {
        assertFalse(OnlyFilesUiState().vaultsLoaded)
    }

    @Test
    fun `finishing the scan applies its vaults when none have arrived yet`() {
        val loaded = OnlyFilesUiState().withVaultsLoaded(scanned)

        assertTrue(loaded.vaultsLoaded)
        assertEquals(scanned, loaded.vaults)
    }

    @Test
    fun `finishing the scan keeps vaults the collector already delivered`() {
        val delivered = listOf(vault("a").copy(isUnlocked = true))

        val loaded = OnlyFilesUiState(vaults = delivered).withVaultsLoaded(scanned)

        assertEquals(delivered, loaded.vaults)
    }

    @Test
    fun `a finished scan with no vaults shows the empty state`() {
        val loaded = OnlyFilesUiState().withVaultsLoaded(emptyList())

        assertTrue(loaded.vaultsLoaded)
        assertTrue(loaded.vaults.isEmpty())
    }

    private fun vault(id: String) = VaultSummary(VaultId.of(id), id, VaultLocationKind.APP_PRIVATE, 0L, isUnlocked = false)
}
