package dev.qtremors.arcile.presentation

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope
import dev.qtremors.arcile.core.privilege.ApplicationAccessCheck
import dev.qtremors.arcile.core.privilege.ApplicationAccessSnapshot
import dev.qtremors.arcile.core.privilege.ApplicationAccessReadiness
import dev.qtremors.arcile.core.privilege.PrivilegeBackendId
import dev.qtremors.arcile.core.privilege.PrivilegeConnectionState
import dev.qtremors.arcile.core.privilege.PrivilegeCoordinator
import dev.qtremors.arcile.core.privilege.PrivilegeFailure
import dev.qtremors.arcile.core.privilege.PrivilegeState
import dev.qtremors.arcile.core.privilege.toApplicationAccessSnapshot
import dev.qtremors.arcile.core.storage.domain.BrowserLocationPreferencesStore
import dev.qtremors.arcile.core.storage.domain.AppStartPage
import dev.qtremors.arcile.core.storage.domain.FileOpenBehavior
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val browserPreferencesStore: BrowserLocationPreferencesStore,
    private val privilegeCoordinator: PrivilegeCoordinator
) : ViewModel() {
    private val accessActionMutex = Mutex()
    private val accessCheck = MutableStateFlow(ApplicationAccessCheck.INITIALIZING)
    private val hasReachedReadyState = MutableStateFlow(false)
    private val actionFailure = MutableStateFlow<PrivilegeFailure?>(null)

    val applicationAccess: StateFlow<ApplicationAccessSnapshot> = combine(
        privilegeCoordinator.state,
        accessCheck,
        hasReachedReadyState,
        actionFailure
    ) { state, check, previouslyReady, failure ->
        if (failure != null && !state.isReady && check == ApplicationAccessCheck.IDLE) {
            ApplicationAccessSnapshot(
                readiness = ApplicationAccessReadiness.Failed(
                    preferredMode = state.preferredMode,
                    normalAccessReady = state.backendStates[PrivilegeBackendId.NORMAL]
                        ?.connectionState == PrivilegeConnectionState.READY,
                    retainedBackend = state.activeBackend,
                    failure = failure,
                    backendState = PrivilegeConnectionState.FAILED
                ),
                hasReachedReadyState = previouslyReady
            )
        } else {
            state.toApplicationAccessSnapshot(
                check = check,
                hasReachedReadyState = previouslyReady
            )
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        PrivilegeState().toApplicationAccessSnapshot(
            check = ApplicationAccessCheck.INITIALIZING
        )
    )

    /** Compatibility name for onboarding while its provider-selection UI is introduced separately. */
    val hasPermission: StateFlow<Boolean> = applicationAccess
        .map { snapshot -> snapshot.isReady }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val fileOpenBehaviors: StateFlow<Map<String, FileOpenBehavior>> =
        browserPreferencesStore.locationPreferencesFlow
            .map { it.fileOpenBehaviors }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    val appStartPage: StateFlow<AppStartPage?> =
        browserPreferencesStore.locationPreferencesFlow
            .map { it.appStartPage }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            privilegeCoordinator.state.collect { state ->
                if (state.isReady) hasReachedReadyState.value = true
            }
        }
        runAccessAction(ApplicationAccessCheck.INITIALIZING) {
            privilegeCoordinator.start()
        }
    }

    /** Passive activity-resume refresh. It never requests authorization. */
    fun refreshAccess() = runAccessAction(ApplicationAccessCheck.RESUME_REFRESH) {
        privilegeCoordinator.refresh()
    }

    /** Explicit user retry. The coordinator may request authorization for an explicit mode. */
    fun reconnectAccess() = runAccessAction(ApplicationAccessCheck.RECONNECTING) {
        privilegeCoordinator.reconnect(requestAuthorization = true)
    }

    /** Explicit fallback; Normal still reports setup required until all-files access is granted. */
    fun useNormalAccess() = runAccessAction(ApplicationAccessCheck.SWITCHING_TO_NORMAL) {
        privilegeCoordinator.useNormal()
    }

    fun updateAppStartPage(page: AppStartPage) {
        viewModelScope.launch {
            browserPreferencesStore.updateAppStartPage(page)
        }
    }

    private fun runAccessAction(
        check: ApplicationAccessCheck,
        action: suspend () -> Unit
    ) {
        viewModelScope.launch {
            accessActionMutex.withLock {
                actionFailure.value = null
                accessCheck.value = check
                try {
                    action()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    actionFailure.value = error as? PrivilegeFailure
                        ?: PrivilegeFailure.Failed("Storage access check failed", error)
                } finally {
                    accessCheck.value = ApplicationAccessCheck.IDLE
                }
            }
        }
    }
}
