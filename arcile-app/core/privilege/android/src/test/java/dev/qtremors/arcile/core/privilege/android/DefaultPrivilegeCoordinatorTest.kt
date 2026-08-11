package dev.qtremors.arcile.core.privilege.android

import dev.qtremors.arcile.core.privilege.PrivilegeBackendId
import dev.qtremors.arcile.core.privilege.PrivilegeBackendState
import dev.qtremors.arcile.core.privilege.PrivilegeConnectionState
import dev.qtremors.arcile.core.privilege.PrivilegeFailure
import dev.qtremors.arcile.core.privilege.PrivilegeMode
import dev.qtremors.arcile.core.privilege.PrivilegePreferenceState
import dev.qtremors.arcile.core.privilege.PrivilegePreferences
import dev.qtremors.arcile.core.privilege.PrivilegeServiceIdentity
import dev.qtremors.arcile.core.privilege.PrivilegeSession
import dev.qtremors.arcile.core.privilege.PrivilegeTransport
import dev.qtremors.arcile.core.privilege.PrivilegedDirectoryPage
import dev.qtremors.arcile.core.privilege.PrivilegedFileClient
import dev.qtremors.arcile.core.privilege.PrivilegedFileEntry
import dev.qtremors.arcile.core.privilege.PrivilegedFileHandle
import dev.qtremors.arcile.core.privilege.PrivilegedFilesystemStats
import dev.qtremors.arcile.core.privilege.PrivilegedHandshake
import dev.qtremors.arcile.core.privilege.PrivilegedOpenMode
import dev.qtremors.arcile.core.privilege.PrivilegedOperationId
import dev.qtremors.arcile.core.privilege.PrivilegedOperationProgress
import dev.qtremors.arcile.core.privilege.android.connection.BackendConnection
import dev.qtremors.arcile.core.privilege.android.connection.BackendConnector
import java.util.ArrayDeque
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultPrivilegeCoordinatorTest {
    @Test
    fun `automatic prefers authorized root without prompting`() = runTest {
        val root = FakeConnector(PrivilegeBackendId.ROOT).apply { enqueueSuccess() }
        val shizuku = FakeConnector(PrivilegeBackendId.SHIZUKU).apply { enqueueSuccess() }
        val coordinator = coordinator(
            preferences = FakePreferences(
                PrivilegePreferenceState(
                    mode = PrivilegeMode.AUTOMATIC,
                    rootPreviouslyAuthorized = true,
                    shizukuPreviouslyAuthorized = true
                )
            ),
            root = root,
            shizuku = shizuku
        )

        coordinator.start()

        assertEquals(PrivilegeBackendId.ROOT, coordinator.state.value.activeBackend)
        assertEquals(listOf(false), root.authorizationRequests)
        assertTrue(shizuku.authorizationRequests.isEmpty())
    }

    @Test
    fun `automatic falls through failed root to authorized Shizuku without prompting`() = runTest {
        val root = FakeConnector(PrivilegeBackendId.ROOT).apply {
            enqueueFailure(PrivilegeFailure.RootUnavailable())
        }
        val shizuku = FakeConnector(PrivilegeBackendId.SHIZUKU).apply { enqueueSuccess() }
        val coordinator = coordinator(
            preferences = FakePreferences(
                PrivilegePreferenceState(
                    mode = PrivilegeMode.AUTOMATIC,
                    rootPreviouslyAuthorized = true,
                    shizukuPreviouslyAuthorized = true
                )
            ),
            root = root,
            shizuku = shizuku
        )

        coordinator.start()

        assertEquals(PrivilegeBackendId.SHIZUKU, coordinator.state.value.activeBackend)
        assertEquals(listOf(false), root.authorizationRequests)
        assertEquals(listOf(false), shizuku.authorizationRequests)
    }

    @Test
    fun `explicit root failure never silently activates normal`() = runTest {
        val root = FakeConnector(PrivilegeBackendId.ROOT).apply {
            enqueueFailure(PrivilegeFailure.RootPermissionDenied())
        }
        val coordinator = coordinator(
            preferences = FakePreferences(PrivilegePreferenceState(mode = PrivilegeMode.ROOT)),
            root = root
        )

        coordinator.start()

        assertNull(coordinator.state.value.activeBackend)
        assertTrue(coordinator.state.value.lastFailure is PrivilegeFailure.RootPermissionDenied)
        assertEquals(
            PrivilegeConnectionState.READY,
            coordinator.state.value.backendStates.getValue(PrivilegeBackendId.NORMAL).connectionState
        )
    }

    @Test
    fun `binder death invalidates generation without automatic fallback`() = runTest {
        val root = FakeConnector(PrivilegeBackendId.ROOT).apply { enqueueSuccess() }
        val coordinator = coordinator(
            preferences = FakePreferences(
                PrivilegePreferenceState(
                    mode = PrivilegeMode.AUTOMATIC,
                    rootPreviouslyAuthorized = true
                )
            ),
            root = root
        )
        coordinator.start()
        val originalSession = coordinator.captureSession().getOrThrow()

        root.latestConnection!!.die()
        runCurrent()

        assertEquals(PrivilegeBackendId.ROOT, coordinator.state.value.activeBackend)
        assertEquals(PrivilegeConnectionState.DISCONNECTED, coordinator.state.value.connectionState)
        assertTrue(coordinator.state.value.connectionGeneration > originalSession.generation)
        assertTrue(coordinator.clientFor(originalSession).isFailure)
    }

    @Test
    fun `switching backend isolates sessions from earlier operations`() = runTest {
        val preferences = FakePreferences(PrivilegePreferenceState(mode = PrivilegeMode.ROOT))
        val root = FakeConnector(PrivilegeBackendId.ROOT).apply { enqueueSuccess() }
        val shizuku = FakeConnector(PrivilegeBackendId.SHIZUKU).apply { enqueueSuccess() }
        val coordinator = coordinator(preferences, root, shizuku)
        coordinator.start()
        val rootSession = coordinator.captureSession().getOrThrow()

        coordinator.selectMode(PrivilegeMode.SHIZUKU, requestAuthorization = true)

        assertEquals(PrivilegeBackendId.SHIZUKU, coordinator.state.value.activeBackend)
        assertEquals(listOf(true), shizuku.authorizationRequests)
        assertTrue(preferences.value.shizukuPreviouslyAuthorized)
        assertTrue(coordinator.clientFor(rootSession).isFailure)
        assertFalse(root.latestConnection!!.isOpen)
    }

    @Test
    fun `normal remains setup required without all files access`() = runTest {
        val coordinator = coordinator(
            preferences = FakePreferences(PrivilegePreferenceState(mode = PrivilegeMode.NORMAL)),
            normalReady = false
        )

        coordinator.start()

        assertNull(coordinator.state.value.activeBackend)
        assertEquals(
            PrivilegeConnectionState.PERMISSION_REQUIRED,
            coordinator.state.value.backendStates.getValue(PrivilegeBackendId.NORMAL).connectionState
        )
    }

    private fun TestScope.coordinator(
        preferences: FakePreferences = FakePreferences(),
        root: FakeConnector = FakeConnector(PrivilegeBackendId.ROOT),
        shizuku: FakeConnector = FakeConnector(PrivilegeBackendId.SHIZUKU),
        normalReady: Boolean = true
    ) = DefaultPrivilegeCoordinator(
        preferences = preferences,
        rootConnector = root,
        shizukuConnector = shizuku,
        normalAccess = object : NormalStorageAccessGateway {
            override fun isReady() = normalReady
        },
        applicationScope = backgroundScope
    )
}

private class FakePreferences(
    initial: PrivilegePreferenceState = PrivilegePreferenceState()
) : PrivilegePreferences {
    private val mutable = MutableStateFlow(initial)
    val value get() = mutable.value
    override val state: Flow<PrivilegePreferenceState> = mutable

    override suspend fun setMode(mode: PrivilegeMode) {
        mutable.value = mutable.value.copy(mode = mode)
    }

    override suspend fun setRootPreviouslyAuthorized(authorized: Boolean) {
        mutable.value = mutable.value.copy(rootPreviouslyAuthorized = authorized)
    }

    override suspend fun setShizukuPreviouslyAuthorized(authorized: Boolean) {
        mutable.value = mutable.value.copy(shizukuPreviouslyAuthorized = authorized)
    }

    override suspend fun setProtectedFilesystemWritesEnabled(enabled: Boolean) {
        mutable.value = mutable.value.copy(protectedFilesystemWritesEnabled = enabled)
    }
}

private class FakeConnector(
    override val backendId: PrivilegeBackendId
) : BackendConnector {
    private val results = ArrayDeque<Result<BackendConnection>>()
    val authorizationRequests = mutableListOf<Boolean>()
    var latestConnection: FakeConnection? = null

    override fun probe() = PrivilegeBackendState(
        backendId = backendId,
        connectionState = PrivilegeConnectionState.DISCONNECTED
    )

    override suspend fun connect(requestAuthorization: Boolean): Result<BackendConnection> {
        authorizationRequests += requestAuthorization
        return results.pollFirst()
            ?: Result.failure(PrivilegeFailure.Failed("No fake connection queued"))
    }

    fun enqueueSuccess() {
        val connection = FakeConnection(backendId)
        latestConnection = connection
        results.addLast(Result.success(connection))
    }

    fun enqueueFailure(failure: PrivilegeFailure) {
        results.addLast(Result.failure(failure))
    }
}

private class FakeConnection(
    override val backendId: PrivilegeBackendId
) : BackendConnection {
    private val deaths = MutableSharedFlow<Unit>(replay = 1)
    override val deathEvents: Flow<Unit> = deaths
    override val client = FakePrivilegedFileClient(backendId)
    var isOpen = true
        private set

    override fun activate(generation: Long): PrivilegedFileClient {
        client.generation = generation
        return client
    }

    override suspend fun close() {
        isOpen = false
    }

    fun die() {
        deaths.tryEmit(Unit)
    }
}

private class FakePrivilegedFileClient(
    backendId: PrivilegeBackendId
) : PrivilegedFileClient {
    private val identity = PrivilegeServiceIdentity(
        effectiveUid = if (backendId == PrivilegeBackendId.ROOT) 0 else 2000,
        pid = 9,
        transport = if (backendId == PrivilegeBackendId.ROOT) {
            PrivilegeTransport.ROOT_SERVICE
        } else {
            PrivilegeTransport.SHIZUKU_USER_SERVICE
        }
    )
    override var session = PrivilegeSession(backendId, 0, identity, emptySet())
    var generation: Long
        get() = session.generation
        set(value) {
            session = session.copy(generation = value)
        }

    override suspend fun handshake() = PrivilegedHandshake(1, identity, emptySet(), 250)
    override suspend fun canonicalizeAndLstat(path: String): Result<PrivilegedFileEntry> = unsupported()
    override suspend fun listDirectory(
        path: String,
        pageToken: String?,
        pageSize: Int
    ): Result<PrivilegedDirectoryPage> = unsupported()
    override suspend fun filesystemStats(path: String): Result<PrivilegedFilesystemStats> = unsupported()
    override suspend fun createFile(path: String): Result<PrivilegedFileEntry> = unsupported()
    override suspend fun createDirectory(path: String): Result<PrivilegedFileEntry> = unsupported()
    override suspend fun open(path: String, mode: PrivilegedOpenMode): Result<PrivilegedFileHandle> = unsupported()
    override suspend fun rename(sourcePath: String, destinationPath: String): Result<Unit> = unsupported()
    override suspend fun copy(
        sourcePath: String,
        destinationPath: String,
        operationId: PrivilegedOperationId,
        onProgress: ((PrivilegedOperationProgress) -> Unit)?
    ): Result<Unit> = unsupported()
    override suspend fun move(
        sourcePath: String,
        destinationPath: String,
        operationId: PrivilegedOperationId,
        onProgress: ((PrivilegedOperationProgress) -> Unit)?
    ): Result<Unit> = unsupported()
    override suspend fun delete(path: String): Result<Unit> = unsupported()
    override suspend fun deleteRecursively(
        path: String,
        operationId: PrivilegedOperationId,
        onProgress: ((PrivilegedOperationProgress) -> Unit)?
    ): Result<Unit> = unsupported()
    override suspend fun secureOverwrite(
        path: String,
        operationId: PrivilegedOperationId,
        onProgress: ((PrivilegedOperationProgress) -> Unit)?
    ): Result<Unit> = unsupported()
    override suspend fun updateTimestamps(
        path: String,
        accessedAtMillis: Long,
        modifiedAtMillis: Long
    ): Result<Unit> = unsupported()
    override suspend fun cancel(operationId: PrivilegedOperationId): Result<Unit> = unsupported()
    override fun close() = Unit

    private fun <T> unsupported(): Result<T> = Result.failure(UnsupportedOperationException())
}
