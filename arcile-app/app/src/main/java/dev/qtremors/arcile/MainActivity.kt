package dev.qtremors.arcile

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.qtremors.arcile.presentation.FileManagerViewModel
import dev.qtremors.arcile.presentation.ui.FileManagerScreen
import dev.qtremors.arcile.presentation.ui.HomeScreen
import dev.qtremors.arcile.presentation.ui.SettingsScreen
import dev.qtremors.arcile.presentation.ui.ToolsScreen
import dev.qtremors.arcile.presentation.ui.components.NavigationDrawerContent
import dev.qtremors.arcile.ui.theme.FileManagerTheme
import dev.qtremors.arcile.ui.theme.ThemeState
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: FileManagerViewModel by viewModels()

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (granted) {
            viewModel.refresh()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            // For a real app, themeState should be stored in DataStore and observed.
            // Using a default one for now until Settings is fully fleshed out.
            var themeState by remember { mutableStateOf(ThemeState()) }

            FileManagerTheme(themeState = themeState) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var hasPermission by remember { mutableStateOf(checkStoragePermission()) }

                    if (hasPermission) {
                        ArcileAppShell(
                            viewModel = viewModel,
                            onExitApp = { finish() },
                            onThemeChange = { themeState = it }
                        )
                    } else {
                        PermissionRequestScreen(
                            onRequestPermission = {
                                requestStoragePermission()
                                hasPermission = checkStoragePermission()
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check permissions when returning to the app
        if (checkStoragePermission()) {
            viewModel.refresh()
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

@Composable
fun ArcileAppShell(
    viewModel: FileManagerViewModel,
    onExitApp: () -> Unit,
    onThemeChange: (ThemeState) -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val navController = rememberNavController()
    
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "home"

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            NavigationDrawerContent(
                currentRoute = currentRoute,
                onNavigate = { route ->
                    coroutineScope.launch { drawerState.close() }
                    navController.navigate(route) {
                        popUpTo("home") { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    ) {
        // We hoist NavHost here instead of managing custom screen stacking.
        // We will pass the Hamburger opening action to the screens since they define their own TopBar (MD3 guideline for distinct screens).

        NavHost(navController = navController, startDestination = "home") {
            composable("home") {
                val state by viewModel.state.collectAsStateWithLifecycle()
                HomeScreen(
                    state = state,
                    onMenuClick = { coroutineScope.launch { drawerState.open() } },
                    onOpenFileBrowser = { navController.navigate("explorer") }
                )
            }
            composable("explorer") {
                val state by viewModel.state.collectAsStateWithLifecycle()
                FileManagerScreen(
                    state = state,
                    onMenuClick = { coroutineScope.launch { drawerState.open() } },
                    onNavigateBack = { 
                        if (!viewModel.navigateBack()) {
                            navController.popBackStack()
                        }
                    },
                    onNavigateTo = { viewModel.navigateToFolder(it) },
                    onToggleSelection = { viewModel.toggleSelection(it) },
                    onClearSelection = { viewModel.clearSelection() },
                    onCreateFolder = { viewModel.createFolder(it) },
                    onDeleteSelected = { viewModel.deleteSelectedFiles() },
                    onClearError = { viewModel.clearError() }
                )
            }
            composable("tools") {
                ToolsScreen(
                    onMenuClick = { coroutineScope.launch { drawerState.open() } },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable("settings") {
                SettingsScreen(
                    onMenuClick = { coroutineScope.launch { drawerState.open() } },
                    onNavigateBack = { navController.popBackStack() },
                    onThemeChange = onThemeChange
                )
            }
        }
    }
}

@Composable
fun PermissionRequestScreen(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Storage Permission Required",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "This application requires permission to read and manage files on your device.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onRequestPermission) {
            Text("Grant Permission")
        }
    }
}