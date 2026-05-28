package dev.qtremors.arcile.feature.recentfiles.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.storage.domain.BrowserPresentationPreferences
import dev.qtremors.arcile.core.storage.domain.FileSortOption

@Composable
internal fun RecentDateHeaderPill(dateHeader: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.primary,
            tonalElevation = 3.dp,
            shadowElevation = 2.dp
        ) {
            Text(
                text = dateHeader,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
            )
        }
    }
}

internal fun shouldGroupRecentFiles(
    showSearchBar: Boolean,
    presentation: BrowserPresentationPreferences
): Boolean = !showSearchBar &&
    presentation.sortOption in setOf(FileSortOption.DATE_NEWEST, FileSortOption.DATE_OLDEST)
