package dev.qtremors.arcile.presentation.ui

import dev.qtremors.arcile.presentation.ui.components.ToolItem
import dev.qtremors.arcile.presentation.ui.components.ToolCard

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp


/**
 * Utilities grid screen listing planned and implemented tools.
 *
 * Currently only the Trash Bin tool is functional. All other tool cards are marked as
 * "Coming Soon" and are not clickable. See TASKS.md F4 for the UX issue around
 * non-interactive cards being visually identical to actionable ones.
 *
 * @param onNavigateBack Called when the user navigates back.
 */
@Composable
fun ToolsScreen(
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("Tools & Utilities") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
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
