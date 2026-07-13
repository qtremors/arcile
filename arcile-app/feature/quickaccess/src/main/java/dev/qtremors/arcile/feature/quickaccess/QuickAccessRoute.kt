package dev.qtremors.arcile.feature.quickaccess

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import dev.qtremors.arcile.core.storage.domain.QuickAccessItem

internal data class QuickAccessActions(
    val navigateBack: () -> Unit,
    val navigateToPath: (String) -> Unit,
    val navigateToSaf: (String) -> Unit,
    val togglePin: (QuickAccessItem) -> Unit,
    val removeItem: (QuickAccessItem) -> Unit,
    val addCustomFolder: (String, String) -> Unit,
    val requestSafFolder: () -> Unit,
    val addFilesShortcut: () -> Unit,
    val addAndroidDataShortcut: () -> Unit,
    val addAndroidObbShortcut: () -> Unit,
    val reorderItems: (List<QuickAccessItem>) -> Unit
)

@Composable
internal fun QuickAccessRoute(
    onNavigateBack: () -> Unit,
    onDestination: (QuickAccessDestination) -> Unit,
    viewModel: QuickAccessViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val contentResolver = LocalContext.current.contentResolver
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            persistTreePermission(contentResolver, uri)
            viewModel.addSafFolder(uri.toString(), folderLabel(uri))
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
            reorderItems = viewModel::updateItemsOrder
        )
    )
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

private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
private const val PRIMARY_STORAGE_ROOT = "primary"
