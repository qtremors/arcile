package dev.qtremors.arcile.feature.onlyfiles

import dev.qtremors.arcile.core.vault.domain.DirectoryId
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.navigation.AppRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OnlyFilesNavigationTest {
    @Test
    fun `regular launch still shows the vault list`() {
        assertNull(AppRoutes.OnlyFiles().toVaultEntry())
    }

    @Test
    fun `an opaque target carries its parent directory and focus id`() {
        val route = AppRoutes.OnlyFiles("vault", "directory", "item")
        assertEquals(
            OnlyFilesEntry(VaultId.of("vault"), DirectoryId.of("directory"), "item"),
            route.toVaultEntry()
        )
    }

    @Test
    fun `location arguments reject filesystem paths`() {
        assertNull(AppRoutes.OnlyFiles("vault", "/storage/emulated/0").toVaultEntry())
        assertNull(AppRoutes.OnlyFiles("vault", focusNodeId = "../item").toVaultEntry())
    }
}
