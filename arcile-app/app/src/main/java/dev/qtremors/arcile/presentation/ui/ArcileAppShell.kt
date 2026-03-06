package dev.qtremors.arcile.presentation.ui

import android.os.Environment
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.qtremors.arcile.navigation.AppRoutes
import dev.qtremors.arcile.presentation.FileManagerViewModel
import dev.qtremors.arcile.ui.theme.ThemeState
import java.io.File

@Composable
fun ArcileAppShell(
    viewModel: FileManagerViewModel,
    currentThemeState: ThemeState,
    onThemeChange: (ThemeState) -> Unit,
    onOpenFile: (String) -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: AppRoutes.HOME

    val bottomNavItems = listOf(
        Triple(AppRoutes.HOME, "Home", Icons.Default.Home),
        Triple(AppRoutes.EXPLORER, "Browse", Icons.Default.Folder),
        Triple(AppRoutes.TOOLS, "Tools", Icons.Default.Build)
    )

    val showBottomBar = currentRoute in bottomNavItems.map { it.first }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { (route, label, icon) ->
                        NavigationBarItem(
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label) },
                            selected = currentRoute == route,
                            onClick = {
                                if (currentRoute != route) {
                                    if (route == AppRoutes.EXPLORER) {
                                        viewModel.openFileBrowser()
                                    }
                                    navController.navigate(route) {
                                        popUpTo(AppRoutes.HOME) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { scaffoldPadding ->
        Box(modifier = Modifier.padding(scaffoldPadding)) {
            NavHost(
                navController = navController,
                startDestination = AppRoutes.HOME,
                enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn() },
                exitTransition = { slideOutHorizontally(targetOffsetX = { -it / 3 }) + fadeOut() },
                popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 3 }) + fadeIn() },
                popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() }
            ) {
                composable(AppRoutes.HOME) {
                    val state by viewModel.state.collectAsStateWithLifecycle()
                    HomeScreen(
                        state = state,
                        onOpenFileBrowser = {
                            viewModel.openFileBrowser()
                            navController.navigate(AppRoutes.EXPLORER) {
                                popUpTo(AppRoutes.HOME) { saveState = true }
                                launchSingleTop = true
                            }
                        },
                        onNavigateToPath = { path ->
                            viewModel.navigateToSpecificFolder(path)
                            navController.navigate(AppRoutes.EXPLORER) {
                                popUpTo(AppRoutes.HOME) { saveState = true }
                                launchSingleTop = true
                            }
                        },
                        onOpenFile = onOpenFile,
                        onSearchQueryChange = { viewModel.updateHomeSearchQuery(it) },
                        onClearSearch = { viewModel.updateHomeSearchQuery("") },
                        onSortOptionChange = { viewModel.updateHomeSortOption(it) },
                        onCategoryClick = { categoryName ->
                            viewModel.navigateToCategory(categoryName)
                            navController.navigate(AppRoutes.EXPLORER) {
                                popUpTo(AppRoutes.HOME) { saveState = true }
                                launchSingleTop = true
                            }
                        },
                        onSettingsClick = {
                            navController.navigate(AppRoutes.SETTINGS)
                        }
                    )
                }
                composable(AppRoutes.EXPLORER) {
                    val state by viewModel.state.collectAsStateWithLifecycle()

                    LaunchedEffect(Unit) {
                        if (state.currentPath.isEmpty()) {
                            viewModel.openFileBrowser()
                        }
                    }

                    FileManagerScreen(
                        state = state,
                        storageRootPath = viewModel.storageRootPath,
                        onNavigateBack = {
                            if (!viewModel.navigateBack()) {
                                navController.popBackStack()
                            }
                        },
                        onNavigateTo = { viewModel.navigateToFolder(it) },
                        onOpenFile = onOpenFile,
                        onToggleSelection = { viewModel.toggleSelection(it) },
                        onClearSelection = { viewModel.clearSelection() },
                        onCreateFolder = { viewModel.createFolder(it) },
                        onDeleteSelected = { viewModel.deleteSelectedFiles() },
                        onRenameFile = { path, newName -> viewModel.renameFile(path, newName) },
                        onSearchQueryChange = { viewModel.updateBrowserSearchQuery(it) },
                        onClearSearch = { viewModel.updateBrowserSearchQuery("") },
                        onSortOptionChange = { viewModel.updateBrowserSortOption(it) },
                        onGridViewChange = { viewModel.setGridView(it) },
                        onClearError = { viewModel.clearError() }
                    )
                }
                composable(AppRoutes.TOOLS) {
                    ToolsScreen()
                }
                composable(AppRoutes.SETTINGS) {
                    SettingsScreen(
                        currentThemeState = currentThemeState,
                        onNavigateBack = { navController.popBackStack() },
                        onThemeChange = onThemeChange
                    )
                }
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
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "This application requires permission to read and manage files on your device.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onRequestPermission) {
            Text("Grant Permission")
        }
    }
}


