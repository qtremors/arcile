package dev.qtremors.arcile.core.ui.archive

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import dev.qtremors.arcile.core.ui.theme.spacing
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.archive.ArchiveOperationStatusMessage
import dev.qtremors.arcile.core.ui.archive.ArchiveViewerState
import dev.qtremors.arcile.core.storage.domain.ArchiveNameEncoding
import dev.qtremors.arcile.core.ui.ExpressiveFilterChip
import dev.qtremors.arcile.core.ui.keyboardInputField
import dev.qtremors.arcile.core.presentation.formatFileSize
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

internal fun ArchiveOperationStatusMessage.stringRes(): Int =
    when (this) {
        ArchiveOperationStatusMessage.ExtractionComplete -> R.string.archive_extraction_complete
        ArchiveOperationStatusMessage.ExtractionCancelled -> R.string.archive_extraction_cancelled
    }

@Composable
internal fun ArchiveContextHeader(
    archiveName: String,
    currentPrefix: String?,
    extractionDestination: String,
    breadcrumbs: List<Pair<String, String?>>,
    onOpenFolder: (String) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    nameEncoding: ArchiveNameEncoding,
    showEncoding: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.spacing.screenGutter, vertical = MaterialTheme.spacing.compactGap),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.compactGap)
    ) {
        Text(
            text = currentPrefix ?: stringResource(R.string.archive_root),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = stringResource(R.string.archive_breadcrumb, archiveName, currentPrefix ?: stringResource(R.string.archive_root)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        LazyRow(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(
                items = breadcrumbs,
                key = { index, (_, path) -> path ?: "root:$index" }
            ) { _, (label, path) ->
                ExpressiveFilterChip(
                    selected = path == currentPrefix,
                    onClick = { onOpenFolder(path.orEmpty()) },
                    label = {
                        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                )
            }
        }
        if (showEncoding) {
            Text(
                text = nameEncoding.displayName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        TextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth().keyboardInputField(),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.archive_search_entries)) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            trailingIcon = if (searchQuery.isNotEmpty()) {
                {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Close, stringResource(R.string.action_clear))
                    }
                }
            } else null,
            shape = MaterialTheme.shapes.extraLarge,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            )
        )
        Text(
            text = stringResource(R.string.archive_destination_preview, extractionDestination),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun ArchiveSummaryHeader(state: ArchiveViewerState) {
    val summary = state.summary ?: return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.spacing.screenGutter, vertical = MaterialTheme.spacing.compactGap),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.compactGap)
    ) {
        Text(
            text = summary.format.displayName,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
        val context = androidx.compose.ui.platform.LocalContext.current
        Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sectionGap)) {
            Text(
                text = pluralStringResource(R.plurals.archive_entry_count, summary.entryCount, summary.entryCount),
                style = MaterialTheme.typography.bodySmall
            )
            Text(formatFileSize(context, summary.totalUncompressedSize), style = MaterialTheme.typography.bodySmall)
        }
        val ratio = summary.compressionRatio?.let { "${(it * 100).toInt()}%" }
            ?: stringResource(R.string.archive_ratio_unavailable)
        Text(
            text = stringResource(R.string.archive_summary_size_ratio, formatFileSize(context, summary.archiveSize), ratio),
            style = MaterialTheme.typography.bodySmall
        )
        summary.newestModifiedAt?.let {
            Text(
                text = stringResource(R.string.archive_newest_modified, DateFormat.getDateTimeInstance().format(Date(it))),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

