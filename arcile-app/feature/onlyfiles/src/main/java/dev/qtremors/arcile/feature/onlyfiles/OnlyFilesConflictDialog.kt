package dev.qtremors.arcile.feature.onlyfiles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import dev.qtremors.arcile.core.storage.domain.FileConflict
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.ui.FileConflictReviewAction
import dev.qtremors.arcile.core.ui.FileConflictReviewDialog
import dev.qtremors.arcile.core.vault.domain.VaultConflictDecision
import dev.qtremors.arcile.core.ui.R as UiR

@Composable
internal fun VaultConflictDialog(
    prompt: VaultConflictPrompt,
    onDecision: (Long, VaultConflictResponse) -> Unit,
    onCancel: () -> Unit
) = key(prompt.requestId) {
    val conflicts = remember(prompt) {
        prompt.conflicts.mapIndexed { index, conflict ->
            val id = index.toString()
            FileConflict(
                sourcePath = id,
                sourceFile = FileModel(
                    conflict.sourceName, "incoming/$id", conflict.sourceSize,
                    conflict.sourceModifiedAt, conflict.sourceIsDirectory
                ),
                existingFile = FileModel(
                    conflict.destinationName, "existing/$id", conflict.destinationSize,
                    conflict.destinationModifiedAt, conflict.destinationIsDirectory
                )
            )
        }
    }
    val future = remember { mutableStateOf<VaultConflictDecision?>(null) }
    FileConflictReviewDialog(
        conflicts = conflicts,
        actions = { conflict -> vaultConflictActions(conflict.sourceFile.isDirectory && conflict.existingFile.isDirectory) },
        onResolve = { decisions ->
            onDecision(prompt.requestId, VaultConflictResponse(decisions.mapKeys { it.key.toInt() }, future.value))
        },
        onDismiss = onCancel,
        onApplyToFuture = if (prompt.allowFuture) ({ decision -> future.value = decision }) else null,
        canApplyTogether = { current, candidate, decision ->
            decision.canApplyTo(
                prompt.conflicts[current.sourcePath.toInt()],
                prompt.conflicts[candidate.sourcePath.toInt()]
            )
        },
        cancelLabel = if (prompt.exporting) UiR.string.action_cancel_transfer else UiR.string.action_cancel_paste
    )
}

private fun vaultConflictActions(canMerge: Boolean): List<FileConflictReviewAction<VaultConflictDecision>> = buildList {
    add(
        if (canMerge) FileConflictReviewAction(VaultConflictDecision.MERGE_DIRECTORIES, UiR.string.action_merge)
        else FileConflictReviewAction(VaultConflictDecision.REPLACE, UiR.string.action_replace)
    )
    add(FileConflictReviewAction(VaultConflictDecision.KEEP_BOTH, UiR.string.action_keep_both))
    add(FileConflictReviewAction(VaultConflictDecision.SKIP, UiR.string.action_skip))
    if (canMerge) add(
        FileConflictReviewAction(VaultConflictDecision.REPLACE, R.string.onlyfiles_replace_folder, secondary = true)
    )
}
