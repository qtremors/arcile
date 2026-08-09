package dev.qtremors.arcile.core.ui.reorder

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import dev.qtremors.arcile.core.ui.testing.ArcileTestTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReorderControlsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `first and last positions disable their boundary actions`() {
        composeRule.setContent {
            ArcileTestTheme {
                ReorderControls(
                    itemLabel = "Downloads",
                    position = 0,
                    itemCount = 3,
                    onMoveUp = {},
                    onMoveDown = {}
                )
                ReorderControls(
                    itemLabel = "Music",
                    position = 2,
                    itemCount = 3,
                    onMoveUp = {},
                    onMoveDown = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription("Move Downloads up").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Move Downloads down").assertIsEnabled()
        composeRule.onNode(hasStateDescription("Downloads, position 1 of 3")).assertExists()
        composeRule.onNodeWithContentDescription("Move Music up").assertIsEnabled()
        composeRule.onNodeWithContentDescription("Move Music down").assertIsNotEnabled()
        composeRule.onNode(hasStateDescription("Music, position 3 of 3")).assertExists()
    }

    @Test
    fun `keyboard movement updates position and keeps logical focus`() {
        var reportedPosition = -1
        composeRule.setContent {
            ArcileTestTheme {
                var position by remember { mutableIntStateOf(1) }
                reportedPosition = position
                ReorderControls(
                    itemLabel = "Downloads",
                    position = position,
                    itemCount = 3,
                    onMoveUp = { position-- },
                    onMoveDown = { position++ }
                )
            }
        }

        composeRule
            .onNodeWithContentDescription("Move Downloads up")
            .performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule
            .onNodeWithContentDescription("Move Downloads up")
            .performKeyInput { pressKey(Key.Enter) }
        composeRule.waitForIdle()

        assertEquals(0, reportedPosition)
        composeRule.onNode(hasStateDescription("Downloads, position 1 of 3")).assertExists()
        composeRule.onNodeWithContentDescription("Move Downloads down").assertIsFocused()
    }
}
