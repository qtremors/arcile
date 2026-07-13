package dev.qtremors.arcile.presentation.ui

import android.content.Context
import android.content.Intent
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
import dev.qtremors.arcile.core.storage.domain.FileCategories
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
        openGalleryPath = ::openGalleryPath,
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

    fun openGalleryPath(path: String, files: List<FileModel>, selectedPaths: Set<String>) {
        openPathWithContext(
            path = path,
            surroundingFiles = files,
            returnToBrowserPage = false,
            selectedPaths = selectedPaths
        )
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

    fun shareViewerFile(file: FileModel, managedTrash: Boolean) {
        coroutineScope.launch {
            ShareHelper.shareFileReferences(
                context,
                listOf(if (managedTrash) file.toManagedTrashReference() else file.toExternalReference())
            )
        }
    }

    fun openViewerFileWith(file: FileModel, managedTrash: Boolean) {
        if (managedTrash) openManagedTrashFileExternally(file, forceChooser = true)
        else onOpenFileWith(file.absolutePath)
    }

    fun openManagedTrashFile(file: FileModel, surroundingFiles: List<FileModel>) {
        if (FileCategories.getCategoryForFile(file.extension, file.mimeType) == FileCategories.Images) {
            val images = (surroundingFiles + file).distinctBy(FileModel::absolutePath).filter {
                !it.isDirectory &&
                    FileCategories.getCategoryForFile(it.extension, it.mimeType) == FileCategories.Images
            }
            openImageViewer(
                resolution = AppFileOpenResolution.ViewImage(
                    path = file.absolutePath,
                    contextPaths = images.map(FileModel::absolutePath)
                ),
                returnToBrowserPage = false,
                selectedPaths = emptySet(),
                contextFiles = images,
                managedTrash = true
            )
        } else {
            openManagedTrashFileExternally(file, forceChooser = false)
        }
    }

    fun openManagedTrashFileWith(file: FileModel) {
        openManagedTrashFileExternally(file, forceChooser = true)
    }

    private fun openManagedTrashFileExternally(file: FileModel, forceChooser: Boolean) {
        coroutineScope.launch {
            runCatching {
                val intent = ExternalFileAccessHelper.createOpenIntent(
                    context,
                    file.toManagedTrashReference()
                )
                context.startActivity(
                    if (forceChooser) {
                        Intent.createChooser(intent, file.name).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    } else {
                        intent.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    }
                )
            }.onFailure { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
                reportError(
                    UiText.StringResource(
                        R.string.cannot_open_file,
                        listOf(error.localizedMessage.orEmpty())
                    )
                )
            }
        }
    }

    suspend fun shareManagedTrashFiles(files: List<FileModel>): Boolean {
        return ShareHelper.shareFileReferences(
            context,
            files.filterNot(FileModel::isDirectory).map { it.toManagedTrashReference() }
        )
    }

    private fun openPathWithContext(
        path: String,
        surroundingFiles: List<FileModel>,
        returnToBrowserPage: Boolean,
        selectedPaths: Set<String> = emptySet()
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
                    returnToBrowserPage,
                    selectedPaths,
                    surroundingFiles
                )
                is AppFileOpenResolution.External -> onOpenFile(resolution.path)
            }
        }
    }

    private fun openImageViewer(
        resolution: AppFileOpenResolution.ViewImage,
        returnToBrowserPage: Boolean,
        selectedPaths: Set<String>,
        contextFiles: List<FileModel>,
        managedTrash: Boolean = false
    ) {
        val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle
        if (resolution.contextPaths.isNotEmpty() && resolution.path in resolution.contextPaths) {
            savedStateHandle?.set(
                AppRoutes.IMAGE_VIEWER_CONTEXT_PATHS_KEY,
                ArrayList(resolution.contextPaths)
            )
            val filesByPath = contextFiles.associateBy(FileModel::absolutePath)
            val orderedFiles = resolution.contextPaths.mapNotNull(filesByPath::get)
            if (orderedFiles.size == resolution.contextPaths.size) {
                savedStateHandle?.set(
                    AppRoutes.IMAGE_VIEWER_CONTEXT_NAMES_KEY,
                    ArrayList(orderedFiles.map(FileModel::name))
                )
                savedStateHandle?.set(
                    AppRoutes.IMAGE_VIEWER_CONTEXT_EXTENSIONS_KEY,
                    ArrayList(orderedFiles.map(FileModel::extension))
                )
                savedStateHandle?.set(
                    AppRoutes.IMAGE_VIEWER_CONTEXT_MIME_TYPES_KEY,
                    ArrayList(orderedFiles.map { it.mimeType.orEmpty() })
                )
                savedStateHandle?.set(
                    AppRoutes.IMAGE_VIEWER_CONTEXT_SIZES_KEY,
                    orderedFiles.map(FileModel::size).toLongArray()
                )
                savedStateHandle?.set(
                    AppRoutes.IMAGE_VIEWER_CONTEXT_MODIFIED_KEY,
                    orderedFiles.map(FileModel::lastModified).toLongArray()
                )
            } else {
                clearViewerContextMetadata(savedStateHandle)
            }
        } else {
            savedStateHandle?.remove<ArrayList<String>>(AppRoutes.IMAGE_VIEWER_CONTEXT_PATHS_KEY)
            clearViewerContextMetadata(savedStateHandle)
        }
        if (selectedPaths.isNotEmpty()) {
            savedStateHandle?.set(
                AppRoutes.IMAGE_VIEWER_SELECTION_PATHS_KEY,
                ArrayList(selectedPaths)
            )
        } else {
            savedStateHandle?.remove<ArrayList<String>>(AppRoutes.IMAGE_VIEWER_SELECTION_PATHS_KEY)
        }
        if (returnToBrowserPage) {
            savedStateHandle?.set(BROWSER_VIEWER_RETURN_PENDING_KEY, true)
        }
        navController.navigate(
            AppRoutes.ImageViewer(
                initialPath = resolution.path,
                returnToBrowserPage = returnToBrowserPage,
                managedTrash = managedTrash
            )
        )
    }

    private fun clearViewerContextMetadata(savedStateHandle: androidx.lifecycle.SavedStateHandle?) {
        savedStateHandle?.remove<ArrayList<String>>(AppRoutes.IMAGE_VIEWER_CONTEXT_NAMES_KEY)
        savedStateHandle?.remove<ArrayList<String>>(AppRoutes.IMAGE_VIEWER_CONTEXT_EXTENSIONS_KEY)
        savedStateHandle?.remove<ArrayList<String>>(AppRoutes.IMAGE_VIEWER_CONTEXT_MIME_TYPES_KEY)
        savedStateHandle?.remove<LongArray>(AppRoutes.IMAGE_VIEWER_CONTEXT_SIZES_KEY)
        savedStateHandle?.remove<LongArray>(AppRoutes.IMAGE_VIEWER_CONTEXT_MODIFIED_KEY)
    }

    private fun FileModel.toExternalReference() =
        ExternalFileAccessHelper.ExternalFileReference(
            path = absolutePath,
            displayName = name,
            sizeBytes = size,
            mimeType = mimeType,
            nodeRef = nodeRef
        )

    private fun FileModel.toManagedTrashReference() =
        ExternalFileAccessHelper.ExternalFileReference(
            path = absolutePath,
            displayName = name,
            sizeBytes = size,
            mimeType = mimeType,
            nodeRef = nodeRef,
            allowManagedTrashPayload = true
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
