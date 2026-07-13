package dev.qtremors.arcile.feature.onboarding

import android.net.Uri
import dev.qtremors.arcile.core.storage.domain.AppVersionCodeProvider
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

    private fun createViewModel(
        store: FakeOnboardingPreferencesStore = FakeOnboardingPreferencesStore(),
        backupGateway: PreferencesBackupGateway = FakePreferencesBackupGateway()
    ) = OnboardingViewModel(
        onboardingPreferencesStore = store,
        backupGateway = backupGateway,
        appVersionCodeProvider = AppVersionCodeProvider { 321 }
    )
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
