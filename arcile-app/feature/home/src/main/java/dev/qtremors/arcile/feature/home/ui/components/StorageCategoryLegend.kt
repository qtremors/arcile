package dev.qtremors.arcile.feature.home.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.storage.domain.CategoryStorage
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.theme.LocalCategoryColors
import dev.qtremors.arcile.core.ui.theme.spacing
import dev.qtremors.arcile.core.presentation.formatFileSize
import dev.qtremors.arcile.core.ui.theme.getCategoryColor

@Composable
internal fun CategoryLegend(
    categoryStorages: List<CategoryStorage>,
    trashBytes: Long = 0L,
    systemBytes: Long = 0L
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .horizontalScroll(scrollState)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space12)
    ) {
        val categoryColors = LocalCategoryColors.current
        val legendItems = categoryStorages
            .sortedByDescending { it.sizeBytes }
            .filter { it.sizeBytes > 0 }
            .map { category ->
                Triple(
                    category.name,
                    category.sizeBytes,
                    getCategoryColor(
                        category.name,
                        categoryColors,
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                )
            } + listOfNotNull(
            trashBytes.takeIf { it > 0L }?.let {
                Triple(stringResource(R.string.trash_bin), it, MaterialTheme.colorScheme.error)
            },
            systemBytes.takeIf { it > 0L }?.let {
                Triple(
                    stringResource(R.string.other_files_system),
                    it,
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        )
        legendItems.forEach { (name, sizeBytes, color) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Text(
                    text = "$name ${formatFileSize(sizeBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

internal fun systemInaccessibleBytes(
    totalBytes: Long,
    freeBytes: Long,
    categoryStorages: List<CategoryStorage>,
    trashBytes: Long
): Long {
    val actualUsedBytes = (totalBytes - freeBytes).coerceAtLeast(0L)
    val visibleBytes = categoryStorages.sumOf { it.sizeBytes } + trashBytes.coerceAtLeast(0L)
    return (actualUsedBytes - visibleBytes).coerceAtLeast(0L)
}
