package dev.qtremors.arcile.presentation.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import dev.qtremors.arcile.utils.AppLogger
import java.io.File
import java.security.MessageDigest

object ExternalFileAccessHelper {
    private const val STAGING_ROOT = "external_access"
    private const val OPEN_STAGING = "open"
    private const val SHARE_STAGING = "share"

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
        return allowedStorageRoots(context).any { root ->
            canonicalPath == root || canonicalPath.startsWith("$root${File.separator}")
        }
    }

    private fun stageFile(context: Context, file: File, purpose: String): File {
        require(file.exists() && file.isFile) { "Source file does not exist" }
        require(isAllowedUserFile(context, file)) { "Unsupported file path" }

        val stagingDir = File(context.cacheDir, "$STAGING_ROOT${File.separator}$purpose").apply { mkdirs() }
        val extension = file.extension.takeIf { it.isNotBlank() }?.let { ".$it" }.orEmpty()
        val stagedFile = File(stagingDir, "${sha1(file.canonicalPath)}$extension")
        file.inputStream().use { input ->
            stagedFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        stagedFile.setLastModified(file.lastModified())
        return stagedFile
    }

    fun createOpenIntent(context: Context, path: String): Intent {
        val sourceFile = File(path)
        val stagedFile = stageFile(context, sourceFile, OPEN_STAGING)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", stagedFile)
        val mimeType = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(sourceFile.extension.lowercase())
            ?: "*/*"
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun createShareUris(context: Context, filePaths: List<String>): List<Uri> {
        return filePaths.mapNotNull { path ->
            runCatching {
                val stagedFile = stageFile(context, File(path), SHARE_STAGING)
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", stagedFile)
            }.getOrElse { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
                AppLogger.w("ExternalFileAccess", "Skipping unsupported share target: $path")
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
