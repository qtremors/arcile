package dev.qtremors.arcile.feature.onlyfiles

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultLocationKind
import dev.qtremors.arcile.core.vault.domain.VaultSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OnlyFilesVaultPinsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `pinning a locked vault reorders its card and keeps the ordinary open callback`() {
        val first = vault("first", "First vault")
        val second = vault("second", "Second vault")
        val pinned = mutableStateOf(emptySet<VaultId>())
        val changed = mutableListOf<Pair<VaultId, Boolean>>()
        val opened = mutableListOf<VaultSummary>()
        compose.setContent {
            MaterialTheme {
                VaultList(
                    vaults = listOf(first, second),
                    pinnedVaults = pinned.value,
                    imports = emptyMap(),
                    biometricVaultIds = emptySet(),
                    onPinVault = { id, value ->
                        changed += id to value
                        pinned.value = if (value) pinned.value + id else pinned.value - id
                    },
                    onOpen = { opened += it },
                    onOpenSettings = { error("Locked vaults offer no settings") },
                    onUnlockBiometric = { error("Expected ordinary unlock") },
                    onCancelImport = {}
                )
            }
        }

        assertTrue(top(first.name) < top(second.name))
        compose.onNodeWithText("Pinned").assertDoesNotExist()
        openActions(second)
        compose.onNodeWithText("Vault settings").assertDoesNotExist()
        compose.onNodeWithText("Pin vault").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.runOnIdle {
            assertEquals(listOf(second.id to true), changed)
            assertTrue(opened.isEmpty())
        }
        assertTrue(top(second.name) < top(first.name))
        // Pinned vaults get their own section above the rest.
        assertTrue(top("Pinned") < top(second.name))
        assertTrue(top(second.name) < top("Other vaults"))
        assertTrue(top("Other vaults") < top(first.name))
        compose.onNodeWithText(second.name).performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.runOnIdle { assertEquals(listOf(second), opened) }

        openActions(second)
        compose.onNodeWithText("Unpin vault").performSemanticsAction(SemanticsActions.OnClick) { it() }
        assertTrue(top(first.name) < top(second.name))
        compose.runOnIdle { assertEquals(listOf(second.id to true, second.id to false), changed) }
        compose.onNodeWithText("Pinned").assertDoesNotExist()
    }

    @Test
    fun `long-pressing an unlocked vault offers its settings`() {
        val unlocked = vault("open", "Open vault").copy(isUnlocked = true)
        val settings = mutableListOf<VaultSummary>()
        compose.setContent {
            MaterialTheme {
                VaultList(
                    vaults = listOf(unlocked),
                    pinnedVaults = emptySet(),
                    imports = emptyMap(),
                    biometricVaultIds = emptySet(),
                    onPinVault = { _, _ -> },
                    onOpen = { error("Long-press must not open the vault") },
                    onOpenSettings = { settings += it },
                    onUnlockBiometric = {},
                    onCancelImport = {}
                )
            }
        }
        openActions(unlocked)
        compose.onNodeWithText("Vault settings").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.runOnIdle { assertEquals(listOf(unlocked), settings) }
    }

    private fun openActions(vault: VaultSummary) {
        compose.onNodeWithText(vault.name).performSemanticsAction(SemanticsActions.OnLongClick) { it() }
    }

    private fun top(name: String) = compose.onNodeWithText(name).fetchSemanticsNode().boundsInRoot.top

    private fun vault(id: String, name: String) =
        VaultSummary(VaultId.of(id), name, VaultLocationKind.APP_PRIVATE, 0L, isUnlocked = false)
}
