package dev.qtremors.arcile.presentation.onboarding

import dev.qtremors.arcile.core.storage.data.OnboardingPreferencesStore
import dev.qtremors.arcile.core.storage.domain.OnboardingPreferences
import dev.qtremors.arcile.testutil.MainDispatcherRule
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
    fun `skip jumps to storage permission and does not complete onboarding`() = runTest(mainDispatcherRule.dispatcher) {
        val store = FakeOnboardingPreferencesStore()
        val viewModel = OnboardingViewModel(store)

        viewModel.skipToPermissions()

        assertEquals(OnboardingStep.SetupPermissions, viewModel.state.value.step)
        assertTrue(viewModel.state.value.skipMode)
        assertFalse(store.preferencesFlow.value.isCompleted)
    }

    @Test
    fun `storage permission step blocks completion until storage is granted`() = runTest(mainDispatcherRule.dispatcher) {
        val store = FakeOnboardingPreferencesStore()
        val viewModel = OnboardingViewModel(store)

        viewModel.skipToPermissions()
        viewModel.next()
        advanceUntilIdle()

        assertEquals(OnboardingStep.SetupPermissions, viewModel.state.value.step)
        assertFalse(store.preferencesFlow.value.isCompleted)
    }

    @Test
    fun `notification denied or skipped still completes after storage is granted`() = runTest(mainDispatcherRule.dispatcher) {
        val store = FakeOnboardingPreferencesStore()
        val viewModel = OnboardingViewModel(store)

        viewModel.skipToPermissions()
        viewModel.updatePermissionState(
            hasStoragePermission = true,
            hasNotificationPermission = false,
            notificationPermissionRequired = true
        )
        viewModel.next()
        viewModel.next()
        advanceUntilIdle()

        assertTrue(store.preferencesFlow.value.isCompleted)
        assertTrue(store.preferencesFlow.value.notificationPermissionHandled)
    }
}

private class FakeOnboardingPreferencesStore : OnboardingPreferencesStore {
    private val _preferencesFlow = MutableStateFlow(OnboardingPreferences())
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

    override suspend fun resetOnboarding() {
        _preferencesFlow.value = OnboardingPreferences()
    }
}
