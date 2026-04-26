package dev.qtremors.arcile.data.manager

import android.content.Context
import android.provider.MediaStore
import android.provider.Settings
import dev.qtremors.arcile.data.FolderStatsStore
import dev.qtremors.arcile.data.provider.VolumeProvider
import dev.qtremors.arcile.data.source.MediaStoreClient
import dev.qtremors.arcile.data.util.PathSafety
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
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.ProviderException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.AEADBadTagException
import javax.crypto.BadPaddingException
import javax.crypto.IllegalBlockSizeException
import javax.crypto.SecretKey
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
    private const val KEYSTORE_MARKER: Byte = 0x01
    private const val FALLBACK_MARKER: Byte = 0x02
    private const val PBKDF2_ITERATIONS = 600_000

    class KeyStoreUnavailableException(message: String, cause: Throwable? = null) : Exception(message, cause)

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

    private fun getKeyStoreKey(createIfMissing: Boolean = true): javax.crypto.SecretKey? {
        return try {
            val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                if (!createIfMissing) return null
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
            keyStore.getKey(KEY_ALIAS, null) as javax.crypto.SecretKey
        } catch (e: Exception) {
            AppLogger.e("TrashCryptoHelper", "KeyStore initialization failed")
            null
        }
    }

    private fun getFallbackKey(context: Context): SecretKeySpec {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "fallback_id"
        val salt = getSalt(context)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(androidId.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
        val tmp = factory.generateSecret(spec)
        return SecretKeySpec(tmp.encoded, "AES")
    }

    private data class CryptoKey(val marker: Byte, val key: SecretKey)

    private fun getEncryptionKey(context: Context): CryptoKey {
        val keyStoreKey = getKeyStoreKey(createIfMissing = true)
        return if (keyStoreKey != null) {
            CryptoKey(KEYSTORE_MARKER, keyStoreKey)
        } else {
            CryptoKey(FALLBACK_MARKER, getFallbackKey(context))
        }
    }

    private fun getDecryptionKey(context: Context, marker: Byte): SecretKey {
        return when (marker) {
            KEYSTORE_MARKER -> getKeyStoreKey(createIfMissing = false)
                ?: throw KeyStoreUnavailableException("Trash metadata was encrypted on another device and cannot be decrypted here.")
            FALLBACK_MARKER -> getFallbackKey(context)
            else -> getEncryptionKey(context).key
        }
    }

    private fun isTransientCryptoFailure(error: Exception): Boolean {
        return error is ProviderException
    }

    private fun isDeterministicCryptoFailure(error: Exception): Boolean {
        return error is java.security.InvalidKeyException ||
            error is BadPaddingException ||
            error is AEADBadTagException ||
            error is IllegalBlockSizeException ||
            error is GeneralSecurityException
    }

    private fun encryptOnce(key: SecretKey, plainText: String, marker: Byte): ByteArray {
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        var iv = cipher.iv
        if (iv == null || iv.size != IV_LENGTH_BYTE) {
            iv = ByteArray(IV_LENGTH_BYTE)
            SecureRandom().nextBytes(iv)
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BIT, iv))
        }
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val byteBuffer = ByteBuffer.allocate(1 + iv.size + cipherText.size)
        byteBuffer.put(marker)
        byteBuffer.put(iv)
        byteBuffer.put(cipherText)
        return byteBuffer.array()
    }

    private fun decryptOnce(context: Context, encryptedData: ByteArray, legacyMode: Boolean): String {
        val marker = if (legacyMode) 0 else encryptedData.first()
        val keyCandidates = if (legacyMode) {
            listOfNotNull(getKeyStoreKey(createIfMissing = false), getFallbackKey(context))
        } else {
            listOf(getDecryptionKey(context, marker))
        }
        var lastFailure: Exception? = null
        for (key in keyCandidates) {
            try {
                return decryptWithKey(encryptedData, legacyMode, key)
            } catch (e: Exception) {
                lastFailure = e
            }
        }
        throw lastFailure ?: IllegalArgumentException("No trash metadata decryption key is available")
    }

    private fun decryptWithKey(encryptedData: ByteArray, legacyMode: Boolean, key: SecretKey): String {
        val byteBuffer = ByteBuffer.wrap(encryptedData)
        if (!legacyMode) byteBuffer.get()
        val iv = ByteArray(IV_LENGTH_BYTE)
        byteBuffer.get(iv)
        val cipherText = ByteArray(byteBuffer.remaining())
        byteBuffer.get(cipherText)

        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BIT, iv))
        return String(cipher.doFinal(cipherText), Charsets.UTF_8)
    }

    fun encrypt(context: Context, plainText: String): ByteArray {
        var retries = 3
        var lastException: Exception? = null
        while (retries > 0) {
            try {
                val cryptoKey = getEncryptionKey(context)
                return encryptOnce(cryptoKey.key, plainText, cryptoKey.marker)
            } catch (e: Exception) {
                lastException = e
                if (isDeterministicCryptoFailure(e) || !isTransientCryptoFailure(e)) break
                retries--
                AppLogger.w("TrashCryptoHelper", "Transient encryption failure, attempts remaining: $retries", e)
            }
        }
        AppLogger.e("TrashCryptoHelper", "Encryption failed after retries")
        throw lastException ?: Exception("Encryption failed")
    }

    fun decrypt(context: Context, encryptedData: ByteArray): String {
        if (encryptedData.size <= IV_LENGTH_BYTE) throw IllegalArgumentException("Encrypted trash metadata is too short")
        var retries = 3
        var lastException: Exception? = null
        val hasMarker = encryptedData.first() == KEYSTORE_MARKER || encryptedData.first() == FALLBACK_MARKER
        while (retries > 0) {
            try {
                return decryptOnce(context, encryptedData, legacyMode = !hasMarker)
            } catch (e: Exception) {
                lastException = e
                if (isDeterministicCryptoFailure(e) || e is KeyStoreUnavailableException || !isTransientCryptoFailure(e)) break
                retries--
                AppLogger.w("TrashCryptoHelper", "Transient decryption failure, attempts remaining: $retries", e)
            }
        }
        AppLogger.e("TrashCryptoHelper", "Decryption failed after retries")
        throw lastException ?: Exception("Decryption failed")
    }
}

class DefaultTrashManager(
    private val context: Context,
    private val volumeProvider: VolumeProvider,
    private val mediaStoreClient: MediaStoreClient,
    private val folderStatsStore: FolderStatsStore
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
        folderStatsStore.invalidate(paths.flatMap(::pathWithAncestors))
        scanMediaFiles(*paths)
    }

    private fun pathWithAncestors(path: String): List<String> {
        return PathSafety.pathWithAncestors(path, volumeProvider.activeStorageRoots)
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
            var hasDeviceBoundUnreadableMetadata = false

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
                                if (e is TrashCryptoHelper.KeyStoreUnavailableException) {
                                    AppLogger.w("TrashManager", "Trash metadata cannot be decrypted on this device", e)
                                    hasDeviceBoundUnreadableMetadata = true
                                    return@forEach
                                }
                                AppLogger.e("TrashManager", "Deleting corrupted trash metadata", e)
                                metadataFile.delete()
                            }
                        }
                    }
                }
            }
            if (hasDeviceBoundUnreadableMetadata) {
                return@withContext Result.failure(
                    IllegalStateException(
                        "Some Trash metadata was encrypted on another device and cannot be restored here. Review or empty Trash before migrating devices."
                    )
                )
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
