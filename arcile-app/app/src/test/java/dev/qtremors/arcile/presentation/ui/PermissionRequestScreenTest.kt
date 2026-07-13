package dev.qtremors.arcile.presentation.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import dev.qtremors.arcile.core.ui.testing.ArcileTestTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PermissionRequestScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `completed onboarding with missing storage shows recovery permission screen`() {
        composeRule.setContent {
            ArcileTestTheme {
                PermissionRequestScreen(onRequestPermission = {})
            }
        }

        composeRule.onNodeWithText("Storage access is off").assertExists()
        composeRule.onNodeWithText("Grant Permission").assertExists()
    }
}
