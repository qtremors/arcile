package dev.qtremors.arcile.presentation.ui.components.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.storage.domain.CategoryStorage
import dev.qtremors.arcile.ui.theme.LocalCategoryColors
import dev.qtremors.arcile.ui.theme.ArcileMotion
import dev.qtremors.arcile.utils.formatFileSize
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.shared.ui.shimmer

@Composable
fun CategoryGrid(
    categoryStorages: List<CategoryStorage>,
    reserveSizeLine: Boolean = false,
    onCategoryClick: (String) -> Unit
) {
    data class CategoryDisplay(
        val id: String,
        val label: String,
        val icon: ImageVector,
        val color: Color,
        val sizeBytes: Long
    )

    val catColors = LocalCategoryColors.current

    val categories = listOf(
        CategoryDisplay("Images", stringResource(R.string.category_images), Icons.Outlined.Image, catColors.images, categoryStorages.find { it.name == "Images" }?.sizeBytes ?: 0),
        CategoryDisplay("Videos", stringResource(R.string.category_videos), Icons.Outlined.VideoFile, catColors.videos, categoryStorages.find { it.name == "Videos" }?.sizeBytes ?: 0),
        CategoryDisplay("Audio", stringResource(R.string.category_audio), Icons.Outlined.AudioFile, catColors.audio, categoryStorages.find { it.name == "Audio" }?.sizeBytes ?: 0),
        CategoryDisplay("Docs", stringResource(R.string.category_docs), Icons.Outlined.Description, catColors.docs, categoryStorages.find { it.name == "Docs" }?.sizeBytes ?: 0),
        CategoryDisplay("Archives", stringResource(R.string.category_archives), Icons.Outlined.FolderZip, catColors.archives, categoryStorages.find { it.name == "Archives" }?.sizeBytes ?: 0),
        CategoryDisplay("APKs", stringResource(R.string.category_apks), Icons.Outlined.Android, catColors.apks, categoryStorages.find { it.name == "APKs" }?.sizeBytes ?: 0),
    )

    val sortedCategories = categories.sortedByDescending { it.sizeBytes }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .height(230.dp)
    ) {
        val columnWidth = maxWidth / 3
        val rowHeight = 115.dp

        categories.forEach { cat ->
            val indexInSorted = sortedCategories.indexOf(cat)
            val column = indexInSorted % 3
            val row = indexInSorted / 3

            val targetX = columnWidth * column
            val targetY = rowHeight * row

            val xOffset by animateDpAsState(
                targetValue = targetX,
                animationSpec = ArcileMotion.rememberSpring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "${cat.id}_xOffset"
            )

            val yOffset by animateDpAsState(
                targetValue = targetY,
                animationSpec = ArcileMotion.rememberSpring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "${cat.id}_yOffset"
            )

            Box(
                modifier = Modifier
                    .width(columnWidth)
                    .height(rowHeight)
                    .offset(x = xOffset, y = yOffset)
            ) {
                CategoryItem(
                    name = cat.label,
                    icon = cat.icon,
                    color = cat.color,
                    sizeBytes = cat.sizeBytes,
                    reserveSizeLine = reserveSizeLine,
                    modifier = Modifier.fillMaxSize(),
                    onClick = { onCategoryClick(cat.id) }
                )
            }
        }
    }
}

@Composable
fun CategoryItem(
    name: String,
    icon: ImageVector,
    color: Color,
    sizeBytes: Long,
    reserveSizeLine: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1f,
        animationSpec = ArcileMotion.rememberSpring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "categoryScale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = onClick
            )
            .padding(8.dp)
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(64.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = name,
                modifier = Modifier.padding(16.dp),
                tint = color
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(2.dp))
        
        val hasSize = sizeBytes > 0
        val sizeAlpha by animateFloatAsState(
            targetValue = if (hasSize) 1f else 0f,
            animationSpec = ArcileMotion.rememberTween(durationMillis = ArcileMotion.Medium3),
            label = "${name}_sizeAlpha"
        )
        
        Box(
            modifier = Modifier
                .height(14.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            if (hasSize || reserveSizeLine) {
                Text(
                    text = if (hasSize) formatFileSize(sizeBytes) else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = sizeAlpha)
                )
                
                if (!hasSize) {
                    Box(
                        modifier = Modifier
                            .width(48.dp)
                            .height(10.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f))
                            .shimmer(visible = true, highlightColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f))
                    )
                }
            }
        }
    }
}
