package dev.qtremors.arcile.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.qtremors.arcile.presentation.ui.components.EmptyState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun emptyState_rendersCorrectly() {
        composeTestRule.setContent {
            EmptyState(
                icon = Icons.Default.Warning,
                title = "Test Title",
                description = "Test Description"
            )
        }

        composeTestRule.onNodeWithText("Test Title").assertIsDisplayed()
        composeTestRule.onNodeWithText("Test Description").assertIsDisplayed()
    }
}
