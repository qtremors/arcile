package dev.qtremors.arcile.feature.onlyfiles

import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.core.storage.domain.ClipboardOperation
import dev.qtremors.arcile.core.ui.ArcileFeedbackSeverity
import dev.qtremors.arcile.core.ui.clipboardStoredFeedback
import dev.qtremors.arcile.core.vault.domain.DirectoryId
import dev.qtremors.arcile.core.vault.domain.VaultBatchResult
import dev.qtremors.arcile.core.vault.domain.VaultFailure
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultItemOutcome
import dev.qtremors.arcile.core.vault.domain.VaultItemResult
import org.junit.Assert.*
import org.junit.Test
import dev.qtremors.arcile.core.ui.R as UiR

class OnlyFilesOperationFeedbackTest {
    private val target = OnlyFilesOperationDestination.VaultDirectory(VaultId.of("vault"), DirectoryId.of("target"))

    @Test
    fun `clipboard feedback uses exactly the Browser copy and cut wording`() {
        for ((vaultAction, browserAction) in listOf(
            VaultClipboardAction.COPY to ClipboardOperation.COPY,
            VaultClipboardAction.MOVE to ClipboardOperation.CUT
        )) {
            for (count in listOf(1, 2, 1500)) {
                assertEquals(clipboardStoredFeedback(browserAction, count).message, clipboardFeedback(vaultAction, count, 8).message)
            }
        }
    }

    @Test
    fun `complete batches reuse Browser success plurals where the operation is equivalent`() {
        val plurals = mapOf(
            OnlyFilesOperationKind.COPY to UiR.plurals.file_operation_copied_items,
            OnlyFilesOperationKind.MOVE to UiR.plurals.file_operation_moved_items,
            OnlyFilesOperationKind.IMPORT to UiR.plurals.file_operation_copied_items,
            OnlyFilesOperationKind.MOVE_IN to UiR.plurals.file_operation_moved_items,
            OnlyFilesOperationKind.MOVE_OUT to UiR.plurals.file_operation_moved_items,
            OnlyFilesOperationKind.TRASH to UiR.plurals.file_operation_trashed_items,
            OnlyFilesOperationKind.EXPORT to R.plurals.onlyfiles_exported_items,
            OnlyFilesOperationKind.RECOVERY to R.plurals.onlyfiles_recovered_items
        )
        for ((kind, plural) in plurals) {
            val feedback = vaultBatchFeedback(batch(VaultItemOutcome.COMPLETED), kind, 8, target)
            assertEquals(UiText.PluralResource(plural, 1, listOf(1)), feedback.message)
            assertEquals(ArcileFeedbackSeverity.Success, feedback.severity)
            assertEquals(target, feedback.destination)
        }
    }

    @Test
    fun `mixed results preserve all outcome counts including rolled back writes`() {
        val resources = mapOf(
            OnlyFilesOperationKind.COPY to R.string.onlyfiles_copy_result,
            OnlyFilesOperationKind.MOVE to R.string.onlyfiles_move_result,
            OnlyFilesOperationKind.IMPORT to R.string.onlyfiles_import_result,
            OnlyFilesOperationKind.MOVE_IN to R.string.onlyfiles_move_in_result,
            OnlyFilesOperationKind.MOVE_OUT to R.string.onlyfiles_move_out_result,
            OnlyFilesOperationKind.TRASH to R.string.onlyfiles_trash_result,
            OnlyFilesOperationKind.EXPORT to R.string.onlyfiles_export_result,
            OnlyFilesOperationKind.RECOVERY to R.string.onlyfiles_recovery_result
        )
        val batch = batch(*VaultItemOutcome.entries.toTypedArray())
        for ((kind, resource) in resources) {
            val feedback = vaultBatchFeedback(batch, kind, 8, target)
            assertEquals(UiText.StringResource(resource, listOf(1, 1, 1, 2)), feedback.message)
            assertEquals(ArcileFeedbackSeverity.Info, feedback.severity)
            assertEquals(target, feedback.destination)
        }
    }

    @Test
    fun `failed and rolled back operations cannot advertise a destination`() {
        for (outcome in listOf(VaultItemOutcome.FAILED, VaultItemOutcome.ROLLED_BACK)) {
            val feedback = vaultBatchFeedback(batch(outcome), OnlyFilesOperationKind.MOVE, 8, target)
            assertEquals(ArcileFeedbackSeverity.Error, feedback.severity)
            assertNull(feedback.destination)
            assertNull(feedback.event({}, {}).actionLabel)
            assertNull(feedback.event({}, {}).onAction)
        }
    }

    @Test
    fun `skipped and empty queues never announce success or offer destination reveal`() {
        for (result in listOf(batch(VaultItemOutcome.SKIPPED), VaultBatchResult(emptyList()))) {
            val feedback = vaultBatchFeedback(result, OnlyFilesOperationKind.COPY, 8, target)
            assertEquals(ArcileFeedbackSeverity.Info, feedback.severity)
            assertNull(feedback.destination)
        }
    }

    @Test
    fun `partial copies still offer inspection of the published destination`() {
        val feedback = vaultBatchFeedback(batch(VaultItemOutcome.PARTIAL), OnlyFilesOperationKind.EXPORT, 8, target)
        assertEquals(target, feedback.destination)
        assertEquals(ArcileFeedbackSeverity.Info, feedback.severity)
        assertEquals(UiText.StringResource(R.string.onlyfiles_show_destination), feedback.event({}, {}).actionLabel)
    }

    @Test
    fun `result presentation does not expose source names or exception text`() {
        val result = VaultBatchResult(listOf(VaultItemResult(
            "secret-source", "sensitive-name.txt", VaultItemOutcome.PARTIAL,
            VaultFailure.Unavailable("secret internal storage path")
        )))
        val feedback = vaultBatchFeedback(result, OnlyFilesOperationKind.MOVE_IN, 8, target)
        val message = feedback.message as UiText.StringResource
        assertEquals(listOf(0, 1, 0, 0), message.args)
        assertFalse(message.toString().contains("secret"))
        assertFalse(message.toString().contains("sensitive"))
    }

    @Test
    fun `completion expires after locking and reopening even the same vault`() {
        val feedback = vaultBatchFeedback(batch(VaultItemOutcome.COMPLETED), OnlyFilesOperationKind.COPY, 8, target)
        assertTrue(feedback.belongsTo(OnlyFilesUiState(selectedVaultId = target.vaultId, mountGeneration = 8)))
        assertFalse(feedback.belongsTo(OnlyFilesUiState(mountGeneration = 9)))
        assertFalse(feedback.belongsTo(OnlyFilesUiState(selectedVaultId = target.vaultId, mountGeneration = 10)))
    }

    @Test
    fun `each completion has an independent identity and dispatches its own callbacks`() {
        var reveals = 0
        var dismissals = 0
        val first = vaultBatchFeedback(batch(VaultItemOutcome.COMPLETED), OnlyFilesOperationKind.COPY, 8, target)
        val second = vaultBatchFeedback(batch(VaultItemOutcome.COMPLETED), OnlyFilesOperationKind.COPY, 8, target)
        assertNotEquals(first.id, second.id)
        val event = first.event({ reveals++ }, { dismissals++ })
        assertEquals(first.id, event.id)
        event.onAction?.invoke()
        assertEquals(1, reveals)
        assertEquals(0, dismissals)
        event.onDismiss?.invoke()
        assertEquals(1, dismissals)
    }

    @Test
    fun `typed failures provide localizable recovery guidance without private error text`() {
        val cases = listOf(
            VaultFailure.Locked(VaultId.of("secret-vault")) to R.string.onlyfiles_operation_locked,
            VaultFailure.RemovableStorageMissing("secret-volume") to R.string.onlyfiles_operation_storage_missing,
            VaultFailure.NotFound(VaultId.of("secret-vault")) to R.string.onlyfiles_operation_storage_missing,
            VaultFailure.NodeNotFound(dev.qtremors.arcile.core.vault.domain.NodeId.of("secret-node")) to R.string.onlyfiles_operation_item_missing,
            VaultFailure.InvalidPath("secret-path") to R.string.onlyfiles_operation_item_missing,
            VaultFailure.NameConflict("secret-file") to R.string.onlyfiles_operation_conflict,
            VaultFailure.PathConflict(dev.qtremors.arcile.core.vault.domain.VaultPath.of("secret-file")) to R.string.onlyfiles_operation_conflict,
            VaultFailure.SourceChanged() to R.string.onlyfiles_operation_changed,
            VaultFailure.ConcurrentMutation() to R.string.onlyfiles_operation_changed,
            VaultFailure.IntegrityFailed("secret-data") to R.string.onlyfiles_operation_integrity,
            VaultFailure.OperationInProgress() to R.string.onlyfiles_import_running,
            VaultFailure.Cancelled() to R.string.onlyfiles_operation_cancelled
        )
        for ((failure, resource) in cases) {
            val feedback = vaultOperationFailure(failure, R.string.onlyfiles_paste_failed, 8)
            assertEquals(UiText.StringResource(resource), feedback.message)
            assertNull(feedback.destination)
            assertFalse(feedback.message.toString().contains("secret"))
            assertEquals(
                if (failure is VaultFailure.Cancelled) ArcileFeedbackSeverity.Info else ArcileFeedbackSeverity.Error,
                feedback.severity
            )
        }
    }

    @Test
    fun `insufficient space reports exact sizes using resource arguments`() {
        assertEquals(
            UiText.StringResource(R.string.onlyfiles_operation_space, listOf(4096L, 1024L)),
            vaultOperationErrorText(VaultFailure.InsufficientSpace(4096, 1024), R.string.onlyfiles_export_failed)
        )
    }

    @Test
    fun `unknown backend errors use the operation-specific fallback`() {
        assertEquals(
            UiText.StringResource(R.string.onlyfiles_export_failed),
            vaultOperationErrorText(IllegalStateException("private path and details"), R.string.onlyfiles_export_failed)
        )
        assertEquals(
            UiText.StringResource(R.string.onlyfiles_paste_failed),
            vaultOperationErrorText(VaultFailure.Unavailable("private path"), R.string.onlyfiles_paste_failed)
        )
    }

    private fun batch(vararg outcomes: VaultItemOutcome) = VaultBatchResult(outcomes.mapIndexed { index, outcome ->
        VaultItemResult("source-$index", "Private $index", outcome)
    })
}
