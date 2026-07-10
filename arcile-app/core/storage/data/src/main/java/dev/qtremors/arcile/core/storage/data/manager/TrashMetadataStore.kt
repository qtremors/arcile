package dev.qtremors.arcile.core.storage.data.manager

import dev.qtremors.arcile.core.storage.domain.StorageVolume
import dev.qtremors.arcile.core.storage.domain.TrashRestoreStatus
import dev.qtremors.arcile.core.runtime.logging.AppLogger
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal sealed interface TrashMetadataReadResult {
    data class Readable(val entity: TrashMetadataEntity) : TrashMetadataReadResult
    data class Unreadable(val id: String) : TrashMetadataReadResult
    data class Orphan(val id: String) : TrashMetadataReadResult
}

internal class TrashMetadataStore {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun trashDirectory(volume: StorageVolume): File {
        val trashDirectory = File(File(File(volume.path), ".arcile"), ".trash")
        if (!trashDirectory.exists()) {
            trashDirectory.mkdirs()
            try {
                File(trashDirectory, ".nomedia").createNewFile()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                AppLogger.e("TrashManager", "Failed to create .nomedia in trash", error)
            }
        }
        return trashDirectory
    }

    fun metadataDirectory(volume: StorageVolume): File {
        val metadataDirectory = File(File(File(volume.path), ".arcile"), ".metadata")
        if (!metadataDirectory.exists()) {
            metadataDirectory.mkdirs()
        }
        return metadataDirectory
    }

    fun write(metadataFile: File, entity: TrashMetadataEntity) {
        metadataFile.writeText(json.encodeToString(entity), Charsets.UTF_8)
    }

    fun read(metadataFile: File, trashDirectory: File): TrashMetadataReadResult {
        val id = metadataFile.nameWithoutExtension
        val trashedFile = File(trashDirectory, id)
        val entity = try {
            json.decodeFromString<TrashMetadataEntity>(
                metadataFile.readText(Charsets.UTF_8)
            )
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            AppLogger.w(
                "TrashManager",
                "Trash metadata is unreadable; preserving payload if present",
                error
            )
            return if (trashedFile.exists()) {
                TrashMetadataReadResult.Unreadable(id)
            } else {
                TrashMetadataReadResult.Orphan(id)
            }
        }
        return when {
            File(trashDirectory, entity.id).exists() ->
                TrashMetadataReadResult.Readable(entity)
            trashedFile.exists() -> TrashMetadataReadResult.Unreadable(id)
            else -> TrashMetadataReadResult.Orphan(entity.id)
        }
    }

    fun restoreStatus(
        entity: TrashMetadataEntity,
        validatePath: (File) -> Result<Unit>
    ): TrashRestoreStatus {
        if (entity.originalPath.isBlank()) return TrashRestoreStatus.DESTINATION_REQUIRED
        val targetFile = File(entity.originalPath)
        if (validatePath(targetFile).isFailure) {
            return TrashRestoreStatus.DESTINATION_REQUIRED
        }
        return if (targetFile.exists()) {
            TrashRestoreStatus.ORIGINAL_CONFLICT_RENAME
        } else {
            TrashRestoreStatus.ORIGINAL_AVAILABLE
        }
    }
}
