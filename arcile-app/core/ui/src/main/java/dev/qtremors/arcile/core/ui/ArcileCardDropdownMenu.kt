package dev.qtremors.arcile.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.theme.menuGroupFirst
import dev.qtremors.arcile.core.ui.theme.menuGroupLast
import dev.qtremors.arcile.core.ui.theme.menuGroupMiddle
import dev.qtremors.arcile.core.ui.theme.menuGroupSingle

@Composable
fun ArcileCardDropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    isSelected: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    shape: Shape? = null
) {
    ArcileDropdownMenuItem(
        text = text,
        onClick = onClick,
        modifier = modifier,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        enabled = enabled,
        isSelected = isSelected,
        contentPadding = contentPadding,
        shape = shape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
    )
}

@Composable
fun ArcileCardDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 4.dp),
    shape: Shape = MaterialTheme.shapes.extraLarge,
    items: List<@Composable () -> Unit>
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier
            .padding(vertical = 2.dp)
            .width(IntrinsicSize.Max)
            .widthIn(min = 160.dp, max = 280.dp),
        offset = offset,
        shape = shape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        shadowElevation = 8.dp,
        tonalElevation = 4.dp
    ) {
        ArcileCardSegmentedMenuItems(items)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExposedDropdownMenuBoxScope.ArcileCardExposedDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    minWidth: Dp = 120.dp,
    maxWidth: Dp = 260.dp,
    shape: Shape = MaterialTheme.shapes.extraLarge,
    items: List<@Composable () -> Unit>
) {
    ExposedDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier
            .padding(vertical = 2.dp)
            .width(IntrinsicSize.Max)
            .widthIn(min = minWidth, max = maxWidth),
        shape = shape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        shadowElevation = 8.dp,
        tonalElevation = 4.dp
    ) {
        ArcileCardSegmentedMenuItems(items)
    }
}

@Composable
private fun ArcileCardSegmentedMenuItems(items: List<@Composable () -> Unit>) {
    items.forEachIndexed { index, item ->
        val itemShape = when {
            items.size == 1 -> MaterialTheme.shapes.menuGroupSingle
            index == 0 -> MaterialTheme.shapes.menuGroupFirst
            index == items.lastIndex -> MaterialTheme.shapes.menuGroupLast
            else -> MaterialTheme.shapes.menuGroupMiddle
        }
        Box(
            modifier = Modifier
                .padding(horizontal = 4.dp, vertical = 1.dp)
                .fillMaxWidth()
                .clip(itemShape)
        ) {
            item()
        }
    }
}
