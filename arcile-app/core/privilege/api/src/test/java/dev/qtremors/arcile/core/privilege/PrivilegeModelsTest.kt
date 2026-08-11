package dev.qtremors.arcile.core.privilege

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivilegeModelsTest {
    @Test
    fun `stable backend ids match persisted values`() {
        assertEquals("root", PrivilegeBackendId.ROOT.value)
        assertEquals("shizuku", PrivilegeBackendId.SHIZUKU.value)
        assertEquals("normal", PrivilegeBackendId.NORMAL.value)
    }

    @Test
    fun `service identity recognizes only supported privilege uids`() {
        val root = PrivilegeServiceIdentity(0, 1, PrivilegeTransport.ROOT_SERVICE)
        val shell = PrivilegeServiceIdentity(2000, 2, PrivilegeTransport.SHIZUKU_USER_SERVICE)

        assertTrue(root.isRoot)
        assertFalse(root.isShell)
        assertTrue(shell.isShell)
        assertEquals(root, root.requireSupportedUid())
        assertEquals(shell, shell.requireSupportedUid())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unexpected service identity is rejected`() {
        PrivilegeServiceIdentity(10000, 3, PrivilegeTransport.SHIZUKU_USER_SERVICE)
            .requireSupportedUid()
    }

    @Test
    fun `state keeps preferred and active backend separate`() {
        val normal = PrivilegeBackendState(
            backendId = PrivilegeBackendId.NORMAL,
            connectionState = PrivilegeConnectionState.READY
        )
        val state = PrivilegeState(
            preferredMode = PrivilegeMode.AUTOMATIC,
            activeBackend = PrivilegeBackendId.NORMAL,
            backendStates = mapOf(PrivilegeBackendId.NORMAL to normal)
        )

        assertEquals(PrivilegeMode.AUTOMATIC, state.preferredMode)
        assertEquals(PrivilegeBackendId.NORMAL, state.activeBackend)
        assertTrue(state.isReady)
    }
}
