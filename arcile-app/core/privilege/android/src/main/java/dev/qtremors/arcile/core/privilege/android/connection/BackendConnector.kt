package dev.qtremors.arcile.core.privilege.android.connection

import dev.qtremors.arcile.core.privilege.PrivilegeBackendId
import dev.qtremors.arcile.core.privilege.PrivilegeBackendState
import dev.qtremors.arcile.core.privilege.PrivilegedFileClient
import kotlinx.coroutines.flow.Flow

internal interface BackendConnector {
    val backendId: PrivilegeBackendId

    fun probe(): PrivilegeBackendState
    suspend fun connect(requestAuthorization: Boolean): Result<BackendConnection>
}

internal interface BackendConnection {
    val backendId: PrivilegeBackendId
    val client: PrivilegedFileClient
    val deathEvents: Flow<Unit>

    suspend fun close()
}
