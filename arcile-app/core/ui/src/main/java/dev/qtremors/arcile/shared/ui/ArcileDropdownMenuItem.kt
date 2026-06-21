package dev.qtremors.arcile.shared.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MenuItemColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import dev.qtremors.arcile.ui.theme.ExpressiveShapes

@Composable
fun ArcileDropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    colors: MenuItemColors? = null,
    contentPadding: PaddingValues = MenuDefaults.DropdownMenuItemContentPadding,
    shape: Shape = ExpressiveShapes.medium
) {
    DropdownMenuItem(
        text = text,
        onClick = onClick,
        modifier = modifier.clip(shape),
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        enabled = enabled,
        colors = colors ?: MenuDefaults.itemColors(),
        contentPadding = contentPadding
    )
}
