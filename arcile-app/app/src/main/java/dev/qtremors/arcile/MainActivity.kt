package dev.qtremors.arcile

import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.StrictMode
import android.os.Trace
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import android.net.Uri
import dev.qtremors.arcile.backup.PreferencesBackupItemStatus
import dev.qtremors.arcile.backup.PreferencesBackupManager
import dev.qtremors.arcile.backup.PreferencesBackupOperationResult
import dev.qtremors.arcile.backup.PreferencesBackupPreview
import dev.qtremors.arcile.core.storage.domain.OnboardingPreferencesStore
import dev.qtremors.arcile.core.storage.domain.OnboardingPreferences
import dev.qtremors.arcile.presentation.MainViewModel
import dev.qtremors.arcile.feature.onboarding.OnboardingStep
import dev.qtremors.arcile.feature.onboarding.OnboardingViewModel
import dev.qtremors.arcile.presentation.ui.ArcileAppShell
import dev.qtremors.arcile.presentation.utils.ExternalFileAccessHelper
import dev.qtremors.arcile.utils.AppLogger
import dev.qtremors.arcile.presentation.ui.PermissionRequestScreen
import dev.qtremors.arcile.feature.onboarding.ui.OnboardingScreen
import dev.qtremors.arcile.feature.onboarding.ui.OnboardingRestoreFailure
import dev.qtremors.arcile.feature.onboarding.ui.OnboardingRestoreItem
import dev.qtremors.arcile.feature.onboarding.ui.OnboardingRestoreState
import dev.qtremors.arcile.ui.theme.ArcileTheme
import dev.qtremors.arcile.ui.theme.ThemePreferences
import dev.qtremors.arcile.ui.theme.ThemeState
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

    @Inject
    lateinit var preferencesBackupManager: PreferencesBackupManager

    private companion object {
        const val FULL_FEATURE_ANDROID_SDK = Build.VERSION_CODES.R
    }

    override fun onCreate(savedInstanceState: Bundle?) {
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
            val themeState by themePreferences.themeState.collectAsStateWithLifecycle(initialValue = ThemeState())
            val onboardingPreferences by produceState<OnboardingPreferences?>(initialValue = null) {
                onboardingPreferencesStore.preferencesFlow.collect { value = it }
            }
            var initialOnboardingPreferences by remember { mutableStateOf<OnboardingPreferences?>(null) }
            val onboardingViewModel: OnboardingViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel()
            val onboardingState by onboardingViewModel.state.collectAsStateWithLifecycle()
            val coroutineScope = rememberCoroutineScope()
            val notificationPermissionRequired = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            val hasNotificationPermission = !notificationPermissionRequired ||
                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
            val notificationLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) {
                onboardingViewModel.handleNotificationPermissionResult()
            }
            var onboardingRestoreState by remember { mutableStateOf<OnboardingRestoreState>(OnboardingRestoreState.Idle) }
            var pendingOnboardingRestoreUri by remember { mutableStateOf<Uri?>(null) }
            val restoreBackupLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri ->
                if (uri != null) {
                    pendingOnboardingRestoreUri = uri
                    onboardingRestoreState = OnboardingRestoreState.Busy
                    coroutineScope.launch {
                        preferencesBackupManager.preview(uri).fold(
                            onSuccess = { preview ->
                                onboardingRestoreState = preview.toOnboardingRestorePreview()
                            },
                            onFailure = { error ->
                                pendingOnboardingRestoreUri = null
                                onboardingRestoreState = OnboardingRestoreState.Failed(error.message ?: getString(R.string.settings_backup_failed_title))
                            }
                        )
                    }
                }
            }

            ArcileTheme(themeState = themeState) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val hasPermission by viewModel.hasPermission.collectAsStateWithLifecycle()

                    LaunchedEffect(hasPermission, hasNotificationPermission, notificationPermissionRequired) {
                        onboardingViewModel.updatePermissionState(
                            hasStoragePermission = hasPermission,
                            hasNotificationPermission = hasNotificationPermission,
                            notificationPermissionRequired = notificationPermissionRequired
                        )
                    }

                    val preferences = onboardingPreferences
                    LaunchedEffect(preferences) {
                        if (initialOnboardingPreferences == null && preferences != null) {
                            initialOnboardingPreferences = preferences
                        }
                    }
                    val suppressManualResetUntilRestart = initialOnboardingPreferences?.isCompleted == true &&
                        preferences != null &&
                        preferences.wasManuallyReset &&
                        !preferences.isCompleted
                    val isOnboardingCompletedForThisRun = preferences?.isCompleted == true || suppressManualResetUntilRestart

                    LaunchedEffect(preferences?.isCompleted, hasPermission, onboardingState.step) {
                        if (preferences != null &&
                            !preferences.isCompleted &&
                            !preferences.wasManuallyReset &&
                            hasPermission &&
                            onboardingState.step == OnboardingStep.WelcomeAndFeatures
                        ) {
                            onboardingViewModel.markExistingUserCompleted()
                        }
                    }

                    val shouldAutoCompleteExistingUser = preferences != null &&
                        !preferences.isCompleted &&
                        !preferences.wasManuallyReset &&
                        hasPermission &&
                        onboardingState.step == OnboardingStep.WelcomeAndFeatures

                    if (preferences == null) {
                        // Keep the themed surface blank until DataStore emits. This prevents a
                        // completed user from seeing the first onboarding page for one frame.
                    } else if (!isOnboardingCompletedForThisRun && !shouldAutoCompleteExistingUser) {
                        OnboardingScreen(
                            state = onboardingState,
                            currentThemeState = themeState,
                            onThemeChange = { newState ->
                                coroutineScope.launch {
                                    themePreferences.saveThemeState(newState)
                                }
                            },
                            onNext = { onboardingViewModel.next() },
                            onBack = { onboardingViewModel.back() },
                            onSkip = { onboardingViewModel.skipToPermissions() },
                            onStepSelected = { step -> onboardingViewModel.setStep(step) },
                            onOpenStoragePermissionSettings = { requestStoragePermission() },
                            onRequestNotificationPermission = {
                                if (notificationPermissionRequired) {
                                    notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    onboardingViewModel.handleNotificationPermissionResult()
                                }
                            },
                            showOlderAndroidWarning = Build.VERSION.SDK_INT < FULL_FEATURE_ANDROID_SDK,
                            restoreState = onboardingRestoreState,
                            onChooseRestoreBackup = {
                                restoreBackupLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                            },
                            onApplyRestoreBackup = {
                                val uri = pendingOnboardingRestoreUri
                                if (uri != null) {
                                    onboardingRestoreState = OnboardingRestoreState.Busy
                                    coroutineScope.launch {
                                        preferencesBackupManager.restoreFrom(uri).fold(
                                            onSuccess = { result ->
                                                onboardingRestoreState = result.toOnboardingRestoreRestored()
                                            },
                                            onFailure = { error ->
                                                onboardingRestoreState = OnboardingRestoreState.Failed(error.message ?: getString(R.string.settings_backup_failed_title))
                                            }
                                        )
                                    }
                                }
                            },
                            onDismissRestoreBackup = {
                                pendingOnboardingRestoreUri = null
                                onboardingRestoreState = OnboardingRestoreState.Idle
                            },
                            onRestartApp = { restartApp() }
                        )
                    } else if (hasPermission) {
                        ArcileAppShell(
                            currentThemeState = themeState,
                            onThemeChange = { newState ->
                                coroutineScope.launch {
                                    themePreferences.saveThemeState(newState)
                                }
                            },
                            onOpenFile = { path -> openFile(path) },
                            onOpenFileWith = { path -> openFileWith(path) },
                            onRestartApp = { restartApp() }
                        )
                    } else {
                        PermissionRequestScreen(
                            onRequestPermission = {
                                requestStoragePermission()
                            }
                        )
                    }
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

    private fun PreferencesBackupPreview.toOnboardingRestorePreview(): OnboardingRestoreState.Preview =
        OnboardingRestoreState.Preview(
            items = items.map { item ->
                OnboardingRestoreItem(
                    label = item.label,
                    status = item.status.toBackupStatusLabel()
                )
            }
        )

    private fun PreferencesBackupOperationResult.toOnboardingRestoreRestored(): OnboardingRestoreState.Restored =
        OnboardingRestoreState.Restored(
            items = items.map { item ->
                OnboardingRestoreItem(
                    label = item.label,
                    status = item.status.toBackupStatusLabel()
                )
            },
            failures = failures.map { failure ->
                OnboardingRestoreFailure(
                    label = failure.label,
                    message = failure.message
                )
            }
        )

    private fun PreferencesBackupItemStatus.toBackupStatusLabel(): String = when (this) {
        PreferencesBackupItemStatus.Exported -> getString(R.string.settings_backup_status_exported)
        PreferencesBackupItemStatus.WillRestore -> getString(R.string.settings_backup_status_will_restore)
        PreferencesBackupItemStatus.WillReset -> getString(R.string.settings_backup_status_will_reset)
        PreferencesBackupItemStatus.Restored -> getString(R.string.settings_backup_status_restored)
        PreferencesBackupItemStatus.Reset -> getString(R.string.settings_backup_status_reset)
    }
}

