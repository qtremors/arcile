package dev.qtremors.arcile.presentation.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.qtremors.arcile.presentation.onboarding.OnboardingStep
import dev.qtremors.arcile.presentation.onboarding.OnboardingUiState
import dev.qtremors.arcile.testutil.ArcileTestTheme
import dev.qtremors.arcile.ui.theme.ThemeState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OnboardingScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `first launch shows onboarding welcome`() {
        composeRule.setContent {
            ArcileTestTheme {
                OnboardingScreen(
                    state = OnboardingUiState(),
                    currentThemeState = ThemeState(),
                    onThemeChange = {},
                    onNext = {},
                    onBack = {},
                    onSkip = {},
                    onOpenStoragePermissionSettings = {},
                    onRequestNotificationPermission = {}
                )
            }
        }

        composeRule.onNodeWithText("Welcome to Arcile").assertExists()
        composeRule.onNodeWithText("Step 1 of 5").assertExists()
        composeRule.onNodeWithText("Skip").assertExists()
    }

    @Test
    fun `skip hides education and moves to storage permission`() {
        var state by mutableStateOf(OnboardingUiState())

        composeRule.setContent {
            ArcileTestTheme {
                OnboardingScreen(
                    state = state,
                    currentThemeState = ThemeState(),
                    onThemeChange = {},
                    onNext = {},
                    onBack = {},
                    onSkip = { state = state.copy(step = OnboardingStep.StoragePermission, skipMode = true) },
                    onOpenStoragePermissionSettings = {},
                    onRequestNotificationPermission = {}
                )
            }
        }

        composeRule.onNodeWithText("Skip").performClick()

        composeRule.onNodeWithText("Allow file access").assertExists()
        composeRule.onNodeWithText("Step 4 of 5").assertExists()
        composeRule.onNodeWithText("Built for real file work").assertDoesNotExist()
        composeRule.onNodeWithText("Continue").assertIsNotEnabled()
    }

    @Test
    fun `theme step renders existing theme controls`() {
        composeRule.setContent {
            ArcileTestTheme {
                OnboardingScreen(
                    state = OnboardingUiState(step = OnboardingStep.Theme),
                    currentThemeState = ThemeState(),
                    onThemeChange = {},
                    onNext = {},
                    onBack = {},
                    onSkip = {},
                    onOpenStoragePermissionSettings = {},
                    onRequestNotificationPermission = {}
                )
            }
        }

        composeRule.onNodeWithText("Make it comfortable").assertExists()
        composeRule.onNodeWithText("Step 3 of 5").assertExists()
        composeRule.onNodeWithText("Theme Mode").assertExists()
        composeRule.onNodeWithText("Accent Color").assertExists()
    }

    @Test
    fun `storage permission step enables continue after permission is granted`() {
        composeRule.setContent {
            ArcileTestTheme {
                OnboardingScreen(
                    state = OnboardingUiState(
                        step = OnboardingStep.StoragePermission,
                        hasStoragePermission = true
                    ),
                    currentThemeState = ThemeState(),
                    onThemeChange = {},
                    onNext = {},
                    onBack = {},
                    onSkip = {},
                    onOpenStoragePermissionSettings = {},
                    onRequestNotificationPermission = {}
                )
            }
        }

        composeRule.onNodeWithText("Storage access is ready").assertExists()
        composeRule.onNodeWithText("Continue").assertIsEnabled()
    }

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
