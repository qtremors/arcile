package dev.qtremors.arcile

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.qtremors.arcile.presentation.MainViewModel
import dev.qtremors.arcile.presentation.ui.ArcileAppShell
import dev.qtremors.arcile.presentation.ui.PermissionRequestScreen
import dev.qtremors.arcile.ui.theme.FileManagerTheme
import dev.qtremors.arcile.ui.theme.ThemePreferences
import dev.qtremors.arcile.ui.theme.ThemeState
import kotlinx.coroutines.launch
import java.io.File
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    @Inject
    lateinit var themePreferences: ThemePreferences

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        viewModel.checkPermission()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge()
        viewModel.checkPermission()

        var keepSplashScreen = true
        lifecycleScope.launch {
            try {
                themePreferences.themeState.first()
            } finally {
                keepSplashScreen = false
            }
        }
        splashScreen.setKeepOnScreenCondition { keepSplashScreen }
        
        setContent {
            val themeState by themePreferences.themeState.collectAsStateWithLifecycle(initialValue = ThemeState())
            val coroutineScope = rememberCoroutineScope()

            FileManagerTheme(themeState = themeState) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val hasPermission by viewModel.hasPermission.collectAsStateWithLifecycle()

                    if (hasPermission) {
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
        try {
            val file = File(path)
            val uri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                file
            )
            val mimeType = MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(file.extension.lowercase())
                ?: "*/*"

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("Arcile", "Failed to open file: $path", e)
            Toast.makeText(this, "Cannot open file: ${e.localizedMessage ?: "No app found"}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestStoragePermission() {
        val intent = viewModel.getPermissionRequestIntent()
        if (intent != null) {
            try {
                startActivity(intent)
            } catch (e: Exception) {
                val fallbackIntent = Intent()
                fallbackIntent.action = android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                startActivity(fallbackIntent)
            }
        } else {
            storagePermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }
}