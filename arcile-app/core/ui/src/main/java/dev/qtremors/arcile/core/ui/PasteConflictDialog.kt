package dev.qtremors.arcile.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.qtremors.arcile.core.presentation.formatFileSize
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.FileConflict
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.rememberArcileHaptics
import dev.qtremors.arcile.core.ui.rememberDateFormatter
import dev.qtremors.arcile.core.ui.theme.ExpressiveShapes
import dev.qtremors.arcile.core.ui.theme.bounceClickable

private val ConflictDialogContentPadding = 24.dp
private val ConflictDialogMaxWidth = ConflictWideLayoutMinWidth + ConflictDialogContentPadding * 2

/**
 * Dialog for resolving paste conflicts.
 *
 * Shows each conflicting file with side-by-side metadata for the incoming and existing
 * files, allowing per-file resolution (Keep Both, Replace, Skip) or a batch "Apply to All" checkbox.
 *
 * @param conflicts List of detected file conflicts to resolve.
 * @param onResolve Called with the final per-file resolutions map when the user confirms.
 * @param onDismiss Called when the user cancels the paste operation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasteConflictDialog(
    conflicts: List<FileConflict>,
    onResolve: (Map<String, ConflictResolution>) -> Unit,
    onDismiss: () -> Unit
) = FileConflictReviewDialog(
    conflicts = conflicts,
    actions = {
        listOf(
            FileConflictReviewAction(
                ConflictResolution.REPLACE,
                if (it.sourceFile.isDirectory) R.string.action_merge else R.string.action_replace
            ),
            FileConflictReviewAction(ConflictResolution.KEEP_BOTH, R.string.action_keep_both),
            FileConflictReviewAction(ConflictResolution.SKIP, R.string.action_skip)
        )
    },
    onResolve = onResolve,
    onDismiss = onDismiss
)

data class FileConflictReviewAction<D>(val decision: D, val label: Int, val secondary: Boolean = false)

@Composable
fun <D : Any> FileConflictReviewDialog(
    conflicts: List<FileConflict>,
    actions: (FileConflict) -> List<FileConflictReviewAction<D>>,
    onResolve: (Map<String, D>) -> Unit,
    onDismiss: () -> Unit,
    onApplyToFuture: ((D) -> Unit)? = null,
    canApplyTogether: (FileConflict, FileConflict, D) -> Boolean = { _, _, _ -> true },
    cancelLabel: Int = R.string.action_cancel_paste
) {
    val haptics = rememberArcileHaptics()
    val resolutions = remember(conflicts) { mutableMapOf<String, D>() }
    val formatter = rememberDateFormatter("MMM dd, yyyy · HH:mm")

    var currentIndex by remember(conflicts) { mutableIntStateOf(0) }
    var applyToAll by remember(conflicts) { mutableStateOf(false) }

    if (currentIndex >= conflicts.size) {
        return
    }

    val currentConflict = conflicts[currentIndex]
    val isLastConflict = currentIndex == conflicts.size - 1
    val availableActions = actions(currentConflict)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = ConflictDialogMaxWidth),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(ConflictDialogContentPadding)
                ) {
                    // Fixed Header: Title and Close action
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (currentConflict.sourceFile.isDirectory) {
                                stringResource(R.string.title_folder_conflict, currentIndex + 1, conflicts.size)
                            } else {
                                stringResource(R.string.title_file_conflict, currentIndex + 1, conflicts.size)
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        val onDismissClick = {
                            haptics.selectionChanged()
                            onDismiss()
                        }
                        IconButton(
                            onClick = onDismissClick,
                            modifier = Modifier.clip(CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.action_close)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Scrollable comparison content
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                    ) {
                        ConflictCard(
                            conflict = currentConflict,
                            resolution = null,
                            formatter = formatter,
                            onResolutionChange = { }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Fixed Footer
                    if (!isLastConflict || onApplyToFuture != null) {
                        val toggleApplyToAllClick = {
                            haptics.selectionChanged()
                            applyToAll = !applyToAll
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .bounceClickable(onClick = toggleApplyToAllClick)
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = applyToAll,
                                onCheckedChange = {
                                    haptics.selectionChanged()
                                    applyToAll = it
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.action_apply_to_all),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Action Buttons in fixed footer
                    val handleAction: (D) -> Unit = { resolution ->
                        haptics.selectionChanged()
                        resolutions[currentConflict.sourcePath] = resolution
                        if (applyToAll) {
                            onApplyToFuture?.invoke(resolution)
                            for (candidate in conflicts.drop(currentIndex + 1)) {
                                if (canApplyTogether(currentConflict, candidate, resolution) &&
                                    actions(candidate).any { it.decision == resolution }
                                ) {
                                    resolutions[candidate.sourcePath] = resolution
                                }
                            }
                        }
                        val next = (currentIndex + 1 until conflicts.size).firstOrNull {
                            conflicts[it].sourcePath !in resolutions
                        }
                        if (next == null) onResolve(resolutions.toMap())
                        else {
                            currentIndex = next
                            applyToAll = false
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        availableActions.filterNot { it.secondary }.forEachIndexed { index, action ->
                            ConflictDecisionButton(
                                index, action.label, Modifier.weight(1f)
                            ) { handleAction(action.decision) }
                        }
                    }
                    availableActions.filter { it.secondary }.forEach { action ->
                        TextButton(
                            onClick = { handleAction(action.decision) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(action.label), color = MaterialTheme.colorScheme.error)
                        }
                    }

                    val onCancelPasteClick = {
                        haptics.selectionChanged()
                        onDismiss()
                    }
                    TextButton(
                        onClick = onCancelPasteClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .bounceClickable(onClick = onCancelPasteClick)
                    ) {
                        Text(stringResource(cancelLabel), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConflictDecisionButton(index: Int, label: Int, modifier: Modifier, onClick: () -> Unit) {
    val padding = PaddingValues(horizontal = 4.dp, vertical = 10.dp)
    val content: @Composable () -> Unit = {
        Text(stringResource(label), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    when (index) {
        0 -> Button(onClick, modifier, shape = ExpressiveShapes.medium, contentPadding = padding) { content() }
        1 -> FilledTonalButton(onClick, modifier, shape = ExpressiveShapes.medium, contentPadding = padding) { content() }
        else -> OutlinedButton(onClick, modifier, shape = ExpressiveShapes.medium, contentPadding = padding) { content() }
    }
}
