package dev.qtremors.arcile

import java.util.UUID

enum class AppLaunchMode {
    ColdLauncher,
    ConfigurationRecreation
}

data class AppLaunchContext(
    val mode: AppLaunchMode,
    val navigationSessionId: String
)

/**
 * Owns process-lifetime launch identity.
 *
 * Android may restore an Activity's saved navigation state after recreating the app process.
 * Arcile intentionally starts a new process on Main/Home while still preserving navigation
 * through configuration recreation inside the same process.
 */
class AppSessionTracker(
    private val navigationSessionIdFactory: () -> String = { UUID.randomUUID().toString() }
) {
    private var hasCreatedMainActivity = false
    private var navigationSessionId = navigationSessionIdFactory()

    @Synchronized
    fun onMainActivityCreated(hasSavedInstanceState: Boolean): AppLaunchContext {
        val mode = if (hasCreatedMainActivity && hasSavedInstanceState) {
            AppLaunchMode.ConfigurationRecreation
        } else {
            AppLaunchMode.ColdLauncher
        }
        if (hasCreatedMainActivity && mode == AppLaunchMode.ColdLauncher) {
            navigationSessionId = navigationSessionIdFactory()
        }
        hasCreatedMainActivity = true
        return AppLaunchContext(
            mode = mode,
            navigationSessionId = navigationSessionId
        )
    }
}
