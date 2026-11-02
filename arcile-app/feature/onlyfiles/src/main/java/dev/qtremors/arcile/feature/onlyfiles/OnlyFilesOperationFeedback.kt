package dev.qtremors.arcile.feature.onlyfiles

import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.core.storage.domain.ClipboardOperation
import dev.qtremors.arcile.core.ui.ArcileFeedbackEvent
import dev.qtremors.arcile.core.ui.ArcileFeedbackSeverity
import dev.qtremors.arcile.core.ui.clipboardStoredFeedback
import dev.qtremors.arcile.core.vault.domain.DirectoryId
import dev.qtremors.arcile.core.vault.domain.VaultBatchResult
import dev.qtremors.arcile.core.vault.domain.VaultFailure
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import dev.qtremors.arcile.core.vault.domain.VaultRenameUndoEntry
import dev.qtremors.arcile.core.vault.domain.VaultMoveUndo
import dev.qtremors.arcile.core.vault.domain.VaultUndoResult
import java.util.UUID
import dev.qtremors.arcile.core.ui.R as UiR

internal sealed interface OnlyFilesOperationDestination {
    data class VaultDirectory(val vaultId: VaultId, val directoryId: DirectoryId) : OnlyFilesOperationDestination
    data class LocalDirectory(val path: String) : OnlyFilesOperationDestination
    data object MainTrash : OnlyFilesOperationDestination
}

internal enum class OnlyFilesOperationKind { COPY, MOVE, IMPORT, MOVE_IN, EXPORT, MOVE_OUT, TRASH, RECOVERY }

internal sealed interface OnlyFilesUndoAction {
    data class Created(val expected: VaultNodeMetadata) : OnlyFilesUndoAction
    data class Renamed(val entries: List<VaultRenameUndoEntry>) : OnlyFilesUndoAction
    data class Moved(val proofs: List<VaultMoveUndo>) : OnlyFilesUndoAction
}

/** All destinations and callbacks expire with the originating unlocked mount. Never persist this. */
internal data class OnlyFilesOperationFeedback(
    val message: UiText,
    val severity: ArcileFeedbackSeverity,
    val mountGeneration: Long,
    val destination: OnlyFilesOperationDestination? = null,
    val id: String = UUID.randomUUID().toString(),
    val undo: OnlyFilesUndoAction? = null,
    val undoResult: VaultUndoResult? = null
) {
    fun belongsTo(state: OnlyFilesUiState): Boolean = mountGeneration == state.mountGeneration

    fun event(onReveal: () -> Unit, onDismiss: () -> Unit, onUndo: () -> Unit = {}): ArcileFeedbackEvent = ArcileFeedbackEvent(
        message = message,
        severity = severity,
        actionLabel = if (undo != null) UiText.StringResource(UiR.string.undo)
            else destination?.let { UiText.StringResource(R.string.onlyfiles_show_destination) },
        onAction = if (undo != null) onUndo else onReveal.takeIf { destination != null },
        onDismiss = onDismiss,
        id = id
    )
}

internal fun clipboardFeedback(action: VaultClipboardAction, count: Int, mount: Long): OnlyFilesOperationFeedback {
    val shared = clipboardStoredFeedback(
        if (action == VaultClipboardAction.MOVE) ClipboardOperation.CUT else ClipboardOperation.COPY, count
    )
    return OnlyFilesOperationFeedback(shared.message, shared.severity, mount)
}

internal fun vaultArchiveFeedback(
    result: VaultBatchResult,
    mount: Long,
    destination: OnlyFilesOperationDestination.VaultDirectory
): OnlyFilesOperationFeedback {
    val completed = result.completed.size
    val partial = result.partial.size
    val skipped = result.skipped.size
    val failed = result.failed.size + result.rolledBack.size
    val allCompleted = result.items.isNotEmpty() && completed == result.items.size
    return OnlyFilesOperationFeedback(
        message = if (allCompleted) UiText.StringResource(R.string.onlyfiles_zip_extracted)
            else UiText.StringResource(R.string.onlyfiles_extract_result, listOf(completed, partial, skipped, failed)),
        severity = if (allCompleted) ArcileFeedbackSeverity.Success else if (failed > 0 && completed == 0 && partial == 0)
            ArcileFeedbackSeverity.Error else ArcileFeedbackSeverity.Info,
        mountGeneration = mount,
        destination = destination.takeIf { completed > 0 || partial > 0 }
    )
}

internal fun vaultBatchFeedback(
    result: VaultBatchResult,
    kind: OnlyFilesOperationKind,
    mount: Long,
    destination: OnlyFilesOperationDestination? = null
): OnlyFilesOperationFeedback {
    val completed = result.completed.size
    val partial = result.partial.size
    val skipped = result.skipped.size
    val failed = result.failed.size + result.rolledBack.size
    val allCompleted = result.items.isNotEmpty() && completed == result.items.size
    val successPlural = when (kind) {
        OnlyFilesOperationKind.COPY, OnlyFilesOperationKind.IMPORT -> UiR.plurals.file_operation_copied_items
        OnlyFilesOperationKind.MOVE, OnlyFilesOperationKind.MOVE_IN, OnlyFilesOperationKind.MOVE_OUT -> UiR.plurals.file_operation_moved_items
        OnlyFilesOperationKind.TRASH -> UiR.plurals.file_operation_trashed_items
        OnlyFilesOperationKind.EXPORT -> R.plurals.onlyfiles_exported_items
        OnlyFilesOperationKind.RECOVERY -> R.plurals.onlyfiles_recovered_items
    }
    val summary = when (kind) {
        OnlyFilesOperationKind.COPY -> R.string.onlyfiles_copy_result
        OnlyFilesOperationKind.MOVE -> R.string.onlyfiles_move_result
        OnlyFilesOperationKind.IMPORT -> R.string.onlyfiles_import_result
        OnlyFilesOperationKind.MOVE_IN -> R.string.onlyfiles_move_in_result
        OnlyFilesOperationKind.EXPORT -> R.string.onlyfiles_export_result
        OnlyFilesOperationKind.MOVE_OUT -> R.string.onlyfiles_move_out_result
        OnlyFilesOperationKind.TRASH -> R.string.onlyfiles_trash_result
        OnlyFilesOperationKind.RECOVERY -> R.string.onlyfiles_recovery_result
    }
    return OnlyFilesOperationFeedback(
        message = if (allCompleted) UiText.PluralResource(successPlural, completed, listOf(completed))
        else UiText.StringResource(summary, listOf(completed, partial, skipped, failed)),
        severity = when {
            allCompleted -> ArcileFeedbackSeverity.Success
            failed > 0 && completed == 0 && partial == 0 -> ArcileFeedbackSeverity.Error
            else -> ArcileFeedbackSeverity.Info
        },
        mountGeneration = mount,
        destination = destination.takeIf { completed > 0 || partial > 0 }
    )
}

internal fun operationNotice(resource: Int, mount: Long, error: Boolean = false) = OnlyFilesOperationFeedback(
    UiText.StringResource(resource),
    if (error) ArcileFeedbackSeverity.Error else ArcileFeedbackSeverity.Info,
    mount
)

/** Preserve actionable failure categories without displaying backend paths, identifiers, or raw English. */
internal fun vaultOperationErrorText(error: Throwable, fallback: Int): UiText = when (error) {
    is VaultFailure.InsufficientSpace -> UiText.StringResource(
        R.string.onlyfiles_operation_space, listOf(error.requiredBytes, error.availableBytes)
    )
    else -> UiText.StringResource(when (error) {
        is VaultFailure.AuthenticationFailed -> R.string.onlyfiles_error_password
        is VaultFailure.WeakPasswordConfirmationRequired -> R.string.onlyfiles_error_weak_password
        is VaultFailure.BiometricInvalidated -> R.string.onlyfiles_error_biometric
        is VaultFailure.UnsupportedFormat -> R.string.onlyfiles_error_format
        is VaultFailure.UnsafeKdfParameters -> R.string.onlyfiles_error_kdf
        is VaultFailure.StaleRegistration -> R.string.onlyfiles_error_registration
        is VaultFailure.DuplicateVault -> R.string.onlyfiles_error_duplicate
        is VaultFailure.FolderNotEmpty -> R.string.onlyfiles_error_folder_not_empty
        is VaultFailure.InvalidName -> R.string.onlyfiles_error_name
        is VaultFailure.SymlinkRejected -> R.string.onlyfiles_error_symlink
        is VaultFailure.TransactionRecoveryFailed -> R.string.onlyfiles_error_recovery
        is VaultFailure.FileTooLarge -> R.string.onlyfiles_error_file_too_large
        is VaultFailure.ExternalGrantExpired -> R.string.onlyfiles_error_grant_expired
        is VaultFailure.ExternalGrantConsumerMismatch -> R.string.onlyfiles_error_grant_consumer
        is VaultFailure.DestructiveConfirmationRequired -> R.string.onlyfiles_error_confirmation
        is VaultFailure.Locked -> R.string.onlyfiles_operation_locked
        is VaultFailure.RemovableStorageMissing, is VaultFailure.NotFound -> R.string.onlyfiles_operation_storage_missing
        is VaultFailure.NodeNotFound, is VaultFailure.InvalidPath -> R.string.onlyfiles_operation_item_missing
        is VaultFailure.NameConflict, is VaultFailure.PathConflict -> R.string.onlyfiles_operation_conflict
        is VaultFailure.SourceChanged, is VaultFailure.ConcurrentMutation -> R.string.onlyfiles_operation_changed
        is VaultFailure.IntegrityFailed, is VaultFailure.ObjectReplaced -> R.string.onlyfiles_operation_integrity
        is VaultFailure.OperationInProgress -> R.string.onlyfiles_import_running
        is VaultFailure.Cancelled -> R.string.onlyfiles_operation_cancelled
        else -> fallback
    })
}

internal fun vaultOperationFailure(error: Throwable, fallback: Int, mount: Long) = OnlyFilesOperationFeedback(
    vaultOperationErrorText(error, fallback),
    if (error is VaultFailure.Cancelled) ArcileFeedbackSeverity.Info else ArcileFeedbackSeverity.Error,
    mount
)

internal fun operationSuccess(resource: Int, mount: Long, args: List<Any> = emptyList()) = OnlyFilesOperationFeedback(
    UiText.StringResource(resource, args), ArcileFeedbackSeverity.Success, mount
)
