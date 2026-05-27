package dev.qtremors.arcile.presentation.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.qtremors.arcile.feature.onboarding.OnboardingStep
import dev.qtremors.arcile.feature.onboarding.OnboardingUiState
import dev.qtremors.arcile.feature.onboarding.ui.OnboardingScreen
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
                    onStepSelected = {},
                    onOpenStoragePermissionSettings = {},
                    onRequestNotificationPermission = {}
                )
            }
        }

        composeRule.onNodeWithText("Welcome to Arcile").assertExists()

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
                    onSkip = { state = state.copy(step = OnboardingStep.SetupPermissions, skipMode = true) },
                    onStepSelected = {},
                    onOpenStoragePermissionSettings = {},
                    onRequestNotificationPermission = {}
                )
            }
        }

        composeRule.onNodeWithText("Skip").performClick()

        composeRule.onNodeWithText("Allow file access").assertExists()
        composeRule.onNodeWithText("Keep file operations visible").assertExists()
        composeRule.onNodeWithText("Privacy Policy & Terms").assertDoesNotExist()
        composeRule.onNodeWithText("Finish").assertIsNotEnabled()
    }

    @Test
    fun `theme step renders accent picker trigger`() {
        composeRule.setContent {
            ArcileTestTheme {
                OnboardingScreen(
                    state = OnboardingUiState(step = OnboardingStep.Theme),
                    currentThemeState = ThemeState(),
                    onThemeChange = {},
                    onNext = {},
                    onBack = {},
                    onSkip = {},
                    onStepSelected = {},
                    onOpenStoragePermissionSettings = {},
                    onRequestNotificationPermission = {}
                )
            }
        }

        composeRule.onNodeWithText("Make it comfortable").assertExists()
        composeRule.onNodeWithText("Theme Mode").assertExists()
        composeRule.onNodeWithText("Accent Color").assertExists()
        composeRule.onNodeWithText("Select Accent Color").assertDoesNotExist()
        composeRule.onNodeWithText("Back").assertExists()
        composeRule.onNodeWithText("Skip").assertExists()
    }

    @Test
    fun `storage permission step enables continue after permission is granted`() {
        composeRule.setContent {
            ArcileTestTheme {
                OnboardingScreen(
                    state = OnboardingUiState(
                        step = OnboardingStep.SetupPermissions,
                        hasStoragePermission = true,
                        notificationPermissionRequired = true
                    ),
                    currentThemeState = ThemeState(),
                    onThemeChange = {},
                    onNext = {},
                    onBack = {},
                    onSkip = {},
                    onStepSelected = {},
                    onOpenStoragePermissionSettings = {},
                    onRequestNotificationPermission = {}
                )
            }
        }

        composeRule.onNodeWithText("Storage access is ready").assertExists()
        composeRule.onNodeWithText("Enable notifications").assertExists()
        composeRule.onNodeWithText("Finish").assertIsEnabled()
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
