package dev.qtremors.arcile.presentation.ui.components.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.R
import dev.qtremors.arcile.domain.PropertiesAccessStatus
import dev.qtremors.arcile.presentation.browser.PropertiesUiModel
import dev.qtremors.arcile.utils.formatFileSize

@Composable
fun PropertiesDialog(
    properties: PropertiesUiModel?,
    isLoading: Boolean,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ok))
            }
        },
        title = {
            Text(stringResource(R.string.properties_title))
        },
        text = {
            if (isLoading && properties == null) {
                Text(stringResource(R.string.properties_loading))
            } else {
                val model = properties ?: return@AlertDialog
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PropertiesRow(stringResource(R.string.properties_name), model.title)
                    PropertiesRow(stringResource(R.string.properties_items), model.itemCount.toString())
                    PropertiesRow(stringResource(R.string.properties_files), model.fileCount.toString())
                    PropertiesRow(stringResource(R.string.properties_folders), model.folderCount.toString())
                    PropertiesRow(stringResource(R.string.properties_size), formatFileSize(model.totalBytes))

                    if (model.isSingleItem && model.isDirectory == true && model.folderFileCount != null && model.folderTotalBytes != null) {
                        PropertiesRow(
                            stringResource(R.string.properties_contains),
                            stringResource(
                                R.string.properties_contains_value,
                                model.folderFileCount.toInt(),
                                formatFileSize(model.folderTotalBytes)
                            )
                        )
                    }
                    model.archiveSummary?.let { archive ->
                        PropertiesRow("Archive format", archive.format.displayName)
                        PropertiesRow("Archive entries", archive.entryCount.toString())
                        PropertiesRow("Archive files", archive.fileCount.toString())
                        PropertiesRow("Archive folders", archive.folderCount.toString())
                        PropertiesRow("Uncompressed size", formatFileSize(archive.totalUncompressedSize))
                        archive.compressionRatio?.let {
                            PropertiesRow("Compression ratio", "${(it * 100).toInt()}%")
                        }
                    }

                    PropertiesRow(stringResource(R.string.properties_location), model.pathSummary)
                    model.newestModifiedAt?.let {
                        PropertiesRow(stringResource(R.string.properties_modified), java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it)))
                    }
                    if (!model.isSingleItem) {
                        model.oldestModifiedAt?.let {
                            PropertiesRow(stringResource(R.string.properties_oldest_modified), java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it)))
                        }
                    }
                    model.mimeTypeSummary?.let {
                        PropertiesRow(stringResource(R.string.properties_type), it)
                    }
                    model.extensionSummary?.let {
                        PropertiesRow(stringResource(R.string.properties_extension), it)
                    }
                    PropertiesRow(stringResource(R.string.properties_hidden_items), model.hiddenCount.toString())
                    PropertiesRow(
                        stringResource(R.string.properties_access),
                        when (model.accessStatus) {
                            PropertiesAccessStatus.Full -> stringResource(R.string.properties_access_full)
                            PropertiesAccessStatus.Partial -> stringResource(R.string.properties_access_partial)
                            PropertiesAccessStatus.Limited -> stringResource(R.string.properties_access_limited)
                        }
                    )
                }
            }
        }
    )
}

@Composable
private fun PropertiesRow(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
