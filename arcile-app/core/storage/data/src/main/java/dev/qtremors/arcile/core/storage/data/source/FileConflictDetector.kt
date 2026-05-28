package dev.qtremors.arcile.core.storage.data.source

import dev.qtremors.arcile.core.storage.domain.FileConflict
import dev.qtremors.arcile.core.storage.domain.FileModel
import java.io.File

class FileConflictDetector {
    fun detectCopyConflicts(sourcePaths: List<String>, destination: File): List<FileConflict> {
        return sourcePaths.mapNotNull { path ->
            val sourceFile = File(path)
            if (!sourceFile.exists()) return@mapNotNull null

            val targetFile = File(destination, sourceFile.name)
            if (!targetFile.exists()) return@mapNotNull null

            FileConflict(
                sourcePath = sourceFile.absolutePath,
                sourceFile = sourceFile.toFileModel(),
                existingFile = targetFile.toFileModel()
            )
        }
    }

    private fun File.toFileModel(): FileModel {
        val ext = extension
        val mime = if (ext.isNotEmpty()) {
            android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase())
        } else null

        return FileModel(
            name = name,
            absolutePath = absolutePath,
            size = if (isFile) length() else 0L,
            lastModified = lastModified(),
            isDirectory = isDirectory,
            extension = ext,
            isHidden = isHidden,
            mimeType = mime
        )
    }
}
