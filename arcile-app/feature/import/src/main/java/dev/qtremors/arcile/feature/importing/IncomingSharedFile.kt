package dev.qtremors.arcile.feature.importing

import android.content.Context
import android.net.Uri
import dev.qtremors.arcile.core.ui.R

internal data class IncomingSharedFile(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long? = null,
    val originalName: String? = null,
    val requiresCountedStream: Boolean = false
)

internal data class IncomingSharePreflightResult(
    val accepted: List<IncomingSharedFile>,
    val rejected: List<IncomingShareFailure>,
    val limitExceeded: Boolean = false
) {
    fun messageOrDefault(defaultMessage: String): String =
        rejected.firstOrNull()?.message ?: defaultMessage
}

internal data class IncomingShareFailure(
    val uri: Uri?,
    val displayName: String?,
    val reason: IncomingShareFailureReason,
    val message: String
)

internal enum class IncomingShareFailureReason {
    UnsupportedScheme,
    ExternalFileUri,
    InvalidName,
    TooManyItems,
    TooLarge,
    CopyFailed
}

internal data class SaveIncomingResult(
    val savedCount: Int,
    val failures: List<IncomingShareFailure>,
    val queued: Boolean = false
) {
    fun userMessage(context: Context): String = when {
        queued -> context.getString(R.string.save_to_arcile_import_started)
        savedCount > 0 && failures.isEmpty() -> context.resources.getQuantityString(
            R.plurals.save_to_arcile_saved_files,
            savedCount,
            savedCount
        )
        savedCount > 0 -> context.getString(
            R.string.save_to_arcile_partial_saved,
            savedCount,
            failures.size
        )
        else -> context.getString(
            R.string.save_to_arcile_failed,
            failures.firstOrNull()?.message.orEmpty()
        )
    }
}
