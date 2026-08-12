package dev.qtremors.arcile.presentation

import dev.qtremors.arcile.core.privilege.ApplicationAccessCheck
import dev.qtremors.arcile.core.privilege.ApplicationAccessReadiness
import dev.qtremors.arcile.core.privilege.PrivilegeBackendId
import dev.qtremors.arcile.core.privilege.PrivilegeBackendState
import dev.qtremors.arcile.core.privilege.PrivilegeConnectionState
import dev.qtremors.arcile.core.privilege.PrivilegeCoordinator
import dev.qtremors.arcile.core.privilege.PrivilegeFailure
import dev.qtremors.arcile.core.privilege.PrivilegeMode
import dev.qtremors.arcile.core.privilege.PrivilegeServiceIdentity
import dev.qtremors.arcile.core.privilege.PrivilegeSession
import dev.qtremors.arcile.core.privilege.PrivilegeState
import dev.qtremors.arcile.core.privilege.PrivilegeTransport
import dev.qtremors.arcile.testutil.FakeFilePreferencesStore
import dev.qtremors.arcile.testutil.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `startup begins with a connecting readiness state`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val coordinator = FakeMainPrivilegeCoordinator().apply {
            startAction = { gate.await() }
        }

        val viewModel = viewModel(coordinator)
        runCurrent()

        val readiness = viewModel.applicationAccess.value.readiness
        assertTrue(readiness is ApplicationAccessReadiness.Connecting)
        assertEquals(
            ApplicationAccessCheck.INITIALIZING,
            (readiness as ApplicationAccessReadiness.Connecting).check
        )
        assertFalse(viewModel.applicationAccess.value.mayShowApplicationContent)

        gate.complete(Unit)
        runCurrent()
    }

    @Test
    fun `Normal ready enters application`() = runTest {
        val coordinator = FakeMainPrivilegeCoordinator().apply {
            startAction = { emit(normalReadyState()) }
        }

        val viewModel = viewModel(coordinator)
        runCurrent()

        assertTrue(viewModel.applicationAccess.value.isReady)
        assertTrue(viewModel.applicationAccess.value.mayShowApplicationContent)
        assertEquals(PrivilegeBackendId.NORMAL, viewModel.applicationAccess.value.activeBackend)
        assertEquals(1, coordinator.startCalls)
    }

    @Test
    fun `Root ready enters without Normal all-files permission`() = runTest {
        val coordinator = FakeMainPrivilegeCoordinator().apply {
            startAction = { emit(rootReadyState(normalReady = false)) }
        }

        val viewModel = viewModel(coordinator)
        runCurrent()

        assertTrue(viewModel.applicationAccess.value.isReady)
        assertEquals(PrivilegeBackendId.ROOT, viewModel.applicationAccess.value.activeBackend)
        assertFalse(viewModel.applicationAccess.value.normalAccessReady)
    }

    @Test
    fun `Shizuku ready enters without Normal all-files permission`() = runTest {
        val coordinator = FakeMainPrivilegeCoordinator().apply {
            startAction = { emit(shizukuReadyState(normalReady = false)) }
        }

        val viewModel = viewModel(coordinator)
        runCurrent()

        assertTrue(viewModel.applicationAccess.value.isReady)
        assertEquals(PrivilegeBackendId.SHIZUKU, viewModel.applicationAccess.value.activeBackend)
        assertFalse(viewModel.applicationAccess.value.normalAccessReady)
    }

    @Test
    fun `fresh setup-required state does not enter application`() = runTest {
        val coordinator = FakeMainPrivilegeCoordinator()

        val viewModel = viewModel(coordinator)
        runCurrent()

        assertTrue(viewModel.applicationAccess.value.readiness is ApplicationAccessReadiness.SetupRequired)
        assertFalse(viewModel.applicationAccess.value.mayShowApplicationContent)
    }

    @Test
    fun `backend loss after entry preserves application composition`() = runTest {
        val coordinator = FakeMainPrivilegeCoordinator().apply {
            startAction = { emit(rootReadyState(normalReady = false)) }
        }
        val viewModel = viewModel(coordinator)
        runCurrent()

        val failure = PrivilegeFailure.BackendDisconnected()
        coordinator.emit(
            disconnectedState(
                backendId = PrivilegeBackendId.ROOT,
                failure = failure,
                normalReady = false
            )
        )
        runCurrent()

        val snapshot = viewModel.applicationAccess.value
        assertFalse(snapshot.isReady)
        assertTrue(snapshot.hasReachedReadyState)
        assertTrue(snapshot.mayShowApplicationContent)
        assertTrue(snapshot.readiness is ApplicationAccessReadiness.Failed)
        assertSame(failure, (snapshot.readiness as ApplicationAccessReadiness.Failed).failure)
    }

    @Test
    fun `resume refresh is passive`() = runTest {
        val coordinator = FakeMainPrivilegeCoordinator().apply {
            startAction = { emit(rootReadyState(normalReady = false)) }
        }
        val viewModel = viewModel(coordinator)
        runCurrent()

        viewModel.refreshAccess()
        runCurrent()

        assertEquals(1, coordinator.refreshCalls)
        assertTrue(coordinator.authorizationRequests.isEmpty())
        assertTrue(viewModel.applicationAccess.value.isReady)
    }

    @Test
    fun `resume refresh reports connecting while probe is pending and keeps content`() = runTest {
        val refreshGate = CompletableDeferred<Unit>()
        val coordinator = FakeMainPrivilegeCoordinator().apply {
            startAction = { emit(rootReadyState(normalReady = false)) }
            refreshAction = { refreshGate.await() }
        }
        val viewModel = viewModel(coordinator)
        runCurrent()

        viewModel.refreshAccess()
        runCurrent()

        val snapshot = viewModel.applicationAccess.value
        assertTrue(snapshot.readiness is ApplicationAccessReadiness.Ready)
        assertTrue(snapshot.mayShowApplicationContent)

        refreshGate.complete(Unit)
        runCurrent()
    }

    @Test
    fun `explicit reconnect allows authorization`() = runTest {
        val coordinator = FakeMainPrivilegeCoordinator()
        val viewModel = viewModel(coordinator)
        runCurrent()

        viewModel.reconnectAccess()
        runCurrent()

        assertEquals(listOf(true), coordinator.authorizationRequests)
    }

    @Test
    fun `reconnect exposes progress for disconnected entered session`() = runTest {
        val reconnectGate = CompletableDeferred<Unit>()
        val coordinator = FakeMainPrivilegeCoordinator().apply {
            startAction = { emit(rootReadyState(normalReady = false)) }
        }
        val viewModel = viewModel(coordinator)
        runCurrent()
        coordinator.emit(
            disconnectedState(
                backendId = PrivilegeBackendId.ROOT,
                failure = PrivilegeFailure.BackendDisconnected(),
                normalReady = false
            )
        )
        coordinator.reconnectAction = { requestAuthorization ->
            assertTrue(requestAuthorization)
            reconnectGate.await()
        }

        viewModel.reconnectAccess()
        runCurrent()

        val snapshot = viewModel.applicationAccess.value
        assertTrue(snapshot.readiness is ApplicationAccessReadiness.Connecting)
        assertEquals(
            ApplicationAccessCheck.RECONNECTING,
            (snapshot.readiness as ApplicationAccessReadiness.Connecting).check
        )
        assertTrue(snapshot.mayShowApplicationContent)

        reconnectGate.complete(Unit)
        runCurrent()
    }

    @Test
    fun `successful reconnect returns to ready without losing entered latch`() = runTest {
        val coordinator = FakeMainPrivilegeCoordinator().apply {
            startAction = { emit(rootReadyState(normalReady = false)) }
            reconnectAction = {
                emit(rootReadyState(normalReady = false, generation = 9))
            }
        }
        val viewModel = viewModel(coordinator)
        runCurrent()
        coordinator.emit(
            disconnectedState(
                PrivilegeBackendId.ROOT,
                PrivilegeFailure.BackendDisconnected(),
                normalReady = false
            )
        )

        viewModel.reconnectAccess()
        runCurrent()

        assertTrue(viewModel.applicationAccess.value.isReady)
        assertTrue(viewModel.applicationAccess.value.hasReachedReadyState)
        assertEquals(9L, (viewModel.applicationAccess.value.readiness as ApplicationAccessReadiness.Ready).connectionGeneration)
    }

    @Test
    fun `Use Normal delegates explicit fallback`() = runTest {
        val coordinator = FakeMainPrivilegeCoordinator()
        val viewModel = viewModel(coordinator)
        runCurrent()

        viewModel.useNormalAccess()
        runCurrent()

        assertEquals(1, coordinator.normalCalls)
    }

    @Test
    fun `Use Normal enters when all-files permission is ready`() = runTest {
        val coordinator = FakeMainPrivilegeCoordinator().apply {
            startAction = { emit(rootReadyState(normalReady = true)) }
            normalAction = { emit(normalReadyState(generation = 12)) }
        }
        val viewModel = viewModel(coordinator)
        runCurrent()

        viewModel.useNormalAccess()
        runCurrent()

        assertTrue(viewModel.applicationAccess.value.isReady)
        assertEquals(PrivilegeBackendId.NORMAL, viewModel.applicationAccess.value.activeBackend)
        assertTrue(viewModel.applicationAccess.value.mayShowApplicationContent)
    }

    @Test
    fun `unexpected access exception becomes visible failure`() = runTest {
        val coordinator = FakeMainPrivilegeCoordinator().apply {
            startAction = { throw IllegalStateException("probe exploded") }
        }

        val viewModel = viewModel(coordinator)
        runCurrent()

        val readiness = viewModel.applicationAccess.value.readiness
        assertTrue(readiness is ApplicationAccessReadiness.Failed)
        readiness as ApplicationAccessReadiness.Failed
        assertTrue(readiness.failure is PrivilegeFailure.Failed)
        assertEquals("Storage access check failed", readiness.failure.message)
    }

    @Test
    fun `successful later refresh clears earlier action failure`() = runTest {
        val coordinator = FakeMainPrivilegeCoordinator().apply {
            startAction = { throw IllegalStateException("first failure") }
            refreshAction = { emit(normalReadyState()) }
        }
        val viewModel = viewModel(coordinator)
        runCurrent()
        assertTrue(viewModel.applicationAccess.value.readiness is ApplicationAccessReadiness.Failed)

        viewModel.refreshAccess()
        runCurrent()

        assertTrue(viewModel.applicationAccess.value.isReady)
    }

    private fun viewModel(coordinator: PrivilegeCoordinator) = MainViewModel(
        browserPreferencesStore = FakeFilePreferencesStore(),
        privilegeCoordinator = coordinator
    )
}

private class FakeMainPrivilegeCoordinator(
    initialState: PrivilegeState = setupRequiredState()
) : PrivilegeCoordinator {
    private val mutableState = MutableStateFlow(initialState)
    override val state: StateFlow<PrivilegeState> = mutableState
    var startCalls = 0
        private set
    var refreshCalls = 0
        private set
    var normalCalls = 0
        private set
    val authorizationRequests = mutableListOf<Boolean>()
    var startAction: suspend FakeMainPrivilegeCoordinator.() -> Unit = {}
    var refreshAction: suspend FakeMainPrivilegeCoordinator.() -> Unit = {}
    var reconnectAction: suspend FakeMainPrivilegeCoordinator.(Boolean) -> Unit = {}
    var normalAction: suspend FakeMainPrivilegeCoordinator.() -> Unit = {}

    override suspend fun start() {
        startCalls += 1
        startAction()
    }

    override suspend fun refresh() {
        refreshCalls += 1
        refreshAction()
    }

    override suspend fun selectMode(mode: PrivilegeMode, requestAuthorization: Boolean) {
        authorizationRequests += requestAuthorization
    }

    override suspend fun reconnect(requestAuthorization: Boolean) {
        authorizationRequests += requestAuthorization
        reconnectAction(requestAuthorization)
    }

    override suspend fun useNormal() {
        normalCalls += 1
        normalAction()
    }

    override fun captureSession(): Result<PrivilegeSession> =
        Result.failure(PrivilegeFailure.BackendDisconnected())

    fun emit(state: PrivilegeState) {
        mutableState.value = state
    }
}

private fun setupRequiredState(): PrivilegeState = PrivilegeState(
    backendStates = baseBackends(normalReady = false)
)

private fun normalReadyState(generation: Long = 1): PrivilegeState = PrivilegeState(
    preferredMode = PrivilegeMode.NORMAL,
    activeBackend = PrivilegeBackendId.NORMAL,
    connectionGeneration = generation,
    backendStates = baseBackends(normalReady = true)
)

private fun rootReadyState(
    normalReady: Boolean,
    generation: Long = 2
): PrivilegeState = privilegedReadyState(
    backendId = PrivilegeBackendId.ROOT,
    mode = PrivilegeMode.ROOT,
    normalReady = normalReady,
    generation = generation
)

private fun shizukuReadyState(
    normalReady: Boolean,
    generation: Long = 3
): PrivilegeState = privilegedReadyState(
    backendId = PrivilegeBackendId.SHIZUKU,
    mode = PrivilegeMode.SHIZUKU,
    normalReady = normalReady,
    generation = generation
)

private fun privilegedReadyState(
    backendId: PrivilegeBackendId,
    mode: PrivilegeMode,
    normalReady: Boolean,
    generation: Long
): PrivilegeState {
    val identity = serviceIdentity(backendId)
    val backends = baseBackends(normalReady).toMutableMap()
    backends[backendId] = PrivilegeBackendState(
        backendId = backendId,
        connectionState = PrivilegeConnectionState.READY,
        identity = identity
    )
    return PrivilegeState(
        preferredMode = mode,
        activeBackend = backendId,
        connectionGeneration = generation,
        backendStates = backends,
        identity = identity
    )
}

private fun disconnectedState(
    backendId: PrivilegeBackendId,
    failure: PrivilegeFailure,
    normalReady: Boolean
): PrivilegeState {
    val backends = baseBackends(normalReady).toMutableMap()
    backends[backendId] = PrivilegeBackendState(
        backendId = backendId,
        connectionState = PrivilegeConnectionState.DISCONNECTED,
        failure = failure
    )
    return PrivilegeState(
        preferredMode = if (backendId == PrivilegeBackendId.ROOT) PrivilegeMode.ROOT else PrivilegeMode.SHIZUKU,
        activeBackend = backendId,
        connectionGeneration = 7,
        backendStates = backends,
        lastFailure = failure
    )
}

private fun baseBackends(normalReady: Boolean): Map<PrivilegeBackendId, PrivilegeBackendState> = mapOf(
    PrivilegeBackendId.ROOT to PrivilegeBackendState(
        PrivilegeBackendId.ROOT,
        PrivilegeConnectionState.UNAVAILABLE
    ),
    PrivilegeBackendId.SHIZUKU to PrivilegeBackendState(
        PrivilegeBackendId.SHIZUKU,
        PrivilegeConnectionState.UNAVAILABLE
    ),
    PrivilegeBackendId.NORMAL to PrivilegeBackendState(
        PrivilegeBackendId.NORMAL,
        if (normalReady) PrivilegeConnectionState.READY else PrivilegeConnectionState.PERMISSION_REQUIRED,
        failure = if (normalReady) null else PrivilegeFailure.Failed("Normal permission required")
    )
)

private fun serviceIdentity(id: PrivilegeBackendId) = PrivilegeServiceIdentity(
    effectiveUid = if (id == PrivilegeBackendId.ROOT) 0 else 2000,
    pid = 70,
    transport = if (id == PrivilegeBackendId.ROOT) {
        PrivilegeTransport.ROOT_SERVICE
    } else {
        PrivilegeTransport.SHIZUKU_USER_SERVICE
    }
)
