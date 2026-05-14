package dev.qtremors.arcile

import android.os.Build
import android.os.Bundle
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
import dev.qtremors.arcile.data.OnboardingPreferencesStore
import dev.qtremors.arcile.domain.OnboardingPreferences
import dev.qtremors.arcile.presentation.MainViewModel
import dev.qtremors.arcile.presentation.onboarding.OnboardingStep
import dev.qtremors.arcile.presentation.onboarding.OnboardingViewModel
import dev.qtremors.arcile.presentation.ui.ArcileAppShell
import dev.qtremors.arcile.presentation.utils.ExternalFileAccessHelper
import dev.qtremors.arcile.utils.AppLogger
import dev.qtremors.arcile.presentation.ui.PermissionRequestScreen
import dev.qtremors.arcile.presentation.ui.OnboardingScreen
import dev.qtremors.arcile.ui.theme.ArcileTheme
import dev.qtremors.arcile.ui.theme.ThemePreferences
import dev.qtremors.arcile.ui.theme.ThemeState
import kotlinx.coroutines.launch
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    @Inject
    lateinit var themePreferences: ThemePreferences

    @Inject
    lateinit var onboardingPreferencesStore: OnboardingPreferencesStore

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge()
        viewModel.checkPermission()

        var keepSplashScreen = true
        lifecycleScope.launch {
            try {
                withTimeoutOrNull(2000L) {
                    themePreferences.themeState.first()
                }
            } finally {
                keepSplashScreen = false
            }
        }
        splashScreen.setKeepOnScreenCondition { keepSplashScreen }
        
        setContent {
            val themeState by themePreferences.themeState.collectAsStateWithLifecycle(initialValue = ThemeState())
            val onboardingPreferences by onboardingPreferencesStore.preferencesFlow.collectAsStateWithLifecycle(
                initialValue = OnboardingPreferences()
            )
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

                    LaunchedEffect(onboardingPreferences.isCompleted, hasPermission, onboardingState.step) {
                        if (!onboardingPreferences.isCompleted &&
                            hasPermission &&
                            onboardingState.step == OnboardingStep.Welcome
                        ) {
                            onboardingViewModel.markExistingUserCompleted()
                        }
                    }

                    val shouldAutoCompleteExistingUser = !onboardingPreferences.isCompleted &&
                        hasPermission &&
                        onboardingState.step == OnboardingStep.Welcome

                    if (!onboardingPreferences.isCompleted && !shouldAutoCompleteExistingUser) {
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
                            onOpenStoragePermissionSettings = { requestStoragePermission() },
                            onRequestNotificationPermission = {
                                if (notificationPermissionRequired) {
                                    notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    onboardingViewModel.handleNotificationPermissionResult()
                                }
                            }
                        )
                    } else if (hasPermission) {
                        ArcileAppShell(
                            currentThemeState = themeState,
                            onThemeChange = { newState ->
                                coroutineScope.launch {
                                    themePreferences.saveThemeState(newState)
                                }
                            },
                            onOpenFile = { path -> openFile(path) }
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

    override fun onResume() {
        super.onResume()
        viewModel.checkPermission()
    }

    // open a file via Intent.ACTION_VIEW using FileProvider
    private fun openFile(path: String) {
        lifecycleScope.launch {
            try {
                if (!ExternalFileAccessHelper.isAllowedUserFile(this@MainActivity, java.io.File(path))) {
                    Toast.makeText(this@MainActivity, "Cannot open sensitive files", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                startActivity(ExternalFileAccessHelper.createOpenIntent(this@MainActivity, path))
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                AppLogger.e("Arcile", "Failed to open file", e)
                Toast.makeText(this@MainActivity, "Cannot open file: ${e.localizedMessage ?: "No app found"}", Toast.LENGTH_SHORT).show()
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
}

