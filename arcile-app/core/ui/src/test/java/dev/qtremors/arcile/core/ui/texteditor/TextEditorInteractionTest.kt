package dev.qtremors.arcile.core.ui.texteditor

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.testing.ArcileTestTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TextEditorInteractionTest {
    @get:Rule val composeRule = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun owner() = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }

    @Test fun `reverse selection formatting participates in ordinary undo and redo`() {
        val owner = owner()
        val reference = "formatting-${java.util.UUID.randomUUID()}"
        composeRule.setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                ArcileTestTheme { StandaloneTextEditor(reference, "Markdown", 16, true, true,
                    onNavigateBack = {}, onShare = {}, onOpenWith = {}, loadContent = { "abc selected xyz" }) }
            }
        }
        composeRule.waitUntil(10_000) { composeRule.onAllNodesWithText("abc selected xyz").fetchSemanticsNodes().isNotEmpty() }
        val session = ViewModelProvider(owner)["text-editor:$reference", TextEditorSession::class.java]
        composeRule.runOnIdle { session.text = TextFieldValue("abc selected xyz", TextRange(12, 4)) }
        composeRule.onNodeWithContentDescription(context.getString(R.string.text_editor_format_bold)).performClick()
        composeRule.onNodeWithText("abc **selected** xyz").assertExists()
        composeRule.onNodeWithContentDescription(context.getString(R.string.text_editor_undo)).performClick()
        composeRule.runOnIdle { assertEquals(TextRange(12, 4), session.text.selection) }
        composeRule.onNodeWithText("abc selected xyz").assertExists()
        composeRule.onNodeWithContentDescription(context.getString(R.string.text_editor_redo)).performClick()
        composeRule.onNodeWithText("abc **selected** xyz").assertExists()
        composeRule.runOnIdle { owner.viewModelStore.clear() }
        clearDraft(context, reference)
    }

    @Test fun `oversized load shows useful feedback and offers another app`() {
        val owner = owner()
        var opened = false
        composeRule.setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                ArcileTestTheme { StandaloneTextEditor("oversized-${java.util.UUID.randomUUID()}", "Large", 0, false, false,
                    onNavigateBack = {}, onShare = {}, onOpenWith = { opened = true },
                    loadContent = { "a".repeat(MAX_TEXT_BYTES + 1) }) }
            }
        }
        val message = context.getString(R.string.text_editor_too_large)
        composeRule.waitUntil(10_000) { composeRule.onAllNodesWithText(message).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText(message).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.open_app)).performClick()
        assertTrue(opened)
        composeRule.runOnIdle { owner.viewModelStore.clear() }
    }
}
