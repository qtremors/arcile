package dev.qtremors.arcile.presentation.ui

import android.webkit.MimeTypeMap
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.core.storage.domain.FileCategories
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.FileOpenBehavior
import dev.qtremors.arcile.core.plugin.android.PluginFileResolution

internal class AppFileOpenResolver(
    private val pluginGateway: PluginFileResolutionGateway,
    private val fileOpenBehaviors: Map<String, FileOpenBehavior> = emptyMap(),
    private val mimeTypeForExtension: (String) -> String? = {
        if (it == "glb") "model/gltf-binary"
        else MimeTypeMap.getSingleton().getMimeTypeFromExtension(it)
    }
) {
    suspend fun resolve(
        path: String,
        surroundingFiles: List<FileModel>
    ): AppFileOpenResolution {
        val knownFile = surroundingFiles.firstOrNull { it.absolutePath == path }
        val extension = knownFile?.extension
            ?.takeIf(String::isNotBlank)
            ?.lowercase()
            ?: path.substringAfterLast('.', "").lowercase()
        val mimeType = knownFile?.mimeType?.takeIf(String::isNotBlank)
            ?: mimeTypeForExtension(extension)
        val category = FileCategories.getCategoryForFile(extension, mimeType)
        if (
            category != null &&
            fileOpenBehaviors[category.id.value] == FileOpenBehavior.EXTERNAL
        ) {
            return AppFileOpenResolution.External(path, forceChooser = true)
        }

        return when (pluginGateway.resolve(path, mimeType, extension)) {
            PluginFileResolution.Launched -> AppFileOpenResolution.Handled
            is PluginFileResolution.Missing,
            is PluginFileResolution.Incompatible,
            is PluginFileResolution.Failed -> AppFileOpenResolution.External(
                path = path,
                forceChooser = true
            )
            PluginFileResolution.NotApplicable -> fallbackResolution(path, extension, surroundingFiles)
        }
    }

    private fun fallbackResolution(
        path: String,
        extension: String,
        surroundingFiles: List<FileModel>
    ): AppFileOpenResolution {
        val archiveFormat = ArchiveFormat.fromPath(path)
        return when {
            extension in FileCategories.APKs.extensions -> AppFileOpenResolution.InstallApk(
                path = path
            )
            archiveFormat?.canBrowse == true -> AppFileOpenResolution.BrowseArchive(path)
            archiveFormat != null -> AppFileOpenResolution.External(path, forceChooser = true)
            extension in FileCategories.Images.extensions -> AppFileOpenResolution.ViewImage(
                path = path,
                contextPaths = surroundingFiles.asSequence()
                    .filterNot(FileModel::isDirectory)
                    .filter {
                        FileCategories.getCategoryForFile(it.extension, it.mimeType) == FileCategories.Images
                    }
                    .map(FileModel::absolutePath)
                    .distinct()
                    .toList()
            )
            extension in FileCategories.Videos.extensions -> AppFileOpenResolution.ViewVideo(
                path = path,
                contextPaths = surroundingFiles.asSequence()
                    .filterNot(FileModel::isDirectory)
                    .filter {
                        FileCategories.getCategoryForFile(it.extension, it.mimeType) == FileCategories.Videos
                    }
                    .map(FileModel::absolutePath)
                    .distinct()
                    .toList()
            )
            extension in FileCategories.Audio.extensions -> AppFileOpenResolution.ViewAudio(path)
            extension == "pdf" -> AppFileOpenResolution.ViewPdf(path)
            else -> AppFileOpenResolution.External(path, forceChooser = true)
        }
    }
}
