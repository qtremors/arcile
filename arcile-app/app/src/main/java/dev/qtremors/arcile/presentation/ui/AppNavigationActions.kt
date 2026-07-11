package dev.qtremors.arcile.presentation.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import dev.qtremors.arcile.core.plugin.android.PluginFileResolution
import dev.qtremors.arcile.core.plugin.android.PluginManager
import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.ui.ArcileFeedbackEvent
import dev.qtremors.arcile.core.ui.ArcileFeedbackSeverity
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.externalfile.ExternalFileAccessHelper
import dev.qtremors.arcile.navigation.AppRoutes
import dev.qtremors.arcile.presentation.utils.ShareHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Stable
internal class AppNavigationActions(
    private val context: Context,
    private val navController: NavHostController,
    private val coroutineScope: CoroutineScope,
    private val fileOpenResolver: AppFileOpenResolver,
    private val onOpenFile: (String) -> Unit,
    private val onOpenFileWith: (String) -> Unit,
    private val onFeedback: (ArcileFeedbackEvent) -> Unit
) {
    var pluginPrompt by mutableStateOf<PluginFileResolution?>(null)
        private set

    val destinationMappers = AppDestinationMappers(
        navigateToBrowser = ::navigateToBrowser,
        openPath = ::openPath,
        openExternalFolder = ::openExternalFolder
    )

    fun dismissPluginPrompt() {
        pluginPrompt = null
    }

    fun navigateToBrowser(route: AppRoutes.Main) {
        navController.navigate(route)
    }

    fun openPath(path: String) {
        openPathWithContext(path, emptyList(), returnToBrowserPage = false)
    }

    fun openPathWithSurroundingImages(path: String, files: List<FileModel>) {
        openPathWithContext(path, files, returnToBrowserPage = false)
    }

    fun openBrowserFile(path: String, files: List<FileModel>) {
        openPathWithContext(path, files, returnToBrowserPage = true)
    }

    fun openFileWith(path: String) {
        onOpenFileWith(path)
    }

    fun openExternalFolder(uri: String) {
        if (!ExternalFileAccessHelper.openInFilesApp(context, uri)) {
            reportError(UiText.StringResource(R.string.could_not_open_folder_files_app))
        }
    }

    suspend fun shareKnownFiles(paths: List<String>, files: List<FileModel>): Boolean {
        val byPath = files.associateBy(FileModel::absolutePath)
        val references = paths.map { path ->
            byPath[path]?.toExternalReference()
                ?: ExternalFileAccessHelper.ExternalFileReference(path = path)
        }
        return ShareHelper.shareFileReferences(context, references)
    }

    fun shareKnownFilesAsync(paths: List<String>, files: List<FileModel>) {
        coroutineScope.launch { shareKnownFiles(paths, files) }
    }

    fun sharePath(path: String) {
        coroutineScope.launch { ShareHelper.shareFiles(context, listOf(path)) }
    }

    private fun openPathWithContext(
        path: String,
        surroundingFiles: List<FileModel>,
        returnToBrowserPage: Boolean
    ) {
        coroutineScope.launch {
            when (val resolution = fileOpenResolver.resolve(path, surroundingFiles)) {
                AppFileOpenResolution.Handled -> Unit
                is AppFileOpenResolution.PluginPrompt -> pluginPrompt = resolution.prompt
                is AppFileOpenResolution.Failed -> reportError(
                    UiText.StringResource(
                        R.string.cannot_open_file,
                        listOf(resolution.error.localizedMessage.orEmpty())
                    )
                )
                is AppFileOpenResolution.BrowseArchive -> navigateToBrowser(
                    AppRoutes.Main(
                        initialPage = BROWSER_PAGE,
                        archivePath = resolution.path,
                        seedInitialPathHistory = false
                    )
                )
                AppFileOpenResolution.UnsupportedArchive -> reportError(
                    UiText.StringResource(R.string.unsupported_archive_format)
                )
                is AppFileOpenResolution.ViewImage -> openImageViewer(
                    resolution,
                    returnToBrowserPage
                )
                is AppFileOpenResolution.External -> onOpenFile(resolution.path)
            }
        }
    }

    private fun openImageViewer(
        resolution: AppFileOpenResolution.ViewImage,
        returnToBrowserPage: Boolean
    ) {
        val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle
        if (resolution.contextPaths.size > 1 && resolution.path in resolution.contextPaths) {
            savedStateHandle?.set(
                AppRoutes.IMAGE_VIEWER_CONTEXT_PATHS_KEY,
                ArrayList(resolution.contextPaths)
            )
        } else {
            savedStateHandle?.remove<ArrayList<String>>(AppRoutes.IMAGE_VIEWER_CONTEXT_PATHS_KEY)
        }
        if (returnToBrowserPage) {
            savedStateHandle?.set(BROWSER_VIEWER_RETURN_PENDING_KEY, true)
        }
        navController.navigate(
            AppRoutes.ImageViewer(
                initialPath = resolution.path,
                returnToBrowserPage = returnToBrowserPage
            )
        )
    }

    private fun FileModel.toExternalReference() =
        ExternalFileAccessHelper.ExternalFileReference(
            path = absolutePath,
            displayName = name,
            sizeBytes = size,
            mimeType = mimeType,
            nodeRef = nodeRef
        )

    private fun reportError(message: UiText) {
        onFeedback(
            ArcileFeedbackEvent(
                message = message,
                severity = ArcileFeedbackSeverity.Error
            )
        )
    }
}

@Composable
internal fun rememberAppNavigationActions(
    navController: NavHostController,
    onOpenFile: (String) -> Unit,
    onOpenFileWith: (String) -> Unit,
    onFeedback: (ArcileFeedbackEvent) -> Unit
): AppNavigationActions {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    return remember(
        context,
        navController,
        coroutineScope,
        onOpenFile,
        onOpenFileWith,
        onFeedback
    ) {
        AppNavigationActions(
            context = context,
            navController = navController,
            coroutineScope = coroutineScope,
            fileOpenResolver = AppFileOpenResolver(
                InstalledPluginFileResolutionGateway(PluginManager(context))
            ),
            onOpenFile = onOpenFile,
            onOpenFileWith = onOpenFileWith,
            onFeedback = onFeedback
        )
    }
}
