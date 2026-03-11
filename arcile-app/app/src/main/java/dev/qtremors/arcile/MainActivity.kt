package dev.qtremors.arcile

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.qtremors.arcile.presentation.ui.ArcileAppShell
import dev.qtremors.arcile.presentation.ui.PermissionRequestScreen
import dev.qtremors.arcile.ui.theme.FileManagerTheme
import dev.qtremors.arcile.ui.theme.ThemePreferences
import dev.qtremors.arcile.ui.theme.ThemeState
import kotlinx.coroutines.launch
import java.io.File
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // reactive permission state — updated in onResume so Compose recomposes
    private val _hasPermission = mutableStateOf(false)

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        _hasPermission.value = granted
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        _hasPermission.value = checkStoragePermission()
        
        // Request peak refresh rate outside of Compose recomposition cycle
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                display
            } else {
                @Suppress("DEPRECATION")
                window.windowManager.defaultDisplay
            }
            
            val modes = display?.supportedModes
            val maxRefreshRateMode = modes?.maxByOrNull { it.refreshRate }
            if (maxRefreshRateMode != null) {
                window.let { win ->
                    val layoutParams = win.attributes
                    layoutParams.preferredDisplayModeId = maxRefreshRateMode.modeId
                    win.attributes = layoutParams
                }
            }
        }

        setContent {
            val themePreferences = remember { ThemePreferences(applicationContext) }
            val themeState by themePreferences.themeState.collectAsStateWithLifecycle(initialValue = ThemeState())
            val coroutineScope = rememberCoroutineScope()

            FileManagerTheme(themeState = themeState) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val hasPermission by _hasPermission

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
        _hasPermission.value = checkStoragePermission()
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

    private fun checkStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            val read = checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
            val write = checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
            read && write
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.addCategory("android.intent.category.DEFAULT")
                intent.data = Uri.parse(String.format("package:%s", applicationContext.packageName))
                startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent()
                intent.action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                startActivity(intent)
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