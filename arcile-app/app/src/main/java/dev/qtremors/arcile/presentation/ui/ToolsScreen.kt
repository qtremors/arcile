package dev.qtremors.arcile.presentation.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.presentation.ui.components.ArcileTopBar

@Composable
fun ToolsScreen(
    onMenuClick: () -> Unit,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            ArcileTopBar(
                title = "Tools & Utilities",
                selectionCount = 0,
                onMenuClick = onMenuClick,
                onClearSelection = {},
                onSearchClick = {}, // Typically omitted or implemented for search within tools
                onSortClick = {},
                onActionSelected = {}
            )
        }
    ) { padding ->
        val tools = listOf(
            ToolItem("FTP Server", Icons.Default.WifiTethering),
            ToolItem("Analyze Storage", Icons.Default.PieChart),
            ToolItem("Clean Junk", Icons.Default.CleaningServices),
            ToolItem("Duplicates", Icons.Default.FilterNone),
            ToolItem("Large Files", Icons.Default.ZoomIn),
            ToolItem("App Manager", Icons.Default.Apps),
            ToolItem("Secure Vault", Icons.Default.Lock),
            ToolItem("Network Share", Icons.Default.Dns)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(8.dp),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(tools) { tool ->
                ToolCard(tool)
            }
        }
    }
}

data class ToolItem(val name: String, val icon: ImageVector)

@Composable
fun ToolCard(item: ToolItem) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* TODO: Implement tool actions */ }
            .aspectRatio(1f),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.name,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }
    }
}
