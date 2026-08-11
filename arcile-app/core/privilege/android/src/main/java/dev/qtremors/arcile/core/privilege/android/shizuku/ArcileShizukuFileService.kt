package dev.qtremors.arcile.core.privilege.android.shizuku

import android.content.Context
import androidx.annotation.Keep
import dev.qtremors.arcile.core.privilege.PrivilegeTransport
import dev.qtremors.arcile.core.privilege.android.remote.PrivilegedFileServiceBinder

@Keep
class ArcileShizukuFileService : PrivilegedFileServiceBinder {
    constructor() : super(
        transport = PrivilegeTransport.SHIZUKU_USER_SERVICE,
        onDestroyService = ::exitProcess
    )

    @Keep
    constructor(@Suppress("UNUSED_PARAMETER") context: Context) : this()

    private companion object {
        fun exitProcess() {
            System.exit(0)
        }
    }
}
