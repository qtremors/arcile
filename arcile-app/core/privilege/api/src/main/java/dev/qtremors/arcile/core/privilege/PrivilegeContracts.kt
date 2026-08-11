package dev.qtremors.arcile.core.privilege

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

data class PrivilegePreferenceState(
    val mode: PrivilegeMode = PrivilegeMode.AUTOMATIC,
    val rootPreviouslyAuthorized: Boolean = false,
    val shizukuPreviouslyAuthorized: Boolean = false,
    val protectedFilesystemWritesEnabled: Boolean = false
)

interface PrivilegePreferences {
    val state: Flow<PrivilegePreferenceState>

    suspend fun setMode(mode: PrivilegeMode)
    suspend fun setRootPreviouslyAuthorized(authorized: Boolean)
    suspend fun setShizukuPreviouslyAuthorized(authorized: Boolean)
    suspend fun setProtectedFilesystemWritesEnabled(enabled: Boolean)
}

interface PrivilegeCoordinator {
    val state: StateFlow<PrivilegeState>

    /** Starts passive selection. This must never trigger a permission prompt. */
    suspend fun start()

    /** Refreshes availability and authorization without prompting. */
    suspend fun refresh()

    /** Changes the persisted preference; prompts are allowed only for explicit user actions. */
    suspend fun selectMode(mode: PrivilegeMode, requestAuthorization: Boolean)

    suspend fun reconnect(requestAuthorization: Boolean)
    suspend fun useNormal()

    /** Captures the immutable backend generation used by one operation. */
    fun captureSession(): Result<PrivilegeSession>
}
