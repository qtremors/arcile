package dev.qtremors.arcile.feature.onlyfiles

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import dev.qtremors.arcile.core.vault.domain.DirectoryId
import dev.qtremors.arcile.core.vault.domain.NodeId
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultNodeCapabilities
import dev.qtremors.arcile.core.vault.domain.VaultNodeKind
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import dev.qtremors.arcile.core.vault.domain.VaultNodeRef
import dev.qtremors.arcile.core.vault.domain.VaultRenameItem
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.RuntimeEnvironment
import dev.qtremors.arcile.core.ui.R as UiR

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OnlyFilesBatchRenameDialogTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the shared preview maps equal filenames in separate folders back to stable vault refs`() {
        val first = node("first", DirectoryId.Root)
        val second = node("second", DirectoryId.of("folder"))
        val expected = listOf(first, second).map { VaultRenameItem(it.ref, it.name, "photo.txt") }
        var submitted = emptyList<VaultRenameItem>()
        compose.setContent {
            MaterialTheme {
                OnlyFilesBatchRenameDialog(
                    nodes = listOf(first, second),
                    loadSiblingNames = { items ->
                        assertEquals(expected.map { it.copy(newName = it.expectedName) }, items)
                        Result.success(emptyMap())
                    },
                    onDismiss = {},
                    onRename = { submitted = it }
                )
            }
        }

        compose.onNodeWithText(label(UiR.string.action_apply_rename)).assertIsNotEnabled()
        renameFromNotesToPhoto()
        compose.onNodeWithText(label(UiR.string.action_apply_rename)).assertIsEnabled()
            .performSemanticsAction(SemanticsActions.OnClick) { click -> click() }

        compose.runOnIdle { assertEquals(expected, submitted) }
    }

    @Test
    fun `a name occupied by an unloaded sibling disables the apply action`() {
        val first = node("first", DirectoryId.Root)
        val second = node("second", DirectoryId.of("folder"))
        compose.setContent {
            MaterialTheme {
                OnlyFilesBatchRenameDialog(
                    nodes = listOf(first, second),
                    loadSiblingNames = { Result.success(mapOf(second.ref.parentId to setOf("PHOTO.TXT"))) },
                    onDismiss = {},
                    onRename = { error("The preview contains a name collision") }
                )
            }
        }

        renameFromNotesToPhoto()

        compose.onNodeWithText(label(UiR.string.action_apply_rename)).assertIsNotEnabled()
    }

    private fun renameFromNotesToPhoto() {
        compose.onNode(hasSetTextAction() and hasText(label(UiR.string.label_find))).performTextInput("notes")
        compose.onNode(hasSetTextAction() and hasText(label(UiR.string.label_replace_with))).performTextInput("photo")
    }

    private fun label(resource: Int): String = RuntimeEnvironment.getApplication().getString(resource)

    private fun node(id: String, parent: DirectoryId) = VaultNodeMetadata(
        VaultNodeRef(VaultId.of("vault"), NodeId.of(id), parent, VaultNodeCapabilities()),
        "notes.txt", VaultNodeKind.FILE, 1L, 0L, 1L
    )
}
