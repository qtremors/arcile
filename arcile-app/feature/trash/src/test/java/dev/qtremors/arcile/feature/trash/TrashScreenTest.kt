package dev.qtremors.arcile.feature.trash

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import dev.qtremors.arcile.feature.trash.TrashState
import dev.qtremors.arcile.testutil.ArcileTestTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TrashScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `empty trash renders trash empty state`() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            ArcileTestTheme {
                TrashScreen(
                    state = TrashState(isLoading = false),
                    navigationActions = testNavigationActions(),
                    selectionActions = testSelectionActions(),
                    restoreActions = testRestoreActions(),
                    deleteActions = testDeleteActions(),
                    presentationActions = testPresentationActions(),
                    feedbackActions = testFeedbackActions()
                )
            }
        }

        composeRule.onNodeWithText("Trash is empty").assertExists()
    }

    @Test
    fun `empty trash search renders search empty state`() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            ArcileTestTheme {
                TrashScreen(
                    state = TrashState(
                        isLoading = false,
                        searchQuery = "invoice",
                        searchResults = emptyList(),
                        isSearching = false
                    ),
                    navigationActions = testNavigationActions(),
                    selectionActions = testSelectionActions(),
                    restoreActions = testRestoreActions(),
                    deleteActions = testDeleteActions(),
                    presentationActions = testPresentationActions(),
                    feedbackActions = testFeedbackActions()
                )
            }
        }

        composeRule.onNodeWithText("No results found").assertExists()
    }
}

private fun testNavigationActions() = TrashNavigationActions {}

private fun testSelectionActions() = TrashSelectionActions({}, {}, {}, {}, {})

private fun testRestoreActions() = TrashRestoreActions({}, {}, { _, _ -> }, {}, {})

private fun testDeleteActions() = TrashDeleteActions({}, {}, {})

private fun testPresentationActions() = TrashPresentationActions({}, {}, {}, {}, null)

private fun testFeedbackActions() = TrashFeedbackActions({}, {}, {})
