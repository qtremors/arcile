package dev.qtremors.arcile.core.privilege.android

import android.os.Environment
import dev.qtremors.arcile.core.privilege.PrivilegeBackendId
import dev.qtremors.arcile.core.privilege.PrivilegeBackendState
import dev.qtremors.arcile.core.privilege.PrivilegeCapability
import dev.qtremors.arcile.core.privilege.PrivilegeConnectionState
import dev.qtremors.arcile.core.privilege.PrivilegeCoordinator
import dev.qtremors.arcile.core.privilege.PrivilegeFailure
import dev.qtremors.arcile.core.privilege.PrivilegeMode
import dev.qtremors.arcile.core.privilege.PrivilegePreferenceState
import dev.qtremors.arcile.core.privilege.PrivilegePreferences
import dev.qtremors.arcile.core.privilege.PrivilegeServiceIdentity
import dev.qtremors.arcile.core.privilege.PrivilegeSession
import dev.qtremors.arcile.core.privilege.PrivilegeState
import dev.qtremors.arcile.core.privilege.PrivilegedFileClient
import dev.qtremors.arcile.core.privilege.PrivilegedFileClientProvider
import dev.qtremors.arcile.core.privilege.android.connection.BackendConnection
import dev.qtremors.arcile.core.privilege.android.connection.BackendConnector
import dev.qtremors.arcile.core.privilege.android.connection.RootBackend
import dev.qtremors.arcile.core.privilege.android.connection.ShizukuBackend
import dev.qtremors.arcile.core.runtime.di.ApplicationScope
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface NormalStorageAccessGateway {
    fun isReady(): Boolean
}

@Singleton
class AndroidNormalStorageAccessGateway @Inject constructor() : NormalStorageAccessGateway {
    override fun isReady(): Boolean = Environment.isExternalStorageManager()
}

@Singleton
class DefaultPrivilegeCoordinator @Inject constructor(
    private val preferences: PrivilegePreferences,
    @param:RootBackend private val rootConnector: BackendConnector,
    @param:ShizukuBackend private val shizukuConnector: BackendConnector,
    private val normalAccess: NormalStorageAccessGateway,
    @param:ApplicationScope private val applicationScope: CoroutineScope
) : PrivilegeCoordinator, PrivilegedFileClientProvider {
    private val mutex = Mutex()
    private val started = AtomicBoolean(false)
    private val mutableState = MutableStateFlow(PrivilegeState())
    private var currentConnection: BackendConnection? = null
    private var deathObserver: Job? = null
    private var generation = 0L

    override val state: StateFlow<PrivilegeState> = mutableState.asStateFlow()

    override suspend fun start() {
        mutex.withLock {
            val preference = preferences.state.first()
            if (started.compareAndSet(false, true)) {
                transition(preference, requestAuthorization = false)
            } else {
                refreshLocked(preference)
            }
        }
    }

    override suspend fun refresh() {
        mutex.withLock {
            refreshLocked(preferences.state.first())
        }
    }

    override suspend fun selectMode(mode: PrivilegeMode, requestAuthorization: Boolean) {
        mutex.withLock {
            preferences.setMode(mode)
            val preference = preferences.state.first().copy(mode = mode)
            transition(
                preference = preference,
                requestAuthorization = requestAuthorization && mode != PrivilegeMode.AUTOMATIC
            )
        }
    }

    override suspend fun reconnect(requestAuthorization: Boolean) {
        mutex.withLock {
            val preference = preferences.state.first()
            transition(
                preference = preference,
                requestAuthorization = requestAuthorization && preference.mode != PrivilegeMode.AUTOMATIC
            )
        }
    }

    override suspend fun useNormal() {
        selectMode(PrivilegeMode.NORMAL, requestAuthorization = false)
    }

    override fun captureSession(): Result<PrivilegeSession> {
        val snapshot = state.value
        val backendId = snapshot.activeBackend
            ?: return Result.failure(PrivilegeFailure.Failed("No privileged backend is active"))
        val identity = snapshot.identity
            ?: return Result.failure(PrivilegeFailure.Failed("The active backend is not privileged"))
        if (!snapshot.isReady || backendId == PrivilegeBackendId.NORMAL) {
            return Result.failure(PrivilegeFailure.BackendDisconnected())
        }
        return Result.success(
            PrivilegeSession(
                backendId = backendId,
                generation = snapshot.connectionGeneration,
                identity = identity,
                capabilities = snapshot.capabilities
            )
        )
    }

    override fun activeClient(): Result<PrivilegedFileClient> =
        captureSession().fold(::clientFor) { Result.failure(it) }

    override fun clientFor(session: PrivilegeSession): Result<PrivilegedFileClient> {
        val snapshot = state.value
        val connection = currentConnection
        if (
            connection == null ||
            !snapshot.isReady ||
            snapshot.activeBackend != session.backendId ||
            snapshot.connectionGeneration != session.generation ||
            connection.backendId != session.backendId ||
            connection.client.session.generation != session.generation
        ) {
            return Result.failure(PrivilegeFailure.BackendDisconnected())
        }
        return Result.success(connection.client)
    }

    private suspend fun refreshLocked(preference: PrivilegePreferenceState) {
        val probes = probeAll()
        val activeBackend = mutableState.value.activeBackend
        val connection = currentConnection

        if (activeBackend == PrivilegeBackendId.NORMAL && connection == null) {
            val normalProbe = probes.getValue(PrivilegeBackendId.NORMAL)
            mutableState.value = if (
                normalProbe.connectionState == PrivilegeConnectionState.READY
            ) {
                mutableState.value.copy(
                    preferredMode = preference.mode,
                    backendStates = probes.toMap(),
                    lastFailure = null
                )
            } else {
                generation += 1
                PrivilegeState(
                    preferredMode = preference.mode,
                    activeBackend = PrivilegeBackendId.NORMAL,
                    connectionGeneration = generation,
                    backendStates = probes.toMap(),
                    lastFailure = normalProbe.failure
                )
            }
            return
        }

        if (connection != null && activeBackend != null) {
            val activeProbe = probes.getValue(activeBackend)
            if (isConnectionStillUsable(activeBackend, activeProbe)) {
                val readyState = mutableState.value.backendStates[activeBackend]
                mutableState.value = mutableState.value.copy(
                    preferredMode = preference.mode,
                    backendStates = probes + listOfNotNull(
                        readyState?.takeIf { it.connectionState == PrivilegeConnectionState.READY }
                            ?.let { activeBackend to it }
                    )
                )
                return
            }
            disconnectForFailure(activeBackend, activeProbe.failure ?: disconnectedFailure(activeBackend), probes)
            return
        }

        if (activeBackend != null && mutableState.value.connectionState == PrivilegeConnectionState.DISCONNECTED) {
            mutableState.value = mutableState.value.copy(
                preferredMode = preference.mode,
                backendStates = probes + (activeBackend to (mutableState.value.backendStates[activeBackend]
                    ?: probes.getValue(activeBackend)))
            )
            return
        }
        transition(preference, requestAuthorization = false)
    }

    private suspend fun transition(
        preference: PrivilegePreferenceState,
        requestAuthorization: Boolean
    ) {
        closeCurrentConnection()
        val probes = probeAll().toMutableMap()
        mutableState.value = PrivilegeState(
            preferredMode = preference.mode,
            connectionGeneration = generation,
            backendStates = probes
        )

        when (preference.mode) {
            PrivilegeMode.AUTOMATIC -> {
                if (preference.rootPreviouslyAuthorized) {
                    val root = connect(rootConnector, requestAuthorization = false, probes)
                    if (root) return
                }
                if (preference.shizukuPreviouslyAuthorized) {
                    val shizuku = connect(shizukuConnector, requestAuthorization = false, probes)
                    if (shizuku) return
                }
                activateNormal(preference.mode, probes)
            }
            PrivilegeMode.ROOT -> {
                val connected = connect(rootConnector, requestAuthorization, probes)
                if (connected && requestAuthorization) {
                    preferences.setRootPreviouslyAuthorized(true)
                } else if (!connected && mutableState.value.lastFailure is PrivilegeFailure.RootPermissionDenied) {
                    preferences.setRootPreviouslyAuthorized(false)
                }
            }
            PrivilegeMode.SHIZUKU -> {
                val connected = connect(shizukuConnector, requestAuthorization, probes)
                if (connected && requestAuthorization) {
                    preferences.setShizukuPreviouslyAuthorized(true)
                } else if (!connected && mutableState.value.lastFailure is PrivilegeFailure.ShizukuPermissionDenied) {
                    preferences.setShizukuPreviouslyAuthorized(false)
                }
            }
            PrivilegeMode.NORMAL -> activateNormal(preference.mode, probes)
        }
    }

    private suspend fun connect(
        connector: BackendConnector,
        requestAuthorization: Boolean,
        probes: MutableMap<PrivilegeBackendId, PrivilegeBackendState>
    ): Boolean {
        val connecting = PrivilegeBackendState(
            backendId = connector.backendId,
            connectionState = PrivilegeConnectionState.CONNECTING
        )
        probes[connector.backendId] = connecting
        mutableState.value = mutableState.value.copy(backendStates = probes.toMap())

        val result = connector.connect(requestAuthorization)
        val connection = result.getOrElse { error ->
            val failure = error as? PrivilegeFailure
                ?: PrivilegeFailure.Failed("Privileged service connection failed", error)
            val failed = failure.toBackendState(connector.backendId)
            probes[connector.backendId] = failed
            mutableState.value = PrivilegeState(
                preferredMode = mutableState.value.preferredMode,
                connectionGeneration = generation,
                backendStates = probes.toMap(),
                lastFailure = failure
            )
            return false
        }

        generation += 1
        val client = connection.activate(generation)
        currentConnection = connection
        val session = client.session
        val ready = PrivilegeBackendState(
            backendId = connector.backendId,
            connectionState = PrivilegeConnectionState.READY,
            identity = session.identity,
            capabilities = session.capabilities
        )
        probes[connector.backendId] = ready
        mutableState.value = PrivilegeState(
            preferredMode = mutableState.value.preferredMode,
            activeBackend = connector.backendId,
            connectionGeneration = generation,
            backendStates = probes.toMap(),
            identity = session.identity,
            capabilities = session.capabilities
        )
        observeDeath(connection)
        return true
    }

    private fun activateNormal(
        preferredMode: PrivilegeMode,
        probes: MutableMap<PrivilegeBackendId, PrivilegeBackendState>
    ) {
        val normalState = probes.getValue(PrivilegeBackendId.NORMAL)
        probes[PrivilegeBackendId.NORMAL] = normalState
        mutableState.value = if (normalState.connectionState == PrivilegeConnectionState.READY) {
            generation += 1
            PrivilegeState(
                preferredMode = preferredMode,
                activeBackend = PrivilegeBackendId.NORMAL,
                connectionGeneration = generation,
                backendStates = probes.toMap()
            )
        } else {
            PrivilegeState(
                preferredMode = preferredMode,
                connectionGeneration = generation,
                backendStates = probes.toMap(),
                lastFailure = normalState.failure
            )
        }
    }

    private fun probeAll(): MutableMap<PrivilegeBackendId, PrivilegeBackendState> {
        val normalReady = normalAccess.isReady()
        return mutableMapOf(
            PrivilegeBackendId.ROOT to rootConnector.probe(),
            PrivilegeBackendId.SHIZUKU to shizukuConnector.probe(),
            PrivilegeBackendId.NORMAL to PrivilegeBackendState(
                backendId = PrivilegeBackendId.NORMAL,
                connectionState = if (normalReady) {
                    PrivilegeConnectionState.READY
                } else {
                    PrivilegeConnectionState.PERMISSION_REQUIRED
                },
                failure = if (normalReady) {
                    null
                } else {
                    PrivilegeFailure.Failed("All-files access is required for Normal storage")
                }
            )
        )
    }

    private fun isConnectionStillUsable(
        backendId: PrivilegeBackendId,
        probe: PrivilegeBackendState
    ): Boolean = when (backendId) {
        PrivilegeBackendId.ROOT -> probe.connectionState == PrivilegeConnectionState.DISCONNECTED
        PrivilegeBackendId.SHIZUKU -> probe.connectionState == PrivilegeConnectionState.DISCONNECTED
        else -> false
    }

    private fun observeDeath(connection: BackendConnection) {
        deathObserver?.cancel()
        deathObserver = applicationScope.launch {
            connection.deathEvents.first()
            mutex.withLock {
                if (currentConnection === connection) {
                    deathObserver = null
                    val probes = probeAll()
                    disconnectForFailure(
                        connection.backendId,
                        disconnectedFailure(connection.backendId),
                        probes
                    )
                }
            }
        }
    }

    private suspend fun disconnectForFailure(
        backendId: PrivilegeBackendId,
        failure: PrivilegeFailure,
        probes: MutableMap<PrivilegeBackendId, PrivilegeBackendState>
    ) {
        closeCurrentConnection()
        generation += 1
        probes[backendId] = PrivilegeBackendState(
            backendId = backendId,
            connectionState = PrivilegeConnectionState.DISCONNECTED,
            failure = failure
        )
        if (mutableState.value.preferredMode == PrivilegeMode.AUTOMATIC) {
            activateNormal(PrivilegeMode.AUTOMATIC, probes)
            return
        }
        mutableState.value = PrivilegeState(
            preferredMode = mutableState.value.preferredMode,
            activeBackend = backendId,
            connectionGeneration = generation,
            backendStates = probes.toMap(),
            lastFailure = failure
        )
    }

    private suspend fun closeCurrentConnection() {
        deathObserver?.cancel()
        deathObserver = null
        val connection = currentConnection
        currentConnection = null
        connection?.close()
    }

    private fun disconnectedFailure(backendId: PrivilegeBackendId) = when (backendId) {
        PrivilegeBackendId.SHIZUKU -> PrivilegeFailure.ShizukuNotRunning()
        else -> PrivilegeFailure.BackendDisconnected()
    }
}

private fun PrivilegeFailure.toBackendState(backendId: PrivilegeBackendId): PrivilegeBackendState {
    val connectionState = when (this) {
        is PrivilegeFailure.RootUnavailable -> PrivilegeConnectionState.UNAVAILABLE
        is PrivilegeFailure.RootPermissionDenied -> PrivilegeConnectionState.PERMISSION_DENIED
        is PrivilegeFailure.ShizukuNotRunning -> PrivilegeConnectionState.INSTALLED_BUT_STOPPED
        is PrivilegeFailure.ShizukuPermissionRequired -> PrivilegeConnectionState.PERMISSION_REQUIRED
        is PrivilegeFailure.ShizukuPermissionDenied -> PrivilegeConnectionState.PERMISSION_DENIED
        is PrivilegeFailure.BackendDisconnected -> PrivilegeConnectionState.DISCONNECTED
        is PrivilegeFailure.Incompatible, is PrivilegeFailure.UnexpectedIdentity ->
            PrivilegeConnectionState.INCOMPATIBLE
        else -> PrivilegeConnectionState.FAILED
    }
    return PrivilegeBackendState(
        backendId = backendId,
        connectionState = connectionState,
        failure = this
    )
}
