package dev.qtremors.arcile.core.storage.domain

import kotlinx.coroutines.flow.Flow

interface OnboardingPreferencesStore {
    val preferencesFlow: Flow<OnboardingPreferences>

    suspend fun markCompleted(completedVersion: Int, notificationPermissionHandled: Boolean)
    suspend fun markNotificationPermissionHandled()
}
