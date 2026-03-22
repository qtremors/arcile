package dev.qtremors.arcile.presentation.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.test.junit4.createComposeRule
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
import dev.qtremors.arcile.R

@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ArcileTopBarTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `selection mode shows selection actions including rename for single item`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var selectedAction: TopBarAction? = null
        var cleared = false

        composeRule.setContent {
            ArcileTestTheme {
                ArcileTopBar(
                    title = "Files",
                    selectionCount = 1,
                    onClearSelection = {},
                    onSearchClick = {},
                    onSortClick = {},
                    onActionSelected = { selectedAction = it }
                )
            }
        }

        composeRule.onNodeWithText("1 selected").assertExists()
        composeRule.onNodeWithContentDescription(context.getString(R.string.action_rename)).performClick()
        assertEquals(TopBarAction.Rename, selectedAction)

        composeRule.onNodeWithContentDescription(context.getString(R.string.clear_selection)).assertExists()
    }

    @Test
    fun `clipboard tray exposes cancel and paste actions`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var cancelCount = 0
        var pasteCount = 0

        composeRule.setContent {
            ArcileTestTheme {
                ArcileTopBar(
                    title = "Files",
                    hasClipboardItems = true,
                    onClearSelection = {},
                    onSearchClick = {},
                    onSortClick = {},
                    onPasteClick = { pasteCount++ },
                    onCancelPaste = { cancelCount++ },
                    onActionSelected = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription(context.getString(R.string.action_paste_here)).performClick()
        composeRule.onNodeWithContentDescription(context.getString(R.string.action_cancel_transfer)).performClick()

        assertEquals(1, pasteCount)
        assertEquals(1, cancelCount)
    }

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
        composeRule.onNodeWithText("Grid View").performClick()

        assertEquals(TopBarAction.GridView, selectedAction)
    }
}

