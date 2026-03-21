package dev.qtremors.arcile.data.manager

import android.content.Context
import android.provider.MediaStore
import dev.qtremors.arcile.data.provider.VolumeProvider
import dev.qtremors.arcile.data.source.MediaStoreClient
import dev.qtremors.arcile.data.util.resolveVolumeForPath
import dev.qtremors.arcile.data.util.trashEnabledVolumes
import dev.qtremors.arcile.domain.FileModel
import dev.qtremors.arcile.domain.StorageVolume
import dev.qtremors.arcile.domain.TrashMetadata
import dev.qtremors.arcile.domain.supportsTrash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException

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
    private val mediaStoreClient: MediaStoreClient
) : TrashManager {

    private fun getTrashDirForVolume(volume: StorageVolume): File {
        val root = File(volume.path)
        val arcileDir = File(root, ".arcile")
        val trashDir = File(arcileDir, ".trash")
        if (!trashDir.exists()) {
            trashDir.mkdirs()
            try {
                File(trashDir, ".nomedia").createNewFile() // Hide from gallery
            } catch (e: Exception) {
                android.util.Log.e("TrashManager", "Failed to create .nomedia in trash", e)
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

    private fun scanMediaFiles(vararg paths: String) {
        if (paths.isEmpty()) return
        android.media.MediaScannerConnection.scanFile(context.applicationContext, paths, null, null)
    }

    // path traversal guard
    private fun validatePath(file: File): Result<Unit> {
        val canonical = file.canonicalPath
        val isAllowed = volumeProvider.activeStorageRoots.any { root ->
            canonical == root || canonical.startsWith(root + File.separator)
        }
        
        if (!isAllowed) {
            return Result.failure(SecurityException("Access denied: path outside storage boundaries"))
        }
        return Result.success(Unit)
    }

    override suspend fun moveToTrash(paths: List<String>): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val volumes = volumeProvider.currentVolumes()
            val scannedPaths = mutableListOf<String>()

            for (path in paths) {
                val file = File(path)
                validatePath(file).onFailure { return@withContext Result.failure(it) }

                if (!file.exists()) continue
                val sourceVolume = resolveVolumeForPath(file.absolutePath, volumes)
                    ?: return@withContext Result.failure(IllegalArgumentException("Unable to resolve storage volume"))
                if (!sourceVolume.kind.supportsTrash) {
                    return@withContext Result.failure(
                        IllegalStateException("Trash is not supported on this storage. Use permanent delete instead.")
                    )
                }
                
                val trashDir = getTrashDirForVolume(sourceVolume)
                val trashMetadataDir = getTrashMetadataDirForVolume(sourceVolume)

                // Don't trash the trash
                val arcileDir = File(sourceVolume.path, ".arcile")
                if (file.absolutePath.startsWith(arcileDir.absolutePath)) continue

                val trashId = java.util.UUID.randomUUID().toString()
                val targetTrashFile = File(trashDir, trashId)
                
                // Write metadata JSON
                val metadataJson = JSONObject().apply {
                    put("id", trashId)
                    put("originalPath", file.absolutePath)
                    put("deletionTime", System.currentTimeMillis())
                    put("sourceVolumeId", sourceVolume.id)
                    put("sourceStorageKind", sourceVolume.kind.name)
                }
                File(trashMetadataDir, "$trashId.json").writeText(metadataJson.toString())

                // Move abstracting name
                val success = file.renameTo(targetTrashFile)
                var fallbackSuccess = false
                if (!success) {
                    // Fallback
                    try {
                        if (file.isDirectory) {
                            file.copyRecursively(targetTrashFile, overwrite = true)
                            if (!file.deleteRecursively()) throw IOException("Failed to delete source directory after copy")
                        } else {
                            file.copyTo(targetTrashFile, overwrite = true)
                            if (!file.delete()) throw IOException("Failed to delete source file after copy")
                        }
                        fallbackSuccess = true
                    } catch (e: Exception) {
                        // Revert metadata and partial copy on failure
                        File(trashMetadataDir, "$trashId.json").delete()
                        if (targetTrashFile.exists()) {
                            if (targetTrashFile.isDirectory) targetTrashFile.deleteRecursively() else targetTrashFile.delete()
                        }
                        if (scannedPaths.isNotEmpty()) {
                            mediaStoreClient.invalidateCache(*scannedPaths.toTypedArray())
                            scanMediaFiles(*scannedPaths.toTypedArray())
                        }
                        return@withContext Result.failure(Exception("Failed to move ${file.name} to trash: ${e.message}", e))
                    }
                }
                
                if (success || fallbackSuccess) {
                    scannedPaths.add(file.absolutePath)
                    // Explicitly delete from MediaStore so it doesn't linger via auto-updates
                    try {
                        val uri = MediaStore.Files.getContentUri("external")
                        context.contentResolver.delete(uri, "${MediaStore.Files.FileColumns.DATA} = ?", arrayOf(file.absolutePath))
                    } catch (e: Exception) {
                        android.util.Log.e("TrashManager", "Failed to explicitly delete from MediaStore", e)
                    }
                }
            }
            mediaStoreClient.invalidateCache(*scannedPaths.toTypedArray())
            scanMediaFiles(*scannedPaths.toTypedArray())
            Result.success(Unit)
        } catch (e: Exception) {
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

                val json = JSONObject(metadataFile.readText())
                val originalPath = json.getString("originalPath")
                
                val originalFileContext = File(originalPath)
                var targetFile = if (destinationPath != null) {
                    File(destinationPath, originalFileContext.name)
                } else {
                    originalFileContext
                }

                if (destinationPath == null) {
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
                    if (trashedFile.isDirectory) {
                        trashedFile.copyRecursively(targetFile, overwrite = true)
                        trashedFile.deleteRecursively()
                    } else {
                        trashedFile.copyTo(targetFile, overwrite = true)
                        trashedFile.delete()
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

            mediaStoreClient.invalidateCache(*scannedPaths.toTypedArray())
            scanMediaFiles(*scannedPaths.toTypedArray())
            Result.success(Unit)
        } catch (e: Exception) {
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
                            file.deleteRecursively()
                        }
                    }
                }
                if (metadataDir.exists()) {
                    metadataDir.listFiles()?.forEach { it.delete() }
                }
            }
            mediaStoreClient.invalidateCache()
            Result.success(Unit)
        } catch (e: Exception) {
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
                            try {
                                val json = JSONObject(metadataFile.readText())
                                val id = json.getString("id")
                                val originalPath = json.getString("originalPath")
                                val deletionTime = json.getLong("deletionTime")
                                val sourceVolId = json.optString("sourceVolumeId", volume.id)
                                val sourceVolKindStr = json.optString("sourceStorageKind", volume.kind.name)
                                val sourceVolKind = dev.qtremors.arcile.domain.StorageKind.entries.find { it.name == sourceVolKindStr } ?: volume.kind
                                
                                val trashedFile = File(trashDir, id)
                                if (trashedFile.exists()) {
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
                                    
                                    list.add(TrashMetadata(id, originalPath, deletionTime, spoofedModel, sourceVolId, sourceVolKind))
                                } else {
                                    android.util.Log.w("TrashManager", "Deleting orphaned trash metadata for id: $id")
                                    metadataFile.delete() 
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("TrashManager", "Deleting corrupted trash metadata: ${metadataFile.name}", e)
                                metadataFile.delete()
                            }
                        }
                    }
                }
            }
            Result.success(list.sortedByDescending { it.deletionTime })
        } catch (e: Exception) {
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
                        if (trashedFile.isDirectory) trashedFile.deleteRecursively() else trashedFile.delete()
                        metadataFile.delete()
                        found = true
                        break
                    }
                }
                if (!found) {
                    android.util.Log.w("TrashManager", "Trash item $trashId not found for deletion")
                }
            }
            mediaStoreClient.invalidateCache()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}