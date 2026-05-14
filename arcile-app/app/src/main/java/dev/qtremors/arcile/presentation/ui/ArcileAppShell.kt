package dev.qtremors.arcile.presentation.ui

import android.net.Uri
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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
import dev.qtremors.arcile.presentation.ui.components.ArcileSnackbarHost

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

    val snackbarHostState = remember { SnackbarHostState() }

    androidx.compose.animation.SharedTransitionLayout {
        Scaffold(
            snackbarHost = { ArcileSnackbarHost(hostState = snackbarHostState) },
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
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
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(88.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(42.dp))
            }
        }
        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = stringResource(R.string.permission_recovery_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.permission_recovery_description),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.PrivacyTip, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.size(12.dp))
            Text(
                text = stringResource(R.string.permission_recovery_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onRequestPermission,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Settings, contentDescription = null)
            Spacer(modifier = Modifier.size(8.dp))
            Text(stringResource(R.string.grant_permission))
        }
    }
}


