package dev.qtremors.arcile.feature.onboarding

import android.content.Context
import dev.qtremors.arcile.core.storage.domain.OnboardingPreferences
import dev.qtremors.arcile.core.storage.domain.OnboardingPreferencesStore
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

    private val context = mockk<Context>(relaxed = true)

    @Test
    fun `next moves from welcome directly to setup permissions`() = runTest(mainDispatcherRule.dispatcher) {
        val store = FakeOnboardingPreferencesStore()
        val viewModel = OnboardingViewModel(store, context)

        viewModel.next()

        assertEquals(OnboardingStep.SetupPermissions, viewModel.state.value.step)
        assertFalse(store.preferencesFlow.value.isCompleted)
    }

    @Test
    fun `back returns from setup permissions to welcome`() = runTest(mainDispatcherRule.dispatcher) {
        val store = FakeOnboardingPreferencesStore()
        val viewModel = OnboardingViewModel(store, context)

        viewModel.next()
        viewModel.back()

        assertEquals(OnboardingStep.WelcomeAndFeatures, viewModel.state.value.step)
    }

    @Test
    fun `storage permission step blocks completion until storage is granted`() = runTest(mainDispatcherRule.dispatcher) {
        val store = FakeOnboardingPreferencesStore()
        val viewModel = OnboardingViewModel(store, context)

        viewModel.next()
        viewModel.next()
        advanceUntilIdle()

        assertEquals(OnboardingStep.SetupPermissions, viewModel.state.value.step)
        assertFalse(store.preferencesFlow.value.isCompleted)
    }

    @Test
    fun `notification denied or skipped still completes after storage is granted`() = runTest(mainDispatcherRule.dispatcher) {
        val store = FakeOnboardingPreferencesStore()
        val viewModel = OnboardingViewModel(store, context)

        viewModel.next()
        viewModel.updatePermissionState(
            hasStoragePermission = true,
            hasNotificationPermission = false,
            notificationPermissionRequired = true
        )
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
}
