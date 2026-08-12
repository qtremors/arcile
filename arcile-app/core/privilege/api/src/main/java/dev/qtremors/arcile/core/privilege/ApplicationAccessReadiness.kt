package dev.qtremors.arcile.core.privilege

/** Why Arcile is currently checking storage access. */
enum class ApplicationAccessCheck {
    IDLE,
    INITIALIZING,
    RESUME_REFRESH,
    RECONNECTING,
    SWITCHING_TO_NORMAL
}

/**
 * Application-level interpretation of the lower-level backend state.
 *
 * This is deliberately not a Boolean permission flag. Root or Shizuku can make the application
 * ready without Normal Android all-files access, and a backend can be temporarily disconnected
 * after the application has already entered without requiring the UI tree to be discarded.
 */
sealed interface ApplicationAccessReadiness {
    val preferredMode: PrivilegeMode
    val normalAccessReady: Boolean

    data class Ready(
        override val preferredMode: PrivilegeMode,
        val activeBackend: PrivilegeBackendId,
        val connectionGeneration: Long,
        val identity: PrivilegeServiceIdentity?,
        override val normalAccessReady: Boolean
    ) : ApplicationAccessReadiness

    data class SetupRequired(
        override val preferredMode: PrivilegeMode,
        override val normalAccessReady: Boolean,
        val rootState: PrivilegeConnectionState,
        val shizukuState: PrivilegeConnectionState,
        val normalState: PrivilegeConnectionState
    ) : ApplicationAccessReadiness

    data class Connecting(
        override val preferredMode: PrivilegeMode,
        override val normalAccessReady: Boolean,
        val backend: PrivilegeBackendId?,
        val check: ApplicationAccessCheck
    ) : ApplicationAccessReadiness

    data class Failed(
        override val preferredMode: PrivilegeMode,
        override val normalAccessReady: Boolean,
        val retainedBackend: PrivilegeBackendId?,
        val failure: PrivilegeFailure,
        val backendState: PrivilegeConnectionState
    ) : ApplicationAccessReadiness
}

/** State consumed by the activity gate. */
data class ApplicationAccessSnapshot(
    val readiness: ApplicationAccessReadiness,
    val hasReachedReadyState: Boolean
) {
    /** New sessions enter only when ready; an entered session remains composed during access loss. */
    val mayShowApplicationContent: Boolean
        get() = hasReachedReadyState || readiness is ApplicationAccessReadiness.Ready

    val isReady: Boolean
        get() = readiness is ApplicationAccessReadiness.Ready

    val activeBackend: PrivilegeBackendId?
        get() = (readiness as? ApplicationAccessReadiness.Ready)?.activeBackend

    val normalAccessReady: Boolean
        get() = readiness.normalAccessReady
}

fun PrivilegeState.toApplicationAccessReadiness(
    check: ApplicationAccessCheck = ApplicationAccessCheck.IDLE
): ApplicationAccessReadiness {
    val normalState = backendState(PrivilegeBackendId.NORMAL)
    val normalReady = normalState == PrivilegeConnectionState.READY
    if (isReady) {
        return ApplicationAccessReadiness.Ready(
            preferredMode = preferredMode,
            activeBackend = requireNotNull(activeBackend),
            connectionGeneration = connectionGeneration,
            identity = identity,
            normalAccessReady = normalReady
        )
    }

    val connectingBackend = backendStates.values
        .firstOrNull { it.connectionState == PrivilegeConnectionState.CONNECTING }
        ?.backendId
    if (check != ApplicationAccessCheck.IDLE || connectingBackend != null) {
        return ApplicationAccessReadiness.Connecting(
            preferredMode = preferredMode,
            normalAccessReady = normalReady,
            backend = connectingBackend ?: activeBackend ?: preferredMode.preferredBackendOrNull(),
            check = check.takeUnless { it == ApplicationAccessCheck.IDLE }
                ?: ApplicationAccessCheck.INITIALIZING
        )
    }

    val retainedBackend = activeBackend
    val retainedState = retainedBackend?.let(::backendState)
    val failure = lastFailure
        ?: retainedBackend?.let { backendStates[it]?.failure }
        ?: preferredMode.preferredBackendOrNull()?.let { backendStates[it]?.failure }
    if (failure != null && shouldSurfaceFailure(retainedBackend, retainedState, preferredMode)) {
        return ApplicationAccessReadiness.Failed(
            preferredMode = preferredMode,
            normalAccessReady = normalReady,
            retainedBackend = retainedBackend ?: preferredMode.preferredBackendOrNull(),
            failure = failure,
            backendState = retainedState ?: failure.connectionState()
        )
    }

    return ApplicationAccessReadiness.SetupRequired(
        preferredMode = preferredMode,
        normalAccessReady = normalReady,
        rootState = backendState(PrivilegeBackendId.ROOT),
        shizukuState = backendState(PrivilegeBackendId.SHIZUKU),
        normalState = normalState
    )
}

fun PrivilegeState.toApplicationAccessSnapshot(
    check: ApplicationAccessCheck = ApplicationAccessCheck.IDLE,
    hasReachedReadyState: Boolean = false
): ApplicationAccessSnapshot = ApplicationAccessSnapshot(
    readiness = toApplicationAccessReadiness(check),
    hasReachedReadyState = hasReachedReadyState || isReady
)

private fun PrivilegeState.backendState(backendId: PrivilegeBackendId): PrivilegeConnectionState =
    backendStates[backendId]?.connectionState ?: PrivilegeConnectionState.UNAVAILABLE

private fun shouldSurfaceFailure(
    retainedBackend: PrivilegeBackendId?,
    retainedState: PrivilegeConnectionState?,
    preferredMode: PrivilegeMode
): Boolean = retainedBackend != null ||
    retainedState in FAILURE_STATES ||
    preferredMode != PrivilegeMode.AUTOMATIC

private fun PrivilegeMode.preferredBackendOrNull(): PrivilegeBackendId? = when (this) {
    PrivilegeMode.ROOT -> PrivilegeBackendId.ROOT
    PrivilegeMode.SHIZUKU -> PrivilegeBackendId.SHIZUKU
    PrivilegeMode.NORMAL -> PrivilegeBackendId.NORMAL
    PrivilegeMode.AUTOMATIC -> null
}

private fun PrivilegeFailure.connectionState(): PrivilegeConnectionState = when (this) {
    is PrivilegeFailure.RootUnavailable -> PrivilegeConnectionState.UNAVAILABLE
    is PrivilegeFailure.RootPermissionDenied -> PrivilegeConnectionState.PERMISSION_DENIED
    is PrivilegeFailure.ShizukuNotRunning -> PrivilegeConnectionState.INSTALLED_BUT_STOPPED
    is PrivilegeFailure.ShizukuPermissionRequired -> PrivilegeConnectionState.PERMISSION_REQUIRED
    is PrivilegeFailure.ShizukuPermissionDenied -> PrivilegeConnectionState.PERMISSION_DENIED
    is PrivilegeFailure.BackendDisconnected -> PrivilegeConnectionState.DISCONNECTED
    is PrivilegeFailure.Incompatible,
    is PrivilegeFailure.UnexpectedIdentity -> PrivilegeConnectionState.INCOMPATIBLE
    is PrivilegeFailure.ConnectionTimedOut,
    is PrivilegeFailure.Failed -> PrivilegeConnectionState.FAILED
}

private val FAILURE_STATES = setOf(
    PrivilegeConnectionState.PERMISSION_DENIED,
    PrivilegeConnectionState.DISCONNECTED,
    PrivilegeConnectionState.INCOMPATIBLE,
    PrivilegeConnectionState.FAILED
)
