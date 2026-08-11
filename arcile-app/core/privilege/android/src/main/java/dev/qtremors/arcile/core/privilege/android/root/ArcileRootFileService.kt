package dev.qtremors.arcile.core.privilege.android.root

import android.content.Intent
import android.os.IBinder
import com.topjohnwu.superuser.ipc.RootService
import dev.qtremors.arcile.core.privilege.PrivilegeTransport
import dev.qtremors.arcile.core.privilege.android.remote.PrivilegedFileServiceBinder

class ArcileRootFileService : RootService() {
    private val binder by lazy {
        PrivilegedFileServiceBinder(PrivilegeTransport.ROOT_SERVICE)
    }

    override fun onBind(intent: Intent): IBinder = binder
}
