package dev.qtremors.arcile.core.ui.texteditor

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.platform.LocalDensity
import dev.qtremors.arcile.core.ui.testing.ArcileTestTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w840dp-h1000dp")
class TextDocumentInfoSheetTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `short information fits its content and closes from its back button`() {
        var closed = false
        var density = 1f
        composeRule.setContent {
            density = LocalDensity.current.density
            ArcileTestTheme { TextDocumentInfoSheet("Notes.md", "/Download/Notes.md", 16, "hello world", { closed = true }) }
        }
        val bounds = composeRule.onNodeWithTag("text-document-info").fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.width <= 560f * density + 1f)
        assertTrue(bounds.height < 600f * density)
        composeRule.onNodeWithContentDescription("Back").performClick()
        assertTrue(closed)
    }

    @Test
    @Config(qualifiers = "w600dp-h480dp")
    fun `long paths scroll without hiding the close control in a short window`() {
        composeRule.setContent {
            ArcileTestTheme { TextDocumentInfoSheet("Notes.md", "/Download/" + "folder/".repeat(100), 16, "hello world", {}) }
        }
        composeRule.onNodeWithText("Characters").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").assertIsDisplayed()
    }
}
