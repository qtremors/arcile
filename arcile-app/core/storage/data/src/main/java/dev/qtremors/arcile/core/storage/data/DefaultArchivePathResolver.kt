package dev.qtremors.arcile.core.storage.data

import dev.qtremors.arcile.core.storage.domain.ArchiveCollisionStyle
import dev.qtremors.arcile.core.storage.domain.ArchivePathRequest
import dev.qtremors.arcile.core.storage.domain.ArchivePathResolver
import dev.qtremors.arcile.core.storage.domain.ArchiveExtractionDestinationStyle
import dev.qtremors.arcile.core.storage.domain.ArchiveExtractionPathRequest
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.withContext

class DefaultArchivePathResolver @Inject constructor(
    private val dispatchers: ArcileDispatchers
) : ArchivePathResolver {
    override suspend fun resolve(request: ArchivePathRequest): Result<String> =
        withContext(dispatchers.io) {
            runCatchingPreservingCancellation {
                require(request.sourcePaths.isNotEmpty()) {
                    "Archive source paths cannot be empty"
                }
                val parentPath = request.parentPath
                    ?.takeIf(String::isNotBlank)
                    ?: File(request.sourcePaths.first()).parent
                    ?: error("Archive destination has no parent")
                val defaultName = if (request.sourcePaths.size == 1) {
                    File(request.sourcePaths.first()).nameWithoutExtension
                        .ifBlank { DEFAULT_ARCHIVE_NAME }
                } else {
                    DEFAULT_ARCHIVE_NAME
                }
                val baseName = request.requestedName
                    ?.removeSuffix(".${request.format.extension}")
                    ?.replace('/', '_')
                    ?.replace('\\', '_')
                    ?.trim()
                    ?.takeIf(String::isNotBlank)
                    ?: defaultName
                var index = 0
                var candidate: File
                do {
                    val suffix = when {
                        index == 0 -> ""
                        request.collisionStyle == ArchiveCollisionStyle.UNDERSCORE -> "_$index"
                        else -> " ($index)"
                    }
                    candidate = File(
                        parentPath,
                        "$baseName$suffix.${request.format.extension}"
                    )
                    index += 1
                } while (candidate.exists())
                candidate.absolutePath.replace('\\', '/')
            }
        }

    override suspend fun resolveExtraction(
        request: ArchiveExtractionPathRequest
    ): Result<String> = withContext(dispatchers.io) {
        runCatchingPreservingCancellation {
            require(ArchiveFormat.isSupported(request.archivePath)) { "Unsupported archive format" }
            val archive = File(request.archivePath)
            val parent = archive.parent
                ?.normalizeSeparators()
                ?.takeIf(String::isNotBlank)
                ?: request.currentPath?.normalizeSeparators()?.takeIf(String::isNotBlank)
                ?: error("Archive destination has no parent")
            when (request.style) {
                ArchiveExtractionDestinationStyle.NAMED_FOLDER ->
                    File(parent, archive.extractionFolderName()).absolutePath.normalizeSeparators()
                ArchiveExtractionDestinationStyle.SAME_FOLDER -> parent
                ArchiveExtractionDestinationStyle.CUSTOM_FOLDER ->
                    request.customDestination?.normalizeSeparators()?.takeIf(String::isNotBlank)
                        ?: parent
            }
        }
    }

    private fun File.extractionFolderName(): String {
        val format = ArchiveFormat.fromPath(name) ?: return nameWithoutExtension
        return name.removeSuffix(".${format.extension}").ifBlank { nameWithoutExtension }
    }

    private fun String.normalizeSeparators(): String = replace('\\', '/')

    private companion object {
        const val DEFAULT_ARCHIVE_NAME = "Archive"
    }
}
