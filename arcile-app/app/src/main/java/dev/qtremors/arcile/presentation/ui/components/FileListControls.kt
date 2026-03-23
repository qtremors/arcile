package dev.qtremors.arcile.presentation.ui.components
import dev.qtremors.arcile.R

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.presentation.FileSortOption

import androidx.compose.ui.res.stringResource
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortOptionDialog(
    title: String,
    selectedOption: FileSortOption,
    showApplyToSubfolders: Boolean,
    onDismiss: () -> Unit,
    onOptionSelected: (FileSortOption, Boolean) -> Unit
) {
    var applyToSubfolders by remember { mutableStateOf(false) }
    var tempSelectedOption by remember { mutableStateOf(selectedOption) }

    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp, top = 8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 4.dp, start = 8.dp)
            )

            FileSortOption.entries.forEach { option ->
                val isSelected = option == tempSelectedOption
                val backgroundColor by androidx.compose.animation.animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                    label = "sort_bg"
                )
                
                androidx.compose.material3.Surface(
                    shape = MaterialTheme.shapes.large,
                    color = backgroundColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .clip(MaterialTheme.shapes.large)
                        .clickable { tempSelectedOption = option }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val labelRes = when(option) {
                            FileSortOption.NAME_ASC -> dev.qtremors.arcile.R.string.sort_name_asc
                            FileSortOption.NAME_DESC -> dev.qtremors.arcile.R.string.sort_name_desc
                            FileSortOption.DATE_NEWEST -> dev.qtremors.arcile.R.string.sort_date_newest
                            FileSortOption.DATE_OLDEST -> dev.qtremors.arcile.R.string.sort_date_oldest
                            FileSortOption.SIZE_LARGEST -> dev.qtremors.arcile.R.string.sort_size_largest
                            FileSortOption.SIZE_SMALLEST -> dev.qtremors.arcile.R.string.sort_size_smallest
                        }
                        Text(
                            text = stringResource(labelRes), 
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                        RadioButton(
                            selected = isSelected,
                            onClick = null // Handled by Surface click
                        )
                    }
                }
            }

            if (showApplyToSubfolders) {
                Spacer(modifier = Modifier.height(4.dp))
                androidx.compose.material3.HorizontalDivider()
                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { applyToSubfolders = !applyToSubfolders }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Checkbox(
                        checked = applyToSubfolders,
                        onCheckedChange = null // Handled by Row click
                    )
                    Text("Apply to this folder and subfolders", style = MaterialTheme.typography.bodyLarge)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(dev.qtremors.arcile.R.string.cancel))
                }
                androidx.compose.material3.FilledTonalButton(onClick = {
                    onOptionSelected(tempSelectedOption, applyToSubfolders)
                    onDismiss()
                }) {
                    Text("Apply")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
