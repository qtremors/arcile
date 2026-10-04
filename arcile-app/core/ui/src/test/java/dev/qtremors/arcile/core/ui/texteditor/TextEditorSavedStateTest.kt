package dev.qtremors.arcile.core.ui.texteditor

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import dev.qtremors.arcile.core.ui.testing.ArcileTestTheme
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TextEditorSavedStateTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `reverting edits clears a matching draft without reviving old edits`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val reference = "reverted-document"
        val draft = File(context.noBackupFilesDir, "text_editor_drafts/${textContentHash(reference)}.draft")
        writeTextDraft(draft, "original", "complete edits")
        val owner = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
        composeRule.setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                ArcileTestTheme {
                    StandaloneTextEditor(reference, "Document", 3L, true, false,
                        onNavigateBack = {}, onShare = {}, onOpenWith = {}, loadContent = { "original" })
                }
            }
        }
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("complete edits").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("complete edits").performTextReplacement("original")
        composeRule.waitUntil(10_000) { !draft.exists() }
        composeRule.onNodeWithText("original").assertExists()
        composeRule.runOnIdle { owner.viewModelStore.clear() }
    }

    @Test
    fun `reopening a changed document offers its retained draft for explicit recovery`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val reference = "changed-document"
        val draft = File(context.noBackupFilesDir, "text_editor_drafts/${textContentHash(reference)}.draft")
        writeTextDraft(draft, "original", "complete edits")
        val owner = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
        composeRule.setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                ArcileTestTheme {
                    StandaloneTextEditor(reference, "Document", 3L, true, false,
                        onNavigateBack = {}, onShare = {}, onOpenWith = {},
                        loadContent = { "truncated" })
                }
            }
        }
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("Recover Draft").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals("complete edits", readTextDraft(draft)?.text)
        composeRule.onNode(hasText("Recover Draft") and hasClickAction()).performClick()
        composeRule.onNodeWithText("complete edits").assertExists()
        composeRule.runOnIdle {
            owner.viewModelStore.clear()
            draft.delete()
        }
    }

    @Test
    fun `document contents stay out of saved state and survive configuration restoration`() {
        verifySavedState(writable = false)
    }

    @Test
    fun `unsaved editable contents also stay out of saved state`() {
        verifySavedState(writable = true)
    }

    private fun verifySavedState(writable: Boolean) {
        val store = ViewModelStore()
        val owner = object : ViewModelStoreOwner { override val viewModelStore = store }
        val session = ViewModelProvider(store, ViewModelProvider.NewInstanceFactory())
            .get("text-editor:test-document", TextEditorSession::class.java)
        session.text = TextFieldValue("small")
        session.original = "original"
        session.initialized = true
        // Keep the loading surface visible so this test measures state saving,
        // without spending resources laying out a multi-megabyte text field.
        var registry: SaveableStateRegistry? = null
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                registry = LocalSaveableStateRegistry.current
                ArcileTestTheme {
                    StandaloneTextEditor("test-document", "Test", 0L, writable, false,
                        onNavigateBack = {}, onShare = {}, onOpenWith = {},
                        loadContent = { awaitCancellation() })
                }
            }
        }
        var smallSize = 0
        composeRule.runOnIdle {
            smallSize = requireNotNull(registry).performSave().toString().length
            session.text = TextFieldValue("edited".repeat(400_000))
            session.undo = listOf(TextFieldValue("previous".repeat(300_000)))
        }
        composeRule.runOnIdle {
            val largeSize = requireNotNull(registry).performSave().toString().length
            assertEquals(smallSize, largeSize)
            assertTrue(largeSize < 8192)
        }
        restoration.emulateSavedInstanceStateRestore()
        composeRule.runOnIdle {
            assertEquals(2_400_000, session.text.text.length)
            assertEquals(2_400_000, session.undo.single().text.length)
            store.clear()
            val restarted = ViewModelProvider(store, ViewModelProvider.NewInstanceFactory())
                .get("text-editor:test-document", TextEditorSession::class.java)
            assertTrue(!restarted.initialized)
            assertEquals("", restarted.text.text)
        }
    }
}
