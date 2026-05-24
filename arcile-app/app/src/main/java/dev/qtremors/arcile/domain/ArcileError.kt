package dev.qtremors.arcile.domain

import dev.qtremors.arcile.R
import dev.qtremors.arcile.presentation.UiText
import java.io.IOException

sealed class ArcileError(
    val userMessage: UiText,
    val recoveryAction: UiText? = null,
    cause: Throwable? = null
) : Exception(cause) {
    class AccessDenied(cause: Throwable? = null) : ArcileError(
        UiText.StringResource(R.string.error_access_denied),
        UiText.StringResource(R.string.error_recovery_check_permissions),
        cause
    )
    class StorageUnavailable(cause: Throwable? = null) : ArcileError(
        UiText.StringResource(R.string.error_storage_unavailable),
        UiText.StringResource(R.string.error_recovery_reconnect_storage),
        cause
    )
    class Conflict(cause: Throwable? = null) : ArcileError(
        UiText.StringResource(R.string.error_conflict),
        UiText.StringResource(R.string.error_recovery_choose_different_name),
        cause
    )
    class InsufficientSpace(cause: Throwable? = null) : ArcileError(
        UiText.StringResource(R.string.error_insufficient_space),
        UiText.StringResource(R.string.error_recovery_free_space),
        cause
    )
    class UnsupportedProvider(cause: Throwable? = null) : ArcileError(
        UiText.StringResource(R.string.error_unsupported_provider),
        UiText.StringResource(R.string.error_recovery_use_permanent_delete),
        cause
    )
    class PartialSuccess(cause: Throwable? = null) : ArcileError(
        UiText.StringResource(R.string.error_partial_success),
        UiText.StringResource(R.string.error_recovery_review_files),
        cause
    )
    class Cancelled(cause: Throwable? = null) : ArcileError(
        UiText.StringResource(R.string.error_operation_cancelled_safe),
        cause = cause
    )
    class CorruptedMetadata(cause: Throwable? = null) : ArcileError(
        UiText.StringResource(R.string.error_corrupted_metadata),
        UiText.StringResource(R.string.error_recovery_choose_destination),
        cause
    )
    class UnsafePath(cause: Throwable? = null) : ArcileError(
        UiText.StringResource(R.string.error_unsafe_path),
        UiText.StringResource(R.string.error_recovery_choose_safe_path),
        cause
    )
    class Unknown(cause: Throwable? = null) : ArcileError(
        UiText.StringResource(R.string.error_file_operation_failed),
        UiText.StringResource(R.string.error_recovery_try_again),
        cause
    )
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

