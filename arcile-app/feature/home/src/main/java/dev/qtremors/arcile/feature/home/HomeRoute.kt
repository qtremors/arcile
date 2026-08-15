package dev.qtremors.arcile.feature.home

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.qtremors.arcile.core.privilege.PrivilegeBackendId
import dev.qtremors.arcile.core.privilege.PrivilegeConnectionState
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.AppStartPage
import dev.qtremors.arcile.core.storage.domain.QuickAccessItem
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.dialogs.AlertDialog
import dev.qtremors.arcile.feature.home.ui.HomeContentIntents
import dev.qtremors.arcile.feature.home.ui.HomeNavigationIntents
import dev.qtremors.arcile.feature.home.ui.HomeScreen

sealed interface HomeDestination {
    data object BrowseRoot : HomeDestination
    data class BrowsePath(val path: String, val backendId: String? = null) : HomeDestination
    data class OpenFile(val path: String, val context: List<FileModel>) : HomeDestination
    data class BrowseCategory(val name: String) : HomeDestination
    data object Settings : HomeDestination
    data object Tools : HomeDestination
    data object About : HomeDestination
    data object Trash : HomeDestination
    data object RecentFiles : HomeDestination
    data object QuickAccess : HomeDestination
    data class ExternalFolder(val uri: String) : HomeDestination
    data class StorageDashboard(val volumeId: String?) : HomeDestination
    data object Cleaner : HomeDestination
    data object ActivityLog : HomeDestination
    data object OnlyFiles : HomeDestination
    data class ShareRecentFile(val path: String, val context: List<FileModel>) : HomeDestination
}

@Composable
fun HomeRoute(
    appStartPage: AppStartPage,
    onAppStartPageChange: (AppStartPage) -> Unit,
    onDestination: (HomeDestination) -> Unit
) {
    val viewModel = hiltViewModel<HomeViewModel>()
    val preferencesViewModel = hiltViewModel<HomePreferencesViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val access by viewModel.accessState.collectAsStateWithLifecycle()
    val accessError by viewModel.accessError.collectAsStateWithLifecycle()
    val homeRecentCarouselLimit by preferencesViewModel.recentCarouselLimit.collectAsStateWithLifecycle()
    var restrictedFolderPrompt by remember { mutableStateOf<QuickAccessItem?>(null) }
    var pendingRestrictedFolder by remember { mutableStateOf<QuickAccessItem?>(null) }

    LaunchedEffect(pendingRestrictedFolder, access.isReady, access.activeBackend, accessError) {
        val item = pendingRestrictedFolder ?: return@LaunchedEffect
        if (accessError != null) {
            pendingRestrictedFolder = null
            return@LaunchedEffect
        }
        val backendId = access.activeBrowserBackendId()
        if (access.isReady && backendId != null) {
            homeRestrictedLocalPath(item.path)?.let { localPath ->
                pendingRestrictedFolder = null
                onDestination(HomeDestination.BrowsePath(localPath, backendId))
            }
        }
    }

    HomeScreen(
        state = state,
        navigationIntents = HomeNavigationIntents(
            openFileBrowser = { onDestination(HomeDestination.BrowseRoot) },
            navigateToPath = { path ->
                if (path == "/") viewModel.loadRootStorageUsage()
                onDestination(HomeDestination.BrowsePath(path))
            },
            openFileWithContext = { path, context ->
                onDestination(HomeDestination.OpenFile(path, context))
            },
            categoryClick = { onDestination(HomeDestination.BrowseCategory(it)) },
            settingsClick = { onDestination(HomeDestination.Settings) },
            navigateToTools = { onDestination(HomeDestination.Tools) },
            navigateToAbout = { onDestination(HomeDestination.About) },
            navigateToTrash = { onDestination(HomeDestination.Trash) },
            navigateToRecentFiles = { onDestination(HomeDestination.RecentFiles) },
            navigateToQuickAccess = { onDestination(HomeDestination.QuickAccess) },
            navigateToExternalFolder = { item ->
                val localPath = homeRestrictedLocalPath(item.path)
                val backendId = access.activeBrowserBackendId()
                if (access.isReady && localPath != null && backendId != null) {
                    onDestination(HomeDestination.BrowsePath(localPath, backendId))
                } else {
                    val rootDetected = access.backendStates[PrivilegeBackendId.ROOT]
                        ?.connectionState
                        ?.let { it != PrivilegeConnectionState.UNAVAILABLE } == true
                    val shizukuAvailable = access.backendStates[PrivilegeBackendId.SHIZUKU]
                        ?.connectionState
                        ?.let { it != PrivilegeConnectionState.UNAVAILABLE } == true
                    if (!rootDetected && shizukuAvailable && localPath != null) {
                        restrictedFolderPrompt = item
                    } else {
                        onDestination(HomeDestination.ExternalFolder(item.path))
                    }
                }
            },
            openStorageDashboard = { onDestination(HomeDestination.StorageDashboard(it)) },
            navigateToCleaner = { onDestination(HomeDestination.Cleaner) },
            navigateToActivity = { onDestination(HomeDestination.ActivityLog) },
            navigateToOnlyFiles = { onDestination(HomeDestination.OnlyFiles) }
        ),
        contentIntents = HomeContentIntents(
            refresh = { viewModel.loadHomeData(HomeRefreshMode.MANUAL) },
            resumeRefresh = { viewModel.loadHomeData(HomeRefreshMode.SILENT) },
            loadRootStorageUsage = viewModel::loadRootStorageUsage,
            shareRecentFile = { path ->
                onDestination(HomeDestination.ShareRecentFile(path, state.displayState.todayRecentFiles))
            },
            setVolumeClassification = viewModel::setVolumeClassification,
            hideClassificationPrompt = viewModel::hideClassificationPrompt
        ),
        appStartPage = appStartPage,
        onAppStartPageChange = onAppStartPageChange,
        homeRecentCarouselLimit = homeRecentCarouselLimit,
        onHomeLayoutPreferencesChange = viewModel::updateHomeLayoutPreferences
    )

    restrictedFolderPrompt?.let { item ->
        AlertDialog(
            onDismissRequest = { restrictedFolderPrompt = null },
            title = { Text(stringResource(R.string.quick_access_shizuku_prompt_title, item.label)) },
            text = { Text(stringResource(R.string.quick_access_shizuku_prompt_description)) },
            confirmButton = {
                Button(
                    onClick = {
                        pendingRestrictedFolder = item
                        restrictedFolderPrompt = null
                        viewModel.enableShizuku()
                    }
                ) {
                    Text(stringResource(R.string.quick_access_enable_shizuku))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        restrictedFolderPrompt = null
                        onDestination(HomeDestination.ExternalFolder(item.path))
                    }
                ) {
                    Text(stringResource(R.string.quick_access_open_in_files))
                }
            }
        )
    }

    accessError?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissAccessError,
            title = { Text(stringResource(R.string.quick_access_error_title)) },
            text = { Text(message) },
            confirmButton = {
                Button(onClick = viewModel::dismissAccessError) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }
}

private fun dev.qtremors.arcile.core.privilege.PrivilegeState.activeBrowserBackendId(): String? =
    when (activeBackend) {
        PrivilegeBackendId.ROOT -> StorageNodeRef.ROOT_BACKEND_ID
        PrivilegeBackendId.SHIZUKU -> StorageNodeRef.SHIZUKU_BACKEND_ID
        else -> null
    }

internal fun homeRestrictedLocalPath(uriString: String): String? = runCatching {
    val uri = Uri.parse(uriString)
    require(uri.scheme == ContentResolver.SCHEME_CONTENT)
    require(uri.authority == EXTERNAL_STORAGE_AUTHORITY)
    val documentId = DocumentsContract.getDocumentId(uri)
    require(documentId.startsWith("$PRIMARY_STORAGE_ROOT:"))
    val relativePath = documentId.removePrefix("$PRIMARY_STORAGE_ROOT:").trim('/')
    require(relativePath.split('/').none { it == "." || it == ".." })
    if (relativePath.isEmpty()) "/storage/emulated/0"
    else "/storage/emulated/0/$relativePath"
}.getOrNull()

private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
private const val PRIMARY_STORAGE_ROOT = "primary"
