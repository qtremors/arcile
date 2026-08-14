package dev.qtremors.arcile.feature.browser

import dev.qtremors.arcile.core.privilege.PrivilegeBackendId
import dev.qtremors.arcile.core.privilege.PrivilegeBackendState
import dev.qtremors.arcile.core.privilege.PrivilegeConnectionState
import dev.qtremors.arcile.core.privilege.PrivilegeFailure
import dev.qtremors.arcile.core.privilege.PrivilegeMode
import dev.qtremors.arcile.core.privilege.PrivilegeServiceIdentity
import dev.qtremors.arcile.core.privilege.PrivilegeState
import dev.qtremors.arcile.core.privilege.PrivilegeTransport
import dev.qtremors.arcile.core.storage.domain.StorageNodeCapabilities
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserBackendAccessTest {
    @Test
    fun `fresh local location remains available before a backend was active`() {
        val result = deriveBrowserBackendAccessState(
            node = StorageNodeRef.local("/storage/emulated/0/Documents"),
            privilegeState = state(),
            normalFallbackPending = false
        )

        assertSame(BrowserBackendAccessState.Available, result)
    }

    @Test
    fun `ready Root keeps a local location available`() {
        val result = deriveBrowserBackendAccessState(
            node = StorageNodeRef.local("/storage/emulated/0/Documents"),
            privilegeState = readyState(PrivilegeBackendId.ROOT),
            normalFallbackPending = false
        )

        assertSame(BrowserBackendAccessState.Available, result)
    }

    @Test
    fun `lost Root protects context at a local location`() {
        val result = deriveBrowserBackendAccessState(
            node = StorageNodeRef.local("/storage/emulated/0/Documents"),
            privilegeState = disconnectedRootState(),
            normalFallbackPending = false
        ) as BrowserBackendAccessState.Lost

        assertEquals(PrivilegeBackendId.ROOT, result.backendId)
        assertEquals(BrowserAccessLossReason.CONNECTION_FAILED, result.reason)
    }

    @Test
    fun `lost Root protects context at the volume root`() {
        val result = deriveBrowserBackendAccessState(
            node = null,
            privilegeState = disconnectedRootState(),
            normalFallbackPending = false
        ) as BrowserBackendAccessState.Lost

        assertEquals(PrivilegeBackendId.ROOT, result.backendId)
    }

    @Test
    fun `revoked Normal permission protects local browser context`() {
        val failure = PrivilegeFailure.Failed("All-files access is required for Normal storage")
        val result = deriveBrowserBackendAccessState(
            node = StorageNodeRef.local("/storage/emulated/0/Documents"),
            privilegeState = state(
                preferredMode = PrivilegeMode.NORMAL,
                activeBackend = PrivilegeBackendId.NORMAL,
                normal = backend(
                    PrivilegeBackendId.NORMAL,
                    PrivilegeConnectionState.PERMISSION_REQUIRED,
                    failure
                ),
                failure = failure
            ),
            normalFallbackPending = false
        ) as BrowserBackendAccessState.Lost

        assertEquals(PrivilegeBackendId.NORMAL, result.backendId)
        assertTrue(result.normalPermissionRequired)
        assertEquals(BrowserAccessLossReason.PERMISSION_REQUIRED, result.reason)
    }

    @Test
    fun `ready Root location is available`() {
        val result = deriveBrowserBackendAccessState(
            node = rootNode(),
            privilegeState = readyState(PrivilegeBackendId.ROOT),
            normalFallbackPending = false
        )

        assertSame(BrowserBackendAccessState.Available, result)
    }

    @Test
    fun `ready Shizuku location is available`() {
        val result = deriveBrowserBackendAccessState(
            node = shizukuNode(),
            privilegeState = readyState(PrivilegeBackendId.SHIZUKU),
            normalFallbackPending = false
        )

        assertSame(BrowserBackendAccessState.Available, result)
    }

    @Test
    fun `different ready backend does not make protected location available`() {
        val result = deriveBrowserBackendAccessState(
            node = rootNode(),
            privilegeState = readyState(PrivilegeBackendId.SHIZUKU),
            normalFallbackPending = false
        ) as BrowserBackendAccessState.Lost

        assertEquals(PrivilegeBackendId.ROOT, result.backendId)
        assertEquals(BrowserAccessLossReason.BACKEND_CHANGED, result.reason)
    }

    @Test
    fun `Normal backend does not silently reinterpret Root location`() {
        val result = deriveBrowserBackendAccessState(
            node = rootNode(),
            privilegeState = readyState(PrivilegeBackendId.NORMAL),
            normalFallbackPending = false
        ) as BrowserBackendAccessState.Lost

        assertEquals(BrowserAccessLossReason.BACKEND_CHANGED, result.reason)
        assertTrue(result.normalAccessReady)
        assertFalse(result.normalFallbackPending)
    }

    @Test
    fun `backend connecting shows reconnecting without clearing context`() {
        val result = deriveBrowserBackendAccessState(
            node = rootNode(),
            privilegeState = state(
                root = backend(PrivilegeBackendId.ROOT, PrivilegeConnectionState.CONNECTING)
            ),
            normalFallbackPending = false
        )

        assertEquals(
            BrowserBackendAccessState.Reconnecting(
                backendId = PrivilegeBackendId.ROOT,
                normalFallbackPending = false
            ),
            result
        )
    }

    @Test
    fun `Normal connecting during explicit fallback shows reconnecting`() {
        val result = deriveBrowserBackendAccessState(
            node = shizukuNode(),
            privilegeState = state(
                preferredMode = PrivilegeMode.NORMAL,
                normal = backend(PrivilegeBackendId.NORMAL, PrivilegeConnectionState.CONNECTING)
            ),
            normalFallbackPending = true
        ) as BrowserBackendAccessState.Reconnecting

        assertEquals(PrivilegeBackendId.SHIZUKU, result.backendId)
        assertTrue(result.normalFallbackPending)
    }

    @Test
    fun `Root disconnection exposes reconnect state`() {
        val failure = PrivilegeFailure.BackendDisconnected()
        val result = deriveBrowserBackendAccessState(
            node = rootNode(),
            privilegeState = state(
                activeBackend = PrivilegeBackendId.ROOT,
                root = backend(
                    PrivilegeBackendId.ROOT,
                    PrivilegeConnectionState.DISCONNECTED,
                    failure
                ),
                failure = failure
            ),
            normalFallbackPending = false
        ) as BrowserBackendAccessState.Lost

        assertEquals(BrowserAccessLossReason.CONNECTION_FAILED, result.reason)
        assertSame(failure, result.failure)
    }

    @Test
    fun `stopped Shizuku has specific loss reason`() {
        val failure = PrivilegeFailure.ShizukuNotRunning()
        val result = deriveBrowserBackendAccessState(
            node = shizukuNode(),
            privilegeState = state(
                activeBackend = PrivilegeBackendId.SHIZUKU,
                shizuku = backend(
                    PrivilegeBackendId.SHIZUKU,
                    PrivilegeConnectionState.INSTALLED_BUT_STOPPED,
                    failure
                ),
                failure = failure
            ),
            normalFallbackPending = false
        ) as BrowserBackendAccessState.Lost

        assertEquals(BrowserAccessLossReason.SERVICE_STOPPED, result.reason)
        assertSame(failure, result.failure)
    }

    @Test
    fun `Shizuku permission required has specific loss reason`() {
        val failure = PrivilegeFailure.ShizukuPermissionRequired()
        val result = deriveBrowserBackendAccessState(
            node = shizukuNode(),
            privilegeState = state(
                shizuku = backend(
                    PrivilegeBackendId.SHIZUKU,
                    PrivilegeConnectionState.PERMISSION_REQUIRED,
                    failure
                ),
                failure = failure
            ),
            normalFallbackPending = false
        ) as BrowserBackendAccessState.Lost

        assertEquals(BrowserAccessLossReason.PERMISSION_REQUIRED, result.reason)
    }

    @Test
    fun `Root permission denied has specific loss reason`() {
        val failure = PrivilegeFailure.RootPermissionDenied()
        val result = deriveBrowserBackendAccessState(
            node = rootNode(),
            privilegeState = state(
                root = backend(
                    PrivilegeBackendId.ROOT,
                    PrivilegeConnectionState.PERMISSION_DENIED,
                    failure
                ),
                failure = failure
            ),
            normalFallbackPending = false
        ) as BrowserBackendAccessState.Lost

        assertEquals(BrowserAccessLossReason.PERMISSION_DENIED, result.reason)
    }

    @Test
    fun `Root unavailable has specific loss reason`() {
        val failure = PrivilegeFailure.RootUnavailable()
        val result = deriveBrowserBackendAccessState(
            node = rootNode(),
            privilegeState = state(
                root = backend(
                    PrivilegeBackendId.ROOT,
                    PrivilegeConnectionState.UNAVAILABLE,
                    failure
                ),
                failure = failure
            ),
            normalFallbackPending = false
        ) as BrowserBackendAccessState.Lost

        assertEquals(BrowserAccessLossReason.BACKEND_UNAVAILABLE, result.reason)
    }

    @Test
    fun `incompatible backend has specific loss reason`() {
        val failure = PrivilegeFailure.Incompatible(2, 1)
        val result = deriveBrowserBackendAccessState(
            node = shizukuNode(),
            privilegeState = state(
                shizuku = backend(
                    PrivilegeBackendId.SHIZUKU,
                    PrivilegeConnectionState.INCOMPATIBLE,
                    failure
                ),
                failure = failure
            ),
            normalFallbackPending = false
        ) as BrowserBackendAccessState.Lost

        assertEquals(BrowserAccessLossReason.BACKEND_INCOMPATIBLE, result.reason)
    }

    @Test
    fun `missing backend state is a connection failure`() {
        val result = deriveBrowserBackendAccessState(
            node = rootNode(),
            privilegeState = PrivilegeState(
                backendStates = mapOf(
                    PrivilegeBackendId.NORMAL to permissionRequired(PrivilegeBackendId.NORMAL)
                )
            ),
            normalFallbackPending = false
        ) as BrowserBackendAccessState.Lost

        assertEquals(BrowserAccessLossReason.CONNECTION_FAILED, result.reason)
        assertNull(result.failure)
    }

    @Test
    fun `normal permission requirement is exposed during fallback`() {
        val result = deriveBrowserBackendAccessState(
            node = rootNode(),
            privilegeState = state(
                preferredMode = PrivilegeMode.NORMAL,
                normal = permissionRequired(PrivilegeBackendId.NORMAL)
            ),
            normalFallbackPending = true
        ) as BrowserBackendAccessState.Lost

        assertTrue(result.normalFallbackPending)
        assertTrue(result.normalPermissionRequired)
        assertFalse(result.normalAccessReady)
    }

    @Test
    fun `ready normal availability is exposed before fallback`() {
        val result = deriveBrowserBackendAccessState(
            node = rootNode(),
            privilegeState = state(normal = backend(PrivilegeBackendId.NORMAL, PrivilegeConnectionState.READY)),
            normalFallbackPending = false
        ) as BrowserBackendAccessState.Lost

        assertTrue(result.normalAccessReady)
        assertFalse(result.normalPermissionRequired)
    }

    @Test
    fun `normal unavailable is neither ready nor permission required`() {
        val result = deriveBrowserBackendAccessState(
            node = rootNode(),
            privilegeState = state(
                normal = backend(PrivilegeBackendId.NORMAL, PrivilegeConnectionState.UNAVAILABLE)
            ),
            normalFallbackPending = true
        ) as BrowserBackendAccessState.Lost

        assertFalse(result.normalAccessReady)
        assertFalse(result.normalPermissionRequired)
    }

    @Test
    fun `action failure is retained independently from backend failure`() {
        val backendFailure = PrivilegeFailure.BackendDisconnected()
        val actionFailure = PrivilegeFailure.ConnectionTimedOut()
        val result = deriveBrowserBackendAccessState(
            node = rootNode(),
            privilegeState = state(
                activeBackend = PrivilegeBackendId.ROOT,
                root = backend(
                    PrivilegeBackendId.ROOT,
                    PrivilegeConnectionState.DISCONNECTED,
                    backendFailure
                ),
                failure = backendFailure
            ),
            normalFallbackPending = false,
            actionFailure = actionFailure
        ) as BrowserBackendAccessState.Lost

        assertSame(backendFailure, result.failure)
        assertSame(actionFailure, result.actionFailure)
    }

    private fun disconnectedRootState(): PrivilegeState {
        val failure = PrivilegeFailure.BackendDisconnected()
        return state(
            activeBackend = PrivilegeBackendId.ROOT,
            root = backend(
                PrivilegeBackendId.ROOT,
                PrivilegeConnectionState.DISCONNECTED,
                failure
            ),
            failure = failure
        )
    }

    private fun readyState(id: PrivilegeBackendId): PrivilegeState {
        if (id == PrivilegeBackendId.NORMAL) {
            return state(
                preferredMode = PrivilegeMode.NORMAL,
                activeBackend = id,
                normal = backend(id, PrivilegeConnectionState.READY)
            )
        }
        val identity = serviceIdentity(id)
        val ready = PrivilegeBackendState(
            backendId = id,
            connectionState = PrivilegeConnectionState.READY,
            identity = identity
        )
        return state(
            preferredMode = if (id == PrivilegeBackendId.ROOT) PrivilegeMode.ROOT else PrivilegeMode.SHIZUKU,
            activeBackend = id,
            root = if (id == PrivilegeBackendId.ROOT) ready else backend(
                PrivilegeBackendId.ROOT,
                PrivilegeConnectionState.UNAVAILABLE
            ),
            shizuku = if (id == PrivilegeBackendId.SHIZUKU) ready else backend(
                PrivilegeBackendId.SHIZUKU,
                PrivilegeConnectionState.UNAVAILABLE
            ),
            identity = identity
        )
    }

    private fun state(
        preferredMode: PrivilegeMode = PrivilegeMode.AUTOMATIC,
        activeBackend: PrivilegeBackendId? = null,
        root: PrivilegeBackendState = backend(
            PrivilegeBackendId.ROOT,
            PrivilegeConnectionState.UNAVAILABLE
        ),
        shizuku: PrivilegeBackendState = backend(
            PrivilegeBackendId.SHIZUKU,
            PrivilegeConnectionState.UNAVAILABLE
        ),
        normal: PrivilegeBackendState = permissionRequired(PrivilegeBackendId.NORMAL),
        identity: PrivilegeServiceIdentity? = null,
        failure: PrivilegeFailure? = null
    ) = PrivilegeState(
        preferredMode = preferredMode,
        activeBackend = activeBackend,
        backendStates = mapOf(
            PrivilegeBackendId.ROOT to root,
            PrivilegeBackendId.SHIZUKU to shizuku,
            PrivilegeBackendId.NORMAL to normal
        ),
        identity = identity,
        lastFailure = failure
    )

    private fun rootNode() = StorageNodeRef.privileged(
        backendId = StorageNodeRef.ROOT_BACKEND_ID,
        displayPath = "/data/local/tmp",
        remoteCanonicalIdentity = "root:/data/local/tmp",
        capabilities = StorageNodeCapabilities(canRead = true, canWrite = true)
    )

    private fun shizukuNode() = StorageNodeRef.privileged(
        backendId = StorageNodeRef.SHIZUKU_BACKEND_ID,
        displayPath = "/storage/emulated/0/Android/data",
        remoteCanonicalIdentity = "shizuku:/storage/emulated/0/Android/data",
        capabilities = StorageNodeCapabilities(canRead = true)
    )

    private fun permissionRequired(id: PrivilegeBackendId) =
        backend(id, PrivilegeConnectionState.PERMISSION_REQUIRED)

    private fun backend(
        id: PrivilegeBackendId,
        state: PrivilegeConnectionState,
        failure: PrivilegeFailure? = null
    ) = PrivilegeBackendState(id, state, failure = failure)

    private fun serviceIdentity(id: PrivilegeBackendId) = PrivilegeServiceIdentity(
        effectiveUid = if (id == PrivilegeBackendId.ROOT) 0 else 2000,
        pid = 50,
        transport = if (id == PrivilegeBackendId.ROOT) {
            PrivilegeTransport.ROOT_SERVICE
        } else {
            PrivilegeTransport.SHIZUKU_USER_SERVICE
        }
    )
}
