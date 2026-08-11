package dev.qtremors.arcile.core.privilege.android.remote

import android.system.ErrnoException
import android.system.OsConstants
import java.io.FileNotFoundException
import java.io.IOException

internal object RemoteFailureCode {
    const val ACCESS_DENIED = 1
    const val PATH_MISSING = 2
    const val PATH_ALREADY_EXISTS = 3
    const val READ_ONLY_FILESYSTEM = 4
    const val UNSUPPORTED_FILE_TYPE = 5
    const val BACKEND_DISCONNECTED = 6
    const val OPERATION_INTERRUPTED = 7
    const val INVALID_PATH = 8
    const val INSUFFICIENT_STORAGE = 9
    const val IO_FAILURE = 10
}

internal const val REMOTE_FAILURE_PREFIX = "ARCILE_FILE_ERROR:"

internal class RemoteFileException(
    val failureCode: Int,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

internal inline fun <T> remoteCall(path: String? = null, block: () -> T): T = try {
    block()
} catch (error: RemoteFileException) {
    throw error
} catch (error: ErrnoException) {
    throw error.toServiceSpecificException(path)
} catch (error: FileNotFoundException) {
    throw RemoteFileException(RemoteFailureCode.PATH_MISSING, safeMessage(path, error), error)
} catch (error: SecurityException) {
    throw RemoteFileException(RemoteFailureCode.ACCESS_DENIED, safeMessage(path, error), error)
} catch (error: InterruptedException) {
    Thread.currentThread().interrupt()
    throw RemoteFileException(RemoteFailureCode.OPERATION_INTERRUPTED, safeMessage(path, error), error)
} catch (error: IOException) {
    throw RemoteFileException(RemoteFailureCode.IO_FAILURE, safeMessage(path, error), error)
} catch (error: IllegalArgumentException) {
    throw RemoteFileException(RemoteFailureCode.INVALID_PATH, safeMessage(path, error), error)
}

private fun ErrnoException.toServiceSpecificException(path: String?): RemoteFileException {
    val code = when (errno) {
        OsConstants.EACCES, OsConstants.EPERM -> RemoteFailureCode.ACCESS_DENIED
        OsConstants.ENOENT, OsConstants.ENOTDIR -> RemoteFailureCode.PATH_MISSING
        OsConstants.EEXIST -> RemoteFailureCode.PATH_ALREADY_EXISTS
        OsConstants.EROFS -> RemoteFailureCode.READ_ONLY_FILESYSTEM
        OsConstants.ENOSPC, OsConstants.EDQUOT -> RemoteFailureCode.INSUFFICIENT_STORAGE
        OsConstants.EINTR, OsConstants.ECANCELED -> RemoteFailureCode.OPERATION_INTERRUPTED
        OsConstants.EINVAL, OsConstants.ENAMETOOLONG -> RemoteFailureCode.INVALID_PATH
        OsConstants.ENOTSUP, OsConstants.EOPNOTSUPP -> RemoteFailureCode.UNSUPPORTED_FILE_TYPE
        else -> RemoteFailureCode.IO_FAILURE
    }
    return RemoteFileException(code, safeMessage(path, this), this)
}

private fun safeMessage(path: String?, error: Throwable): String = buildString {
    if (path != null) append(path)
    val detail = error.message?.substringBefore('\n')?.take(160)
    if (!detail.isNullOrBlank()) {
        if (isNotEmpty()) append(": ")
        append(detail)
    }
}.ifBlank { "Privileged filesystem operation failed" }
