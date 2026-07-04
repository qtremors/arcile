package dev.qtremors.arcile.core.presentation

import dev.qtremors.arcile.core.runtime.R
import dev.qtremors.arcile.core.storage.domain.ArcileError

val ArcileError.userMessage: UiText
    get() = when (this) {
        is ArcileError.AccessDenied -> UiText.StringResource(R.string.error_access_denied)
        is ArcileError.StorageUnavailable -> UiText.StringResource(R.string.error_storage_unavailable)
        is ArcileError.Conflict -> UiText.StringResource(R.string.error_conflict)
        is ArcileError.InsufficientSpace -> UiText.StringResource(R.string.error_insufficient_space)
        is ArcileError.UnsupportedProvider -> UiText.StringResource(R.string.error_unsupported_provider)
        is ArcileError.PartialSuccess -> UiText.StringResource(R.string.error_partial_success)
        is ArcileError.Cancelled -> UiText.StringResource(R.string.error_operation_cancelled_safe)
        is ArcileError.CorruptedMetadata -> UiText.StringResource(R.string.error_corrupted_metadata)
        is ArcileError.UnsafePath -> UiText.StringResource(R.string.error_unsafe_path)
        is ArcileError.Unknown -> UiText.StringResource(R.string.error_file_operation_failed)
    }

val ArcileError.recoveryAction: UiText?
    get() = when (this) {
        is ArcileError.AccessDenied -> UiText.StringResource(R.string.error_recovery_check_permissions)
        is ArcileError.StorageUnavailable -> UiText.StringResource(R.string.error_recovery_reconnect_storage)
        is ArcileError.Conflict -> UiText.StringResource(R.string.error_recovery_choose_different_name)
        is ArcileError.InsufficientSpace -> UiText.StringResource(R.string.error_recovery_free_space)
        is ArcileError.UnsupportedProvider -> UiText.StringResource(R.string.error_recovery_use_permanent_delete)
        is ArcileError.PartialSuccess -> UiText.StringResource(R.string.error_recovery_review_files)
        is ArcileError.Cancelled -> null
        is ArcileError.CorruptedMetadata -> UiText.StringResource(R.string.error_recovery_choose_destination)
        is ArcileError.UnsafePath -> UiText.StringResource(R.string.error_recovery_choose_safe_path)
        is ArcileError.Unknown -> UiText.StringResource(R.string.error_recovery_try_again)
    }
