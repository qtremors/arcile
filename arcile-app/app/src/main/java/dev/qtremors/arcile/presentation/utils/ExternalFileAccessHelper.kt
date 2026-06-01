package dev.qtremors.arcile.presentation.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import dev.qtremors.arcile.utils.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

object ExternalFileAccessHelper {
    private const val STAGING_ROOT = "external_access"
    private const val SHARE_STAGING = "share"
    private const val MAX_CACHE_SIZE_BYTES = 500L * 1024 * 1024 // 500MB
    private const val MAX_CACHE_AGE_MS = 6L * 60 * 60 * 1000 // 6 hours
    private const val MAX_SHARE_FILE_BYTES = 256L * 1024 * 1024 // 256MB
    private const val MAX_SHARE_BATCH_BYTES = 750L * 1024 * 1024 // 750MB

    data class StagingCacheStats(
        val fileCount: Int,
        val sizeBytes: Long
    )

    data class ShareTarget(
        val uri: Uri,
        val mimeType: String,
        val displayName: String,
        val sizeBytes: Long
    )

    internal var directOpenUriFactory: (Context, File) -> Uri = ::createFileProviderUri

    internal fun resetDirectOpenUriFactoryForTest() {
        directOpenUriFactory = ::createFileProviderUri
    }

    fun cleanupStagingArea(context: Context): StagingCacheStats {
        val baseStagingDir = File(context.cacheDir, STAGING_ROOT)
        if (!baseStagingDir.exists()) return StagingCacheStats(fileCount = 0, sizeBytes = 0L)

        val allStagedFiles = baseStagingDir.walkTopDown()
            .filter { it.isFile }
            .sortedBy {
                runCatching {
                    java.nio.file.Files.readAttributes(
                        it.toPath(),
                        java.nio.file.attribute.BasicFileAttributes::class.java
                    ).creationTime().toMillis()
                }.getOrDefault(it.lastModified())
            }
            .toList()

        var currentSizeBytes = allStagedFiles.sumOf { it.length() }
        val now = System.currentTimeMillis()

        for (file in allStagedFiles) {
            val ageMs = runCatching {
                now - java.nio.file.Files.readAttributes(
                    file.toPath(),
                    java.nio.file.attribute.BasicFileAttributes::class.java
                ).creationTime().toMillis()
            }.getOrDefault(0L) // Default to 0 age if unknown to avoid accidental delete

            val isOld = ageMs > MAX_CACHE_AGE_MS
            val isOversized = currentSizeBytes > MAX_CACHE_SIZE_BYTES

            if (isOld || isOversized) {
                val size = file.length()
                if (file.delete()) {
                    currentSizeBytes -= size
                }
            } else {
                break
            }
        }

        pruneEmptyDirectories(baseStagingDir)
        return getStagingCacheStats(context)
    }

    fun clearStagingArea(context: Context): StagingCacheStats {
        File(context.cacheDir, STAGING_ROOT).deleteRecursively()
        return StagingCacheStats(fileCount = 0, sizeBytes = 0L)
    }

    fun getStagingCacheStats(context: Context): StagingCacheStats {
        val baseStagingDir = File(context.cacheDir, STAGING_ROOT)
        if (!baseStagingDir.exists()) return StagingCacheStats(fileCount = 0, sizeBytes = 0L)
        val files = baseStagingDir.walkTopDown().filter { it.isFile }.toList()
        return StagingCacheStats(
            fileCount = files.size,
            sizeBytes = files.sumOf { it.length() }
        )
    }

    private fun pruneEmptyDirectories(root: File) {
        root.walkBottomUp()
            .filter { it.isDirectory && it != root && (it.list()?.isEmpty() != false) }
            .forEach { it.delete() }
    }

    private fun sha1(value: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(value.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun allowedStorageRoots(context: Context): List<String> {
        val roots = mutableSetOf<String>()
        roots += Environment.getExternalStorageDirectory().canonicalPath
        context.getExternalFilesDirs(null).mapNotNullTo(roots) { dir ->
            dir?.parentFile?.parentFile?.parentFile?.parentFile?.canonicalPath
        }
        return roots.toList()
    }

    fun isAllowedUserFile(context: Context, file: File): Boolean {
        val canonicalPath = file.canonicalPath
        val normalizedSegments = canonicalPath
            .split(File.separatorChar, '/', '\\')
            .map { it.lowercase() }
            .filter { it.isNotBlank() }
        val disallowedRoots = listOfNotNull(
            context.cacheDir.canonicalPath,
            context.filesDir?.canonicalPath,
            context.dataDir?.canonicalPath
        )
        if (disallowedRoots.any { canonicalPath == it || canonicalPath.startsWith("$it${File.separator}") }) {
            return false
        }
        if (canonicalPath.contains("${File.separator}.arcile${File.separator}") || canonicalPath.endsWith("${File.separator}.arcile")) {    
            return false
        }
        if (normalizedSegments.zipWithNext().any { (first, second) ->
                first == "android" && (second == "data" || second == "obb")
            }
        ) {
            return false
        }
        return allowedStorageRoots(context).any { root ->
            canonicalPath == root || canonicalPath.startsWith("$root${File.separator}")
        }
    }

    private fun mimeTypeFor(file: File): String =
        MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(file.extension.lowercase())
            ?: "*/*"

    private fun createFileProviderUri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    private suspend fun stageFile(context: Context, file: File, purpose: String): File = withContext(Dispatchers.IO) {
        require(file.exists() && file.isFile) { "Source file does not exist" }
        require(isAllowedUserFile(context, file)) { "Unsupported file path" }

        cleanupStagingArea(context)

        val stagingDir = File(context.cacheDir, "$STAGING_ROOT${File.separator}$purpose").apply { mkdirs() }
        val extension = file.extension.takeIf { it.isNotBlank() }?.let { ".$it" }.orEmpty()
        val stagedFile = File(stagingDir, "${sha1(file.canonicalPath)}$extension")
        file.inputStream().use { input ->
            stagedFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        stagedFile.setLastModified(file.lastModified())
        stagedFile
    }

    private fun validateShareBatch(files: List<File>) {
        var totalSize = 0L
        files.forEach { file ->
            val size = file.length()
            require(size <= MAX_SHARE_FILE_BYTES) {
                "Share target is too large for staged handoff"
            }
            totalSize = Math.addExact(totalSize, size)
            require(totalSize <= MAX_SHARE_BATCH_BYTES) {
                "Share selection is too large for staged handoff"
            }
        }
    }

    suspend fun createOpenIntent(context: Context, path: String): Intent {
        val sourceFile = File(path)
        require(sourceFile.exists() && sourceFile.isFile) { "Source file does not exist" }
        require(isAllowedUserFile(context, sourceFile)) { "Unsupported file path" }

        val uri = runCatching {
            directOpenUriFactory(context, sourceFile)
        }.getOrElse { error ->
            throw IllegalArgumentException("Unable to create file access grant", error)
        }
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeTypeFor(sourceFile))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    suspend fun createShareUris(context: Context, filePaths: List<String>): List<Uri> {
        return createShareTargets(context, filePaths).map { it.uri }
    }

    suspend fun createShareTargets(context: Context, filePaths: List<String>): List<ShareTarget> = withContext(Dispatchers.IO) {
        val files = filePaths.map(::File)
        validateShareBatch(files)

        files.mapNotNull { file ->
            runCatching {
                val stagedFile = stageFile(context, file, SHARE_STAGING)
                ShareTarget(
                    uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", stagedFile),
                    mimeType = mimeTypeFor(file),
                    displayName = file.name,
                    sizeBytes = file.length()
                )
            }.getOrElse { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
                AppLogger.w("ExternalFileAccess", "Skipping unsupported share target: ${file.absolutePath}")
                null
            }
        }
    }

    fun openInFilesApp(context: Context, uriString: String): Boolean {
        return runCatching {
            val uri = Uri.parse(uriString)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

                val packageInfos = context.packageManager.getPackagesHoldingPermissions(
                    arrayOf(android.Manifest.permission.MANAGE_DOCUMENTS),
                    0
                )
                val documentsUiPackage = packageInfos.firstOrNull { it.packageName.endsWith(".documentsui") }?.packageName
                    ?: packageInfos.firstOrNull()?.packageName
                documentsUiPackage?.let { setPackage(it) }
            }
            context.startActivity(intent)
            true
        }.getOrElse { error ->
            if (error is kotlinx.coroutines.CancellationException) throw error
            AppLogger.e("ExternalFileAccess", "Failed to open folder in Files app", error)
            false
        }
    }
}
