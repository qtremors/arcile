package dev.qtremors.arcile.core.privilege

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplicationAccessReadinessTest {
    @Test
    fun `normal ready satisfies application access`() {
        val state = state(
            activeBackend = PrivilegeBackendId.NORMAL,
            normal = ready(PrivilegeBackendId.NORMAL),
            generation = 4
        )

        val readiness = state.toApplicationAccessReadiness()

        assertTrue(readiness is ApplicationAccessReadiness.Ready)
        readiness as ApplicationAccessReadiness.Ready
        assertEquals(PrivilegeBackendId.NORMAL, readiness.activeBackend)
        assertEquals(4L, readiness.connectionGeneration)
        assertTrue(readiness.normalAccessReady)
        assertNull(readiness.identity)
    }

    @Test
    fun `root ready satisfies application access without normal permission`() {
        val identity = serviceIdentity(PrivilegeBackendId.ROOT)
        val state = state(
            preferredMode = PrivilegeMode.ROOT,
            activeBackend = PrivilegeBackendId.ROOT,
            root = ready(PrivilegeBackendId.ROOT, identity),
            normal = permissionRequired(PrivilegeBackendId.NORMAL),
            identity = identity,
            generation = 8
        )

        val readiness = state.toApplicationAccessReadiness()

        assertTrue(readiness is ApplicationAccessReadiness.Ready)
        readiness as ApplicationAccessReadiness.Ready
        assertEquals(PrivilegeBackendId.ROOT, readiness.activeBackend)
        assertFalse(readiness.normalAccessReady)
        assertSame(identity, readiness.identity)
    }

    @Test
    fun `Shizuku shell ready satisfies application access without normal permission`() {
        val identity = serviceIdentity(PrivilegeBackendId.SHIZUKU)
        val state = state(
            preferredMode = PrivilegeMode.SHIZUKU,
            activeBackend = PrivilegeBackendId.SHIZUKU,
            shizuku = ready(PrivilegeBackendId.SHIZUKU, identity),
            normal = permissionRequired(PrivilegeBackendId.NORMAL),
            identity = identity
        )

        val readiness = state.toApplicationAccessReadiness()

        assertEquals(PrivilegeBackendId.SHIZUKU, (readiness as ApplicationAccessReadiness.Ready).activeBackend)
        assertFalse(readiness.normalAccessReady)
    }

    @Test
    fun `initial check is connecting instead of briefly requesting setup`() {
        val readiness = state().toApplicationAccessReadiness(ApplicationAccessCheck.INITIALIZING)

        assertTrue(readiness is ApplicationAccessReadiness.Connecting)
        readiness as ApplicationAccessReadiness.Connecting
        assertEquals(ApplicationAccessCheck.INITIALIZING, readiness.check)
        assertNull(readiness.backend)
    }

    @Test
    fun `resume refresh retains the current backend while checking`() {
        val state = state(
            preferredMode = PrivilegeMode.ROOT,
            activeBackend = PrivilegeBackendId.ROOT,
            root = disconnected(PrivilegeBackendId.ROOT),
            failure = PrivilegeFailure.BackendDisconnected()
        )

        val readiness = state.toApplicationAccessReadiness(ApplicationAccessCheck.RESUME_REFRESH)

        assertTrue(readiness is ApplicationAccessReadiness.Connecting)
        assertEquals(PrivilegeBackendId.ROOT, (readiness as ApplicationAccessReadiness.Connecting).backend)
    }

    @Test
    fun `backend connecting state is connecting even with idle action`() {
        val state = state(
            preferredMode = PrivilegeMode.SHIZUKU,
            shizuku = backend(PrivilegeBackendId.SHIZUKU, PrivilegeConnectionState.CONNECTING)
        )

        val readiness = state.toApplicationAccessReadiness()

        assertTrue(readiness is ApplicationAccessReadiness.Connecting)
        readiness as ApplicationAccessReadiness.Connecting
        assertEquals(PrivilegeBackendId.SHIZUKU, readiness.backend)
        assertEquals(ApplicationAccessCheck.INITIALIZING, readiness.check)
    }

    @Test
    fun `automatic setup required exposes every backend state`() {
        val state = state(
            root = permissionRequired(PrivilegeBackendId.ROOT),
            shizuku = backend(PrivilegeBackendId.SHIZUKU, PrivilegeConnectionState.INSTALLED_BUT_STOPPED),
            normal = permissionRequired(PrivilegeBackendId.NORMAL)
        )

        val readiness = state.toApplicationAccessReadiness()

        assertTrue(readiness is ApplicationAccessReadiness.SetupRequired)
        readiness as ApplicationAccessReadiness.SetupRequired
        assertEquals(PrivilegeConnectionState.PERMISSION_REQUIRED, readiness.rootState)
        assertEquals(PrivilegeConnectionState.INSTALLED_BUT_STOPPED, readiness.shizukuState)
        assertEquals(PrivilegeConnectionState.PERMISSION_REQUIRED, readiness.normalState)
    }

    @Test
    fun `automatic unavailable probes remain setup required rather than failed`() {
        val readiness = state(
            root = backend(PrivilegeBackendId.ROOT, PrivilegeConnectionState.UNAVAILABLE),
            shizuku = backend(PrivilegeBackendId.SHIZUKU, PrivilegeConnectionState.UNAVAILABLE),
            normal = permissionRequired(PrivilegeBackendId.NORMAL)
        ).toApplicationAccessReadiness()

        assertTrue(readiness is ApplicationAccessReadiness.SetupRequired)
    }

    @Test
    fun `explicit root denial is failed and retains root as recovery target`() {
        val failure = PrivilegeFailure.RootPermissionDenied()
        val readiness = state(
            preferredMode = PrivilegeMode.ROOT,
            root = backend(PrivilegeBackendId.ROOT, PrivilegeConnectionState.PERMISSION_DENIED, failure),
            normal = ready(PrivilegeBackendId.NORMAL),
            failure = failure
        ).toApplicationAccessReadiness()

        assertTrue(readiness is ApplicationAccessReadiness.Failed)
        readiness as ApplicationAccessReadiness.Failed
        assertEquals(PrivilegeBackendId.ROOT, readiness.retainedBackend)
        assertEquals(PrivilegeConnectionState.PERMISSION_DENIED, readiness.backendState)
        assertTrue(readiness.normalAccessReady)
        assertSame(failure, readiness.failure)
    }

    @Test
    fun `dead active backend is failed even in automatic mode`() {
        val failure = PrivilegeFailure.ShizukuNotRunning()
        val readiness = state(
            activeBackend = PrivilegeBackendId.SHIZUKU,
            shizuku = backend(
                PrivilegeBackendId.SHIZUKU,
                PrivilegeConnectionState.DISCONNECTED,
                failure
            ),
            failure = failure
        ).toApplicationAccessReadiness()

        assertTrue(readiness is ApplicationAccessReadiness.Failed)
        readiness as ApplicationAccessReadiness.Failed
        assertEquals(PrivilegeBackendId.SHIZUKU, readiness.retainedBackend)
        assertEquals(PrivilegeConnectionState.DISCONNECTED, readiness.backendState)
    }

    @Test
    fun `Shizuku stopped failure maps to installed but stopped`() {
        val failure = PrivilegeFailure.ShizukuNotRunning()
        val readiness = state(
            preferredMode = PrivilegeMode.SHIZUKU,
            shizuku = backend(
                PrivilegeBackendId.SHIZUKU,
                PrivilegeConnectionState.INSTALLED_BUT_STOPPED,
                failure
            ),
            failure = failure
        ).toApplicationAccessReadiness() as ApplicationAccessReadiness.Failed

        assertEquals(PrivilegeConnectionState.INSTALLED_BUT_STOPPED, readiness.backendState)
    }

    @Test
    fun `timeout failure maps to failed`() {
        val failure = PrivilegeFailure.ConnectionTimedOut()
        val readiness = state(
            preferredMode = PrivilegeMode.ROOT,
            failure = failure
        ).toApplicationAccessReadiness() as ApplicationAccessReadiness.Failed

        assertEquals(PrivilegeConnectionState.FAILED, readiness.backendState)
    }

    @Test
    fun `incompatible failure maps to incompatible`() {
        val failure = PrivilegeFailure.Incompatible(2, 1)
        val readiness = state(
            preferredMode = PrivilegeMode.SHIZUKU,
            failure = failure
        ).toApplicationAccessReadiness() as ApplicationAccessReadiness.Failed

        assertEquals(PrivilegeConnectionState.INCOMPATIBLE, readiness.backendState)
    }

    @Test
    fun `fresh snapshot cannot show application while setup is required`() {
        val snapshot = state().toApplicationAccessSnapshot()

        assertFalse(snapshot.isReady)
        assertFalse(snapshot.mayShowApplicationContent)
        assertFalse(snapshot.hasReachedReadyState)
    }

    @Test
    fun `ready snapshot can show application`() {
        val snapshot = state(
            activeBackend = PrivilegeBackendId.NORMAL,
            normal = ready(PrivilegeBackendId.NORMAL)
        ).toApplicationAccessSnapshot()

        assertTrue(snapshot.isReady)
        assertTrue(snapshot.mayShowApplicationContent)
        assertTrue(snapshot.hasReachedReadyState)
    }

    @Test
    fun `previously ready snapshot keeps application visible after access loss`() {
        val failure = PrivilegeFailure.BackendDisconnected()
        val snapshot = state(
            activeBackend = PrivilegeBackendId.ROOT,
            root = disconnected(PrivilegeBackendId.ROOT, failure),
            failure = failure
        ).toApplicationAccessSnapshot(hasReachedReadyState = true)

        assertFalse(snapshot.isReady)
        assertTrue(snapshot.mayShowApplicationContent)
        assertTrue(snapshot.hasReachedReadyState)
    }

    @Test
    fun `previously ready snapshot keeps application visible while reconnecting`() {
        val snapshot = state(
            activeBackend = PrivilegeBackendId.ROOT,
            root = backend(PrivilegeBackendId.ROOT, PrivilegeConnectionState.CONNECTING)
        ).toApplicationAccessSnapshot(
            check = ApplicationAccessCheck.RECONNECTING,
            hasReachedReadyState = true
        )

        assertTrue(snapshot.mayShowApplicationContent)
        assertTrue(snapshot.readiness is ApplicationAccessReadiness.Connecting)
    }

    @Test
    fun `snapshot reports normal access independently from active root`() {
        val rootIdentity = serviceIdentity(PrivilegeBackendId.ROOT)
        val snapshot = state(
            activeBackend = PrivilegeBackendId.ROOT,
            root = ready(PrivilegeBackendId.ROOT, rootIdentity),
            normal = ready(PrivilegeBackendId.NORMAL),
            identity = rootIdentity
        ).toApplicationAccessSnapshot()

        assertEquals(PrivilegeBackendId.ROOT, snapshot.activeBackend)
        assertTrue(snapshot.normalAccessReady)
    }

    private fun state(
        preferredMode: PrivilegeMode = PrivilegeMode.AUTOMATIC,
        activeBackend: PrivilegeBackendId? = null,
        root: PrivilegeBackendState = backend(PrivilegeBackendId.ROOT, PrivilegeConnectionState.UNAVAILABLE),
        shizuku: PrivilegeBackendState = backend(
            PrivilegeBackendId.SHIZUKU,
            PrivilegeConnectionState.UNAVAILABLE
        ),
        normal: PrivilegeBackendState = permissionRequired(PrivilegeBackendId.NORMAL),
        identity: PrivilegeServiceIdentity? = null,
        failure: PrivilegeFailure? = null,
        generation: Long = 0
    ) = PrivilegeState(
        preferredMode = preferredMode,
        activeBackend = activeBackend,
        connectionGeneration = generation,
        backendStates = mapOf(
            PrivilegeBackendId.ROOT to root,
            PrivilegeBackendId.SHIZUKU to shizuku,
            PrivilegeBackendId.NORMAL to normal
        ),
        identity = identity,
        lastFailure = failure
    )

    private fun ready(
        id: PrivilegeBackendId,
        identity: PrivilegeServiceIdentity? = if (id == PrivilegeBackendId.NORMAL) null else serviceIdentity(id)
    ) = PrivilegeBackendState(
        backendId = id,
        connectionState = PrivilegeConnectionState.READY,
        identity = identity
    )

    private fun permissionRequired(id: PrivilegeBackendId) =
        backend(id, PrivilegeConnectionState.PERMISSION_REQUIRED)

    private fun disconnected(
        id: PrivilegeBackendId,
        failure: PrivilegeFailure = PrivilegeFailure.BackendDisconnected()
    ) = backend(id, PrivilegeConnectionState.DISCONNECTED, failure)

    private fun backend(
        id: PrivilegeBackendId,
        connectionState: PrivilegeConnectionState,
        failure: PrivilegeFailure? = null
    ) = PrivilegeBackendState(id, connectionState, failure = failure)

    private fun serviceIdentity(id: PrivilegeBackendId) = PrivilegeServiceIdentity(
        effectiveUid = if (id == PrivilegeBackendId.ROOT) 0 else 2000,
        pid = 22,
        transport = if (id == PrivilegeBackendId.ROOT) {
            PrivilegeTransport.ROOT_SERVICE
        } else {
            PrivilegeTransport.SHIZUKU_USER_SERVICE
        }
    )
}
