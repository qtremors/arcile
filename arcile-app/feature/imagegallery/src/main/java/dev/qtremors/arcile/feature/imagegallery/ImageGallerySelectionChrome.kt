package dev.qtremors.arcile.feature.imagegallery

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.category.CategorySelectionTopBar

@Composable
internal fun FloatingGallerySelectionTopBar(
    selectedCount: Int,
    selectedSize: String,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    modifier: Modifier = Modifier
) {
    CategorySelectionTopBar(
        selectedCountText = androidx.compose.ui.res.pluralStringResource(R.plurals.selected_count, selectedCount, selectedCount),
        selectedSizeText = selectedSize,
        onClearSelection = onClearSelection,
        onSelectAll = onSelectAll,
        onInvertSelection = onInvertSelection,
        modifier = modifier
    )
}
