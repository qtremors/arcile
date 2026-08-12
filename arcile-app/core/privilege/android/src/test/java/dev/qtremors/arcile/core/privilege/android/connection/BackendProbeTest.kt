package dev.qtremors.arcile.core.privilege.android.connection

import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.core.privilege.PrivilegeConnectionState
import dev.qtremors.arcile.core.privilege.PrivilegeFailure
import dev.qtremors.arcile.core.privilege.android.root.RootBackendConnector
import dev.qtremors.arcile.core.privilege.android.root.RootFacade
import dev.qtremors.arcile.core.privilege.android.shizuku.ShizukuBackendConnector
import dev.qtremors.arcile.core.privilege.android.shizuku.ShizukuFacade
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import java.io.Closeable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import rikka.shizuku.Shizuku

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
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

    @Test
    fun `root connection timeout unbinds the pending service`() = runTest {
        val facade = RecordingRootFacade()
        val connector = RootBackendConnector(
            context,
            facade,
            testDispatchers(StandardTestDispatcher(testScheduler))
        )

        val result = async { connector.connect(requestAuthorization = false) }
        runCurrent()
        advanceTimeBy(10_001)
        runCurrent()

        assertEquals(1, facade.bindCalls)
        assertEquals(1, facade.unbindCalls)
        assert(result.await().exceptionOrNull() is PrivilegeFailure.ConnectionTimedOut)
    }

    @Test
    fun `root connection cancellation unbinds the pending service`() = runTest {
        val facade = RecordingRootFacade()
        val connector = RootBackendConnector(
            context,
            facade,
            testDispatchers(StandardTestDispatcher(testScheduler))
        )

        val request = launch { connector.connect(requestAuthorization = false) }
        runCurrent()
        request.cancelAndJoin()

        assertEquals(1, facade.bindCalls)
        assertEquals(1, facade.unbindCalls)
    }

    @Test
    fun `Shizuku connection timeout unbinds the pending user service`() = runTest {
        val facade = FakeShizukuFacade(
            managerInstalled = true,
            binderAlive = true,
            permission = PackageManager.PERMISSION_GRANTED
        )
        val connector = ShizukuBackendConnector(
            context,
            facade,
            testDispatchers(StandardTestDispatcher(testScheduler))
        )

        val result = async { connector.connect(requestAuthorization = false) }
        runCurrent()
        advanceTimeBy(10_001)
        runCurrent()

        assertEquals(1, facade.bindCalls)
        assertEquals(1, facade.unbindCalls)
        assert(result.await().exceptionOrNull() is PrivilegeFailure.ConnectionTimedOut)
    }

    @Test
    fun `Shizuku connection cancellation unbinds the pending user service`() = runTest {
        val facade = FakeShizukuFacade(
            managerInstalled = true,
            binderAlive = true,
            permission = PackageManager.PERMISSION_GRANTED
        )
        val connector = ShizukuBackendConnector(
            context,
            facade,
            testDispatchers(StandardTestDispatcher(testScheduler))
        )

        val request = launch { connector.connect(requestAuthorization = false) }
        runCurrent()
        request.cancelAndJoin()

        assertEquals(1, facade.bindCalls)
        assertEquals(1, facade.unbindCalls)
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

    private fun testDispatchers(dispatcher: kotlinx.coroutines.CoroutineDispatcher) = ArcileDispatchers(
        io = dispatcher,
        default = dispatcher,
        main = dispatcher,
        storage = dispatcher
    )
}

private class RecordingRootFacade : RootFacade {
    var bindCalls = 0
        private set
    var unbindCalls = 0
        private set

    override fun cachedAuthorization() = true
    override fun isRootBinaryAvailable() = true

    override fun bind(intent: android.content.Intent, connection: ServiceConnection) {
        bindCalls += 1
    }

    override fun unbind(connection: ServiceConnection) {
        unbindCalls += 1
    }
}

private class FakeShizukuFacade(
    private val managerInstalled: Boolean,
    private val binderAlive: Boolean,
    private val apiVersion: Int = 13,
    private val permission: Int = PackageManager.PERMISSION_DENIED
) : ShizukuFacade {
    var bindCalls = 0
        private set
    var unbindCalls = 0
        private set

    override fun isManagerInstalled() = managerInstalled
    override fun isBinderAlive() = binderAlive
    override fun apiVersion() = apiVersion
    override fun checkPermission() = permission
    override fun shouldShowPermissionRationale() = false
    override suspend fun requestPermission() = false
    override fun bindUserService(args: Shizuku.UserServiceArgs, connection: ServiceConnection) {
        bindCalls += 1
    }
    override fun unbindUserService(
        args: Shizuku.UserServiceArgs,
        connection: ServiceConnection,
        remove: Boolean
    ) {
        unbindCalls += 1
    }
    override fun addBinderDeadListener(listener: () -> Unit) = Closeable { }
}
