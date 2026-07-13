package dev.qtremors.arcile.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import dev.qtremors.arcile.core.ui.testing.ArcileTestTheme
import dev.qtremors.arcile.core.ui.theme.LocalReducedMotionEnabled
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EmptyStateTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `empty state renders title description and custom action slot`() {
        composeRule.setContent {
            ArcileTestTheme {
                CompositionLocalProvider(LocalReducedMotionEnabled provides true) {
                    EmptyState(
                        icon = Icons.Default.Info,
                        title = "Nothing here",
                        description = "Try adding an item first.",
                        action = { Box { Text("Retry") } }
                    )
                }
            }
        }

        composeRule.onNodeWithText("Nothing here").assertExists()
        composeRule.onNodeWithText("Try adding an item first.").assertExists()
        composeRule.onNodeWithText("Retry").assertExists()
    }

    @Test
    fun `folder variant renders localized default copy`() {
        composeRule.setContent {
            ArcileTestTheme {
                CompositionLocalProvider(LocalReducedMotionEnabled provides true) {
                    EmptyState(variant = EmptyStateVariant.Folder)
                }
            }
        }

        composeRule.onNodeWithText("Empty Directory").assertExists()
        composeRule.onNodeWithText("This folder doesn't have any files yet. You can create one using the + button.").assertExists()
    }

    @Test
    fun `archive variant renders localized default copy`() {
        composeRule.setContent {
            ArcileTestTheme {
                CompositionLocalProvider(LocalReducedMotionEnabled provides true) {
                    EmptyState(variant = EmptyStateVariant.Archive)
                }
            }
        }

        composeRule.onNodeWithText("Archive is empty").assertExists()
        composeRule.onNodeWithText("This archive does not contain any files or folders.").assertExists()
    }

    @Test
    fun `reduce motion override renders content immediately`() {
        composeRule.setContent {
            ArcileTestTheme {
                CompositionLocalProvider(LocalReducedMotionEnabled provides true) {
                    EmptyState(
                        variant = EmptyStateVariant.Search,
                        title = "No matches",
                        description = "Try another query."
                    )
                }
            }
        }

        composeRule.onNodeWithText("No matches").assertExists()
        composeRule.onNodeWithText("Try another query.").assertExists()
    }
}
