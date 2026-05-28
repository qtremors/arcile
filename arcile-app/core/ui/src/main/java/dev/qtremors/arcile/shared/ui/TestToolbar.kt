package dev.qtremors.arcile.shared.ui

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TestToolbar() {
    HorizontalFloatingToolbar(
        expanded = true,
        content = {}
    )
}
