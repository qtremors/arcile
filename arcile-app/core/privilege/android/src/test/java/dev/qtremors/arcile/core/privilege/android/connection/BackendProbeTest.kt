package dev.qtremors.arcile.core.privilege.android.connection

import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.core.privilege.PrivilegeConnectionState
import dev.qtremors.arcile.core.privilege.android.root.RootBackendConnector
import dev.qtremors.arcile.core.privilege.android.root.RootFacade
import dev.qtremors.arcile.core.privilege.android.shizuku.ShizukuBackendConnector
import dev.qtremors.arcile.core.privilege.android.shizuku.ShizukuFacade
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import java.io.Closeable
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import rikka.shizuku.Shizuku

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackendProbeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dispatchers = ArcileDispatchers(
        io = Dispatchers.IO,
        default = Dispatchers.Default,
        main = Dispatchers.Unconfined,
        storage = Dispatchers.IO
    )

    @Test
    fun `root probe distinguishes unavailable required denied and authorized`() {
        assertEquals(
            PrivilegeConnectionState.UNAVAILABLE,
            rootProbe(binary = false, authorization = null)
        )
        assertEquals(
            PrivilegeConnectionState.PERMISSION_REQUIRED,
            rootProbe(binary = true, authorization = null)
        )
        assertEquals(
            PrivilegeConnectionState.PERMISSION_DENIED,
            rootProbe(binary = true, authorization = false)
        )
        assertEquals(
            PrivilegeConnectionState.DISCONNECTED,
            rootProbe(binary = true, authorization = true)
        )
    }

    @Test
    fun `dead Shizuku binder never trusts cached permission`() {
        val connector = ShizukuBackendConnector(
            context,
            FakeShizukuFacade(
                managerInstalled = true,
                binderAlive = false,
                permission = PackageManager.PERMISSION_GRANTED
            ),
            dispatchers
        )

        assertEquals(PrivilegeConnectionState.INSTALLED_BUT_STOPPED, connector.probe().connectionState)
    }

    @Test
    fun `live Shizuku reports permission and compatibility states`() {
        val permissionRequired = ShizukuBackendConnector(
            context,
            FakeShizukuFacade(managerInstalled = true, binderAlive = true, apiVersion = 13),
            dispatchers
        )
        val incompatible = ShizukuBackendConnector(
            context,
            FakeShizukuFacade(managerInstalled = true, binderAlive = true, apiVersion = 10),
            dispatchers
        )

        assertEquals(PrivilegeConnectionState.PERMISSION_REQUIRED, permissionRequired.probe().connectionState)
        assertEquals(PrivilegeConnectionState.INCOMPATIBLE, incompatible.probe().connectionState)
    }

    private fun rootProbe(binary: Boolean, authorization: Boolean?): PrivilegeConnectionState {
        val connector = RootBackendConnector(
            context,
            object : RootFacade {
                override fun cachedAuthorization() = authorization
                override fun isRootBinaryAvailable() = binary
                override fun bind(intent: android.content.Intent, connection: ServiceConnection) = Unit
                override fun unbind(connection: ServiceConnection) = Unit
            },
            dispatchers
        )
        return connector.probe().connectionState
    }
}

private class FakeShizukuFacade(
    private val managerInstalled: Boolean,
    private val binderAlive: Boolean,
    private val apiVersion: Int = 13,
    private val permission: Int = PackageManager.PERMISSION_DENIED
) : ShizukuFacade {
    override fun isManagerInstalled() = managerInstalled
    override fun isBinderAlive() = binderAlive
    override fun apiVersion() = apiVersion
    override fun checkPermission() = permission
    override fun shouldShowPermissionRationale() = false
    override suspend fun requestPermission() = false
    override fun bindUserService(args: Shizuku.UserServiceArgs, connection: ServiceConnection) = Unit
    override fun unbindUserService(
        args: Shizuku.UserServiceArgs,
        connection: ServiceConnection,
        remove: Boolean
    ) = Unit
    override fun addBinderDeadListener(listener: () -> Unit) = Closeable { }
}
