package dev.qtremors.arcile.core.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.qtremors.arcile.core.ui.theme.LocalReducedMotionEnabled
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EmptyStateInstrumentedTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun emptyState_rendersCorrectly() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalReducedMotionEnabled provides true) {
                EmptyState(
                    icon = Icons.Default.Warning,
                    title = "Test Title",
                    description = "Test Description"
                )
            }
        }

        composeTestRule.onNodeWithText("Test Title").assertIsDisplayed()
        composeTestRule.onNodeWithText("Test Description").assertIsDisplayed()
    }
}
