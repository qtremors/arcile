package dev.qtremors.arcile.domain

data class OnboardingPreferences(
    val isCompleted: Boolean = false,
    val completedVersion: Int = 0,
    val notificationPermissionHandled: Boolean = false,
    val wasManuallyReset: Boolean = false
)
