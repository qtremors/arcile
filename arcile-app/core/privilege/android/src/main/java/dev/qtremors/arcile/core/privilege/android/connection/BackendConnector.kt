package dev.qtremors.arcile.core.privilege.android.connection

import dev.qtremors.arcile.core.privilege.PrivilegeBackendId
import dev.qtremors.arcile.core.privilege.PrivilegeBackendState
import dev.qtremors.arcile.core.privilege.PrivilegedFileClient
import kotlinx.coroutines.flow.Flow
import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RootBackend

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ShizukuBackend

interface BackendConnector {
    val backendId: PrivilegeBackendId

    fun probe(): PrivilegeBackendState
    suspend fun connect(requestAuthorization: Boolean): Result<BackendConnection>
}

interface BackendConnection {
    val backendId: PrivilegeBackendId
    val client: PrivilegedFileClient
    val deathEvents: Flow<Unit>

    fun activate(generation: Long): PrivilegedFileClient
    suspend fun close()
}
