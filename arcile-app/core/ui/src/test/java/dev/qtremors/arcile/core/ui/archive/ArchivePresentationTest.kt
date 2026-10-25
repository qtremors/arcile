package dev.qtremors.arcile.core.ui.archive

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.qtremors.arcile.core.storage.domain.ArchiveNameEncoding
import dev.qtremors.arcile.core.ui.testing.ArcileTestTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ArchivePresentationTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `private archive password draft is excluded from saved state`() = checkPasswordRestore(privateSession = true)

    @Test
    fun `ordinary archive password draft retains existing restoration behavior`() = checkPasswordRestore(privateSession = false)

    private fun checkPasswordRestore(privateSession: Boolean) {
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            ArcileTestTheme {
                ArchivePasswordDialog({}, {}, ArchiveNameEncoding.UTF_8, false, {}, privateSession)
            }
        }
        val secret = "private-archive-secret"
        composeRule.onNode(hasSetTextAction()).performTextInput(secret)
        restoration.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithContentDescription("Show password").performClick()
        val restored = composeRule.onNode(hasSetTextAction()).fetchSemanticsNode()
            .config[SemanticsProperties.EditableText].text
        assertEquals(if (privateSession) "" else secret, restored)
    }

    @Test
    fun `private archive creation name is excluded from saved state`() {
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            ArcileTestTheme {
                CreateArchiveDialog(
                    "", 1, "Vault/docs", emptySet(), {}, { _, _, _, _ -> },
                    formats = listOf(dev.qtremors.arcile.core.storage.domain.ArchiveFormat.ZIP),
                    privateSession = true
                )
            }
        }
        composeRule.onNode(hasSetTextAction()).performTextInput("private-output-name")
        restoration.emulateSavedInstanceStateRestore()
        assertEquals("", composeRule.onNode(hasSetTextAction()).fetchSemanticsNode()
            .config[SemanticsProperties.EditableText].text)
    }

    @Test
    fun `protected screen uses the display name and blocks extraction when the listing is incomplete`() {
        var extracts = 0
        var opened = "unset"
        composeRule.setContent {
            ArcileTestTheme {
                ArchiveViewerScreen(
                    ArchiveViewerState(
                        archivePath = "vault:opaque-reference", displayName = "Protected.zip", privateSession = true,
                        currentPrefix = "docs", selectedItems = setOf("docs/item.txt"),
                        isLoading = false, extractionEnabled = false, notice = "Listing limit reached"
                    ),
                    ArchiveNavigationActions({}, { false }, { opened = it }, {}),
                    ArchiveExtractionActions({ extracts++ }, { extracts++ }, {}, {}, {}, {}, {}, {}),
                    ArchiveConflictActions({ _, _ -> }, {}, {}, {}),
                    ArchiveSelectionActions({}, {}, { extracts++ }, {})
                )
            }
        }
        composeRule.onNodeWithContentDescription("Extract archive").assertIsNotEnabled()
        composeRule.onNodeWithText("Listing limit reached").assertExists()
        composeRule.onNodeWithText("Root").performClick()
        composeRule.runOnIdle {
            assertEquals("", opened)
            assertEquals(0, extracts)
        }
    }
}
