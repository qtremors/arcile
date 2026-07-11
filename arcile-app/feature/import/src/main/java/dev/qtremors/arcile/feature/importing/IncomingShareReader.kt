package dev.qtremors.arcile.feature.importing

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import dev.qtremors.arcile.core.operation.android.MAX_IMPORT_BYTES
import dev.qtremors.arcile.core.operation.android.MAX_IMPORT_ITEMS
import dev.qtremors.arcile.core.operation.android.sanitizeIncomingFileName
import dev.qtremors.arcile.core.ui.R
import java.io.File

internal object IncomingShareReader {
    fun fromIntent(context: Context, intent: Intent): List<IncomingSharedFile> =
        preflightFromIntent(context, intent).accepted

    fun preflightFromIntent(context: Context, intent: Intent): IncomingSharePreflightResult {
        val uris = buildList {
            intent.streamUri()?.let(::add)
            intent.streamUris()?.let(::addAll)
            collectClipData(intent.clipData).forEach(::add)
        }.distinct()
        if (uris.size > MAX_IMPORT_ITEMS) {
            return IncomingSharePreflightResult(
                accepted = emptyList(),
                rejected = listOf(
                    IncomingShareFailure(
                        uri = null,
                        displayName = null,
                        reason = IncomingShareFailureReason.TooManyItems,
                        message = context.getString(
                            R.string.save_to_arcile_too_many_files,
                            MAX_IMPORT_ITEMS
                        )
                    )
                ),
                limitExceeded = true
            )
        }

        val accepted = mutableListOf<IncomingSharedFile>()
        val rejected = mutableListOf<IncomingShareFailure>()
        var knownBytes = 0L
        uris.forEach { uri ->
            val metadata = resolveMetadata(context, uri)
            val originalName = metadata.name
                ?: uri.lastPathSegment?.substringAfterLast('/')
                ?: "shared-file"
            validateUriScheme(context, uri)?.let { failure ->
                rejected += failure.copy(displayName = originalName)
                return@forEach
            }

            val size = metadata.size?.takeIf { it >= 0L }
            if (size != null) {
                knownBytes += size
                if (knownBytes > MAX_IMPORT_BYTES) {
                    rejected += IncomingShareFailure(
                        uri = uri,
                        displayName = originalName,
                        reason = IncomingShareFailureReason.TooLarge,
                        message = context.getString(R.string.save_to_arcile_too_large)
                    )
                    return@forEach
                }
            }
            val displayName = sanitizeIncomingFileName(
                rawName = originalName,
                fallbackExtension = fallbackExtension(uri),
                existingNames = accepted.mapTo(mutableSetOf(), IncomingSharedFile::displayName)
            )
            accepted += IncomingSharedFile(
                uri = uri,
                displayName = displayName,
                sizeBytes = size,
                originalName = originalName,
                requiresCountedStream = size == null
            )
        }
        return IncomingSharePreflightResult(
            accepted = accepted,
            rejected = rejected,
            limitExceeded = rejected.any { it.reason == IncomingShareFailureReason.TooLarge }
        )
    }

    private fun collectClipData(clipData: ClipData?): List<Uri> {
        if (clipData == null) return emptyList()
        return (0 until clipData.itemCount).mapNotNull { index -> clipData.getItemAt(index).uri }
    }

    private fun validateUriScheme(context: Context, uri: Uri): IncomingShareFailure? =
        when (uri.scheme?.lowercase()) {
            "content" -> null
            "file" -> if (isAppOwnedFileUri(context, uri)) {
                null
            } else {
                IncomingShareFailure(
                    uri = uri,
                    displayName = null,
                    reason = IncomingShareFailureReason.ExternalFileUri,
                    message = context.getString(R.string.save_to_arcile_external_file_uri)
                )
            }
            else -> IncomingShareFailure(
                uri = uri,
                displayName = null,
                reason = IncomingShareFailureReason.UnsupportedScheme,
                message = context.getString(R.string.save_to_arcile_unsupported_source)
            )
        }

    private fun isAppOwnedFileUri(context: Context, uri: Uri): Boolean {
        val source = uri.path
            ?.let(::File)
            ?.let { runCatching { it.canonicalFile }.getOrNull() }
            ?: return false
        return appOwnedRoots(context).any { root ->
            val canonicalRoot = runCatching { root.canonicalFile }.getOrNull() ?: return@any false
            source == canonicalRoot ||
                source.absolutePath.startsWith(canonicalRoot.absolutePath + File.separator)
        }
    }

    private fun appOwnedRoots(context: Context): List<File> = listOfNotNull(
        context.filesDir,
        context.cacheDir,
        context.externalCacheDir,
        context.getExternalFilesDir(null)
    )

    private fun resolveMetadata(context: Context, uri: Uri): IncomingMetadata {
        var metadata = IncomingMetadata()
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor -> metadata = cursor.readOpenableMetadata() }
        }
        if (uri.scheme?.lowercase() == "file") {
            uri.path?.let(::File)?.let { file ->
                metadata = metadata.copy(
                    name = metadata.name?.takeIf(String::isNotBlank) ?: file.name,
                    size = metadata.size ?: file.takeIf(File::exists)?.length()
                )
            }
        }
        return metadata.copy(name = metadata.name?.takeIf(String::isNotBlank))
    }
}

private fun Intent.streamUri(): Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
} else {
    @Suppress("DEPRECATION")
    getParcelableExtra(Intent.EXTRA_STREAM)
}

private fun Intent.streamUris(): List<Uri>? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableArrayListExtra(Intent.EXTRA_STREAM)
    }

private data class IncomingMetadata(
    val name: String? = null,
    val size: Long? = null
)

private fun Cursor.readOpenableMetadata(): IncomingMetadata {
    if (!moveToFirst()) return IncomingMetadata()
    val nameIndex = getColumnIndex(OpenableColumns.DISPLAY_NAME)
    val sizeIndex = getColumnIndex(OpenableColumns.SIZE)
    return IncomingMetadata(
        name = nameIndex.takeIf { it >= 0 }?.let(::getString),
        size = sizeIndex.takeIf { it >= 0 && !isNull(it) }?.let(::getLong)
    )
}

private fun fallbackExtension(uri: Uri): String? = uri.lastPathSegment
    ?.substringAfterLast('/', missingDelimiterValue = "")
    ?.substringAfterLast('.', missingDelimiterValue = "")
    ?.takeIf { extension ->
        extension.isNotBlank() &&
            extension.length <= 16 &&
            extension.all(Char::isLetterOrDigit)
    }
