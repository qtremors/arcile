package dev.qtremors.arcile.presentation.ui.components.lists

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Alignment
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
fun FileList(
    files: List<FileModel>,
    selectedFiles: Set<String>,
    onNavigateTo: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
    onSelectMultiple: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    zoom: Float = 1f,
    showThumbnails: Boolean = true,
    folderStatsByPath: Map<String, FolderStats> = emptyMap(),
    folderStatsLoadingPaths: Set<String> = emptySet(),
    contentPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(0.dp)
) {
    val formatter = rememberDateFormatter("MMM dd, yyyy  h:mm a")
    val haptics = rememberArcileHaptics()
    var lastInteractedIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(files) { lastInteractedIndex = null }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        contentPadding = contentPadding
    ) {
        itemsIndexed(
            items = files,
            key = { _, file -> file.absolutePath },
            contentType = { _, file -> if (file.isDirectory) "directory" else "file" }
        ) { index, file ->
            val isSelected = selectedFiles.contains(file.absolutePath)
            FileItemRow(
                modifier = Modifier.animateItem(),
                file = file,
                formattedDate = formatter.format(Date(file.lastModified)),
                isSelected = isSelected,
                isInSelectionMode = selectedFiles.isNotEmpty(),
                zoom = zoom,
                showThumbnails = showThumbnails,
                folderStats = folderStatsByPath[file.absolutePath],
                isFolderStatsLoading = folderStatsLoadingPaths.contains(file.absolutePath),
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
fun FileItemRow(
    file: FileModel,
    formattedDate: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    isInSelectionMode: Boolean = false,
    onOpenDirectly: () -> Unit = {},
    onToggleSelectionDirectly: () -> Unit = {},
    modifier: Modifier = Modifier,
    zoom: Float = 1f,
    showThumbnails: Boolean = true,
    folderStats: FolderStats? = null,
    isFolderStatsLoading: Boolean = false
) {
    val animatedHorizontalPadding by animateDpAsState(
        targetValue = if (isSelected) 8.dp else 0.dp,
        label = "listItemHPadding"
    )
    val animatedVerticalPadding by animateDpAsState(
        targetValue = if (isSelected) 4.dp else 0.dp,
        label = "listItemVPadding"
    )
    val animatedScale by animateFloatAsState(targetValue = zoom, label = "listZoom")

    val iconSize = (48.dp * animatedScale).coerceIn(40.dp, 64.dp)
    val contentPadding = (16.dp * animatedScale).coerceIn(12.dp, 20.dp)
    
    val titleStyle = MaterialTheme.typography.titleMedium.scaled(animatedScale).copy(
        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
    )
    val supportStyle = MaterialTheme.typography.bodySmall.scaled(animatedScale).copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    
    val folderSubtitle = if (file.isDirectory) {
        folderSubtitleText(folderStats)
    } else {
        null
    }

    Surface(
        shape = if (isSelected) MaterialTheme.shapes.large else MaterialTheme.shapes.extraLarge,
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        modifier = modifier
            .padding(horizontal = animatedHorizontalPadding, vertical = animatedVerticalPadding)
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
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = contentPadding, vertical = contentPadding * 0.75f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(iconSize)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
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
                            .size(128)
                            .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                            .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                        error = {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = dev.qtremors.arcile.presentation.ui.components.getFileIconVector(file),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(iconSize * 0.5f)
                                )
                            }
                        }
                    )
                } else {
                    Icon(
                        imageVector = dev.qtremors.arcile.presentation.ui.components.getFileIconVector(file),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(iconSize * 0.5f)
                    )
                }
            }

            Spacer(modifier = Modifier.width(contentPadding))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = file.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = titleStyle,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (file.isDirectory) {
                            folderSubtitle ?: stringResource(R.string.folder_label)
                        } else {
                            formatFileSize(file.size)
                        },
                        style = supportStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    Text(
                        text = formattedDate,
                        style = supportStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End
                    )
                }
            }
        }
    }
}

private fun TextStyle.scaled(zoom: Float): TextStyle = copy(
    fontSize = fontSize * zoom,
    lineHeight = lineHeight * zoom
)

