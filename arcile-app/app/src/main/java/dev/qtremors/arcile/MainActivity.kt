package dev.qtremors.arcile

import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.StrictMode
import android.os.Trace
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.qtremors.arcile.core.storage.domain.OnboardingPreferencesStore
import dev.qtremors.arcile.presentation.MainViewModel
import dev.qtremors.arcile.feature.onboarding.OnboardingRoute
import dev.qtremors.arcile.presentation.ui.ArcileAppShell
import dev.qtremors.arcile.core.ui.externalfile.ExternalFileAccessHelper
import dev.qtremors.arcile.core.runtime.logging.AppLogger
import dev.qtremors.arcile.presentation.ui.PermissionRequestScreen
import dev.qtremors.arcile.core.ui.theme.ArcileTheme
import dev.qtremors.arcile.core.ui.theme.ThemePreferences
import dev.qtremors.arcile.core.ui.theme.ThemeState
import dev.qtremors.arcile.core.ui.R
import kotlinx.coroutines.launch
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.system.exitProcess

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    @Inject
    lateinit var themePreferences: ThemePreferences

    @Inject
    lateinit var onboardingPreferencesStore: OnboardingPreferencesStore

    override fun onCreate(savedInstanceState: Bundle?) {
        val appLaunchContext = (application as ArcileApp)
            .appSessionTracker
            .onMainActivityCreated(hasSavedInstanceState = savedInstanceState != null)
        installDebugStrictMode()
        val splashScreen = traceStartupSection("Arcile.installSplashScreen") {
            installSplashScreen()
        }
        traceStartupSection("Arcile.activityOnCreate") {
            super.onCreate(savedInstanceState)
        }
        
        enableEdgeToEdge()
        traceStartupSection("Arcile.permissionCheck") {
            viewModel.checkPermission()
        }

        var keepSplashScreen = true
        lifecycleScope.launch {
            try {
                traceStartupSection("Arcile.splashPreferencePreload") {
                    withTimeoutOrNull(2000L) {
                        themePreferences.themeState.first()
                        onboardingPreferencesStore.preferencesFlow.first()
                    }
                }
            } finally {
                keepSplashScreen = false
            }
        }
        splashScreen.setKeepOnScreenCondition { keepSplashScreen }
        
        traceStartupSection("Arcile.setContent") {
            setContent {
                val themeState by themePreferences.themeState.collectAsStateWithLifecycle(
                    initialValue = ThemeState()
                )
                val hasPermission by viewModel.hasPermission.collectAsStateWithLifecycle()
                val coroutineScope = rememberCoroutineScope()

                ArcileTheme(themeState = themeState) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        OnboardingRoute(
                            currentThemeState = themeState,
                            onThemeChange = { newState ->
                                coroutineScope.launch {
                                    themePreferences.saveThemeState(newState)
                                }
                            },
                            hasStoragePermission = hasPermission,
                            onOpenStoragePermissionSettings = ::requestStoragePermission,
                            onRestartApp = ::restartApp,
                            appContent = {
                                ArcileAppShell(
                                    appLaunchContext = appLaunchContext,
                                    currentThemeState = themeState,
                                    onThemeChange = { newState ->
                                        coroutineScope.launch {
                                            themePreferences.saveThemeState(newState)
                                        }
                                    },
                                    onOpenFile = ::openFile,
                                    onOpenFileWith = ::openFileWith,
                                    onRestartApp = ::restartApp
                                )
                            },
                            permissionContent = {
                                PermissionRequestScreen(
                                    onRequestPermission = ::requestStoragePermission
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkPermission()
    }

    // open a file via Intent.ACTION_VIEW using FileProvider
    private fun openFile(path: String) {
        lifecycleScope.launch {
            try {
                if (!path.isContentReference() &&
                    !ExternalFileAccessHelper.isAllowedUserFile(this@MainActivity, java.io.File(path))
                ) {
                    Toast.makeText(this@MainActivity, getString(R.string.cannot_open_sensitive_files), Toast.LENGTH_SHORT).show()
                    return@launch
                }
                startActivity(ExternalFileAccessHelper.createOpenIntent(this@MainActivity, path))
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                AppLogger.e("Arcile", "Failed to open file", e)
                val reason = e.localizedMessage ?: getString(R.string.no_app_found)
                Toast.makeText(this@MainActivity, getString(R.string.cannot_open_file, reason), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openFileWith(path: String) {
        lifecycleScope.launch {
            try {
                if (!path.isContentReference() &&
                    !ExternalFileAccessHelper.isAllowedUserFile(this@MainActivity, java.io.File(path))
                ) {
                    Toast.makeText(this@MainActivity, getString(R.string.cannot_open_sensitive_files), Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val intent = ExternalFileAccessHelper.createOpenIntent(this@MainActivity, path)
                val chooser = android.content.Intent.createChooser(intent, getString(R.string.image_gallery_open_with))
                startActivity(chooser)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                AppLogger.e("Arcile", "Failed to open file with chooser", e)
                val reason = e.localizedMessage ?: getString(R.string.no_app_found)
                Toast.makeText(this@MainActivity, getString(R.string.cannot_open_file, reason), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun requestStoragePermission() {
        val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
        intent.addCategory("android.intent.category.DEFAULT")
        intent.data = android.net.Uri.parse(String.format("package:%s", packageName))
        try {
            startActivity(intent)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            val fallbackIntent = android.content.Intent()
            fallbackIntent.action = android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
            startActivity(fallbackIntent)
        }
    }

    private fun restartApp() {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        if (launchIntent != null) {
            val pendingIntent = android.app.PendingIntent.getActivity(
                this,
                0,
                launchIntent,
                android.app.PendingIntent.FLAG_CANCEL_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
            alarmManager.set(
                android.app.AlarmManager.RTC,
                System.currentTimeMillis() + 250L,
                pendingIntent
            )
            finishAffinity()
            Process.killProcess(Process.myPid())
            exitProcess(0)
        } else {
            recreate()
        }
    }

    private fun installDebugStrictMode() {
        if (!BuildConfig.DEBUG) return

        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
                .build()
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .detectActivityLeaks()
                .penaltyLog()
                .build()
        )
    }

    private inline fun <T> traceStartupSection(name: String, block: () -> T): T {
        Trace.beginSection(name)
        return try {
            block()
        } finally {
            Trace.endSection()
        }
    }

    private fun String.isContentReference(): Boolean =
        runCatching { android.net.Uri.parse(this).scheme == "content" }.getOrDefault(false)

}

