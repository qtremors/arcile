package dev.qtremors.arcile.feature.onlyfiles

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.storage.domain.SaveDestinationDirectory

internal enum class VaultFolderPickerMode { CREATE, ATTACH }

internal data class VaultFolderPickerState(
    val mode: VaultFolderPickerMode,
    val current: SaveDestinationDirectory? = null,
    val entries: List<SaveDestinationDirectory> = emptyList(),
    val isLoading: Boolean = false
)

@Composable
internal fun VaultFolderPickerDialog(
    state: VaultFolderPickerState,
    onOpen: (String) -> Unit,
    onUp: () -> Unit,
    onChoose: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.current != null) {
                    IconButton(onClick = onUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.onlyfiles_back))
                    }
                }
                Text(
                    state.current?.name ?: stringResource(R.string.onlyfiles_storage_locations),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.current?.let {
                    Text(it.path, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (state.isLoading) {
                    Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                        items(state.entries, key = { it.path }) { directory ->
                            Row(
                                Modifier.fillMaxWidth().clickable { onOpen(directory.path) }.padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Outlined.Folder, null)
                                Text(directory.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onChoose, enabled = state.current?.canSave == true && !state.isLoading) {
                Text(
                    stringResource(
                        if (state.mode == VaultFolderPickerMode.CREATE) {
                            R.string.onlyfiles_use_this_folder
                        } else {
                            R.string.onlyfiles_add_this_vault
                        }
                    )
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.onlyfiles_cancel)) } }
    )
}
