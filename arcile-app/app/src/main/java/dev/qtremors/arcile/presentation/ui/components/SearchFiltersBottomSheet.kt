package dev.qtremors.arcile.presentation.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.domain.FileCategories
import dev.qtremors.arcile.domain.SearchFilters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchFiltersBottomSheet(
    currentFilters: SearchFilters,
    onApplyFilters: (SearchFilters) -> Unit,
    onDismiss: () -> Unit,
    showCategoryFilter: Boolean = true
) {

    val categories = listOf("All") + FileCategories.all.map { it.name }

    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Search Filters",
                    style = MaterialTheme.typography.titleLarge
                )
                TextButton(
                    onClick = {
                        onApplyFilters(SearchFilters())
                    }
                ) {
                    Text("Clear All")
                }
            }

            Text("Item Type", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            ) {
                SingleChoiceSegmentedButtonRow {
                    val options = listOf("Any", "Folders", "Files")
                    options.forEachIndexed { index, label ->
                        val selected = when(label) {
                            "Any" -> currentFilters.itemType == null || currentFilters.itemType == "Any"
                            else -> currentFilters.itemType == label
                        }
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                            onClick = { onApplyFilters(currentFilters.copy(itemType = if (label == "Any") null else label)) },
                            selected = selected
                        ) {
                            Text(label)
                        }
                    }
                }
            }

            if (showCategoryFilter) {
                Text("File Type", style = MaterialTheme.typography.titleMedium)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(categories) { category ->
                        val isSelected = if (category == "All") currentFilters.fileType == null else currentFilters.fileType == category
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                onApplyFilters(currentFilters.copy(fileType = if (category == "All") null else category))
                            },
                            label = { Text(category) }
                        )
                    }
                }
            }


            Text("File Size", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            ) {
                SingleChoiceSegmentedButtonRow {
                    val options = listOf(
                        "Any" to (null to null),
                        "< 10 MB" to (null to 10L * 1024 * 1024),
                        "10 - 100 MB" to (10L * 1024 * 1024 to 100L * 1024 * 1024),
                        "> 100 MB" to (100L * 1024 * 1024 to null)
                    )
                    options.forEachIndexed { index, (label, sizeRange) ->
                        val selected = currentFilters.minSize == sizeRange.first && currentFilters.maxSize == sizeRange.second
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                            onClick = { onApplyFilters(currentFilters.copy(minSize = sizeRange.first, maxSize = sizeRange.second)) },
                            selected = selected
                        ) {
                            Text(label, maxLines = 1)
                        }
                    }
                }
            }

            Text("Date Modified", style = MaterialTheme.typography.titleMedium)
            val dayMillis = 24L * 60 * 60 * 1000
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            ) {
                val now = System.currentTimeMillis()
                val cal = java.util.Calendar.getInstance()
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                cal.set(java.util.Calendar.MINUTE, 0)
                cal.set(java.util.Calendar.SECOND, 0)
                cal.set(java.util.Calendar.MILLISECOND, 0)
                val todayMidnight = cal.timeInMillis

                SingleChoiceSegmentedButtonRow {
                    val options = listOf(
                        "Any" to (null to null),
                        "Today" to (todayMidnight to null),
                        "Last 7 days" to (now - 7 * dayMillis to null),
                        "Last 30 days" to (now - 30 * dayMillis to null)
                    )
                    options.forEachIndexed { index, (label, dateRange) ->
                        val selected = currentFilters.minDateMillis == dateRange.first && currentFilters.maxDateMillis == dateRange.second

                        SegmentedButton(                            shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                            onClick = { onApplyFilters(currentFilters.copy(minDateMillis = dateRange.first, maxDateMillis = dateRange.second)) },
                            selected = selected
                        ) {
                            Text(label, maxLines = 1)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
