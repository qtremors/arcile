package dev.qtremors.arcile.core.privilege

enum class PrivilegeMode {
    AUTOMATIC,
    ROOT,
    SHIZUKU,
    NORMAL
}

@JvmInline
value class PrivilegeBackendId private constructor(val value: String) {
    companion object {
        val ROOT = PrivilegeBackendId("root")
        val SHIZUKU = PrivilegeBackendId("shizuku")
        val NORMAL = PrivilegeBackendId("normal")

        fun of(value: String): PrivilegeBackendId {
            require(value.isNotBlank()) { "Privilege backend id must not be blank" }
            require(value.none(Char::isWhitespace)) { "Privilege backend id must not contain whitespace" }
            return PrivilegeBackendId(value)
        }
    }
}

enum class PrivilegeConnectionState {
    UNAVAILABLE,
    INSTALLED_BUT_STOPPED,
    PERMISSION_REQUIRED,
    PERMISSION_DENIED,
    CONNECTING,
    READY,
    DISCONNECTED,
    INCOMPATIBLE,
    FAILED
}

enum class PrivilegeTransport {
    ROOT_SERVICE,
    SHIZUKU_USER_SERVICE,
    NORMAL_ANDROID
}

enum class PrivilegeCapability {
    CANONICALIZE,
    STAT,
    LIST_DIRECTORY,
    FILESYSTEM_STATS,
    CREATE_FILE,
    CREATE_DIRECTORY,
    READ,
    WRITE,
    RENAME,
    COPY,
    MOVE,
    DELETE,
    RECURSIVE_DELETE,
    SECURE_OVERWRITE,
    UPDATE_TIMESTAMPS,
    RANDOM_ACCESS,
    CANCEL_OPERATION
}

data class PrivilegeServiceIdentity(
    val effectiveUid: Int,
    val pid: Int,
    val transport: PrivilegeTransport,
    val selinuxContext: String? = null
) {
    val isRoot: Boolean get() = effectiveUid == ROOT_UID
    val isShell: Boolean get() = effectiveUid == SHELL_UID

    fun requireSupportedUid(): PrivilegeServiceIdentity {
        require(isRoot || isShell) { "Unsupported privileged service UID: $effectiveUid" }
        return this
    }

    companion object {
        const val ROOT_UID = 0
        const val SHELL_UID = 2000
    }
}

sealed class PrivilegeFailure(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {
    class RootPermissionDenied(cause: Throwable? = null) :
        PrivilegeFailure("Root permission was denied", cause)

    class RootUnavailable(cause: Throwable? = null) :
        PrivilegeFailure("Root is unavailable", cause)

    class ShizukuNotRunning(cause: Throwable? = null) :
        PrivilegeFailure("Shizuku is not running", cause)

    class ShizukuPermissionRequired(cause: Throwable? = null) :
        PrivilegeFailure("Shizuku permission is required", cause)

    class ShizukuPermissionDenied(cause: Throwable? = null) :
        PrivilegeFailure("Shizuku permission was denied", cause)

    class ConnectionTimedOut(cause: Throwable? = null) :
        PrivilegeFailure("The privileged service connection timed out", cause)

    class BackendDisconnected(cause: Throwable? = null) :
        PrivilegeFailure("The privileged service disconnected", cause)

    class Incompatible(
        val expectedProtocol: Int,
        val actualProtocol: Int,
        cause: Throwable? = null
    ) : PrivilegeFailure(
        "Privileged service protocol $actualProtocol is incompatible with $expectedProtocol",
        cause
    )

    class UnexpectedIdentity(
        val effectiveUid: Int,
        cause: Throwable? = null
    ) : PrivilegeFailure("Privileged service returned unsupported UID $effectiveUid", cause)

    class Failed(message: String, cause: Throwable? = null) : PrivilegeFailure(message, cause)
}

data class PrivilegeBackendState(
    val backendId: PrivilegeBackendId,
    val connectionState: PrivilegeConnectionState,
    val identity: PrivilegeServiceIdentity? = null,
    val capabilities: Set<PrivilegeCapability> = emptySet(),
    val failure: PrivilegeFailure? = null
) {
    init {
        require(connectionState == PrivilegeConnectionState.READY || identity == null) {
            "Only a ready backend may expose a service identity"
        }
    }
}

data class PrivilegeState(
    val preferredMode: PrivilegeMode = PrivilegeMode.AUTOMATIC,
    val activeBackend: PrivilegeBackendId? = null,
    val connectionGeneration: Long = 0,
    val backendStates: Map<PrivilegeBackendId, PrivilegeBackendState> = emptyMap(),
    val identity: PrivilegeServiceIdentity? = null,
    val capabilities: Set<PrivilegeCapability> = emptySet(),
    val lastFailure: PrivilegeFailure? = null
) {
    val connectionState: PrivilegeConnectionState
        get() = activeBackend?.let(backendStates::get)?.connectionState
            ?: when {
                lastFailure != null -> PrivilegeConnectionState.FAILED
                else -> PrivilegeConnectionState.UNAVAILABLE
            }

    val isReady: Boolean
        get() = activeBackend != null && connectionState == PrivilegeConnectionState.READY
}

data class PrivilegeSession(
    val backendId: PrivilegeBackendId,
    val generation: Long,
    val identity: PrivilegeServiceIdentity,
    val capabilities: Set<PrivilegeCapability>
)
