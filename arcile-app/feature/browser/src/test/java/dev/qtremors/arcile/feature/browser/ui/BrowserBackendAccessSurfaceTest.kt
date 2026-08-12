package dev.qtremors.arcile.feature.browser.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.qtremors.arcile.core.privilege.PrivilegeBackendId
import dev.qtremors.arcile.core.privilege.PrivilegeConnectionState
import dev.qtremors.arcile.core.privilege.PrivilegeFailure
import dev.qtremors.arcile.core.ui.testing.ArcileTestTheme
import dev.qtremors.arcile.feature.browser.BrowserAccessLossReason
import dev.qtremors.arcile.feature.browser.BrowserBackendAccessState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BrowserBackendAccessSurfaceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `available state renders no blocking content`() {
        render(BrowserBackendAccessState.Available)

        composeRule.onNodeWithText("Protected storage access was lost").assertDoesNotExist()
        composeRule.onNodeWithText("Reconnect").assertDoesNotExist()
    }

    @Test
    fun `lost Root state offers reconnect and Normal fallback`() {
        render(lost(PrivilegeBackendId.ROOT))

        composeRule.onNodeWithText("Protected storage access was lost").assertExists()
        composeRule.onNodeWithText("Your current Root location and browser state are preserved.")
            .assertExists()
        composeRule.onNodeWithText("Reconnect").assertExists()
        composeRule.onNodeWithText("Use Normal").assertExists()
    }

    @Test
    fun `lost Shizuku state names retained provider`() {
        render(lost(PrivilegeBackendId.SHIZUKU))

        composeRule.onNodeWithText("Your current Shizuku location and browser state are preserved.")
            .assertExists()
    }

    @Test
    fun `reconnect action is forwarded`() {
        var reconnects = 0
        render(lost(PrivilegeBackendId.ROOT), onReconnect = { reconnects += 1 })

        composeRule.onNodeWithText("Reconnect").performClick()

        assertEquals(1, reconnects)
    }

    @Test
    fun `Normal fallback action is forwarded`() {
        var fallbacks = 0
        render(lost(PrivilegeBackendId.ROOT), onUseNormal = { fallbacks += 1 })

        composeRule.onNodeWithText("Use Normal").performClick()

        assertEquals(1, fallbacks)
    }

    @Test
    fun `pending fallback requiring permission offers grant and provider return`() {
        render(
            lost(
                backendId = PrivilegeBackendId.SHIZUKU,
                normalState = PrivilegeConnectionState.PERMISSION_REQUIRED,
                fallbackPending = true
            )
        )

        composeRule.onNodeWithText("Normal storage permission is required").assertExists()
        composeRule.onNodeWithText("Grant Normal access").assertExists()
        composeRule.onNodeWithText("Return to provider").assertExists()
        composeRule.onNodeWithText("Use Normal").assertDoesNotExist()
    }

    @Test
    fun `revoked Normal permission offers grant without a duplicate Normal fallback`() {
        render(
            lost(
                backendId = PrivilegeBackendId.NORMAL,
                normalState = PrivilegeConnectionState.PERMISSION_REQUIRED
            )
        )

        composeRule.onNodeWithText("Normal storage permission is required").assertExists()
        composeRule.onNodeWithText("Grant Normal access").assertExists()
        composeRule.onNodeWithText("Reconnect").assertExists()
        composeRule.onNodeWithText("Use Normal").assertDoesNotExist()
        composeRule.onNodeWithText("Return to provider").assertDoesNotExist()
    }

    @Test
    fun `Normal reconnecting state hides duplicate fallback action`() {
        render(
            BrowserBackendAccessState.Reconnecting(
                backendId = PrivilegeBackendId.NORMAL,
                normalFallbackPending = false
            )
        )

        composeRule.onNodeWithText("Use Normal").assertDoesNotExist()
    }

    @Test
    fun `grant Normal action is forwarded`() {
        var grants = 0
        render(
            state = lost(
                normalState = PrivilegeConnectionState.PERMISSION_REQUIRED,
                fallbackPending = true
            ),
            onGrantNormal = { grants += 1 }
        )

        composeRule.onNodeWithText("Grant Normal access").performClick()

        assertEquals(1, grants)
    }

    @Test
    fun `reconnecting provider shows progress and Normal alternative`() {
        render(
            BrowserBackendAccessState.Reconnecting(
                backendId = PrivilegeBackendId.ROOT,
                normalFallbackPending = false
            )
        )

        composeRule.onNodeWithText("Reconnecting to protected storage").assertExists()
        composeRule.onNodeWithText("Waiting for the Root service…").assertExists()
        composeRule.onNodeWithText("Use Normal").assertExists()
    }

    @Test
    fun `pending Normal reconnect hides duplicate fallback action`() {
        render(
            BrowserBackendAccessState.Reconnecting(
                backendId = PrivilegeBackendId.ROOT,
                normalFallbackPending = true
            )
        )

        composeRule.onNodeWithText("Waiting for Normal Android storage access…").assertExists()
        composeRule.onNodeWithText("Use Normal").assertDoesNotExist()
    }

    @Test
    fun `action failure is shown without replacing recovery actions`() {
        render(
            lost().copy(
                actionFailure = PrivilegeFailure.ConnectionTimedOut()
            )
        )

        composeRule.onNodeWithText("The privileged service connection timed out").assertExists()
        composeRule.onNodeWithText("Reconnect").assertExists()
    }

    private fun render(
        state: BrowserBackendAccessState,
        onReconnect: () -> Unit = {},
        onUseNormal: () -> Unit = {},
        onGrantNormal: () -> Unit = {}
    ) {
        composeRule.setContent {
            ArcileTestTheme {
                BrowserBackendAccessSurface(
                    state = state,
                    onReconnect = onReconnect,
                    onUseNormal = onUseNormal,
                    onGrantNormalAccess = onGrantNormal
                )
            }
        }
    }

    private fun lost(
        backendId: PrivilegeBackendId = PrivilegeBackendId.ROOT,
        normalState: PrivilegeConnectionState = PrivilegeConnectionState.READY,
        fallbackPending: Boolean = false
    ) = BrowserBackendAccessState.Lost(
        backendId = backendId,
        reason = BrowserAccessLossReason.CONNECTION_FAILED,
        failure = PrivilegeFailure.BackendDisconnected(),
        normalState = normalState,
        normalFallbackPending = fallbackPending
    )
}
