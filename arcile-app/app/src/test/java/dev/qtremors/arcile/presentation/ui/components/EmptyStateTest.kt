package dev.qtremors.arcile.presentation.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import dev.qtremors.arcile.testutil.ArcileTestTheme
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
                EmptyState(
                    icon = Icons.Default.Info,
                    title = "Nothing here",
                    description = "Try adding an item first.",
                    action = { Box { Text("Retry") } }
                )
            }
        }

        composeRule.onNodeWithText("Nothing here").assertExists()
        composeRule.onNodeWithText("Try adding an item first.").assertExists()
        composeRule.onNodeWithText("Retry").assertExists()
    }
}
