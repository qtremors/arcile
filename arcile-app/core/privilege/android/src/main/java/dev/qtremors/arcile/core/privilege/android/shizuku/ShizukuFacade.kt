package dev.qtremors.arcile.core.privilege.android.shizuku

import android.content.Context
import android.content.pm.PackageManager
import android.content.ServiceConnection
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.Closeable
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import rikka.shizuku.Shizuku

internal interface ShizukuFacade {
    fun isManagerInstalled(): Boolean
    fun isBinderAlive(): Boolean
    fun apiVersion(): Int
    fun checkPermission(): Int
    fun shouldShowPermissionRationale(): Boolean
    suspend fun requestPermission(): Boolean
    fun bindUserService(args: Shizuku.UserServiceArgs, connection: ServiceConnection)
    fun unbindUserService(args: Shizuku.UserServiceArgs, connection: ServiceConnection, remove: Boolean)
    fun addBinderDeadListener(listener: () -> Unit): Closeable
}

internal class AndroidShizukuFacade @Inject constructor(
    @param:ApplicationContext private val context: Context
) : ShizukuFacade {
    private val permissionRequestCode = AtomicInteger(24_000)

    @Suppress("DEPRECATION")
    override fun isManagerInstalled(): Boolean {
        if (isBinderAlive()) return true
        return runCatching {
            context.packageManager.getPackagesHoldingPermissions(
                arrayOf(SHIZUKU_PERMISSION),
                PackageManager.MATCH_DISABLED_COMPONENTS
            ).isNotEmpty()
        }.getOrDefault(false)
    }

    override fun isBinderAlive(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    override fun apiVersion(): Int = if (isBinderAlive()) {
        runCatching { Shizuku.getVersion() }.getOrDefault(0)
    } else {
        0
    }

    override fun checkPermission(): Int = if (isBinderAlive()) {
        runCatching { Shizuku.checkSelfPermission() }.getOrDefault(PackageManager.PERMISSION_DENIED)
    } else {
        PackageManager.PERMISSION_DENIED
    }

    override fun shouldShowPermissionRationale(): Boolean = isBinderAlive() &&
        runCatching { Shizuku.shouldShowRequestPermissionRationale() }.getOrDefault(false)

    override suspend fun requestPermission(): Boolean = suspendCancellableCoroutine { continuation ->
        if (!isBinderAlive()) {
            continuation.resume(false)
            return@suspendCancellableCoroutine
        }
        val requestCode = permissionRequestCode.incrementAndGet()
        lateinit var listener: Shizuku.OnRequestPermissionResultListener
        listener = Shizuku.OnRequestPermissionResultListener { resultRequestCode, grantResult ->
            if (resultRequestCode == requestCode) {
                Shizuku.removeRequestPermissionResultListener(listener)
                if (continuation.isActive) {
                    continuation.resume(grantResult == PackageManager.PERMISSION_GRANTED)
                }
            }
        }
        Shizuku.addRequestPermissionResultListener(listener)
        continuation.invokeOnCancellation {
            Shizuku.removeRequestPermissionResultListener(listener)
        }
        try {
            Shizuku.requestPermission(requestCode)
        } catch (_: Throwable) {
            Shizuku.removeRequestPermissionResultListener(listener)
            if (continuation.isActive) continuation.resume(false)
        }
    }

    override fun bindUserService(args: Shizuku.UserServiceArgs, connection: ServiceConnection) {
        Shizuku.bindUserService(args, connection)
    }

    override fun unbindUserService(
        args: Shizuku.UserServiceArgs,
        connection: ServiceConnection,
        remove: Boolean
    ) {
        Shizuku.unbindUserService(args, connection, remove)
    }

    override fun addBinderDeadListener(listener: () -> Unit): Closeable {
        val wrapped = Shizuku.OnBinderDeadListener(listener)
        Shizuku.addBinderDeadListener(wrapped)
        return Closeable { Shizuku.removeBinderDeadListener(wrapped) }
    }

    private companion object {
        const val SHIZUKU_PERMISSION = "moe.shizuku.manager.permission.API_V23"
    }
}
