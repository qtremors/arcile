package dev.qtremors.arcile.core.privilege.android.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
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
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku

internal class ShizukuBackendConnector @Inject constructor(
    @ApplicationContext context: Context,
    private val facade: ShizukuFacade,
    private val dispatchers: ArcileDispatchers
) : BackendConnector {
    override val backendId = PrivilegeBackendId.SHIZUKU
    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName(context, ArcileShizukuFileService::class.java)
    )
        .daemon(false)
        .tag("arcile_privileged_files")
        .version(PRIVILEGED_FILESYSTEM_PROTOCOL_VERSION)
        .processNameSuffix("arcile_privilege")

    override fun probe(): PrivilegeBackendState {
        val state = when {
            !facade.isManagerInstalled() -> PrivilegeConnectionState.UNAVAILABLE
            !facade.isBinderAlive() -> PrivilegeConnectionState.INSTALLED_BUT_STOPPED
            facade.apiVersion() < MINIMUM_SHIZUKU_API -> PrivilegeConnectionState.INCOMPATIBLE
            facade.checkPermission() == PackageManager.PERMISSION_GRANTED -> PrivilegeConnectionState.DISCONNECTED
            facade.shouldShowPermissionRationale() -> PrivilegeConnectionState.PERMISSION_DENIED
            else -> PrivilegeConnectionState.PERMISSION_REQUIRED
        }
        return PrivilegeBackendState(
            backendId = backendId,
            connectionState = state,
            failure = when (state) {
                PrivilegeConnectionState.INSTALLED_BUT_STOPPED -> PrivilegeFailure.ShizukuNotRunning()
                PrivilegeConnectionState.PERMISSION_REQUIRED -> PrivilegeFailure.ShizukuPermissionRequired()
                PrivilegeConnectionState.PERMISSION_DENIED -> PrivilegeFailure.ShizukuPermissionDenied()
                PrivilegeConnectionState.INCOMPATIBLE ->
                    PrivilegeFailure.Incompatible(MINIMUM_SHIZUKU_API, facade.apiVersion())
                else -> null
            }
        )
    }

    override suspend fun connect(requestAuthorization: Boolean): Result<BackendConnection> = runCatching {
        when {
            !facade.isManagerInstalled() -> throw PrivilegeFailure.ShizukuNotRunning()
            !facade.isBinderAlive() -> throw PrivilegeFailure.ShizukuNotRunning()
            facade.apiVersion() < MINIMUM_SHIZUKU_API ->
                throw PrivilegeFailure.Incompatible(MINIMUM_SHIZUKU_API, facade.apiVersion())
        }
        if (facade.checkPermission() != PackageManager.PERMISSION_GRANTED) {
            if (!requestAuthorization) throw PrivilegeFailure.ShizukuPermissionRequired()
            if (!facade.requestPermission()) throw PrivilegeFailure.ShizukuPermissionDenied()
            if (!facade.isBinderAlive()) throw PrivilegeFailure.ShizukuNotRunning()
        }

        val pending = PendingShizukuConnection(facade, userServiceArgs)
        val binder = try {
            withTimeout(CONNECTION_TIMEOUT_MILLIS) { pending.awaitBinder() }
        } catch (error: kotlinx.coroutines.TimeoutCancellationException) {
            pending.close()
            throw PrivilegeFailure.ConnectionTimedOut(error)
        } catch (error: Throwable) {
            pending.close()
            throw error
        }

        try {
            createConnection(pending, binder)
        } catch (error: Throwable) {
            pending.close()
            throw error
        }
    }

    private suspend fun createConnection(
        pending: PendingShizukuConnection,
        binder: IBinder
    ): BackendConnection = withContext(dispatchers.io) {
        if (!facade.isBinderAlive() || !binder.pingBinder()) {
            throw PrivilegeFailure.BackendDisconnected()
        }
        val service = IPrivilegedFileService.Stub.asInterface(binder)
        val handshake = service.handshake().toDomain()
        if (handshake.protocolVersion != PRIVILEGED_FILESYSTEM_PROTOCOL_VERSION) {
            throw PrivilegeFailure.Incompatible(
                PRIVILEGED_FILESYSTEM_PROTOCOL_VERSION,
                handshake.protocolVersion
            )
        }
        if (
            handshake.identity.transport != PrivilegeTransport.SHIZUKU_USER_SERVICE ||
            (!handshake.identity.isRoot && !handshake.identity.isShell)
        ) {
            throw PrivilegeFailure.UnexpectedIdentity(handshake.identity.effectiveUid)
        }
        val session = PrivilegeSession(
            backendId = backendId,
            generation = 0,
            identity = handshake.identity,
            capabilities = handshake.capabilities
        )
        val client = BinderPrivilegedFileClient(session, service, dispatchers) {
            pending.closeWithoutWait()
        }
        pending.attachDeathListeners(binder)
        ShizukuConnection(pending, client)
    }

    private companion object {
        const val MINIMUM_SHIZUKU_API = 11
        const val CONNECTION_TIMEOUT_MILLIS = 10_000L
    }
}

private class ShizukuConnection(
    private val pending: PendingShizukuConnection,
    override val client: BinderPrivilegedFileClient
) : BackendConnection {
    override val backendId = PrivilegeBackendId.SHIZUKU
    override val deathEvents = pending.deathEvents
    override suspend fun close() = pending.close()
}

private class PendingShizukuConnection(
    private val facade: ShizukuFacade,
    private val args: Shizuku.UserServiceArgs
) : ServiceConnection {
    private val closed = AtomicBoolean(false)
    private val disconnected = CompletableDeferred<Unit>()
    private val deaths = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var continuation: CancellableContinuation<IBinder>? = null
    private var binderDeadRegistration: Closeable? = null
    val deathEvents = deaths.asSharedFlow()

    suspend fun awaitBinder(): IBinder = suspendCancellableCoroutine { pending ->
        continuation = pending
        pending.invokeOnCancellation { closeWithoutWait() }
        try {
            facade.bindUserService(args, this)
        } catch (error: Throwable) {
            continuation = null
            if (pending.isActive) pending.resumeWithException(error)
        }
    }

    fun attachDeathListeners(binder: IBinder) {
        binder.linkToDeath({ deaths.tryEmit(Unit) }, 0)
        binderDeadRegistration = facade.addBinderDeadListener { deaths.tryEmit(Unit) }
    }

    override fun onServiceConnected(name: ComponentName, service: IBinder) {
        continuation?.let { pending ->
            continuation = null
            if (pending.isActive) pending.resume(service)
        }
    }

    override fun onServiceDisconnected(name: ComponentName) {
        disconnected.complete(Unit)
        deaths.tryEmit(Unit)
    }

    override fun onBindingDied(name: ComponentName) {
        disconnected.complete(Unit)
        deaths.tryEmit(Unit)
    }

    override fun onNullBinding(name: ComponentName) {
        continuation?.let { pending ->
            continuation = null
            if (pending.isActive) {
                pending.resumeWithException(PrivilegeFailure.Failed("Shizuku service returned no binder"))
            }
        }
    }

    suspend fun close() {
        if (!closed.compareAndSet(false, true)) return
        try {
            facade.unbindUserService(args, this, true)
            withTimeoutOrNull(DISCONNECT_WAIT_MILLIS) { disconnected.await() }
            delay(REBIND_SETTLE_MILLIS)
        } finally {
            binderDeadRegistration?.close()
            binderDeadRegistration = null
        }
    }

    fun closeWithoutWait() {
        if (!closed.compareAndSet(false, true)) return
        try {
            facade.unbindUserService(args, this, true)
        } finally {
            binderDeadRegistration?.close()
            binderDeadRegistration = null
        }
    }

    private companion object {
        const val DISCONNECT_WAIT_MILLIS = 750L
        const val REBIND_SETTLE_MILLIS = 100L
    }
}
