package dev.qtremors.arcile.feature.trash

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.shared.ui.ExpressiveFilterChip
import dev.qtremors.arcile.ui.theme.ExpressiveShapes
import dev.qtremors.arcile.ui.theme.bounceClickable

@Composable
internal fun TrashFilterRow(
    selected: TrashFilter,
    onFilterChange: (TrashFilter) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TrashFilter.entries.forEach { filter ->
            ExpressiveFilterChip(
                selected = selected == filter,
                onClick = { onFilterChange(filter) },
                label = {
                    Text(
                        when (filter) {
                            TrashFilter.ALL -> stringResource(R.string.trash_filter_all)
                            TrashFilter.CAN_RESTORE -> stringResource(R.string.trash_filter_can_restore)
                            TrashFilter.NEEDS_DESTINATION -> stringResource(R.string.trash_filter_needs_destination)
                            TrashFilter.RECOVERED -> stringResource(R.string.trash_filter_recovered)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }
    }
}

@Composable
internal fun TrashSortDialog(
    selected: TrashSortOption,
    onDismiss: () -> Unit,
    onSelect: (TrashSortOption) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.trash_sort_title)) },
        text = {
            Column {
                TrashSortOption.entries.forEach { option ->
                    ListItem(
                        headlineContent = { Text(trashSortLabel(option)) },
                        modifier = Modifier
                            .clip(ExpressiveShapes.medium)
                            .bounceClickable { onSelect(option) },
                        colors = ListItemDefaults.colors(
                            containerColor = if (selected == option) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                Color.Transparent
                            }
                        )
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                shape = ExpressiveShapes.medium,
                modifier = Modifier.bounceClickable(onClick = onDismiss)
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun trashSortLabel(option: TrashSortOption): String {
    return when (option) {
        TrashSortOption.DELETED_NEWEST -> stringResource(R.string.trash_sort_deleted_newest)
        TrashSortOption.DELETED_OLDEST -> stringResource(R.string.trash_sort_deleted_oldest)
        TrashSortOption.NAME_ASC -> stringResource(R.string.sort_name_asc)
        TrashSortOption.NAME_DESC -> stringResource(R.string.sort_name_desc)
        TrashSortOption.SIZE_LARGEST -> stringResource(R.string.sort_size_largest)
        TrashSortOption.SIZE_SMALLEST -> stringResource(R.string.sort_size_smallest)
        TrashSortOption.TYPE -> stringResource(R.string.trash_sort_type)
        TrashSortOption.ORIGINAL_FOLDER -> stringResource(R.string.trash_sort_original_folder)
    }
}
