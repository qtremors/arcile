package dev.qtremors.arcile.core.storage.domain

import java.io.IOException

sealed class ArcileError(
    cause: Throwable? = null
) : Exception(cause) {
    class AccessDenied(cause: Throwable? = null) : ArcileError(cause)
    class StorageUnavailable(cause: Throwable? = null) : ArcileError(cause)
    class Conflict(cause: Throwable? = null) : ArcileError(cause)
    class InsufficientSpace(cause: Throwable? = null) : ArcileError(cause)
    class UnsupportedProvider(cause: Throwable? = null) : ArcileError(cause)
    class PartialSuccess(cause: Throwable? = null) : ArcileError(cause)
    class Cancelled(cause: Throwable? = null) : ArcileError(cause)
    class CorruptedMetadata(cause: Throwable? = null) : ArcileError(cause)
    class UnsafePath(cause: Throwable? = null) : ArcileError(cause)
    class Unknown(cause: Throwable? = null) : ArcileError(cause)
}

fun Throwable.toArcileError(): ArcileError = when (this) {
    is ArcileError -> this
    is FileOperationException.AccessDenied -> ArcileError.AccessDenied(this)
    is FileOperationException.NotFound -> ArcileError.StorageUnavailable(this)
    is FileOperationException.IOError -> ArcileError.StorageUnavailable(this)
    is SecurityException -> ArcileError.AccessDenied(this)
    is IOException -> ArcileError.StorageUnavailable(this)
    is IllegalArgumentException -> when {
        message.orEmpty().contains("unsafe", ignoreCase = true) -> ArcileError.UnsafePath(this)
        message.orEmpty().contains("conflict", ignoreCase = true) ||
            message.orEmpty().contains("already exists", ignoreCase = true) -> ArcileError.Conflict(this)
        else -> ArcileError.Unknown(this)
    }
    is kotlinx.coroutines.CancellationException -> ArcileError.Cancelled(this)
    else -> when {
        message.orEmpty().contains("space", ignoreCase = true) -> ArcileError.InsufficientSpace(this)
        message.orEmpty().contains("not supported", ignoreCase = true) -> ArcileError.UnsupportedProvider(this)
        message.orEmpty().contains("Moved ", ignoreCase = false) -> ArcileError.PartialSuccess(this)
        message.orEmpty().contains("metadata", ignoreCase = true) -> ArcileError.CorruptedMetadata(this)
        message.orEmpty().contains("Access denied", ignoreCase = true) -> ArcileError.AccessDenied(this)
        else -> ArcileError.Unknown(this)
    }
}
