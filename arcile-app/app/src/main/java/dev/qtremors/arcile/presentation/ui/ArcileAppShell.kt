package dev.qtremors.arcile.presentation.ui

import android.net.Uri
import androidx.compose.animation.ExperimentalSharedTransitionApi
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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.qtremors.arcile.navigation.AppRoutes
import dev.qtremors.arcile.presentation.browser.BrowserViewModel
import dev.qtremors.arcile.presentation.home.HomeRefreshMode
import dev.qtremors.arcile.presentation.home.HomeViewModel
import dev.qtremors.arcile.presentation.recentfiles.RecentFilesViewModel
import dev.qtremors.arcile.presentation.trash.TrashViewModel
import androidx.compose.ui.res.stringResource
import dev.qtremors.arcile.R
import dev.qtremors.arcile.ui.theme.ThemeState
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ArcileAppShell(
    currentThemeState: ThemeState,
    onThemeChange: (ThemeState) -> Unit,
    onOpenFile: (String) -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: AppRoutes.Home
    val context = LocalContext.current

    androidx.compose.animation.SharedTransitionLayout {
        Scaffold(
            contentWindowInsets = WindowInsets(0)
        ) { scaffoldPadding ->
            Box(modifier = Modifier.padding(scaffoldPadding)) {
                AppNavigationGraph(
                    navController = navController,
                    currentThemeState = currentThemeState,
                    onThemeChange = onThemeChange,
                    onOpenFile = onOpenFile
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
            text = stringResource(R.string.permission_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.permission_description),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onRequestPermission) {
            Text(stringResource(R.string.grant_permission))
        }
    }
}


