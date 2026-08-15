package dev.qtremors.arcile.feature.settings.ui

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import dev.qtremors.arcile.core.ui.theme.ThemeState
import dev.qtremors.arcile.core.ui.testing.ArcileTestTheme
import dev.qtremors.arcile.core.privilege.PrivilegeBackendId
import dev.qtremors.arcile.core.privilege.PrivilegeBackendState
import dev.qtremors.arcile.core.privilege.PrivilegeConnectionState
import dev.qtremors.arcile.core.privilege.PrivilegeMode
import dev.qtremors.arcile.core.privilege.PrivilegePreferenceState
import dev.qtremors.arcile.core.privilege.PrivilegeServiceIdentity
import dev.qtremors.arcile.core.privilege.PrivilegeState
import dev.qtremors.arcile.core.privilege.PrivilegeTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsSectionsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `thumbnail row forwards inverse preference`() {
        var requestedValue: Boolean? = null
        composeRule.setContent {
            ArcileTestTheme {
                SettingsSwitchRow(
                    title = "Thumbnails",
                    description = "Show previews",
                    checked = true,
                    switchTag = "thumbnail_switch",
                    rowTag = "thumbnail_setting_row",
                    onCheckedChange = { requestedValue = it }
                )
            }
        }

        composeRule.onNodeWithTag("thumbnail_switch").performClick()

        assertEquals(false, requestedValue)
    }

    @Test
    fun `enabling double line filenames disables marquee`() {
        val updatedTheme = ThemeState(
            doubleLineFilenames = false,
            marqueeFilenames = true
        ).withDoubleLineFilenames(true)

        assertTrue(updatedTheme.doubleLineFilenames)
        assertFalse(updatedTheme.marqueeFilenames)
    }

    @Test
    fun `enabling marquee filenames disables double line mode`() {
        val updatedTheme = ThemeState(
            doubleLineFilenames = true,
            marqueeFilenames = false
        ).withMarqueeFilenames(true)

        assertTrue(updatedTheme.marqueeFilenames)
        assertFalse(updatedTheme.doubleLineFilenames)
    }

    @Test
    fun `landscape dual pane setting updates independently`() {
        val original = ThemeState(marqueeFilenames = true)
        val updated = original.withLandscapeDualPane(true)

        assertTrue(updated.landscapeDualPaneEnabled)
        assertTrue(updated.marqueeFilenames)
    }

    @Test
    fun `busy external cache cannot launch a second clear`() {
        var clearCount = 0
        composeRule.setContent {
            ArcileTestTheme {
                SettingsStorageSection(
                    cache = SettingsExternalCacheState(fileCount = 3, isBusy = true),
                    onOpenStorageManagement = {},
                    onClearExternalCache = { clearCount += 1 }
                )
            }
        }

        composeRule.onNodeWithTag("external_cache_setting_row")
            .assertIsNotEnabled()
            .performClick()

        assertEquals(0, clearCount)
    }

    @Test
    fun `idle external cache launches one clear`() {
        var clearCount = 0
        composeRule.setContent {
            ArcileTestTheme {
                SettingsStorageSection(
                    cache = SettingsExternalCacheState(fileCount = 3, isBusy = false),
                    onOpenStorageManagement = {},
                    onClearExternalCache = { clearCount += 1 }
                )
            }
        }

        composeRule.onNodeWithTag("external_cache_setting_row").performClick()

        assertEquals(1, clearCount)
    }

    @Test
    fun `Shizuku status icon setting updates independently`() {
        val updated = ThemeState(
            showShizukuStatusIcon = true,
            harmonizeColors = false
        ).withShizukuStatusIcon(false)

        assertFalse(updated.showShizukuStatusIcon)
        assertFalse(updated.harmonizeColors)
    }

    @Test
    fun `access section exposes one Shizuku switch and forwards changes`() {
        var shizukuEnabled: Boolean? = null
        composeRule.setContent {
            ArcileTestTheme {
                SettingsAccessSection(
                    state = SettingsAccessState(),
                    actions = accessActions(shizukuEnabledChange = { shizukuEnabled = it })
                )
            }
        }

        composeRule.onNodeWithTag("shizuku_enabled_switch").performClick()

        assertEquals(true, shizukuEnabled)
    }

    @Test
    fun `protected writes require confirmation before enabling`() {
        var protectedWrites: Boolean? = null
        composeRule.setContent {
            ArcileTestTheme {
                SettingsAccessSection(
                    state = rootAccessState(),
                    actions = accessActions(protectedWritesChange = { protectedWrites = it })
                )
            }
        }

        composeRule.onNodeWithTag("protected_writes_switch")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Enable protected writes?").assertExists()
        assertEquals(null, protectedWrites)

        composeRule.onNodeWithText("Enable protected writes").performClick()

        composeRule.runOnIdle {
            assertEquals(true, protectedWrites)
        }
    }

    @Test
    fun `protected writes stay disabled without a Root identity`() {
        composeRule.setContent {
            ArcileTestTheme {
                SettingsAccessSection(
                    state = SettingsAccessState(),
                    actions = accessActions()
                )
            }
        }

        composeRule.onNodeWithTag("protected_writes_row").assertIsNotEnabled()
    }

    @Test
    fun `detected Root hides provider controls and explains automatic fallback`() {
        composeRule.setContent {
            ArcileTestTheme {
                SettingsAccessSection(
                    state = SettingsAccessState(
                        preference = PrivilegePreferenceState(mode = PrivilegeMode.ROOT),
                        access = PrivilegeState(
                            preferredMode = PrivilegeMode.ROOT,
                            backendStates = mapOf(
                                PrivilegeBackendId.ROOT to PrivilegeBackendState(
                                    backendId = PrivilegeBackendId.ROOT,
                                    connectionState = PrivilegeConnectionState.DISCONNECTED
                                )
                            )
                        )
                    ),
                    actions = accessActions()
                )
            }
        }

        composeRule.onNodeWithText(
            "This is a rooted device. Arcile will use normal access until root is available."
        ).assertExists()
        composeRule.onNodeWithTag("shizuku_enabled_row").assertDoesNotExist()
    }

    @Test
    fun `ready Shizuku is shown as the automatic active provider`() {
        composeRule.setContent {
            ArcileTestTheme {
                SettingsAccessSection(
                    state = SettingsAccessState(
                        preference = PrivilegePreferenceState(mode = PrivilegeMode.SHIZUKU),
                        access = PrivilegeState(
                            preferredMode = PrivilegeMode.SHIZUKU,
                            activeBackend = PrivilegeBackendId.SHIZUKU,
                            backendStates = mapOf(
                                PrivilegeBackendId.SHIZUKU to PrivilegeBackendState(
                                    backendId = PrivilegeBackendId.SHIZUKU,
                                    connectionState = PrivilegeConnectionState.READY
                                )
                            ),
                            identity = PrivilegeServiceIdentity(
                                effectiveUid = 2000,
                                pid = 42,
                                transport = PrivilegeTransport.SHIZUKU_USER_SERVICE
                            )
                        )
                    ),
                    actions = accessActions()
                )
            }
        }

        composeRule.onNodeWithText("Shizuku is enabled and used automatically.").assertExists()
    }

}

private fun accessActions(
    shizukuEnabledChange: (Boolean) -> Unit = {},
    protectedWritesChange: (Boolean) -> Unit = {}
) = SettingsAccessActions(
    shizukuEnabledChange = shizukuEnabledChange,
    grantNormalPermission = {},
    openShizukuManager = {},
    protectedWritesChange = protectedWritesChange
)

private fun rootAccessState(): SettingsAccessState {
    val identity = PrivilegeServiceIdentity(
        effectiveUid = 0,
        pid = 42,
        transport = PrivilegeTransport.ROOT_SERVICE
    )
    return SettingsAccessState(
        preference = PrivilegePreferenceState(mode = PrivilegeMode.ROOT),
        access = PrivilegeState(
            preferredMode = PrivilegeMode.ROOT,
            activeBackend = PrivilegeBackendId.ROOT,
            backendStates = mapOf(
                PrivilegeBackendId.ROOT to PrivilegeBackendState(
                    backendId = PrivilegeBackendId.ROOT,
                    connectionState = PrivilegeConnectionState.READY,
                    identity = identity
                )
            ),
            identity = identity
        )
    )
}
