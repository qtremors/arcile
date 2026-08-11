package dev.qtremors.arcile.core.privilege.android.root

import android.content.Intent
import android.content.ServiceConnection
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ipc.RootService
import java.io.File
import javax.inject.Inject

internal interface RootFacade {
    /** Returns true/false for a manager decision and null when authorization has not been requested. */
    fun cachedAuthorization(): Boolean?
    fun isRootBinaryAvailable(): Boolean
    fun bind(intent: Intent, connection: ServiceConnection)
    fun unbind(connection: ServiceConnection)
}

internal class LibsuRootFacade @Inject constructor() : RootFacade {
    override fun cachedAuthorization(): Boolean? = runCatching { Shell.isAppGrantedRoot() }.getOrNull()

    override fun isRootBinaryAvailable(): Boolean {
        val candidates = buildList {
            add("/system/bin/su")
            add("/system/xbin/su")
            add("/sbin/su")
            add("/debug_ramdisk/su")
            System.getenv("PATH")
                ?.split(File.pathSeparatorChar)
                ?.filter(String::isNotBlank)
                ?.forEach { add(File(it, "su").path) }
        }
        return candidates.distinct().any { runCatching { File(it).isFile }.getOrDefault(false) }
    }

    override fun bind(intent: Intent, connection: ServiceConnection) {
        RootService.bind(intent, connection)
    }

    override fun unbind(connection: ServiceConnection) {
        RootService.unbind(connection)
    }
}
