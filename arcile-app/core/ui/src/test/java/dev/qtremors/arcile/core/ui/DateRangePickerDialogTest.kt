package dev.qtremors.arcile.core.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.WindowSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.testing.ArcileTestTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DateRangePickerDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `search and sort dialogs stay usable in 320 dp landscape with large text and IME`() {
        verifyBothDialogs(
            windowSize = DpSize(width = 480.dp, height = 320.dp),
            imeInset = 48.dp
        )
    }

    @Test
    fun `search and sort dialogs stay usable in 480 dp portrait with large text and IME`() {
        verifyBothDialogs(
            windowSize = DpSize(width = 320.dp, height = 480.dp),
            imeInset = 96.dp
        )
    }

    private fun verifyBothDialogs(windowSize: DpSize, imeInset: Dp) {
        var screen by mutableStateOf(DialogUnderTest.Search)
        var searchConfirmations = 0
        var sortChanges = 0

        composeRule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.WindowSize(windowSize) then
                    DeviceConfigurationOverride.FontScale(2f)
            ) {
                ArcileTestTheme {
                    when (screen) {
                        DialogUnderTest.Search -> SearchDateRangeDialog(
                            initialStartMillis = null,
                            initialEndMillis = null,
                            onDismiss = {},
                            onConfirm = { _, _ ->
                                searchConfirmations++
                                screen = DialogUnderTest.Sort
                            },
                            contentInsets = WindowInsets(bottom = imeInset)
                        )

                        DialogUnderTest.Sort -> SortDateRangeSection(
                            minDateMillis = null,
                            maxDateMillis = null,
                            onDateRangeChange = { _, _ -> sortChanges++ },
                            dialogContentInsets = WindowInsets(bottom = imeInset)
                        )
                    }
                }
            }
        }

        assertDialogFieldsAndActionsReachable()
        composeRule.onNodeWithText("OK").performClick()
        composeRule.runOnIdle { assertEquals(1, searchConfirmations) }

        composeRule.onNodeWithText("Select range", substring = true).performClick()
        assertDialogFieldsAndActionsReachable()
        composeRule.onNodeWithText("OK").performClick()
        composeRule.runOnIdle { assertEquals(1, sortChanges) }
    }

    private fun assertDialogFieldsAndActionsReachable() {
        composeRule.onNodeWithTag(DateRangeDialogTestTags.Actions).assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").assertIsDisplayed()
        composeRule.onNodeWithText("OK").assertIsDisplayed()

        val inputFields = composeRule.onAllNodes(hasSetTextAction(), useUnmergedTree = true)
        assertEquals(2, inputFields.fetchSemanticsNodes().size)
        repeat(2) { index ->
            inputFields[index].performScrollTo().assertIsDisplayed()
        }

        composeRule.onNodeWithTag(DateRangeDialogTestTags.Actions).assertIsDisplayed()
    }

    private enum class DialogUnderTest {
        Search,
        Sort
    }
}
