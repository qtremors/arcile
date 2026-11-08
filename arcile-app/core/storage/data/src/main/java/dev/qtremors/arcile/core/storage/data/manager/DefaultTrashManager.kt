package dev.qtremors.arcile.core.storage.data.manager

import dev.qtremors.arcile.core.storage.data.rethrowIfCancellation
import dev.qtremors.arcile.core.storage.data.runCatchingPreservingCancellation
import android.content.Context
import androidx.core.net.toUri
import dev.qtremors.arcile.core.storage.data.MutationFinalizer
import dev.qtremors.arcile.core.storage.data.MutationJournal
import dev.qtremors.arcile.core.storage.data.NoOpMutationJournal
import dev.qtremors.arcile.core.storage.data.provider.VolumeProvider
import dev.qtremors.arcile.core.storage.data.source.FileTransferEngine
import dev.qtremors.arcile.core.storage.data.util.PathSafety
import dev.qtremors.arcile.core.storage.data.util.resolveVolumeForPath
import dev.qtremors.arcile.core.storage.data.util.trashEnabledVolumes
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.StorageVolume
import dev.qtremors.arcile.core.storage.domain.TrashMetadata
import dev.qtremors.arcile.core.storage.domain.TrashRestoreStatus
import dev.qtremors.arcile.core.storage.domain.TrashStorageUsage
import dev.qtremors.arcile.core.storage.domain.supportsTrash
import dev.qtremors.arcile.core.operation.BulkFileOperationProgress
import dev.qtremors.arcile.core.runtime.logging.AppLogger
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption
import dev.qtremors.arcile.core.storage.data.MutationTreeSnapshot
import dev.qtremors.arcile.core.storage.domain.RestoredTrashItem
import dev.qtremors.arcile.core.storage.domain.PartialTrashRestoreException

class DefaultTrashManager(
    private val context: Context,
    private val volumeProvider: VolumeProvider,
    private val mutationFinalizer: MutationFinalizer,
    private val dispatchers: ArcileDispatchers = ArcileDispatchers.Standard,
    private val mutationJournal: MutationJournal = NoOpMutationJournal(),
    private val rename: (File, File) -> Boolean = { source, target -> source.renameTo(target) },
    private val transferEngine: FileTransferEngine = FileTransferEngine(
        validatePath = { file ->
            PathSafety.validatePath(file, volumeProvider.activeStorageRoots)
        },
        validateMutationPath = { file ->
            PathSafety.validatePath(file, volumeProvider.activeStorageRoots, PathSafety.OperationPolicy.RECURSIVE_MUTATE)
        },
        rename = rename,
        mutationJournal = mutationJournal
    ),
    private val undoFileKey: ((File) -> String?)? = null
) : TrashManager {
    private val metadataStore = TrashMetadataStore()
    private val restoreUndo = linkedMapOf<String, Pair<String, MutationTreeSnapshot>>()
    private val externalTrashMutex = Mutex()

    override suspend fun acceptExternalItem(
        displayName: String,
        writePayload: suspend (String) -> Unit,
        afterPublished: suspend () -> Unit
    ): Result<Unit> = externalTrashMutex.withLock {
        withContext(dispatchers.io) {
            runCatchingPreservingCancellation {
                require(isSafeExternalName(displayName)) { "Invalid Trash item name" }
                val volume = trashEnabledVolumes(volumeProvider.currentVolumes())
                    .sortedByDescending(StorageVolume::isPrimary).firstOrNull()
                    ?: throw IOException("Main Trash is unavailable")
                val managed = File(volume.path, ".arcile")
                val trash = File(managed, ".trash")
                val metadata = File(managed, ".metadata")
                val stagingRoot = File(metadata, EXTERNAL_TRASH_STAGING)
                requireManagedTrashDirectory(volume, trash)
                requireManagedTrashDirectory(volume, metadata)
                requireManagedTrashDirectory(volume, stagingRoot)
                File(trash, ".nomedia").createNewFile()
                File(stagingRoot, ".nomedia").createNewFile()
                recoverExternalTrashStaging(volume)
                val id = UUID.randomUUID().toString()
                val stage = Files.createDirectory(File(stagingRoot, id).toPath()).toFile()
                val stagedPayload = File(stage, "payload")
                val stagedMetadata = File(stage, "metadata.json")
                val publishedPayload = File(trash, id)
                val publishedMetadata = File(metadata, "$id.json")
                var published = false
                var moved = false
                try {
                    writePayload(stagedPayload.absolutePath)
                    ensureActive()
                    val snapshot = MutationTreeSnapshot.capture(stagedPayload, undoFileKey)
                    metadataStore.write(stagedMetadata, TrashMetadataEntity(
                        id = id, originalPath = "", displayName = displayName,
                        deletionTime = System.currentTimeMillis(), sourceVolumeId = volume.id,
                        sourceStorageKind = volume.kind.name
                    ))
                    FileOutputStream(stagedMetadata, true).use { it.fd.sync() }
                    Files.move(stagedPayload.toPath(), publishedPayload.toPath())
                    moved = true
                    if (!snapshot.matches(publishedPayload, undoFileKey, requireIdentity = false)) {
                        throw IOException("Trash payload could not be verified")
                    }
                    ensureActive()
                    Files.move(stagedMetadata.toPath(), publishedMetadata.toPath())
                    published = true
                    afterPublished()
                } finally {
                    cleanExternalTrashStage(stage, publishedPayload.takeIf { !published && moved })
                }
            }
        }
    }

    private fun recoverExternalTrashStaging(volume: StorageVolume) {
        val metadata = File(File(volume.path, ".arcile"), ".metadata")
        val stages = File(metadata, EXTERNAL_TRASH_STAGING)
        if (!Files.exists(stages.toPath(), LinkOption.NOFOLLOW_LINKS)) return
        requireManagedTrashDirectory(volume, stages)
        val trash = File(File(volume.path, ".arcile"), ".trash")
        requireManagedTrashDirectory(volume, trash)
        for (stage in stages.listFiles().orEmpty()) {
            val id = stage.name
            if (runCatchingPreservingCancellation { UUID.fromString(id).toString() }.getOrNull() != id) continue
            if (!Files.isDirectory(stage.toPath(), LinkOption.NOFOLLOW_LINKS)) continue
            val stagedMetadata = File(stage, "metadata.json")
            val payloadWasMoved = Files.isRegularFile(stagedMetadata.toPath(), LinkOption.NOFOLLOW_LINKS) &&
                metadataStore.isExternalStageRecord(stagedMetadata, id) &&
                !Files.exists(File(stage, "payload").toPath(), LinkOption.NOFOLLOW_LINKS)
            val sidecar = File(metadata, "$id.json")
            val unpublished = payloadWasMoved && !Files.exists(sidecar.toPath(), LinkOption.NOFOLLOW_LINKS)
            cleanExternalTrashStage(stage, File(trash, id).takeIf { unpublished })
        }
    }

    private fun requireManagedTrashDirectory(volume: StorageVolume, directory: File) {
        val root = File(volume.path).canonicalFile.toPath()
        val configured = File(volume.path).absoluteFile.toPath().normalize()
        val relative = configured.relativize(directory.absoluteFile.toPath().normalize())
        require(relative.nameCount > 0 && relative.getName(0).toString() == ".arcile")
        var current = root
        for (part in relative) {
            current = current.resolve(part)
            if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                try {
                    Files.createDirectory(current)
                } catch (_: FileAlreadyExistsException) {
                    // Another Trash operation may have created the directory.
                }
            }
            if (!Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) throw IOException("Main Trash path is invalid")
        }
    }

    private fun cleanExternalTrashStage(stage: File, unpublishedPayload: File?) {
        try {
            unpublishedPayload?.let(::deleteExternalTrashTree)
            deleteExternalTrashTree(stage)
        } catch (error: IOException) {
            AppLogger.e("TrashManager", "Failed to clean up a vault Trash staging item", error)
        }
    }

    private fun deleteExternalTrashTree(file: File) {
        val root = file.toPath()
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return
        Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
    }

    private fun isSafeExternalName(name: String): Boolean =
        name.isNotBlank() && name != "." && name != ".." && name.none { it == '/' || it == '\\' || it == '\u0000' }

    private companion object { const val EXTERNAL_TRASH_STAGING = ".vault-staging" }

    private suspend fun finalizeMutation(vararg paths: String) {
        mutationFinalizer.finalize(*paths)
    }

    private fun validatePath(file: File): Result<Unit> {
        return PathSafety.validatePath(file, volumeProvider.activeStorageRoots)
    }

    private fun validateDestructivePath(file: File): Result<Unit> {
        return PathSafety.validatePath(file, volumeProvider.activeStorageRoots, PathSafety.OperationPolicy.RECURSIVE_MUTATE)
    }

    override suspend fun moveToTrash(
        paths: List<String>,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ): Result<Unit> =
        moveToTrashTargets(paths.map { TrashTarget(path = it) }, onProgress)

    override suspend fun moveToTrashTargets(
        targets: List<TrashTarget>,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ): Result<Unit> = withContext(dispatchers.io) {
        try {
            val volumes = volumeProvider.currentVolumes()
            val scannedPaths = mutableListOf<String>()

            for (target in targets) {
                val path = target.path
                val file = File(path)
                validateDestructivePath(file).onFailure {
                    if (scannedPaths.isNotEmpty()) finalizeMutation(*scannedPaths.toTypedArray())
                    val msg = if (scannedPaths.isNotEmpty()) "Moved ${scannedPaths.size} of ${targets.size} items to trash. Failed on ${file.name}: Access denied" else "Access denied"
                    return@withContext Result.failure(Exception(msg, it))
                }

                if (!file.exists()) continue
                val sourceVolume = resolveVolumeForPath(file.absolutePath, volumes)
                    ?: run {
                        if (scannedPaths.isNotEmpty()) finalizeMutation(*scannedPaths.toTypedArray())
                        val msg = if (scannedPaths.isNotEmpty()) "Moved ${scannedPaths.size} of ${targets.size} items to trash. Failed on ${file.name}: Unable to resolve storage volume" else "Unable to resolve storage volume"
                        return@withContext Result.failure(Exception(msg))
                    }
                if (!sourceVolume.kind.supportsTrash) {
                    if (scannedPaths.isNotEmpty()) finalizeMutation(*scannedPaths.toTypedArray())
                    val msg = if (scannedPaths.isNotEmpty()) "Moved ${scannedPaths.size} of ${targets.size} items to trash. Failed on ${file.name}: Trash not supported on this storage." else "Trash is not supported on this storage. Use permanent delete instead."
                    return@withContext Result.failure(Exception(msg))
                }

                val trashDir = metadataStore.trashDirectory(sourceVolume)
                val trashMetadataDir = metadataStore.metadataDirectory(sourceVolume)
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
                    metadataStore.write(destFile, metadataEntity)
                } catch (e: Exception) {
                    e.rethrowIfCancellation()
                    AppLogger.e("TrashManager", "Failed to write trash metadata, aborting move to trash", e)
                    return@withContext Result.failure(Exception("Failed to write trash metadata: ${e.message}", e))
                }

                val success = rename(file, targetTrashFile)
                var fallbackSuccess = false
                if (!success) {
                    try {
                        mutationJournal.recordTrashFallback(
                            sourcePath = file.absolutePath,
                            payloadPath = targetTrashFile.absolutePath,
                            metadataPath = destFile.absolutePath
                        )
                        transferEngine.moveToTarget(
                            source = file,
                            target = targetTrashFile,
                            attemptRename = false,
                            onProgress = onProgress
                        ).getOrThrow()
                        mutationJournal.forgetTrashFallback(targetTrashFile.absolutePath, destFile.absolutePath)
                        fallbackSuccess = true
                    } catch (e: Exception) {
                        // The engine owns rollback. A retained output may now be the only
                        // complete copy, so keep its metadata and recovery record too.
                        if (!targetTrashFile.exists()) {
                            destFile.delete()
                            mutationJournal.forgetTrashFallback(targetTrashFile.absolutePath, destFile.absolutePath)
                        }
                        if (scannedPaths.isNotEmpty()) {
                            finalizeMutation(*scannedPaths.toTypedArray())
                        }
                        e.rethrowIfCancellation()
                        val msg = if (scannedPaths.isNotEmpty()) "Moved ${scannedPaths.size} of ${targets.size} items to trash. Failed on ${file.name}: ${e.message}" else "Failed to move ${file.name} to trash: ${e.message}"
                        return@withContext Result.failure(Exception(msg, e))
                    }
                }

                if (success || fallbackSuccess) {
                    scannedPaths.add(file.absolutePath)
                    deleteMediaStoreContentUri(target.contentUri)
                }
            }
            finalizeMutation(*scannedPaths.toTypedArray())
            Result.success(Unit)
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Result.failure(e)
        }
    }

    override suspend fun restoreFromTrash(trashIds: List<String>, destinationPath: String?): Result<Unit> =
        restoreWithResults(trashIds, destinationPath).fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { Result.failure((it as? PartialTrashRestoreException)?.cause ?: it) }
        )

    override suspend fun restoreWithResults(trashIds: List<String>, destinationPath: String?): Result<List<RestoredTrashItem>> =
        externalTrashMutex.withLock { restoreUnlocked(trashIds, destinationPath) }

    private suspend fun restoreUnlocked(trashIds: List<String>, destinationPath: String?): Result<List<RestoredTrashItem>> = withContext(dispatchers.io) {
        val restored = mutableListOf<RestoredTrashItem>()
        val scannedPaths = mutableListOf<String>()
        suspend fun failure(error: Throwable): Result<List<RestoredTrashItem>> {
            if (scannedPaths.isNotEmpty()) finalizeMutation(*scannedPaths.toTypedArray())
            return Result.failure(if (restored.isEmpty()) error else PartialTrashRestoreException(restored.toList(), error))
        }
        try {
            val volumes = volumeProvider.currentVolumes()

            val legacyIds = trashIds.filter { it.startsWith("legacy:") || !it.contains(":") }.map { it.removePrefix("legacy:") }
            val idsRequiringDestination = mutableListOf<String>()

            for (id in legacyIds) {
                var metadataFile: File? = null
                var trashedFile: File? = null

                for (volume in trashEnabledVolumes(volumes)) {
                    val mdDir = metadataStore.metadataDirectory(volume)
                    val trDir = metadataStore.trashDirectory(volume)

                    val candidateMd = File(mdDir, "$id.json")
                    val candidateTr = File(trDir, id)

                    if (candidateMd.exists() && candidateTr.exists()) {
                        metadataFile = candidateMd
                        trashedFile = candidateTr
                        break
                    }
                }

                if (metadataFile == null || trashedFile == null) continue

                val entity = (
                    metadataStore.read(
                        metadataFile,
                        trashedFile.parentFile ?: continue
                    ) as? TrashMetadataReadResult.Readable
                )?.entity
                val originalPath = entity?.originalPath.orEmpty()

                val originalFileContext = if (originalPath.isNotBlank()) File(originalPath)
                else File(entity?.displayName?.takeIf(::isSafeExternalName) ?: "Recovered Item ($id)")
                var targetFile = if (destinationPath != null) {
                    File(destinationPath, originalFileContext.name)
                } else {
                    originalFileContext
                }

                if (destinationPath == null) {
                    if (entity == null || originalPath.isBlank()) {
                        idsRequiringDestination.add("legacy:$id")
                        continue
                    }
                    val validationResult = validatePath(targetFile)
                    if (validationResult.isFailure) {
                        idsRequiringDestination.add("legacy:$id")
                        continue
                    }
                } else {
                    validatePath(targetFile).onFailure { return@withContext failure(it) }
                }

                val renamedForConflict = targetFile.exists()
                if (renamedForConflict) {
                    val timestamp = System.currentTimeMillis()
                    val conflictName = "${targetFile.nameWithoutExtension}.restore-conflict-$timestamp-${UUID.randomUUID()}" +
                        (if (targetFile.extension.isNotEmpty()) ".${targetFile.extension}" else "")
                    targetFile = File(targetFile.parentFile, conflictName)
                }

                targetFile.parentFile?.mkdirs()

                val sourceSnapshot = runCatchingPreservingCancellation { MutationTreeSnapshot.capture(trashedFile, undoFileKey) }.getOrNull()

                val success = rename(trashedFile, targetFile)
                if (!success) {
                    validateDestructivePath(trashedFile).onFailure { return@withContext failure(it) }
                    transferEngine.moveToTarget(
                        source = trashedFile,
                        target = targetFile,
                        attemptRename = false
                    ).getOrElse {
                        return@withContext failure(IOException("Failed to restore ${targetFile.name}: ${it.message}", it))
                    }
                }

                if (targetFile.exists()) {
                    if (sourceSnapshot != null) check(sourceSnapshot.matches(targetFile, undoFileKey, requireIdentity = false)) { "Restored contents could not be verified" }
                    val outputSnapshot = if (sourceSnapshot == null) null else
                        runCatchingPreservingCancellation { MutationTreeSnapshot.capture(targetFile, undoFileKey) }.getOrNull()
                    val token = if (outputSnapshot != null && outputSnapshot.entries.all { it.identity.fileKey != null }) UUID.randomUUID().toString() else null
                    if (token != null) synchronized(restoreUndo) {
                        while (restoreUndo.size >= 512) restoreUndo.remove(restoreUndo.keys.first())
                        restoreUndo[token] = targetFile.absolutePath to requireNotNull(outputSnapshot)
                    }
                    restored += RestoredTrashItem(id, targetFile.absolutePath, token, renamedForConflict)
                    metadataFile.delete()
                    scannedPaths.add(targetFile.absolutePath)
                }
            }

            if (idsRequiringDestination.isNotEmpty()) {
                return@withContext failure(dev.qtremors.arcile.core.storage.domain.DestinationRequiredException(idsRequiringDestination))
            }

            finalizeMutation(*scannedPaths.toTypedArray())
            Result.success(restored.toList())
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            failure(e)
        }
    }

    override suspend fun undoRestore(items: List<RestoredTrashItem>): Result<Unit> = withContext(dispatchers.io) {
        try {
            for (item in items) {
                val saved = synchronized(restoreUndo) { restoreUndo[item.undoToken] }
                    ?: throw IOException("The restored item can no longer be safely undone")
                check(saved.first == item.path && saved.second.matches(File(item.path), undoFileKey)) {
                    "The restored item changed; it has been kept"
                }
                moveToTrash(listOf(item.path)).getOrThrow()
                synchronized(restoreUndo) { restoreUndo.remove(item.undoToken) }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Result.failure(e)
        }
    }

    override suspend fun emptyTrash(): Result<Unit> = externalTrashMutex.withLock { emptyTrashUnlocked() }

    private suspend fun emptyTrashUnlocked(): Result<Unit> = withContext(dispatchers.io) {
        try {
            val volumes = volumeProvider.currentVolumes()
            val changedPaths = mutableListOf<String>()

            for (volume in trashEnabledVolumes(volumes)) {
                val trashDir = metadataStore.trashDirectory(volume)
                val metadataDir = metadataStore.metadataDirectory(volume)

                if (trashDir.exists()) {
                    trashDir.listFiles()?.forEach { file ->
                        if (file.name != ".metadata" && file.name != ".nomedia") {
                            validateDestructivePath(file).onFailure { return@withContext Result.failure(it) }
                            changedPaths.add(file.absolutePath)
                            file.deleteRecursively()
                        }
                    }
                }
                if (metadataDir.exists()) {
                    metadataDir.listFiles()?.forEach { it.delete() }
                }
                changedPaths.add(volume.path)
            }
            finalizeMutation(*changedPaths.toTypedArray())
            Result.success(Unit)
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Result.failure(e)
        }
    }

    override suspend fun getTrashFiles(): Result<List<TrashMetadata>> = externalTrashMutex.withLock { getTrashFilesUnlocked() }

    private suspend fun getTrashFilesUnlocked(): Result<List<TrashMetadata>> = withContext(dispatchers.io) {
        try {
            val list = mutableListOf<TrashMetadata>()
            val volumes = volumeProvider.currentVolumes()

            for (volume in trashEnabledVolumes(volumes)) {
                recoverExternalTrashStaging(volume)
                val trashDir = metadataStore.trashDirectory(volume)
                val trashMetadataDir = metadataStore.metadataDirectory(volume)

                if (trashDir.exists() && trashMetadataDir.exists()) {
                    trashMetadataDir.listFiles()
                        .orEmpty()
                        .asSequence()
                        .filter { it.isFile && it.extension == "json" }
                        .forEach { metadataFile ->
                            when (val metadata = metadataStore.read(metadataFile, trashDir)) {
                                is TrashMetadataReadResult.Readable -> {
                                    val entity = metadata.entity
                                    val id = entity.id
                                    val originalPath = entity.originalPath
                                    val deletionTime = entity.deletionTime
                                    val sourceVolId = entity.sourceVolumeId ?: volume.id
                                    val sourceVolKindStr = entity.sourceStorageKind ?: volume.kind.name
                                    val sourceVolKind = dev.qtremors.arcile.core.storage.domain.StorageKind.entries.find { it.name == sourceVolKindStr } ?: volume.kind
                                    val trashedFile = File(trashDir, id)
                                    val originalFileContext = if (originalPath.isNotBlank()) File(originalPath)
                                    else File(entity.displayName?.takeIf(::isSafeExternalName) ?: "Recovered Item ($id)")
                                    val spoofedModel = FileModel(
                                        name = originalFileContext.name,
                                        reference = trashedFile.absolutePath,
                                        size = if (trashedFile.isFile) trashedFile.length() else 0L,
                                        lastModified = trashedFile.lastModified(),
                                        isDirectory = trashedFile.isDirectory,
                                        extension = originalFileContext.extension,
                                        isHidden = false
                                    )

                                    list.add(
                                        TrashMetadata(
                                            id,
                                            originalPath,
                                            deletionTime,
                                            spoofedModel,
                                            sourceVolId,
                                            sourceVolKind,
                                            metadataStore.restoreStatus(entity, ::validatePath)
                                        )
                                    )
                                }
                                is TrashMetadataReadResult.Unreadable -> {
                                    val id = metadata.id
                                    val trashedFile = File(trashDir, id)
                                    val spoofedModel = FileModel(
                                        name = "Recovered Item ($id)",
                                        reference = trashedFile.absolutePath,
                                        size = if (trashedFile.isFile) trashedFile.length() else 0L,
                                        lastModified = trashedFile.lastModified(),
                                        isDirectory = trashedFile.isDirectory,
                                        extension = "",
                                        isHidden = false
                                    )
                                    list.add(TrashMetadata(id, "", trashedFile.lastModified(), spoofedModel, volume.id, volume.kind, TrashRestoreStatus.RECOVERED_ITEM))
                                }
                                is TrashMetadataReadResult.Orphan -> {
                                    AppLogger.w("TrashManager", "Deleting orphaned trash metadata")
                                    metadataFile.delete()
                                }
                        }
                    }
                }
            }
            Result.success(list.sortedByDescending { it.deletionTime })
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Result.failure(e)
        }
    }

    override suspend fun getTrashStorageUsage(): Result<TrashStorageUsage> = withContext(dispatchers.io) {
        try {
            val volumes = volumeProvider.currentVolumes()
            val byVolume = trashEnabledVolumes(volumes).associate { volume ->
                val trashDir = File(File(volume.path, ".arcile"), ".trash")
                volume.id to trashDir.listFiles()
                    .orEmpty()
                    .asSequence()
                    .filter { it.name != ".nomedia" && it.name != ".metadata" }
                    .sumOf(::trashPayloadSize)
            }.filterValues { it > 0L }
            Result.success(TrashStorageUsage(totalBytes = byVolume.values.sum(), byVolumeId = byVolume))
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Result.failure(e)
        }
    }

    private fun deleteMediaStoreContentUri(contentUri: String?) {
        if (contentUri.isNullOrBlank()) return
        try {
            context.contentResolver.delete(contentUri.toUri(), null, null)
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            AppLogger.e("TrashManager", "Failed to explicitly delete from MediaStore", e)
        }
    }

    private fun trashPayloadSize(file: File): Long {
        if (!file.exists() || file.name == ".nomedia") return 0L
        if (file.isFile) return file.length()
        return file.walkTopDown()
            .asSequence()
            .filter { it.isFile && it.name != ".nomedia" }
            .sumOf { it.length() }
    }

    override suspend fun deletePermanentlyFromTrash(trashIds: List<String>): Result<Unit> =
        externalTrashMutex.withLock { deleteTrashUnlocked(trashIds) }

    private suspend fun deleteTrashUnlocked(trashIds: List<String>): Result<Unit> = withContext(dispatchers.io) {
        try {
            val volumes = volumeProvider.currentVolumes()
            val changedPaths = mutableListOf<String>()
            for (trashId in trashIds) {
                var found = false
                for (volume in trashEnabledVolumes(volumes)) {
                    val trashDir = metadataStore.trashDirectory(volume)
                    val metadataDir = metadataStore.metadataDirectory(volume)

                    val trashedFile = File(trashDir, trashId)
                    val metadataFile = File(metadataDir, "$trashId.json")

                    if (trashedFile.exists() || metadataFile.exists()) {
                        if (trashedFile.exists()) {
                            validateDestructivePath(trashedFile).onFailure { return@withContext Result.failure(it) }
                            changedPaths.add(trashedFile.absolutePath)
                        }
                        if (trashedFile.isDirectory) trashedFile.deleteRecursively() else trashedFile.delete()
                        metadataFile.delete()
                        changedPaths.add(volume.path)
                        found = true
                        break
                    }
                }
                if (!found) {
                    AppLogger.w("TrashManager", "Trash item not found for deletion")
                }
            }
            finalizeMutation(*changedPaths.toTypedArray())
            Result.success(Unit)
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Result.failure(e)
        }
    }
}
