package dev.qtremors.arcile.presentation.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun Breadcrumbs(
    path: String,
    onPathSegmentClick: (String) -> Unit
) {
    val scrollState = rememberScrollState()

    // Scroll to the end whenever the path changes
    LaunchedEffect(path) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val segments = path.split("/").filter { it.isNotEmpty() }
            
            if (segments.isEmpty() || path == "/") {
                Text(
                    text = "Internal Storage",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Text(
                    text = "Storage",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clickable { onPathSegmentClick("/") }
                )
                
                var currentBuiltPath = ""
                segments.forEachIndexed { index, segment ->
                    currentBuiltPath += "/$segment"
                    val isLast = index == segments.size - 1
                    
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.padding(horizontal = 4.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    
                    Text(
                        text = segment,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                        color = if (isLast) MaterialTheme.colorScheme.primary else Color.Unspecified,
                        modifier = Modifier.clickable(enabled = !isLast) {
                            onPathSegmentClick(currentBuiltPath)
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp)) // Extra padding at the end for scrolling
        }
    }
}
