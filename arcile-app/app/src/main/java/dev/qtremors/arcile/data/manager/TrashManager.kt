package dev.qtremors.arcile.data.manager

import android.content.Context
import android.provider.MediaStore
import android.provider.Settings
import dev.qtremors.arcile.data.provider.VolumeProvider
import dev.qtremors.arcile.data.source.MediaStoreClient
import dev.qtremors.arcile.data.util.resolveVolumeForPath
import dev.qtremors.arcile.data.util.trashEnabledVolumes
import dev.qtremors.arcile.domain.FileModel
import dev.qtremors.arcile.domain.StorageVolume
import dev.qtremors.arcile.domain.TrashMetadata
import dev.qtremors.arcile.domain.supportsTrash
import dev.qtremors.arcile.utils.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

@Serializable
data class TrashMetadataEntity(
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

private object TrashCryptoHelper {
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BIT = 128
    private const val IV_LENGTH_BYTE = 12
    private const val PREFS_NAME = "trash_crypto_prefs"
    private const val PREF_SALT = "crypto_salt"
    private const val KEY_ALIAS = "arcile_trash_key"

    private var keystoreKey: javax.crypto.SecretKey? = null
    private var fallbackKey: SecretKeySpec? = null

    private fun getSalt(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saltString = prefs.getString(PREF_SALT, null)
        if (saltString != null) {
            return android.util.Base64.decode(saltString, android.util.Base64.DEFAULT)
        }
        val newSalt = ByteArray(32)
        SecureRandom().nextBytes(newSalt)
        prefs.edit().putString(PREF_SALT, android.util.Base64.encodeToString(newSalt, android.util.Base64.DEFAULT)).apply()
        return newSalt
    }

    private fun getKeyStoreKey(): javax.crypto.SecretKey? {
        if (keystoreKey != null) return keystoreKey
        return try {
            val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                val keyGenerator = javax.crypto.KeyGenerator.getInstance(
                    android.security.keystore.KeyProperties.KEY_ALGORITHM_AES,
                    "AndroidKeyStore"
                )
                val keyGenParameterSpec = android.security.keystore.KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
                keyGenerator.init(keyGenParameterSpec)
                keyGenerator.generateKey()
            }
            val key = keyStore.getKey(KEY_ALIAS, null) as javax.crypto.SecretKey
            keystoreKey = key
            key
        } catch (e: Exception) {
            AppLogger.e("TrashCryptoHelper", "KeyStore initialization failed")
            null
        }
    }

    private fun getFallbackKey(context: Context): SecretKeySpec {
        fallbackKey?.let { return it }
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "fallback_id"
        val salt = getSalt(context)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(androidId.toCharArray(), salt, 10000, 256)
        val tmp = factory.generateSecret(spec)
        val key = SecretKeySpec(tmp.encoded, "AES")
        fallbackKey = key
        return key
    }

    private fun getKey(context: Context): javax.crypto.SecretKey {
        return getKeyStoreKey() ?: getFallbackKey(context)
    }

    fun encrypt(context: Context, plainText: String): ByteArray {
        var retries = 3
        var lastException: Exception? = null
        while (retries > 0) {
            try {
                val key = getKey(context)
                val cipher = Cipher.getInstance(ALGORITHM)
                cipher.init(Cipher.ENCRYPT_MODE, key)
                var iv = cipher.iv
                if (iv == null || iv.size != IV_LENGTH_BYTE) {
                    iv = ByteArray(IV_LENGTH_BYTE)
                    SecureRandom().nextBytes(iv)
                    val parameterSpec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
                    cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec)
                }
                val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

                val byteBuffer = ByteBuffer.allocate(iv.size + cipherText.size)
                byteBuffer.put(iv)
                byteBuffer.put(cipherText)
                return byteBuffer.array()
            } catch (e: Exception) {
                lastException = e
                retries--
            }
        }
        AppLogger.e("TrashCryptoHelper", "Encryption failed after retries")
        throw lastException ?: Exception("Encryption failed")
    }

    fun decrypt(context: Context, encryptedData: ByteArray): String {
        var retries = 3
        var lastException: Exception? = null
        while (retries > 0) {
            try {
                val key = getKey(context)
                val cipher = Cipher.getInstance(ALGORITHM)
                val byteBuffer = ByteBuffer.wrap(encryptedData)
                val iv = ByteArray(IV_LENGTH_BYTE)
                byteBuffer.get(iv)
                val cipherText = ByteArray(byteBuffer.remaining())
                byteBuffer.get(cipherText)

                val parameterSpec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
                cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec)
                val plainText = cipher.doFinal(cipherText)
                return String(plainText, Charsets.UTF_8)
            } catch (e: Exception) {
                lastException = e
                retries--
            }
        }
        AppLogger.e("TrashCryptoHelper", "Decryption failed after retries")
        throw lastException ?: Exception("Decryption failed")
    }
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

    private fun scanMediaFiles(vararg paths: String) {
        if (paths.isEmpty()) return
        android.media.MediaScannerConnection.scanFile(context.applicationContext, paths, null, null)
    }

    private suspend fun finalizeMutation(vararg paths: String) {
        mediaStoreClient.invalidateCache(*paths)
        volumeProvider.invalidateCache()
        scanMediaFiles(*paths)
    }

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

    private fun verifyRestoreCopy(source: File, target: File): Boolean {
        if (!target.exists()) return false
        if (source.isFile && (!target.isFile || source.length() != target.length())) return false
        if (source.isDirectory && !target.isDirectory) return false
        return true
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
                val arcileDir = File(sourceVolume.path, ".arcile")
                if (file.absolutePath.startsWith(arcileDir.absolutePath)) continue

                val trashId = java.util.UUID.randomUUID().toString()
                val targetTrashFile = File(trashDir, trashId)

                val metadataEntity = TrashMetadataEntity(
                    id = trashId,
                    originalPath = file.absolutePath,
                    deletionTime = System.currentTimeMillis(),
                    sourceVolumeId = sourceVolume.id,
                    sourceStorageKind = sourceVolume.kind.name
                )
                val jsonFormat = Json { ignoreUnknownKeys = true }
                val jsonString = jsonFormat.encodeToString(metadataEntity)
                val destFile = File(trashMetadataDir, "$trashId.json")
                try {
                    val encryptedBytes = TrashCryptoHelper.encrypt(context, jsonString)
                    destFile.writeBytes(encryptedBytes)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    AppLogger.e("TrashManager", "Failed to encrypt trash metadata, aborting move to trash", e)
                    return@withContext Result.failure(Exception("Failed to secure trash metadata: ${e.message}", e))
                }

                val success = file.renameTo(targetTrashFile)
                var fallbackSuccess = false
                if (!success) {
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
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        File(trashMetadataDir, "$trashId.json").delete()
                        if (targetTrashFile.exists()) {
                            if (targetTrashFile.isDirectory) targetTrashFile.deleteRecursively() else targetTrashFile.delete()
                        }
                        if (scannedPaths.isNotEmpty()) {
                            finalizeMutation(*scannedPaths.toTypedArray())
                        }
                        return@withContext Result.failure(Exception("Failed to move ${file.name} to trash: ${e.message}", e))
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

                val jsonFormat = Json { ignoreUnknownKeys = true }
                val jsonString = try {
                    val bytes = metadataFile.readBytes()
                    TrashCryptoHelper.decrypt(context, bytes)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    val text = metadataFile.readText()
                    if (text.trimStart().startsWith("{")) {
                        text
                    } else {
                        throw Exception("Failed to decrypt or parse metadata", e)
                    }
                }
                val entity = jsonFormat.decodeFromString<TrashMetadataEntity>(jsonString)
                val originalPath = entity.originalPath

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
                            try {
                                val jsonFormat = Json { ignoreUnknownKeys = true }
                                val jsonString = try {
                                    val bytes = metadataFile.readBytes()
                                    TrashCryptoHelper.decrypt(context, bytes)
                                } catch (e: Exception) {
                                    if (e is kotlinx.coroutines.CancellationException) throw e
                                    val text = metadataFile.readText()
                                    if (text.trimStart().startsWith("{")) {
                                        text
                                    } else {
                                        throw Exception("Failed to decrypt or parse metadata", e)
                                    }
                                }
                                val entity = jsonFormat.decodeFromString<TrashMetadataEntity>(jsonString)
                                val id = entity.id
                                val originalPath = entity.originalPath
                                val deletionTime = entity.deletionTime
                                val sourceVolId = entity.sourceVolumeId ?: volume.id
                                val sourceVolKindStr = entity.sourceStorageKind ?: volume.kind.name
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
                                    AppLogger.w("TrashManager", "Deleting orphaned trash metadata")
                                    metadataFile.delete()
                                }
                            } catch (e: Exception) {
                                if (e is kotlinx.coroutines.CancellationException) throw e
                                AppLogger.e("TrashManager", "Deleting corrupted trash metadata", e)
                                metadataFile.delete()
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
