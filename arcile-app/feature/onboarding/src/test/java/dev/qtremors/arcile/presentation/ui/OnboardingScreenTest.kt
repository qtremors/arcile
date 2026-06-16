package dev.qtremors.arcile.presentation.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import dev.qtremors.arcile.feature.onboarding.OnboardingStep
import dev.qtremors.arcile.feature.onboarding.OnboardingUiState
import dev.qtremors.arcile.feature.onboarding.ui.OnboardingRestoreItem
import dev.qtremors.arcile.feature.onboarding.ui.OnboardingRestoreState
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
    fun `first launch shows merged info page without skip or restore header`() {
        composeRule.setContent {
            ArcileTestTheme {
                OnboardingScreen(
                    state = OnboardingUiState(),
                    currentThemeState = ThemeState(),
                    onThemeChange = {},
                    onNext = {},
                    onBack = {},
                    onStepSelected = {},
                    onOpenStoragePermissionSettings = {},
                    onRequestNotificationPermission = {}
                )
            }
        }
        
        composeRule.onNodeWithText("Welcome to Arcile").assertExists()
        composeRule.onAllNodesWithText("Multi-Volume Support").assertCountEquals(2)
        composeRule.onNodeWithText("Offline by Design").assertExists()
        composeRule.onNodeWithText("Restore").assertDoesNotExist()
        composeRule.onNodeWithText("Skip").assertDoesNotExist()
        composeRule.onAllNodesWithTag("onboardingStepIndicator").assertCountEquals(2)
    }

    @Test
    fun `restore preview shows backed up preference groups`() {
        composeRule.setContent {
            ArcileTestTheme {
                OnboardingScreen(
                    state = OnboardingUiState(step = OnboardingStep.SetupPermissions),
                    currentThemeState = ThemeState(),
                    onThemeChange = {},
                    onNext = {},
                    onBack = {},
                    onStepSelected = {},
                    onOpenStoragePermissionSettings = {},
                    onRequestNotificationPermission = {},
                    restoreState = OnboardingRestoreState.Preview(
                        items = listOf(
                            OnboardingRestoreItem("Theme and appearance", "Will restore"),
                            OnboardingRestoreItem("Home tools", "Will reset")
                        )
                    )
                )
            }
        }

        composeRule.onNodeWithText("Restore this backup?").assertExists()
        composeRule.onNodeWithText("Theme and appearance").assertExists()
        composeRule.onNodeWithText("Will restore").assertExists()
        composeRule.onNodeWithText("Home tools").assertExists()
        composeRule.onNodeWithText("Will reset").assertExists()
    }

    @Test
    fun `setup page includes restore theme storage and notification controls`() {
        composeRule.setContent {
            ArcileTestTheme {
                OnboardingScreen(
                    state = OnboardingUiState(
                        step = OnboardingStep.SetupPermissions,
                        notificationPermissionRequired = true
                    ),
                    currentThemeState = ThemeState(),
                    onThemeChange = {},
                    onNext = {},
                    onBack = {},
                    onStepSelected = {},
                    onOpenStoragePermissionSettings = {},
                    onRequestNotificationPermission = {}
                )
            }
        }

        composeRule.onNodeWithText("Storage Access (Required)").assertExists()
        composeRule.onNodeWithText("Restore from file").assertExists()
        composeRule.onNodeWithText("Theme Mode").assertExists()
        composeRule.onNodeWithText("Accent Color").assertExists()
        composeRule.onNodeWithText("Enable").assertExists()
        composeRule.onNodeWithText("Back").assertExists()
        composeRule.onNodeWithText("Skip").assertDoesNotExist()
        composeRule.onNodeWithText("Finish").assertIsNotEnabled()
    }

    @Test
    fun `storage permission step enables finish after permission is granted`() {
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
                    onStepSelected = {},
                    onOpenStoragePermissionSettings = {},
                    onRequestNotificationPermission = {}
                )
            }
        }

        composeRule.onNodeWithText("Storage Access Granted").assertExists()
        composeRule.onNodeWithText("Enable").assertExists()
        composeRule.onNodeWithText("Finish").assertIsEnabled()
    }
}
