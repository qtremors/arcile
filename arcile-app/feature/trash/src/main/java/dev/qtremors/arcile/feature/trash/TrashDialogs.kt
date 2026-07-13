package dev.qtremors.arcile.feature.trash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.storage.domain.isIndexed
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.theme.ExpressiveShapes
import dev.qtremors.arcile.core.ui.theme.bounceClickable

@Composable
internal fun RestoreDestinationDialog(
    state: TrashState,
    onDismiss: () -> Unit,
    onRestore: (List<String>, String) -> Unit
) {
    val indexedVolumes = state.availableVolumes.filter { it.kind.isIndexed }
    if (indexedVolumes.isEmpty()) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.no_restore_destination_title)) },
            text = { Text(stringResource(R.string.no_restore_destination_description)) },
            confirmButton = {
                DialogTextButton(
                    text = stringResource(R.string.dismiss),
                    onClick = onDismiss
                )
            }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_restore_destination_title)) },
        text = {
            Column {
                Text(stringResource(R.string.select_restore_destination_description))
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn {
                    items(indexedVolumes) { volume ->
                        ListItem(
                            headlineContent = { Text(volume.name) },
                            supportingContent = {
                                Text(
                                    volume.path,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            modifier = Modifier
                                .clip(ExpressiveShapes.medium)
                                .bounceClickable {
                                    onRestore(state.selectedTrashIdsForDestination, volume.path)
                                    onDismiss()
                                },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
            }
        },
        confirmButton = {
            DialogTextButton(
                text = stringResource(R.string.cancel),
                onClick = onDismiss
            )
        }
    )
}

@Composable
internal fun TrashPropertiesDialog(
    properties: TrashPropertiesUiModel?,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.properties_title)) },
        text = {
            val model = properties ?: return@AlertDialog
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(model.title, style = MaterialTheme.typography.titleMedium)
                model.rows.forEach { (label, value) ->
                    Column {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = value,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        },
        confirmButton = {
            DialogTextButton(
                text = stringResource(R.string.ok),
                onClick = onDismiss
            )
        }
    )
}

@Composable
private fun DialogTextButton(
    text: String,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        shape = ExpressiveShapes.medium,
        modifier = Modifier.bounceClickable(onClick = onClick)
    ) {
        Text(text)
    }
}
