package dev.qtremors.arcile.data.manager

import android.content.Context
import android.provider.MediaStore
import dev.qtremors.arcile.data.MutationFinalizer
import dev.qtremors.arcile.data.provider.VolumeProvider
import dev.qtremors.arcile.data.util.PathSafety
import dev.qtremors.arcile.data.util.resolveVolumeForPath
import dev.qtremors.arcile.data.util.trashEnabledVolumes
import dev.qtremors.arcile.domain.FileModel
import dev.qtremors.arcile.domain.StorageVolume
import dev.qtremors.arcile.domain.TrashMetadata
import dev.qtremors.arcile.domain.TrashRestoreStatus
import dev.qtremors.arcile.domain.supportsTrash
import dev.qtremors.arcile.utils.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.security.MessageDigest

@Serializable
data class TrashMetadataEntity(
    val schemaVersion: Int = 1,
    val id: String,
    val originalPath: String,
    val deletionTime: Long,
    val sourceVolumeId: String? = null,
    val sourceStorageKind: String? = null
)

interface TrashManager {
    suspend fun moveToTrash(paths: List<String>): Result<Unit>
    suspend fun restoreFromTrash(trashIds: List<String>, destinationPath: String?): Result<Unit>
    suspend fun emptyTrash(): Result<Unit>
    suspend fun getTrashFiles(): Result<List<TrashMetadata>>
    suspend fun deletePermanentlyFromTrash(trashIds: List<String>): Result<Unit>
}

class DefaultTrashManager(
    private val context: Context,
    private val volumeProvider: VolumeProvider,
    private val mutationFinalizer: MutationFinalizer
) : TrashManager {
    private val jsonFormat = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private sealed class MetadataReadResult {
        data class Readable(val entity: TrashMetadataEntity) : MetadataReadResult()
        data class Unreadable(val id: String) : MetadataReadResult()
        data class Orphan(val id: String) : MetadataReadResult()
    }

    private fun getTrashDirForVolume(volume: StorageVolume): File {
        val root = File(volume.path)
        val arcileDir = File(root, ".arcile")
        val trashDir = File(arcileDir, ".trash")
        if (!trashDir.exists()) {
            trashDir.mkdirs()
            try {
                File(trashDir, ".nomedia").createNewFile()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                AppLogger.e("TrashManager", "Failed to create .nomedia in trash", e)
            }
        }
        return trashDir
    }

    private fun getTrashMetadataDirForVolume(volume: StorageVolume): File {
        val root = File(volume.path)
        val arcileDir = File(root, ".arcile")
        val metadataDir = File(arcileDir, ".metadata")
        if (!metadataDir.exists()) {
            metadataDir.mkdirs()
        }
        return metadataDir
    }

    private fun writeMetadata(metadataFile: File, entity: TrashMetadataEntity) {
        metadataFile.writeText(jsonFormat.encodeToString(entity), Charsets.UTF_8)
    }

    private fun readMetadata(metadataFile: File, trashDir: File): MetadataReadResult {
        val id = metadataFile.nameWithoutExtension
        val trashedFile = File(trashDir, id)
        val entity = try {
            jsonFormat.decodeFromString<TrashMetadataEntity>(metadataFile.readText(Charsets.UTF_8))
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            AppLogger.w("TrashManager", "Trash metadata is unreadable; preserving payload if present", e)
            return if (trashedFile.exists()) MetadataReadResult.Unreadable(id) else MetadataReadResult.Orphan(id)
        }
        return if (File(trashDir, entity.id).exists()) {
            MetadataReadResult.Readable(entity)
        } else if (trashedFile.exists()) {
            MetadataReadResult.Unreadable(id)
        } else {
            MetadataReadResult.Orphan(entity.id)
        }
    }

    private fun restoreStatusFor(entity: TrashMetadataEntity): TrashRestoreStatus {
        if (entity.originalPath.isBlank()) return TrashRestoreStatus.DESTINATION_REQUIRED
        val targetFile = File(entity.originalPath)
        val validationResult = validatePath(targetFile)
        if (validationResult.isFailure) return TrashRestoreStatus.DESTINATION_REQUIRED
        return if (targetFile.exists()) {
            TrashRestoreStatus.ORIGINAL_CONFLICT_RENAME
        } else {
            TrashRestoreStatus.ORIGINAL_AVAILABLE
        }
    }

    private suspend fun finalizeMutation(vararg paths: String) {
        mutationFinalizer.finalize(*paths)
    }

    private fun validatePath(file: File): Result<Unit> {
        return PathSafety.validatePath(file, volumeProvider.activeStorageRoots)
    }

    private fun validateDestructivePath(file: File): Result<Unit> {
        return PathSafety.validatePath(file, volumeProvider.activeStorageRoots, rejectSymlinks = true)
    }

    private fun verifyRestoreCopy(source: File, target: File): Boolean {
        return verifyCopyIntegrity(source, target)
    }

    private fun verifyCopyIntegrity(source: File, target: File): Boolean {
        if (!source.exists() || !target.exists()) return false
        if (source.isFile) {
            return target.isFile &&
                source.length() == target.length() &&
                sha256(source).contentEquals(sha256(target))
        }
        if (!source.isDirectory || !target.isDirectory) return false

        val sourceFiles = source.walkTopDown().filter { it.isFile }.toList()
        val targetFiles = target.walkTopDown().filter { it.isFile }.toList()
        if (sourceFiles.size != targetFiles.size) return false

        return sourceFiles.all { sourceChild ->
            val relativePath = sourceChild.relativeTo(source).path
            val targetChild = File(target, relativePath)
            targetChild.isFile &&
                sourceChild.length() == targetChild.length() &&
                sha256(sourceChild).contentEquals(sha256(targetChild))
        }
    }

    private fun sha256(file: File): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest()
    }

    override suspend fun moveToTrash(paths: List<String>): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val volumes = volumeProvider.currentVolumes()
            val scannedPaths = mutableListOf<String>()

            for (path in paths) {
                val file = File(path)
                validateDestructivePath(file).onFailure {
                    if (scannedPaths.isNotEmpty()) finalizeMutation(*scannedPaths.toTypedArray())
                    val msg = if (scannedPaths.isNotEmpty()) "Moved ${scannedPaths.size} of ${paths.size} items to trash. Failed on ${file.name}: Access denied" else "Access denied"
                    return@withContext Result.failure(Exception(msg, it))
                }

                if (!file.exists()) continue
                val sourceVolume = resolveVolumeForPath(file.absolutePath, volumes)
                    ?: run {
                        if (scannedPaths.isNotEmpty()) finalizeMutation(*scannedPaths.toTypedArray())
                        val msg = if (scannedPaths.isNotEmpty()) "Moved ${scannedPaths.size} of ${paths.size} items to trash. Failed on ${file.name}: Unable to resolve storage volume" else "Unable to resolve storage volume"
                        return@withContext Result.failure(Exception(msg))
                    }
                if (!sourceVolume.kind.supportsTrash) {
                    if (scannedPaths.isNotEmpty()) finalizeMutation(*scannedPaths.toTypedArray())
                    val msg = if (scannedPaths.isNotEmpty()) "Moved ${scannedPaths.size} of ${paths.size} items to trash. Failed on ${file.name}: Trash not supported on this storage." else "Trash is not supported on this storage. Use permanent delete instead."
                    return@withContext Result.failure(Exception(msg))
                }

                val trashDir = getTrashDirForVolume(sourceVolume)
                val trashMetadataDir = getTrashMetadataDirForVolume(sourceVolume)
                val arcileDir = File(sourceVolume.path, ".arcile")
                if (file.absolutePath.startsWith(arcileDir.absolutePath)) continue

                val trashId = java.util.UUID.randomUUID().toString()
                val targetTrashFile = File(trashDir, trashId)

                val metadataEntity = TrashMetadataEntity(
                    schemaVersion = 1,
                    id = trashId,
                    originalPath = file.absolutePath,
                    deletionTime = System.currentTimeMillis(),
                    sourceVolumeId = sourceVolume.id,
                    sourceStorageKind = sourceVolume.kind.name
                )
                val destFile = File(trashMetadataDir, "$trashId.json")
                try {
                    writeMetadata(destFile, metadataEntity)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    AppLogger.e("TrashManager", "Failed to write trash metadata, aborting move to trash", e)
                    return@withContext Result.failure(Exception("Failed to write trash metadata: ${e.message}", e))
                }

                val success = file.renameTo(targetTrashFile)
                var fallbackSuccess = false
                if (!success) {
                    try {
                        if (file.isDirectory) {
                            if (!file.copyRecursively(targetTrashFile, overwrite = true)) {
                                throw IOException("Failed to copy source directory to trash")
                            }
                            if (!verifyCopyIntegrity(file, targetTrashFile)) {
                                throw IOException("Failed to verify trashed directory copy")
                            }
                            if (!file.deleteRecursively()) throw IOException("Failed to delete source directory after copy")
                        } else {
                            file.copyTo(targetTrashFile, overwrite = true)
                            if (!verifyCopyIntegrity(file, targetTrashFile)) {
                                throw IOException("Failed to verify trashed file copy")
                            }
                            if (!file.delete()) throw IOException("Failed to delete source file after copy")
                        }
                        fallbackSuccess = true
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        File(trashMetadataDir, "$trashId.json").delete()
                        if (targetTrashFile.exists()) {
                            if (targetTrashFile.isDirectory) targetTrashFile.deleteRecursively() else targetTrashFile.delete()
                        }
                        if (scannedPaths.isNotEmpty()) {
                            finalizeMutation(*scannedPaths.toTypedArray())
                        }
                        val msg = if (scannedPaths.isNotEmpty()) "Moved ${scannedPaths.size} of ${paths.size} items to trash. Failed on ${file.name}: ${e.message}" else "Failed to move ${file.name} to trash: ${e.message}"
                        return@withContext Result.failure(Exception(msg, e))
                    }
                }

                if (success || fallbackSuccess) {
                    scannedPaths.add(file.absolutePath)
                    try {
                        val uri = MediaStore.Files.getContentUri("external")
                        val projection = arrayOf(MediaStore.Files.FileColumns._ID)
                        val selection = "${MediaStore.Files.FileColumns.DATA} = ?"
                        val selectionArgs = arrayOf(file.absolutePath)
                        context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID))
                                val itemUri = android.content.ContentUris.withAppendedId(uri, id)
                                context.contentResolver.delete(itemUri, null, null)
                            }
                        }
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        AppLogger.e("TrashManager", "Failed to explicitly delete from MediaStore", e)
                    }
                }
            }
            finalizeMutation(*scannedPaths.toTypedArray())
            Result.success(Unit)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        }
    }

    override suspend fun restoreFromTrash(trashIds: List<String>, destinationPath: String?): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val volumes = volumeProvider.currentVolumes()

            val legacyIds = trashIds.filter { it.startsWith("legacy:") || !it.contains(":") }.map { it.removePrefix("legacy:") }
            val scannedPaths = mutableListOf<String>()
            val idsRequiringDestination = mutableListOf<String>()

            for (id in legacyIds) {
                var metadataFile: File? = null
                var trashedFile: File? = null

                for (volume in trashEnabledVolumes(volumes)) {
                    val mdDir = getTrashMetadataDirForVolume(volume)
                    val trDir = getTrashDirForVolume(volume)

                    val candidateMd = File(mdDir, "$id.json")
                    val candidateTr = File(trDir, id)

                    if (candidateMd.exists() && candidateTr.exists()) {
                        metadataFile = candidateMd
                        trashedFile = candidateTr
                        break
                    }
                }

                if (metadataFile == null || trashedFile == null) continue

                val entity = (readMetadata(metadataFile, trashedFile.parentFile ?: continue) as? MetadataReadResult.Readable)?.entity
                val originalPath = entity?.originalPath.orEmpty()

                val originalFileContext = if (entity != null) {
                    File(originalPath)
                } else {
                    File("Recovered Item ($id)")
                }
                var targetFile = if (destinationPath != null) {
                    File(destinationPath, originalFileContext.name)
                } else {
                    originalFileContext
                }

                if (destinationPath == null) {
                    if (entity == null) {
                        idsRequiringDestination.add("legacy:$id")
                        continue
                    }
                    val validationResult = validatePath(targetFile)
                    if (validationResult.isFailure) {
                        idsRequiringDestination.add("legacy:$id")
                        continue
                    }
                } else {
                    validatePath(targetFile).onFailure { return@withContext Result.failure(it) }
                }

                if (targetFile.exists()) {
                    val timestamp = System.currentTimeMillis()
                    val conflictName = "${targetFile.nameWithoutExtension}.restore-conflict-$timestamp" +
                        (if (targetFile.extension.isNotEmpty()) ".${targetFile.extension}" else "")
                    targetFile = File(targetFile.parentFile, conflictName)
                }

                targetFile.parentFile?.mkdirs()

                val success = trashedFile.renameTo(targetFile)
                if (!success) {
                    validateDestructivePath(trashedFile).onFailure { return@withContext Result.failure(it) }
                    if (trashedFile.isDirectory) {
                        if (!trashedFile.copyRecursively(targetFile, overwrite = true)) {
                            if (targetFile.exists()) targetFile.deleteRecursively()
                            return@withContext Result.failure(IOException("Failed to copy trashed directory for restore"))
                        }
                        if (!verifyRestoreCopy(trashedFile, targetFile)) {
                            if (targetFile.exists()) targetFile.deleteRecursively()
                            return@withContext Result.failure(IOException("Failed to verify restored directory copy for ${targetFile.name}"))
                        }
                        if (!trashedFile.deleteRecursively()) {
                            if (targetFile.exists()) targetFile.deleteRecursively()
                            return@withContext Result.failure(IOException("Failed to delete trashed directory after restore copy"))
                        }
                    } else {
                        trashedFile.copyTo(targetFile, overwrite = true)
                        if (!verifyRestoreCopy(trashedFile, targetFile)) {
                            if (targetFile.exists()) targetFile.delete()
                            return@withContext Result.failure(IOException("Failed to verify restored file copy for ${targetFile.name}"))
                        }
                        if (!trashedFile.delete()) {
                            if (targetFile.exists()) targetFile.delete()
                            return@withContext Result.failure(IOException("Failed to delete trashed file after restore copy"))
                        }
                    }
                }

                if (targetFile.exists()) {
                    metadataFile.delete()
                    scannedPaths.add(targetFile.absolutePath)
                }
            }

            if (idsRequiringDestination.isNotEmpty()) {
                return@withContext Result.failure(dev.qtremors.arcile.domain.DestinationRequiredException(idsRequiringDestination))
            }

            finalizeMutation(*scannedPaths.toTypedArray())
            Result.success(Unit)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        }
    }

    override suspend fun emptyTrash(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val volumes = volumeProvider.currentVolumes()

            for (volume in trashEnabledVolumes(volumes)) {
                val trashDir = getTrashDirForVolume(volume)
                val metadataDir = getTrashMetadataDirForVolume(volume)

                if (trashDir.exists()) {
                    trashDir.listFiles()?.forEach { file ->
                        if (file.name != ".metadata" && file.name != ".nomedia") {
                            validateDestructivePath(file).onFailure { return@withContext Result.failure(it) }
                            file.deleteRecursively()
                        }
                    }
                }
                if (metadataDir.exists()) {
                    metadataDir.listFiles()?.forEach { it.delete() }
                }
            }
            finalizeMutation()
            Result.success(Unit)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        }
    }

    override suspend fun getTrashFiles(): Result<List<TrashMetadata>> = withContext(Dispatchers.IO) {
        try {
            val list = mutableListOf<TrashMetadata>()
            val volumes = volumeProvider.currentVolumes()

            for (volume in trashEnabledVolumes(volumes)) {
                val trashDir = getTrashDirForVolume(volume)
                val trashMetadataDir = getTrashMetadataDirForVolume(volume)

                if (trashDir.exists() && trashMetadataDir.exists()) {
                    trashMetadataDir.listFiles()?.forEach { metadataFile ->
                        if (metadataFile.isFile && metadataFile.extension == "json") {
                            when (val metadata = readMetadata(metadataFile, trashDir)) {
                                is MetadataReadResult.Readable -> {
                                    val entity = metadata.entity
                                    val id = entity.id
                                    val originalPath = entity.originalPath
                                    val deletionTime = entity.deletionTime
                                    val sourceVolId = entity.sourceVolumeId ?: volume.id
                                    val sourceVolKindStr = entity.sourceStorageKind ?: volume.kind.name
                                    val sourceVolKind = dev.qtremors.arcile.domain.StorageKind.entries.find { it.name == sourceVolKindStr } ?: volume.kind
                                    val trashedFile = File(trashDir, id)
                                    val originalFileContext = File(originalPath)
                                    val spoofedModel = FileModel(
                                        name = originalFileContext.name,
                                        absolutePath = trashedFile.absolutePath,
                                        size = if (trashedFile.isFile) trashedFile.length() else 0L,
                                        lastModified = trashedFile.lastModified(),
                                        isDirectory = trashedFile.isDirectory,
                                        extension = originalFileContext.extension,
                                        isHidden = false
                                    )

                                    list.add(TrashMetadata(id, originalPath, deletionTime, spoofedModel, sourceVolId, sourceVolKind, restoreStatusFor(entity)))
                                }
                                is MetadataReadResult.Unreadable -> {
                                    val id = metadata.id
                                    val trashedFile = File(trashDir, id)
                                    val spoofedModel = FileModel(
                                        name = "Recovered Item ($id)",
                                        absolutePath = trashedFile.absolutePath,
                                        size = if (trashedFile.isFile) trashedFile.length() else 0L,
                                        lastModified = trashedFile.lastModified(),
                                        isDirectory = trashedFile.isDirectory,
                                        extension = "",
                                        isHidden = false
                                    )
                                    list.add(TrashMetadata(id, "", trashedFile.lastModified(), spoofedModel, volume.id, volume.kind, TrashRestoreStatus.RECOVERED_ITEM))
                                }
                                is MetadataReadResult.Orphan -> {
                                    AppLogger.w("TrashManager", "Deleting orphaned trash metadata")
                                    metadataFile.delete()
                                }
                            }
                        }
                    }
                }
            }
            Result.success(list.sortedByDescending { it.deletionTime })
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        }
    }

    override suspend fun deletePermanentlyFromTrash(trashIds: List<String>): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val volumes = volumeProvider.currentVolumes()
            for (trashId in trashIds) {
                var found = false
                for (volume in trashEnabledVolumes(volumes)) {
                    val trashDir = getTrashDirForVolume(volume)
                    val metadataDir = getTrashMetadataDirForVolume(volume)

                    val trashedFile = File(trashDir, trashId)
                    val metadataFile = File(metadataDir, "$trashId.json")

                    if (trashedFile.exists() || metadataFile.exists()) {
                        if (trashedFile.exists()) {
                            validateDestructivePath(trashedFile).onFailure { return@withContext Result.failure(it) }
                        }
                        if (trashedFile.isDirectory) trashedFile.deleteRecursively() else trashedFile.delete()
                        metadataFile.delete()
                        found = true
                        break
                    }
                }
                if (!found) {
                    AppLogger.w("TrashManager", "Trash item not found for deletion")
                }
            }
            finalizeMutation()
            Result.success(Unit)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        }
    }
}
