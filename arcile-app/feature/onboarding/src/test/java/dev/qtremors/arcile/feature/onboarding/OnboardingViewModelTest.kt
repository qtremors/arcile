package dev.qtremors.arcile.feature.onboarding

import android.net.Uri
import dev.qtremors.arcile.core.storage.domain.AppVersionCodeProvider
import dev.qtremors.arcile.core.privilege.PrivilegeCoordinator
import dev.qtremors.arcile.core.privilege.PrivilegeBackendId
import dev.qtremors.arcile.core.privilege.PrivilegeBackendState
import dev.qtremors.arcile.core.privilege.PrivilegeConnectionState
import dev.qtremors.arcile.core.privilege.PrivilegeMode
import dev.qtremors.arcile.core.privilege.PrivilegeServiceIdentity
import dev.qtremors.arcile.core.privilege.PrivilegeSession
import dev.qtremors.arcile.core.privilege.PrivilegeState
import dev.qtremors.arcile.core.privilege.PrivilegeTransport
import dev.qtremors.arcile.core.storage.domain.OnboardingPreferences
import dev.qtremors.arcile.core.storage.domain.OnboardingPreferencesStore
import dev.qtremors.arcile.core.ui.backup.PreferencesBackupGateway
import dev.qtremors.arcile.core.ui.backup.PreferencesBackupItem
import dev.qtremors.arcile.core.ui.backup.PreferencesBackupItemStatus
import dev.qtremors.arcile.core.ui.backup.PreferencesBackupOperationResult
import dev.qtremors.arcile.core.ui.backup.PreferencesBackupPreview
import dev.qtremors.arcile.testutil.MainDispatcherRule
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `next moves from welcome directly to setup permissions`() = runTest(mainDispatcherRule.dispatcher) {
        val store = FakeOnboardingPreferencesStore()
        val viewModel = createViewModel(store)

        viewModel.next()

        assertEquals(OnboardingStep.SetupPermissions, viewModel.state.value.step)
        assertFalse(store.preferencesFlow.value.isCompleted)
    }

    @Test
    fun `back returns from setup permissions to welcome`() = runTest(mainDispatcherRule.dispatcher) {
        val store = FakeOnboardingPreferencesStore()
        val viewModel = createViewModel(store)

        viewModel.next()
        viewModel.back()

        assertEquals(OnboardingStep.WelcomeAndFeatures, viewModel.state.value.step)
    }

    @Test
    fun `storage permission step blocks completion until storage is granted`() = runTest(mainDispatcherRule.dispatcher) {
        val store = FakeOnboardingPreferencesStore()
        val viewModel = createViewModel(store)

        viewModel.next()
        viewModel.next()
        advanceUntilIdle()

        assertEquals(OnboardingStep.SetupPermissions, viewModel.state.value.step)
        assertFalse(store.preferencesFlow.value.isCompleted)
    }

    @Test
    fun `notification denied or skipped still completes after storage is granted`() = runTest(mainDispatcherRule.dispatcher) {
        val store = FakeOnboardingPreferencesStore()
        val viewModel = createViewModel(store)

        viewModel.next()
        viewModel.updatePermissionState(
            hasStoragePermission = true,
            hasNotificationPermission = false,
            notificationPermissionRequired = true
        )
        viewModel.next()
        advanceUntilIdle()

        assertTrue(store.preferencesFlow.value.isCompleted)
        assertEquals(321, store.preferencesFlow.value.completedVersion)
        assertTrue(store.preferencesFlow.value.notificationPermissionHandled)
    }

    @Test
    fun `preferences state is loaded from store`() = runTest(mainDispatcherRule.dispatcher) {
        val store = FakeOnboardingPreferencesStore(
            OnboardingPreferences(isCompleted = true, completedVersion = 123)
        )
        val viewModel = createViewModel(store)

        advanceUntilIdle()

        assertTrue(viewModel.state.value.preferencesLoaded)
        assertTrue(viewModel.state.value.isCompleted)
    }

    @Test
    fun `backup preview and restore retain gateway results`() = runTest(mainDispatcherRule.dispatcher) {
        val item = PreferencesBackupItem(
            id = "theme",
            label = "Theme",
            status = PreferencesBackupItemStatus.WillRestore
        )
        val gateway = FakePreferencesBackupGateway(
            previewResult = Result.success(PreferencesBackupPreview(10L, listOf(item))),
            restoreResult = Result.success(
                PreferencesBackupOperationResult(
                    items = listOf(item.copy(status = PreferencesBackupItemStatus.Restored))
                )
            )
        )
        val viewModel = createViewModel(backupGateway = gateway)
        val uri = mockk<Uri>()

        viewModel.previewBackup(uri)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.backupState is OnboardingBackupState.Preview)

        viewModel.restoreBackup(uri)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.backupState is OnboardingBackupState.Restored)
    }

    @Test
    fun `automatic setup authorizes detected Root then restores automatic selection`() = runTest(mainDispatcherRule.dispatcher) {
        val coordinator = FakePrivilegeCoordinator()
        coordinator.emit(
            PrivilegeState(
                backendStates = mapOf(
                    PrivilegeBackendId.ROOT to PrivilegeBackendState(
                        backendId = PrivilegeBackendId.ROOT,
                        connectionState = PrivilegeConnectionState.PERMISSION_REQUIRED
                    )
                )
            )
        )
        val viewModel = createViewModel(privilegeCoordinator = coordinator)

        viewModel.prepareAutomaticAccess()
        advanceUntilIdle()

        assertEquals(
            listOf(PrivilegeMode.ROOT to true, PrivilegeMode.AUTOMATIC to false),
            coordinator.selections
        )
    }

    @Test
    fun `automatic setup skips Root authorization when Root is unavailable`() = runTest(mainDispatcherRule.dispatcher) {
        val coordinator = FakePrivilegeCoordinator()
        val viewModel = createViewModel(privilegeCoordinator = coordinator)

        viewModel.prepareAutomaticAccess()
        advanceUntilIdle()

        assertEquals(listOf(PrivilegeMode.AUTOMATIC to false), coordinator.selections)
    }

    @Test
    fun `ready Root access completes onboarding without Normal access`() =
        runTest(mainDispatcherRule.dispatcher) {
            val store = FakeOnboardingPreferencesStore()
            val coordinator = FakePrivilegeCoordinator()
            val viewModel = createViewModel(store, privilegeCoordinator = coordinator)
            viewModel.next()
            coordinator.emit(
                PrivilegeState(
                    preferredMode = PrivilegeMode.ROOT,
                    activeBackend = PrivilegeBackendId.ROOT,
                    backendStates = mapOf(
                        PrivilegeBackendId.ROOT to PrivilegeBackendState(
                            backendId = PrivilegeBackendId.ROOT,
                            connectionState = PrivilegeConnectionState.READY
                        )
                    ),
                    identity = PrivilegeServiceIdentity(
                        effectiveUid = 0,
                        pid = 42,
                        transport = PrivilegeTransport.ROOT_SERVICE
                    )
                )
            )
            advanceUntilIdle()

            viewModel.next()
            advanceUntilIdle()

            assertTrue(store.preferencesFlow.value.isCompleted)
        }

    private fun createViewModel(
        store: FakeOnboardingPreferencesStore = FakeOnboardingPreferencesStore(),
        backupGateway: PreferencesBackupGateway = FakePreferencesBackupGateway(),
        privilegeCoordinator: PrivilegeCoordinator = FakePrivilegeCoordinator()
    ) = OnboardingViewModel(
        onboardingPreferencesStore = store,
        backupGateway = backupGateway,
        appVersionCodeProvider = AppVersionCodeProvider { 321 },
        privilegeCoordinator = privilegeCoordinator
    )
}

private class FakePrivilegeCoordinator : PrivilegeCoordinator {
    private val mutableState = MutableStateFlow(PrivilegeState())
    override val state: StateFlow<PrivilegeState> = mutableState.asStateFlow()
    var selectedMode: PrivilegeMode? = null
    var requestedAuthorization: Boolean = false
    val selections = mutableListOf<Pair<PrivilegeMode, Boolean>>()

    fun emit(state: PrivilegeState) {
        mutableState.value = state
    }

    override suspend fun start() = Unit
    override suspend fun refresh() = Unit
    override suspend fun selectMode(mode: PrivilegeMode, requestAuthorization: Boolean) {
        selectedMode = mode
        requestedAuthorization = requestAuthorization
        selections += mode to requestAuthorization
        mutableState.value = mutableState.value.copy(preferredMode = mode)
    }

    override suspend fun reconnect(requestAuthorization: Boolean) {
        requestedAuthorization = requestAuthorization
    }

    override suspend fun useNormal() {
        selectedMode = PrivilegeMode.NORMAL
    }

    override fun captureSession(): Result<PrivilegeSession> =
        Result.failure(IllegalStateException("No active backend"))
}

private class FakeOnboardingPreferencesStore(
    initial: OnboardingPreferences = OnboardingPreferences()
) : OnboardingPreferencesStore {
    private val _preferencesFlow = MutableStateFlow(initial)
    override val preferencesFlow: StateFlow<OnboardingPreferences> = _preferencesFlow.asStateFlow()

    override suspend fun markCompleted(completedVersion: Int, notificationPermissionHandled: Boolean) {
        _preferencesFlow.value = OnboardingPreferences(
            isCompleted = true,
            completedVersion = completedVersion,
            notificationPermissionHandled = notificationPermissionHandled
        )
    }

    override suspend fun markNotificationPermissionHandled() {
        _preferencesFlow.value = _preferencesFlow.value.copy(notificationPermissionHandled = true)
    }
}

private class FakePreferencesBackupGateway(
    private val previewResult: Result<PreferencesBackupPreview> =
        Result.failure(UnsupportedOperationException()),
    private val restoreResult: Result<PreferencesBackupOperationResult> =
        Result.failure(UnsupportedOperationException())
) : PreferencesBackupGateway {
    override suspend fun exportTo(uri: Uri): Result<PreferencesBackupOperationResult> =
        Result.failure(UnsupportedOperationException())

    override suspend fun preview(uri: Uri): Result<PreferencesBackupPreview> = previewResult

    override suspend fun restoreFrom(uri: Uri): Result<PreferencesBackupOperationResult> =
        restoreResult
}
