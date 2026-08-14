package dev.qtremors.arcile.core.privilege.android.root

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.qtremors.arcile.core.privilege.PRIVILEGED_FILESYSTEM_PROTOCOL_VERSION
import dev.qtremors.arcile.core.privilege.PrivilegeBackendId
import dev.qtremors.arcile.core.privilege.PrivilegeBackendState
import dev.qtremors.arcile.core.privilege.PrivilegeConnectionState
import dev.qtremors.arcile.core.privilege.PrivilegeFailure
import dev.qtremors.arcile.core.privilege.PrivilegeSession
import dev.qtremors.arcile.core.privilege.PrivilegeTransport
import dev.qtremors.arcile.core.privilege.android.connection.BackendConnection
import dev.qtremors.arcile.core.privilege.android.connection.BackendConnector
import dev.qtremors.arcile.core.privilege.android.connection.BinderPrivilegedFileClient
import dev.qtremors.arcile.core.privilege.android.connection.toDomain
import dev.qtremors.arcile.core.privilege.android.remote.IPrivilegedFileService
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class RootBackendConnector @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val facade: RootFacade,
    private val dispatchers: ArcileDispatchers
) : BackendConnector {
    override val backendId = PrivilegeBackendId.ROOT

    override fun probe(): PrivilegeBackendState {
        val binaryAvailable = facade.isRootBinaryAvailable()
        val authorization = facade.cachedAuthorization()
        val state = when {
            !binaryAvailable -> PrivilegeConnectionState.UNAVAILABLE
            authorization == true -> PrivilegeConnectionState.DISCONNECTED
            authorization == false -> PrivilegeConnectionState.PERMISSION_DENIED
            else -> PrivilegeConnectionState.PERMISSION_REQUIRED
        }
        return PrivilegeBackendState(
            backendId = backendId,
            connectionState = state,
            failure = when (state) {
                PrivilegeConnectionState.UNAVAILABLE -> PrivilegeFailure.RootUnavailable()
                PrivilegeConnectionState.PERMISSION_DENIED -> PrivilegeFailure.RootPermissionDenied()
                else -> null
            }
        )
    }

    override suspend fun connect(requestAuthorization: Boolean): Result<BackendConnection> = runCatching {
        val probe = probe()
        when {
            probe.connectionState == PrivilegeConnectionState.UNAVAILABLE ->
                throw PrivilegeFailure.RootUnavailable()
            facade.cachedAuthorization() == false && !requestAuthorization ->
                throw PrivilegeFailure.RootPermissionDenied()
            facade.cachedAuthorization() != true && !requestAuthorization ->
                throw PrivilegeFailure.Failed("Root permission is required")
        }

        val intent = Intent(context, ArcileRootFileService::class.java)
        val pending = PendingRootConnection(facade)
        val binder = try {
            withTimeout(CONNECTION_TIMEOUT_MILLIS) {
                pending.awaitBinder(intent)
            }
        } catch (error: kotlinx.coroutines.TimeoutCancellationException) {
            pending.close()
            throw PrivilegeFailure.ConnectionTimedOut(error)
        } catch (error: Throwable) {
            pending.close()
            if (facade.cachedAuthorization() == false) {
                throw PrivilegeFailure.RootPermissionDenied(error)
            }
            throw error
        }

        try {
            createConnection(pending, binder)
        } catch (error: Throwable) {
            pending.close()
            throw error
        }
    }

    private suspend fun createConnection(pending: PendingRootConnection, binder: IBinder): BackendConnection =
        withContext(dispatchers.io) {
            if (!binder.pingBinder()) throw PrivilegeFailure.BackendDisconnected()
            val service = IPrivilegedFileService.Stub.asInterface(binder)
            val handshake = service.handshake().toDomain()
            if (handshake.protocolVersion != PRIVILEGED_FILESYSTEM_PROTOCOL_VERSION) {
                throw PrivilegeFailure.Incompatible(
                    PRIVILEGED_FILESYSTEM_PROTOCOL_VERSION,
                    handshake.protocolVersion
                )
            }
            if (!handshake.identity.isRoot || handshake.identity.transport != PrivilegeTransport.ROOT_SERVICE) {
                throw PrivilegeFailure.UnexpectedIdentity(handshake.identity.effectiveUid)
            }
            val session = PrivilegeSession(
                backendId = backendId,
                generation = 0,
                identity = handshake.identity,
                capabilities = handshake.capabilities
            )
            val client = BinderPrivilegedFileClient(session, service, dispatchers, pending::close)
            pending.attachDeathRecipient(binder)
            RootConnection(pending, client)
        }

    private companion object {
        const val CONNECTION_TIMEOUT_MILLIS = 10_000L
    }
}

private class RootConnection(
    private val pending: PendingRootConnection,
    override val client: BinderPrivilegedFileClient
) : BackendConnection {
    override val backendId = PrivilegeBackendId.ROOT
    override val deathEvents = pending.deathEvents
    override fun activate(generation: Long) = client.activate(generation)
    override suspend fun close() = pending.close()
}

private class PendingRootConnection(
    private val facade: RootFacade
) : ServiceConnection {
    private val closed = AtomicBoolean(false)
    private val deaths = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var continuation: CancellableContinuation<IBinder>? = null
    val deathEvents = deaths.asSharedFlow()

    suspend fun awaitBinder(intent: Intent): IBinder = suspendCancellableCoroutine { pending ->
        continuation = pending
        pending.invokeOnCancellation { close() }
        try {
            facade.bind(intent, this)
        } catch (error: Throwable) {
            continuation = null
            if (pending.isActive) pending.resumeWithException(error)
        }
    }

    fun attachDeathRecipient(binder: IBinder) {
        binder.linkToDeath({ deaths.tryEmit(Unit) }, 0)
    }

    override fun onServiceConnected(name: ComponentName, service: IBinder) {
        continuation?.let { pending ->
            continuation = null
            if (pending.isActive) pending.resume(service)
        }
    }

    override fun onServiceDisconnected(name: ComponentName) {
        deaths.tryEmit(Unit)
    }

    override fun onBindingDied(name: ComponentName) {
        deaths.tryEmit(Unit)
        continuation?.let { pending ->
            continuation = null
            if (pending.isActive) pending.resumeWithException(PrivilegeFailure.BackendDisconnected())
        }
    }

    override fun onNullBinding(name: ComponentName) {
        continuation?.let { pending ->
            continuation = null
            if (pending.isActive) pending.resumeWithException(PrivilegeFailure.Failed("Root service returned no binder"))
        }
    }

    fun close() {
        if (closed.compareAndSet(false, true)) runCatching { facade.unbind(this) }
    }
}
