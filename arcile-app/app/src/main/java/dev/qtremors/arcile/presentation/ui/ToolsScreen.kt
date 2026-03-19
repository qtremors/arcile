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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.qtremors.arcile.R
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
                title = { Text(stringResource(R.string.tools_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        val tools = listOf(
            ToolItem(stringResource(R.string.tool_ftp), Icons.Default.WifiTethering),
            ToolItem(stringResource(R.string.tool_analyze), Icons.Default.PieChart),
            ToolItem(stringResource(R.string.tool_clean), Icons.Default.CleaningServices),
            ToolItem(stringResource(R.string.tool_duplicates), Icons.Default.FilterNone),
            ToolItem(stringResource(R.string.tool_large), Icons.Default.ZoomIn),
            ToolItem(stringResource(R.string.tool_manager), Icons.Default.Apps),
            ToolItem(stringResource(R.string.tool_onlyfiles), Icons.Default.Lock),
            ToolItem(stringResource(R.string.tool_share), Icons.Default.Dns)
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
