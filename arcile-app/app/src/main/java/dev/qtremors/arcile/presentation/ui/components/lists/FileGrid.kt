package dev.qtremors.arcile.presentation.ui.components.lists

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import dev.qtremors.arcile.R
import dev.qtremors.arcile.domain.FileCategories
import dev.qtremors.arcile.domain.FileModel
import dev.qtremors.arcile.domain.FolderStats
import dev.qtremors.arcile.presentation.utils.rememberDateFormatter
import dev.qtremors.arcile.utils.formatFileSize
import java.io.File
import java.util.Date

import dev.qtremors.arcile.presentation.ui.components.rememberArcileHaptics

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
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState(),
    minCellSize: Dp = 100.dp,
    showThumbnails: Boolean = true,
    folderStatsByPath: Map<String, FolderStats> = emptyMap(),
    folderStatsLoadingPaths: Set<String> = emptySet(),
    contentPadding: PaddingValues = PaddingValues(16.dp)
) {
    val formatter = rememberDateFormatter("MMM dd, yyyy  h:mm a")
    val haptics = rememberArcileHaptics()
    var lastInteractedIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(files) { lastInteractedIndex = null }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = minCellSize),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        state = gridState,
        modifier = modifier.fillMaxWidth()
    ) {
        itemsIndexed(
            items = files,
            key = { _, file -> file.absolutePath },
            contentType = { _, file -> if (file.isDirectory) "directory" else "file" }
        ) { index, file ->
            val isSelected = selectedFiles.contains(file.absolutePath)
            FileGridItem(
                modifier = Modifier.animateItem(),
                file = file,
                formattedDate = formatter.format(Date(file.lastModified)),
                isSelected = isSelected,
                isInSelectionMode = selectedFiles.isNotEmpty(),
                folderStats = folderStatsByPath[file.absolutePath],
                isFolderStatsLoading = folderStatsLoadingPaths.contains(file.absolutePath),
                showThumbnails = showThumbnails,
                onClick = {
                    if (selectedFiles.isNotEmpty()) {
                        lastInteractedIndex = index
                        onToggleSelection(file.absolutePath)
                        haptics.selectionChanged()
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
                        haptics.selectionChanged()
                        lastInteractedIndex = index
                    } else {
                        val wasEmpty = selectedFiles.isEmpty()
                        onToggleSelection(file.absolutePath)
                        if (wasEmpty) {
                            haptics.selectionStart()
                        } else {
                            haptics.selectionChanged()
                        }
                        lastInteractedIndex = index
                    }
                },
                onOpenDirectly = {
                    if (file.isDirectory) {
                        onNavigateTo(file.absolutePath)
                    } else {
                        onOpenFile(file.absolutePath)
                    }
                },
                onToggleSelectionDirectly = {
                    val wasEmpty = selectedFiles.isEmpty()
                    onToggleSelection(file.absolutePath)
                    if (wasEmpty) {
                        haptics.selectionStart()
                    } else {
                        haptics.selectionChanged()
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileGridItem(
    file: FileModel,
    formattedDate: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    isInSelectionMode: Boolean = false,
    showThumbnails: Boolean = true,
    folderStats: FolderStats? = null,
    isFolderStatsLoading: Boolean = false,
    onOpenDirectly: () -> Unit = {},
    onToggleSelectionDirectly: () -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = 0.8f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "gridItemScale"
    )

    val folderSubtitle = if (file.isDirectory) {
        folderSubtitleText(folderStats)
    } else {
        null
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .alpha(if (file.name.startsWith(".")) 0.5f else 1f)
            .fileItemSemantics(
                file = file,
                isSelected = isSelected,
                formattedDate = formattedDate,
                folderStatsText = folderSubtitle,
                isInSelectionMode = isInSelectionMode,
                onClick = onClick,
                onLongClick = onLongClick,
                onOpenDirectly = onOpenDirectly,
                onToggleSelectionDirectly = onToggleSelectionDirectly
            )
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
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
            val isMedia = showThumbnails && !file.isDirectory && (
                FileCategories.Images.extensions.contains(file.extension) ||
                    FileCategories.Videos.extensions.contains(file.extension) ||
                    FileCategories.APKs.extensions.contains(file.extension) ||
                    FileCategories.Audio.extensions.contains(file.extension)
                )

            if (isMedia) {
                SubcomposeAsyncImage(
                    model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                        .data(File(file.absolutePath))
                        .crossfade(true)
                        .crossfade(300)
                        .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                        .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    contentScale = ContentScale.Crop,
                    loading = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = dev.qtremors.arcile.presentation.ui.components.getFileIconVector(file),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    },
                    error = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = dev.qtremors.arcile.presentation.ui.components.getFileIconVector(file),
                                contentDescription = null,
                                tint = if (file.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }
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
                        imageVector = dev.qtremors.arcile.presentation.ui.components.getFileIconVector(file),
                        contentDescription = null,
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
                    text = if (file.isDirectory) {
                        folderSubtitle ?: stringResource(R.string.folder_label)
                    } else {
                        formatFileSize(file.size)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}


