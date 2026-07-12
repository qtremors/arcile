package dev.qtremors.arcile.core.storage.data

import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import dev.qtremors.arcile.core.storage.domain.SaveDestinationBrowser
import dev.qtremors.arcile.core.storage.domain.SaveDestinationDirectory
import dev.qtremors.arcile.core.storage.domain.StorageVolume
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.withContext

class DefaultSaveDestinationBrowser @Inject constructor(
    private val dispatchers: ArcileDispatchers
) : SaveDestinationBrowser {
    override suspend fun resolve(
        path: String?,
        volumes: List<StorageVolume>
    ): Result<SaveDestinationDirectory?> = withContext(dispatchers.io) {
        runCatchingPreservingCancellation {
            path?.takeIf(String::isNotBlank)?.let(::File)?.toValidDirectory(volumes)
        }
    }

    override suspend fun children(
        path: String,
        volumes: List<StorageVolume>
    ): Result<List<SaveDestinationDirectory>> =
        withContext(dispatchers.io) {
            runCatchingPreservingCancellation {
                val resolved = File(path).toValidDirectory(volumes)
                    ?: error("Save destination is unavailable")
                val directory = File(resolved.path)
                directory.listFiles { child -> child.isDirectory && child.canRead() }
                    ?.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, File::getName))
                    ?.mapNotNull { it.toValidDirectory(volumes) }
                    .orEmpty()
            }
        }

    override suspend fun parent(
        path: String,
        volumes: List<StorageVolume>
    ): Result<SaveDestinationDirectory?> = withContext(dispatchers.io) {
        runCatchingPreservingCancellation {
            val current = File(path).canonicalFile
            val isVolumeRoot = volumes.any { volume ->
                runCatchingPreservingCancellation { File(volume.path).canonicalFile }
                    .getOrNull() == current
            }
            if (isVolumeRoot) null else current.parentFile?.toValidDirectory(volumes)
        }
    }

    private fun File.toValidDirectory(volumes: List<StorageVolume>): SaveDestinationDirectory? {
        if (!exists() || !isDirectory || !canRead()) return null
        val directory = canonicalFile
        val belongsToVolume = volumes.any { volume ->
            val root = runCatchingPreservingCancellation { File(volume.path).canonicalFile }
                .getOrNull() ?: return@any false
            directory.toPath().startsWith(root.toPath())
        }
        return directory.takeIf { belongsToVolume }?.toSaveDirectory()
    }

    private fun File.toSaveDirectory() = SaveDestinationDirectory(
        path = absolutePath,
        name = name.ifBlank { absolutePath },
        canSave = canWrite()
    )
}
