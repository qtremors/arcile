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
    fun `access section exposes all providers and forwards selection`() {
        var selected: PrivilegeMode? = null
        composeRule.setContent {
            ArcileTestTheme {
                SettingsAccessSection(
                    state = SettingsAccessState(),
                    actions = accessActions(selectMode = { selected = it })
                )
            }
        }

        composeRule.onNodeWithTag("storage_access_mode_automatic").assertExists()
        composeRule.onNodeWithTag("storage_access_mode_root").performClick()

        assertEquals(PrivilegeMode.ROOT, selected)
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
    fun `failed explicit Root offers reconnect and Normal fallback`() {
        var reconnects = 0
        var normalFallbacks = 0
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
                    actions = accessActions(
                        reconnect = { reconnects++ },
                        useNormal = { normalFallbacks++ }
                    )
                )
            }
        }

        composeRule.onNodeWithTag("storage_access_reconnect")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithTag("storage_access_use_normal")
            .performSemanticsAction(SemanticsActions.OnClick)

        composeRule.runOnIdle {
            assertEquals(1, reconnects)
            assertEquals(1, normalFallbacks)
        }
    }

    @Test
    fun `ready Shizuku shell identity is shown accurately`() {
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

        composeRule.onNodeWithText("Connected as ADB shell").assertExists()
    }

}

private fun accessActions(
    selectMode: (PrivilegeMode) -> Unit = {},
    reconnect: () -> Unit = {},
    useNormal: () -> Unit = {},
    protectedWritesChange: (Boolean) -> Unit = {}
) = SettingsAccessActions(
    selectMode = selectMode,
    reconnect = reconnect,
    useNormal = useNormal,
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
