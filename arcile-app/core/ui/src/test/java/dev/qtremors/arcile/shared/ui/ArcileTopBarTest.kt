package dev.qtremors.arcile.shared.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.qtremors.arcile.testutil.ArcileTestTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import dev.qtremors.arcile.core.ui.R

@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ArcileTopBarTest {

    @get:Rule
    val composeRule = createComposeRule()

    @org.junit.Ignore("Outdated UI test, dropdown interactions fail in Robolectric after 0.6.0")
    @Test
    fun `overflow menu dispatches grid view action`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var selectedAction: TopBarAction? = null

        composeRule.setContent {
            ArcileTestTheme {
                ArcileTopBar(
                    title = "Files",
                    showGridViewAction = true,
                    onClearSelection = {},
                    onSearchClick = {},
                    onSortClick = {},
                    onActionSelected = { selectedAction = it }
                )
            }
        }

        composeRule.onNodeWithContentDescription(context.getString(R.string.action_more_options)).performClick()
        composeRule.waitForIdle()
        composeRule.onNode(androidx.compose.ui.test.hasText("Grid View") or androidx.compose.ui.test.hasText("Grid View", ignoreCase = true), useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        assertEquals(TopBarAction.GridView, selectedAction)
    }

}

