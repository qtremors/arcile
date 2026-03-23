package dev.qtremors.arcile.presentation.ui.components
import dev.qtremors.arcile.R
import androidx.compose.ui.res.stringResource

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import dev.qtremors.arcile.domain.ConflictResolution
import dev.qtremors.arcile.domain.FileCategories
import dev.qtremors.arcile.domain.FileConflict
import dev.qtremors.arcile.domain.FileModel
import dev.qtremors.arcile.utils.formatFileSize
import java.io.File
import dev.qtremors.arcile.presentation.utils.rememberDateFormatter

/**
 * Full-screen dialog for resolving paste conflicts.
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
) {
    val resolutions = remember { mutableMapOf<String, ConflictResolution>() }
    val formatter = rememberDateFormatter("MMM dd, yyyy Â· HH:mm")
    
    var currentIndex by remember { mutableStateOf(0) }
    var applyToAll by remember { mutableStateOf(false) }

    if (currentIndex >= conflicts.size) {
        // Fallback in case state gets weird, though we normally call onResolve and close before this
        return
    }

    val currentConflict = conflicts[currentIndex]
    val isLastConflict = currentIndex == conflicts.size - 1

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Title
                Text(
                    text = if (currentConflict.sourceFile.isDirectory) stringResource(R.string.title_folder_conflict, currentIndex + 1, conflicts.size) else stringResource(R.string.title_file_conflict, currentIndex + 1, conflicts.size),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                ConflictCard(
                    conflict = currentConflict,
                    resolution = null,
                    formatter = formatter,
                    onResolutionChange = { }
                )

                if (!isLastConflict) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable { applyToAll = !applyToAll }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = applyToAll,
                            onCheckedChange = { applyToAll = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.action_apply_to_all),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val handleAction = { resolution: ConflictResolution ->
                        if (applyToAll) {
                            for (i in currentIndex until conflicts.size) {
                                resolutions[conflicts[i].sourcePath] = resolution
                            }
                            onResolve(resolutions)
                        } else {
                            resolutions[currentConflict.sourcePath] = resolution
                            if (isLastConflict) {
                                onResolve(resolutions)
                            } else {
                                currentIndex++
                            }
                        }
                    }

                    FilledTonalButton(
                        onClick = { handleAction(ConflictResolution.KEEP_BOTH) },
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.action_keep_both), maxLines = 1, overflow = TextOverflow.Ellipsis) }

                    FilledTonalButton(
                        onClick = { handleAction(ConflictResolution.REPLACE) },
                        modifier = Modifier.weight(1f)
                    ) { Text(if (currentConflict.sourceFile.isDirectory) stringResource(R.string.action_merge) else stringResource(R.string.action_replace), maxLines = 1, overflow = TextOverflow.Ellipsis) }

                    FilledTonalButton(
                        onClick = { handleAction(ConflictResolution.SKIP) },
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.action_skip), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.action_cancel_paste), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

