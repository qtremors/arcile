package dev.qtremors.arcile.presentation.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.qtremors.arcile.core.privilege.ApplicationAccessCheck
import dev.qtremors.arcile.core.privilege.ApplicationAccessReadiness
import dev.qtremors.arcile.core.privilege.PrivilegeBackendId
import dev.qtremors.arcile.core.privilege.PrivilegeConnectionState
import dev.qtremors.arcile.core.privilege.PrivilegeFailure
import dev.qtremors.arcile.core.privilege.PrivilegeMode
import dev.qtremors.arcile.core.ui.testing.ArcileTestTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
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

    @Test
    fun `connecting Root shows progress without permission actions`() {
        render(
            ApplicationAccessReadiness.Connecting(
                preferredMode = PrivilegeMode.ROOT,
                normalAccessReady = false,
                backend = PrivilegeBackendId.ROOT,
                check = ApplicationAccessCheck.INITIALIZING
            )
        )

        composeRule.onNodeWithText("Connecting to storage").assertExists()
        composeRule.onNodeWithText("Connecting to the authorized Root service…").assertExists()
        composeRule.onNodeWithText("Grant Permission").assertDoesNotExist()
        composeRule.onNodeWithText("Reconnect").assertDoesNotExist()
    }

    @Test
    fun `connecting Shizuku identifies provider`() {
        render(
            ApplicationAccessReadiness.Connecting(
                preferredMode = PrivilegeMode.SHIZUKU,
                normalAccessReady = false,
                backend = PrivilegeBackendId.SHIZUKU,
                check = ApplicationAccessCheck.RECONNECTING
            )
        )

        composeRule.onNodeWithText("Connecting to the authorized Shizuku service…").assertExists()
    }

    @Test
    fun `Root failure offers reconnect and Normal fallback`() {
        render(
            ApplicationAccessReadiness.Failed(
                preferredMode = PrivilegeMode.ROOT,
                normalAccessReady = true,
                retainedBackend = PrivilegeBackendId.ROOT,
                failure = PrivilegeFailure.RootPermissionDenied(),
                backendState = PrivilegeConnectionState.PERMISSION_DENIED
            )
        )

        composeRule.onNodeWithText("Storage access was interrupted").assertExists()
        composeRule.onNodeWithText("Reconnect").assertExists()
        composeRule.onNodeWithText("Use Normal").assertExists()
        composeRule.onNodeWithText("Grant Permission").assertDoesNotExist()
    }

    @Test
    fun `stopped Shizuku gives useful recovery text`() {
        render(
            ApplicationAccessReadiness.Failed(
                preferredMode = PrivilegeMode.SHIZUKU,
                normalAccessReady = false,
                retainedBackend = PrivilegeBackendId.SHIZUKU,
                failure = PrivilegeFailure.ShizukuNotRunning(),
                backendState = PrivilegeConnectionState.INSTALLED_BUT_STOPPED
            )
        )

        composeRule.onNodeWithText(
            "Shizuku is not running. Start it, then reconnect, or use Normal Android access."
        ).assertExists()
    }

    @Test
    fun `Normal failure asks for Android permission only`() {
        render(
            ApplicationAccessReadiness.Failed(
                preferredMode = PrivilegeMode.NORMAL,
                normalAccessReady = false,
                retainedBackend = PrivilegeBackendId.NORMAL,
                failure = PrivilegeFailure.Failed("Normal access missing"),
                backendState = PrivilegeConnectionState.PERMISSION_REQUIRED
            )
        )

        composeRule.onNodeWithText("Grant Permission").assertExists()
        composeRule.onNodeWithText("Use Normal").assertDoesNotExist()
        composeRule.onNodeWithText("Reconnect").assertDoesNotExist()
    }

    @Test
    fun `reconnect action is forwarded once`() {
        var reconnects = 0
        render(
            readiness = failedRoot(),
            onReconnect = { reconnects += 1 }
        )

        composeRule.onNodeWithText("Reconnect").performClick()

        assertEquals(1, reconnects)
    }

    @Test
    fun `Use Normal action is forwarded once`() {
        var fallbacks = 0
        render(
            readiness = failedRoot(),
            onUseNormal = { fallbacks += 1 }
        )

        composeRule.onNodeWithText("Use Normal").performClick()

        assertEquals(1, fallbacks)
    }

    @Test
    fun `grant permission action is forwarded once`() {
        var grants = 0
        composeRule.setContent {
            ArcileTestTheme {
                PermissionRequestScreen(onRequestPermission = { grants += 1 })
            }
        }

        composeRule.onNodeWithText("Grant Permission").performClick()

        assertEquals(1, grants)
    }

    private fun render(
        readiness: ApplicationAccessReadiness,
        onReconnect: () -> Unit = {},
        onUseNormal: () -> Unit = {}
    ) {
        composeRule.setContent {
            ArcileTestTheme {
                PermissionRequestScreen(
                    onRequestPermission = {},
                    readiness = readiness,
                    onReconnect = onReconnect,
                    onUseNormal = onUseNormal
                )
            }
        }
    }

    private fun failedRoot() = ApplicationAccessReadiness.Failed(
        preferredMode = PrivilegeMode.ROOT,
        normalAccessReady = true,
        retainedBackend = PrivilegeBackendId.ROOT,
        failure = PrivilegeFailure.BackendDisconnected(),
        backendState = PrivilegeConnectionState.DISCONNECTED
    )
}
