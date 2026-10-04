package dev.qtremors.arcile.core.ui.category

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import dev.qtremors.arcile.core.storage.domain.ClipboardOperation
import dev.qtremors.arcile.core.storage.domain.ClipboardState
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.ui.testing.ArcileTestTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CategoryClipboardToolbarTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `paste status announces ready items and keeps contents and paste actions accessible`() {
        val files = listOf(FileModel("a.txt", "/a.txt"), FileModel("b.txt", "/b.txt"))
        val state = mutableStateOf(CategoryFileActionState(clipboardState = ClipboardState(ClipboardOperation.COPY, files.take(1))))
        var contentsOpened = false
        var pasted = false
        composeRule.setContent {
            ArcileTestTheme {
                CategoryClipboardToolbar(state.value, true, { pasted = true }, {}, { contentsOpened = true })
            }
        }
        composeRule.onNodeWithContentDescription("1 item ready to paste").assertIsDisplayed().performClick()
        assertTrue(contentsOpened)
        composeRule.runOnIdle { state.value = state.value.copy(clipboardState = ClipboardState(ClipboardOperation.COPY, files)) }
        composeRule.onNodeWithContentDescription("2 items ready to paste").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Paste here").performClick()
        assertTrue(pasted)
    }
}
