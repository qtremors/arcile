package dev.qtremors.arcile.presentation.ui.components.lists

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.qtremors.arcile.domain.FileCategories
import dev.qtremors.arcile.domain.FileModel
import dev.qtremors.arcile.utils.formatFileSize
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileGrid(
    files: List<FileModel>,
    selectedFiles: Set<String>,
    onNavigateTo: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
    onSelectMultiple: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
) {
    val formatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    var lastInteractedIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(files) { lastInteractedIndex = null }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 100.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        state = gridState,
        modifier = modifier.fillMaxWidth()
    ) {
        items(files.size, key = { index -> files[index].absolutePath }) { index ->
            val file = files[index]
            FileGridItem(
                modifier = Modifier.animateItem(),
                file = file,
                formattedDate = formatter.format(Date(file.lastModified)),
                isSelected = selectedFiles.contains(file.absolutePath),
                onClick = {
                    if (selectedFiles.isNotEmpty()) {
                        lastInteractedIndex = index
                        onToggleSelection(file.absolutePath)
                    } else if (file.isDirectory) {
                        onNavigateTo(file.absolutePath)
                    } else {
                        onOpenFile(file.absolutePath)
                    }
                },
                onLongClick = {
                    if (selectedFiles.isNotEmpty() && lastInteractedIndex != null && lastInteractedIndex != index) {
                        val start = minOf(lastInteractedIndex!!, index)
                        val end = maxOf(lastInteractedIndex!!, index)
                        val rangePaths = files.subList(start, end + 1).map { it.absolutePath }
                        onSelectMultiple(rangePaths)
                        lastInteractedIndex = index
                    } else {
                        lastInteractedIndex = index
                        onToggleSelection(file.absolutePath)
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileGridItem(
    modifier: Modifier = Modifier,
    file: FileModel,
    formattedDate: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = 0.8f,
            stiffness = 380f
        ),
        label = "gridItemScale"
    )

    val contentDesc = "${file.name}, ${if (file.isDirectory) "Folder" else formatFileSize(file.size)}, Modified $formattedDate"

    Card(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .alpha(if (file.name.startsWith(".")) 0.5f else 1f)
            .semantics(mergeDescendants = true) {
                contentDescription = contentDesc
                selected = isSelected
            }            .combinedClickable(
                interactionSource = interactionSource,
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = if (isSelected) MaterialTheme.shapes.large else MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
        ) {
            val isMedia = !file.isDirectory && (FileCategories.Images.extensions.contains(file.name.substringAfterLast('.').lowercase()) ||
                    FileCategories.Videos.extensions.contains(file.name.substringAfterLast('.').lowercase()) ||
                    FileCategories.APKs.extensions.contains(file.name.substringAfterLast('.').lowercase()) ||
                    FileCategories.Audio.extensions.contains(file.name.substringAfterLast('.').lowercase()))

            if (isMedia) {
                AsyncImage(
                    model = File(file.absolutePath),
                    contentDescription = "Thumbnail",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (file.isDirectory) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                        contentDescription = if (file.isDirectory) "Folder" else "File",
                        tint = if (file.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        append(formattedDate)
                        if (!file.isDirectory) {
                            append(" | ")
                            append(formatFileSize(file.size))
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
