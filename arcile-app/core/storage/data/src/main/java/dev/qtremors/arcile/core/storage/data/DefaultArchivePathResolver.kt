package dev.qtremors.arcile.core.storage.data

import dev.qtremors.arcile.core.storage.domain.ArchiveCollisionStyle
import dev.qtremors.arcile.core.storage.domain.ArchivePathRequest
import dev.qtremors.arcile.core.storage.domain.ArchivePathResolver
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.withContext

class DefaultArchivePathResolver @Inject constructor(
    private val dispatchers: ArcileDispatchers
) : ArchivePathResolver {
    override suspend fun resolve(request: ArchivePathRequest): Result<String> =
        withContext(dispatchers.io) {
            runCatching {
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

    private companion object {
        const val DEFAULT_ARCHIVE_NAME = "Archive"
    }
}
