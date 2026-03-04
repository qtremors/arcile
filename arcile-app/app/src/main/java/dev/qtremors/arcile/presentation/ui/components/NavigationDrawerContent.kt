package dev.qtremors.arcile.presentation.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun NavigationDrawerContent(
    currentRoute: String,
    onNavigate: (String) -> Unit
) {
    ModalDrawerSheet {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Arcile",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp)
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(modifier = Modifier.height(16.dp))

        DrawerItem(
            label = "Home",
            icon = Icons.Default.Home,
            isSelected = currentRoute == "home",
            onClick = { onNavigate("home") }
        )
        DrawerItem(
            label = "Tools & Utilities",
            icon = Icons.Default.Build,
            isSelected = currentRoute == "tools",
            onClick = { onNavigate("tools") }
        )
        DrawerItem(
            label = "Settings",
            icon = Icons.Default.Settings,
            isSelected = currentRoute == "settings",
            onClick = { onNavigate("settings") }
        )
    }
}

@Composable
private fun DrawerItem(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    NavigationDrawerItem(
        label = { Text(label) },
        icon = { Icon(icon, contentDescription = null) },
        selected = isSelected,
        onClick = onClick,
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
    )
}
