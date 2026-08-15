package dev.qtremors.arcile.feature.quickaccess

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.qtremors.arcile.core.privilege.PrivilegeBackendId
import dev.qtremors.arcile.core.privilege.PrivilegeConnectionState
import dev.qtremors.arcile.core.storage.domain.QuickAccessItem
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.dialogs.AlertDialog

internal data class QuickAccessActions(
    val navigateBack: () -> Unit,
    val navigateToPath: (String) -> Unit,
    val navigateToSaf: (String) -> Unit,
    val navigateToRestrictedFolder: (QuickAccessItem) -> Unit,
    val togglePin: (QuickAccessItem) -> Unit,
    val removeItem: (QuickAccessItem) -> Unit,
    val addCustomFolder: (String, String) -> Unit,
    val requestSafFolder: () -> Unit,
    val addFilesShortcut: () -> Unit,
    val addAndroidDataShortcut: () -> Unit,
    val addAndroidObbShortcut: () -> Unit,
    val movePinnedItem: (String, Int) -> Unit
)

@Composable
internal fun QuickAccessRoute(
    onNavigateBack: () -> Unit,
    onDestination: (QuickAccessDestination) -> Unit,
    viewModel: QuickAccessViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val access by viewModel.accessState.collectAsStateWithLifecycle()
    var restrictedFolderPrompt by remember { mutableStateOf<QuickAccessItem?>(null) }
    var pendingRestrictedFolder by remember { mutableStateOf<QuickAccessItem?>(null) }
    val contentResolver = LocalContext.current.contentResolver
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            persistTreePermission(contentResolver, uri)
            viewModel.addSafFolder(uri.toString(), folderLabel(uri))
        }
    }

    LaunchedEffect(
        pendingRestrictedFolder,
        access.isReady,
        access.activeBackend,
        state.error
    ) {
        val item = pendingRestrictedFolder ?: return@LaunchedEffect
        if (state.error != null) {
            pendingRestrictedFolder = null
            return@LaunchedEffect
        }
        val backendId = when (access.activeBackend) {
            PrivilegeBackendId.ROOT -> StorageNodeRef.ROOT_BACKEND_ID
            PrivilegeBackendId.SHIZUKU -> StorageNodeRef.SHIZUKU_BACKEND_ID
            else -> null
        }
        if (access.isReady && backendId != null) {
            restrictedLocalPath(item.path)?.let { localPath ->
                pendingRestrictedFolder = null
                onDestination(QuickAccessDestination.LocalPath(localPath, backendId))
            }
        }
    }

    QuickAccessScreen(
        state = state,
        actions = QuickAccessActions(
            navigateBack = onNavigateBack,
            navigateToPath = { path ->
                onDestination(QuickAccessDestination.LocalPath(path))
            },
            navigateToSaf = { uri ->
                onDestination(QuickAccessDestination.ExternalFolder(uri))
            },
            navigateToRestrictedFolder = { item ->
                val privilegedAccessReady = access.isReady &&
                    access.activeBackend in setOf(
                        PrivilegeBackendId.ROOT,
                        PrivilegeBackendId.SHIZUKU
                    )
                val localPath = restrictedLocalPath(item.path)
                if (privilegedAccessReady && localPath != null) {
                    val backendId = when (access.activeBackend) {
                        PrivilegeBackendId.ROOT -> StorageNodeRef.ROOT_BACKEND_ID
                        PrivilegeBackendId.SHIZUKU -> StorageNodeRef.SHIZUKU_BACKEND_ID
                        else -> null
                    }
                    onDestination(QuickAccessDestination.LocalPath(localPath, backendId))
                } else {
                    val rootDetected = access.backendStates[PrivilegeBackendId.ROOT]
                        ?.connectionState
                        ?.let { it != PrivilegeConnectionState.UNAVAILABLE } == true
                    val shizukuAvailable = access.backendStates[PrivilegeBackendId.SHIZUKU]
                        ?.connectionState
                        ?.let { it != PrivilegeConnectionState.UNAVAILABLE } == true
                    if (!rootDetected && shizukuAvailable) {
                        restrictedFolderPrompt = item
                    } else {
                        onDestination(QuickAccessDestination.ExternalFolder(item.path))
                    }
                }
            },
            togglePin = viewModel::togglePin,
            removeItem = viewModel::removeCustomItem,
            addCustomFolder = viewModel::addCustomFolder,
            requestSafFolder = { folderPicker.launch(null) },
            addFilesShortcut = {
                viewModel.addFilesAppShortcut(restrictedExternalStorageUri("").toString())
            },
            addAndroidDataShortcut = {
                viewModel.addExternalHandoffFolder(
                    restrictedExternalStorageUri("Android/data").toString(),
                    "Android/data"
                )
            },
            addAndroidObbShortcut = {
                viewModel.addExternalHandoffFolder(
                    restrictedExternalStorageUri("Android/obb").toString(),
                    "Android/obb"
                )
            },
            movePinnedItem = viewModel::movePinnedItem
        )
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
                        onDestination(QuickAccessDestination.ExternalFolder(item.path))
                    }
                ) {
                    Text(stringResource(R.string.quick_access_open_in_files))
                }
            }
        )
    }

    state.error?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            title = { Text(stringResource(R.string.quick_access_error_title)) },
            text = { Text(message) },
            confirmButton = {
                Button(onClick = viewModel::dismissError) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }
}

internal fun restrictedExternalStorageUri(relativeDocumentPath: String): Uri {
    val normalizedPath = relativeDocumentPath
        .trim()
        .replace('\\', '/')
        .trim('/')
    require(normalizedPath.split('/').none { it == "." || it == ".." }) {
        "Restricted folder path must not contain relative segments"
    }
    val treeUri = DocumentsContract.buildTreeDocumentUri(
        EXTERNAL_STORAGE_AUTHORITY,
        PRIMARY_STORAGE_ROOT
    )
    val documentId = if (normalizedPath.isEmpty()) {
        "$PRIMARY_STORAGE_ROOT:"
    } else {
        "$PRIMARY_STORAGE_ROOT:$normalizedPath"
    }
    return DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
}

internal fun folderLabel(uri: Uri): String {
    val documentId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
    val candidate = documentId
        ?.substringAfter(':', missingDelimiterValue = documentId)
        ?.trimEnd('/')
        ?.substringAfterLast('/')
        ?.takeIf(String::isNotBlank)
    return candidate ?: "New Folder"
}

internal fun persistTreePermission(contentResolver: ContentResolver, uri: Uri): Boolean {
    val readAndWrite = Intent.FLAG_GRANT_READ_URI_PERMISSION or
        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    return runCatching {
        contentResolver.takePersistableUriPermission(uri, readAndWrite)
        true
    }.recoverCatching {
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        true
    }.getOrDefault(false)
}

internal fun restrictedLocalPath(uriString: String): String? = runCatching {
    val uri = Uri.parse(uriString)
    require(uri.scheme == ContentResolver.SCHEME_CONTENT)
    require(uri.authority == EXTERNAL_STORAGE_AUTHORITY)
    val documentId = DocumentsContract.getDocumentId(uri)
    require(documentId == "$PRIMARY_STORAGE_ROOT:" || documentId.startsWith("$PRIMARY_STORAGE_ROOT:"))
    val relativePath = documentId.removePrefix("$PRIMARY_STORAGE_ROOT:").trim('/')
    require(relativePath.split('/').none { it == "." || it == ".." })
    if (relativePath.isEmpty()) "/storage/emulated/0"
    else "/storage/emulated/0/$relativePath"
}.getOrNull()

private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
private const val PRIMARY_STORAGE_ROOT = "primary"
