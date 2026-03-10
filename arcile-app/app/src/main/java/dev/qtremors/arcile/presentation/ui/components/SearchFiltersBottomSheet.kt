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
    onDismiss: () -> Unit
) {
    val categories = listOf("All") + FileCategories.all.map { it.name }

    ModalBottomSheet(onDismissRequest = onDismiss) {
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
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            ) {
                FilterChip(
                    selected = currentFilters.itemType == null || currentFilters.itemType == "Any",
                    onClick = { onApplyFilters(currentFilters.copy(itemType = null)) },
                    label = { Text("Any") }
                )
                FilterChip(
                    selected = currentFilters.itemType == "Folders",
                    onClick = { onApplyFilters(currentFilters.copy(itemType = "Folders")) },
                    label = { Text("Folders") }
                )
                FilterChip(
                    selected = currentFilters.itemType == "Files",
                    onClick = { onApplyFilters(currentFilters.copy(itemType = "Files")) },
                    label = { Text("Files") }
                )
            }

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

            Text("File Size", style = MaterialTheme.typography.titleMedium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            ) {
                FilterChip(
                    selected = currentFilters.minSize == null && currentFilters.maxSize == null,
                    onClick = { onApplyFilters(currentFilters.copy(minSize = null, maxSize = null)) },
                    label = { Text("Any") }
                )
                FilterChip(
                    selected = currentFilters.minSize == null && currentFilters.maxSize == 10L * 1024 * 1024,
                    onClick = { onApplyFilters(currentFilters.copy(minSize = null, maxSize = 10L * 1024 * 1024)) },
                    label = { Text("< 10 MB") }
                )
                FilterChip(
                    selected = currentFilters.minSize == 10L * 1024 * 1024 && currentFilters.maxSize == 100L * 1024 * 1024,
                    onClick = { onApplyFilters(currentFilters.copy(minSize = 10L * 1024 * 1024, maxSize = 100L * 1024 * 1024)) },
                    label = { Text("10 MB - 100 MB") }
                )
                FilterChip(
                    selected = currentFilters.minSize == 100L * 1024 * 1024 && currentFilters.maxSize == null,
                    onClick = { onApplyFilters(currentFilters.copy(minSize = 100L * 1024 * 1024, maxSize = null)) },
                    label = { Text("> 100 MB") }
                )
            }

            Text("Date Modified", style = MaterialTheme.typography.titleMedium)
            val now = remember { System.currentTimeMillis() }
            val dayMillis = 24L * 60 * 60 * 1000
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            ) {
                FilterChip(
                    selected = currentFilters.minDateMillis == null && currentFilters.maxDateMillis == null,
                    onClick = { onApplyFilters(currentFilters.copy(minDateMillis = null, maxDateMillis = null)) },
                    label = { Text("Any time") }
                )
                FilterChip(
                    selected = currentFilters.minDateMillis != null && currentFilters.minDateMillis == now - dayMillis,
                    onClick = { onApplyFilters(currentFilters.copy(minDateMillis = now - dayMillis, maxDateMillis = null)) },
                    label = { Text("Today") }
                )
                FilterChip(
                    selected = currentFilters.minDateMillis != null && currentFilters.minDateMillis == now - 7 * dayMillis,
                    onClick = { onApplyFilters(currentFilters.copy(minDateMillis = now - 7 * dayMillis, maxDateMillis = null)) },
                    label = { Text("Last 7 days") }
                )
                  FilterChip(
                    selected = currentFilters.minDateMillis != null && currentFilters.minDateMillis == now - 30 * dayMillis,
                    onClick = { onApplyFilters(currentFilters.copy(minDateMillis = now - 30 * dayMillis, maxDateMillis = null)) },
                    label = { Text("Last 30 days") }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
