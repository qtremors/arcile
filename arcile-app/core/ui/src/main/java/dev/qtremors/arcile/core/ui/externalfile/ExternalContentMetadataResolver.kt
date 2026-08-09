package dev.qtremors.arcile.core.ui.externalfile

import android.content.ContentResolver
import android.os.CancellationSignal
import android.provider.OpenableColumns
import android.net.Uri
import java.io.IOException
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout

data class ExternalContentMetadata(
    val mimeType: String?,
    val displayName: String?,
    val sizeBytes: Long?
)

suspend fun resolveExternalContentMetadata(
    contentResolver: ContentResolver,
    uri: Uri,
    declaredMimeType: String?,
    ioDispatcher: CoroutineDispatcher,
    timeoutMillis: Long = EXTERNAL_PROVIDER_TIMEOUT_MS
): Result<ExternalContentMetadata> = try {
    Result.success(
        withTimeout(timeoutMillis) {
            resolveOnDispatcher(contentResolver, uri, declaredMimeType, ioDispatcher)
        }
    )
} catch (error: TimeoutCancellationException) {
    Result.failure(IOException("The storage provider did not respond in time", error))
} catch (error: kotlinx.coroutines.CancellationException) {
    throw error
} catch (error: Exception) {
    Result.failure(error)
}

private suspend fun resolveOnDispatcher(
    contentResolver: ContentResolver,
    uri: Uri,
    declaredMimeType: String?,
    dispatcher: CoroutineDispatcher
): ExternalContentMetadata = suspendCancellableCoroutine { continuation ->
    val cancellationSignal = CancellationSignal()
    continuation.invokeOnCancellation { cancellationSignal.cancel() }
    dispatcher.dispatch(continuation.context, Runnable {
        if (!continuation.isActive) return@Runnable
        try {
            contentResolver.openFileDescriptor(uri, "r", cancellationSignal)?.use { descriptor ->
                if (!descriptor.fileDescriptor.valid()) throw IOException("The shared file is no longer readable")
            } ?: throw IOException("The shared file is no longer readable")
            var displayName: String? = null
            var sizeBytes: Long? = null
            contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null,
                cancellationSignal
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0 && !cursor.isNull(nameIndex)) displayName = cursor.getString(nameIndex)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) sizeBytes = cursor.getLong(sizeIndex)
                }
            }
            val metadata = ExternalContentMetadata(
                mimeType = declaredMimeType ?: contentResolver.getType(uri),
                displayName = displayName,
                sizeBytes = sizeBytes
            )
            if (continuation.isActive) continuation.resumeWith(Result.success(metadata))
        } catch (error: Exception) {
            if (continuation.isActive) continuation.resumeWith(Result.failure(error))
        }
    })
}

private const val EXTERNAL_PROVIDER_TIMEOUT_MS = 5_000L
