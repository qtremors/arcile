package dev.qtremors.arcile.feature.archive

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import dev.qtremors.arcile.core.ui.theme.spacing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ListItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.feature.archive.ArchiveOperationStatusMessage
import dev.qtremors.arcile.feature.archive.ArchiveViewerState
import dev.qtremors.arcile.core.operation.OperationCompletionStatus
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.core.storage.domain.ArchiveNameEncoding
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.ui.EmptyState
import dev.qtremors.arcile.core.ui.EmptyStateVariant
import dev.qtremors.arcile.core.ui.ExpressiveFilterChip
import dev.qtremors.arcile.core.ui.rememberArcileHaptics
import dev.qtremors.arcile.core.ui.ArcileScreenScaffold
import dev.qtremors.arcile.core.ui.ConflictCard
import dev.qtremors.arcile.core.ui.keyboardInputField
import dev.qtremors.arcile.core.presentation.formatFileSize
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
                    onClick = { if (path != null) onOpenFolder(path) },
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
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth().keyboardInputField(),
            singleLine = true,
            label = { Text(stringResource(R.string.archive_search_entries)) }
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
        Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sectionGap)) {
            Text(
                text = pluralStringResource(R.plurals.archive_entry_count, summary.entryCount, summary.entryCount),
                style = MaterialTheme.typography.bodySmall
            )
            Text(formatFileSize(summary.totalUncompressedSize), style = MaterialTheme.typography.bodySmall)
        }
        val ratio = summary.compressionRatio?.let { "${(it * 100).toInt()}%" }
            ?: stringResource(R.string.archive_ratio_unavailable)
        Text(
            text = stringResource(R.string.archive_summary_size_ratio, formatFileSize(summary.archiveSize), ratio),
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

