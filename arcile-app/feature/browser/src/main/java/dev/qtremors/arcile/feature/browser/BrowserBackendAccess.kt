package dev.qtremors.arcile.feature.browser

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import dev.qtremors.arcile.core.privilege.PrivilegeBackendId
import dev.qtremors.arcile.core.privilege.PrivilegeConnectionState
import dev.qtremors.arcile.core.privilege.PrivilegeCoordinator
import dev.qtremors.arcile.core.privilege.PrivilegeFailure
import dev.qtremors.arcile.core.privilege.PrivilegeMode
import dev.qtremors.arcile.core.privilege.PrivilegeState
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import dev.qtremors.arcile.core.storage.domain.isPrivileged
import dev.qtremors.arcile.feature.browser.delegate.BrowserNavigationController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal enum class BrowserAccessLossReason {
    SERVICE_STOPPED,
    PERMISSION_REQUIRED,
    PERMISSION_DENIED,
    BACKEND_UNAVAILABLE,
    BACKEND_INCOMPATIBLE,
    BACKEND_CHANGED,
    CONNECTION_FAILED
}

@Immutable
internal sealed interface BrowserBackendAccessState {
    data object Available : BrowserBackendAccessState

    data class Reconnecting(
        val backendId: PrivilegeBackendId,
        val normalFallbackPending: Boolean
    ) : BrowserBackendAccessState

    data class Lost(
        val backendId: PrivilegeBackendId,
        val reason: BrowserAccessLossReason,
        val failure: PrivilegeFailure?,
        val normalState: PrivilegeConnectionState,
        val normalFallbackPending: Boolean,
        val actionFailure: PrivilegeFailure? = null
    ) : BrowserBackendAccessState {
        val normalAccessReady: Boolean
            get() = normalState == PrivilegeConnectionState.READY

        val normalPermissionRequired: Boolean
            get() = normalState == PrivilegeConnectionState.PERMISSION_REQUIRED
    }
}

internal fun deriveBrowserBackendAccessState(
    node: StorageNodeRef?,
    privilegeState: PrivilegeState,
    normalFallbackPending: Boolean,
    actionFailure: PrivilegeFailure? = null
): BrowserBackendAccessState {
    val backendId = expectedBrowserBackend(node, privilegeState)
        ?: return BrowserBackendAccessState.Available
    val backendState = privilegeState.backendStates[backendId]
    val normalState = privilegeState.backendStates[PrivilegeBackendId.NORMAL]
        ?.connectionState
        ?: PrivilegeConnectionState.UNAVAILABLE

    if (
        privilegeState.isReady &&
        privilegeState.activeBackend == backendId &&
        backendState?.connectionState == PrivilegeConnectionState.READY
    ) {
        return BrowserBackendAccessState.Available
    }

    val normalIsConnecting = normalFallbackPending &&
        privilegeState.backendStates[PrivilegeBackendId.NORMAL]?.connectionState ==
            PrivilegeConnectionState.CONNECTING
    if (backendState?.connectionState == PrivilegeConnectionState.CONNECTING || normalIsConnecting) {
        return BrowserBackendAccessState.Reconnecting(
            backendId = backendId,
            normalFallbackPending = normalFallbackPending
        )
    }

    val failure = backendState?.failure
        ?: privilegeState.lastFailure?.takeIf {
            privilegeState.activeBackend == backendId || privilegeState.activeBackend == null
        }
    return BrowserBackendAccessState.Lost(
        backendId = backendId,
        reason = accessLossReason(
            requestedBackend = backendId,
            backendState = backendState?.connectionState,
            activeBackend = privilegeState.activeBackend,
            failure = failure
        ),
        failure = failure,
        normalState = normalState,
        normalFallbackPending = normalFallbackPending,
        actionFailure = actionFailure
    )
}

internal class BrowserBackendAccessController(
    private val scope: CoroutineScope,
    private val coordinator: PrivilegeCoordinator,
    private val navigation: BrowserNavigationController,
    private val savedStateHandle: SavedStateHandle,
    private val onCommitNormalFallback: () -> Unit
) {
    private val actionMutex = Mutex()
    private val normalFallbackPending = MutableStateFlow(
        savedStateHandle[PENDING_NORMAL_FALLBACK_KEY] ?: false
    )
    private val actionFailure = MutableStateFlow<PrivilegeFailure?>(null)

    val state: StateFlow<BrowserBackendAccessState> = combine(
        navigation.state,
        coordinator.state,
        normalFallbackPending,
        actionFailure
    ) { navigationState, privilegeState, pending, failure ->
        deriveBrowserBackendAccessState(
            node = navigationState.currentNodeRef,
            privilegeState = privilegeState,
            normalFallbackPending = pending,
            actionFailure = failure
        )
    }.stateIn(
        scope,
        SharingStarted.Eagerly,
        deriveBrowserBackendAccessState(
            node = navigation.state.value.currentNodeRef,
            privilegeState = coordinator.state.value,
            normalFallbackPending = normalFallbackPending.value
        )
    )

    init {
        scope.launch {
            combine(
                navigation.state,
                coordinator.state,
                normalFallbackPending
            ) { navigationState, privilegeState, pending ->
                Triple(navigationState, privilegeState, pending)
            }.collect { (navigationState, privilegeState, pending) ->
                if (
                    pending &&
                    privilegeState.isReady &&
                    privilegeState.activeBackend == PrivilegeBackendId.NORMAL
                ) {
                    commitNormalFallback()
                    return@collect
                }
                val expectedBackend = expectedBrowserBackend(
                    navigationState.currentNodeRef,
                    privilegeState
                )
                if (pending && expectedBackend !in PRIVILEGED_BACKENDS) {
                    setNormalFallbackPending(false)
                }
            }
        }
    }

    fun reconnect() {
        val backendId = expectedBrowserBackend(
            navigation.state.value.currentNodeRef,
            coordinator.state.value
        ) ?: return
        scope.launch {
            actionMutex.withLock {
                setNormalFallbackPending(false)
                actionFailure.value = null
                try {
                    coordinator.selectMode(
                        mode = backendId.toPrivilegeMode(),
                        requestAuthorization = true
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    actionFailure.value = error.asPrivilegeFailure()
                }
            }
        }
    }

    fun useNormal() {
        val expectedBackend = expectedBrowserBackend(
            navigation.state.value.currentNodeRef,
            coordinator.state.value
        )
        if (expectedBackend !in PRIVILEGED_BACKENDS) return
        setNormalFallbackPending(true)
        scope.launch {
            actionMutex.withLock {
                actionFailure.value = null
                try {
                    coordinator.useNormal()
                    val state = coordinator.state.value
                    if (state.isReady && state.activeBackend == PrivilegeBackendId.NORMAL) {
                        commitNormalFallback()
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    actionFailure.value = error.asPrivilegeFailure()
                }
            }
        }
    }

    fun clearActionFailure() {
        actionFailure.value = null
    }

    private fun commitNormalFallback() {
        if (!normalFallbackPending.value) return
        setNormalFallbackPending(false)
        actionFailure.value = null
        onCommitNormalFallback()
        navigation.openPrimaryStorage()
    }

    private fun setNormalFallbackPending(value: Boolean) {
        normalFallbackPending.value = value
        savedStateHandle[PENDING_NORMAL_FALLBACK_KEY] = value
    }

    private companion object {
        const val PENDING_NORMAL_FALLBACK_KEY = "browser_pending_normal_access_fallback"
    }
}

private fun StorageNodeRef.privilegeBackendIdOrNull(): PrivilegeBackendId? = when {
    !isPrivileged -> null
    backendId == StorageNodeRef.ROOT_BACKEND_ID -> PrivilegeBackendId.ROOT
    backendId == StorageNodeRef.SHIZUKU_BACKEND_ID -> PrivilegeBackendId.SHIZUKU
    else -> null
}

private fun expectedBrowserBackend(
    node: StorageNodeRef?,
    privilegeState: PrivilegeState
): PrivilegeBackendId? = node?.privilegeBackendIdOrNull()
    ?: privilegeState.activeBackend?.takeIf { !privilegeState.isReady }

private val PRIVILEGED_BACKENDS = setOf(
    PrivilegeBackendId.ROOT,
    PrivilegeBackendId.SHIZUKU
)

private fun accessLossReason(
    requestedBackend: PrivilegeBackendId,
    backendState: PrivilegeConnectionState?,
    activeBackend: PrivilegeBackendId?,
    failure: PrivilegeFailure?
): BrowserAccessLossReason = when {
    failure is PrivilegeFailure.ShizukuNotRunning -> BrowserAccessLossReason.SERVICE_STOPPED
    failure is PrivilegeFailure.ShizukuPermissionRequired -> BrowserAccessLossReason.PERMISSION_REQUIRED
    failure is PrivilegeFailure.RootPermissionDenied ||
        failure is PrivilegeFailure.ShizukuPermissionDenied -> BrowserAccessLossReason.PERMISSION_DENIED
    failure is PrivilegeFailure.RootUnavailable -> BrowserAccessLossReason.BACKEND_UNAVAILABLE
    failure is PrivilegeFailure.Incompatible ||
        failure is PrivilegeFailure.UnexpectedIdentity -> BrowserAccessLossReason.BACKEND_INCOMPATIBLE
    activeBackend != null && activeBackend != requestedBackend -> BrowserAccessLossReason.BACKEND_CHANGED
    backendState == PrivilegeConnectionState.INSTALLED_BUT_STOPPED -> BrowserAccessLossReason.SERVICE_STOPPED
    backendState == PrivilegeConnectionState.PERMISSION_REQUIRED -> BrowserAccessLossReason.PERMISSION_REQUIRED
    backendState == PrivilegeConnectionState.PERMISSION_DENIED -> BrowserAccessLossReason.PERMISSION_DENIED
    backendState == PrivilegeConnectionState.UNAVAILABLE -> BrowserAccessLossReason.BACKEND_UNAVAILABLE
    backendState == PrivilegeConnectionState.INCOMPATIBLE -> BrowserAccessLossReason.BACKEND_INCOMPATIBLE
    else -> BrowserAccessLossReason.CONNECTION_FAILED
}

private fun PrivilegeBackendId.toPrivilegeMode(): PrivilegeMode = when (this) {
    PrivilegeBackendId.ROOT -> PrivilegeMode.ROOT
    PrivilegeBackendId.SHIZUKU -> PrivilegeMode.SHIZUKU
    else -> PrivilegeMode.NORMAL
}

private fun Throwable.asPrivilegeFailure(): PrivilegeFailure = this as? PrivilegeFailure
    ?: PrivilegeFailure.Failed("Storage access action failed", this)
