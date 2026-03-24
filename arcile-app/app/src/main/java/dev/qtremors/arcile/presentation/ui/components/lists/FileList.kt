package dev.qtremors.arcile.presentation.ui.components.lists
import dev.qtremors.arcile.R
import androidx.compose.ui.res.stringResource

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.qtremors.arcile.domain.FileCategories
import dev.qtremors.arcile.domain.FileModel
import dev.qtremors.arcile.utils.formatFileSize
import java.io.File
import dev.qtremors.arcile.presentation.utils.rememberDateFormatter
import java.util.Date

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
    listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
) {
    val formatter = rememberDateFormatter("MMM dd, yyyy")
    var lastInteractedIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(files) { lastInteractedIndex = null }

    LazyColumn(modifier = modifier.fillMaxWidth(), state = listState) {
        items(files.size, key = { index -> "${files[index].absolutePath}_$index" }) { index ->
            val file = files[index]
            FileItemRow(
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
fun FileItemRow(
    file: FileModel,
    formattedDate: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedSurfaceColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        label = "listItemColor"
    )

    val animatedHorizontalPadding by animateDpAsState(
        targetValue = if (isSelected) 8.dp else 0.dp,
        label = "listItemHPadding"
    )
    val animatedVerticalPadding by animateDpAsState(
        targetValue = if (isSelected) 4.dp else 0.dp,
        label = "listItemVPadding"
    )

    val contentDesc = "${file.name}, ${if (file.isDirectory) "Folder" else formatFileSize(file.size)}, Modified $formattedDate"

    Surface(
        shape = if (isSelected) MaterialTheme.shapes.large else MaterialTheme.shapes.extraLarge,
        color = animatedSurfaceColor,
        modifier = modifier
            .padding(horizontal = animatedHorizontalPadding, vertical = animatedVerticalPadding)
            .alpha(if (file.name.startsWith(".")) 0.5f else 1f)
            .semantics(mergeDescendants = true) {
                contentDescription = contentDesc
                selected = isSelected
            }            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        ListItem(
            leadingContent = {
                val isMedia = !file.isDirectory && file.extension != null && (
                        FileCategories.Images.extensions.contains(file.extension) ||
                        FileCategories.Videos.extensions.contains(file.extension) ||
                        FileCategories.APKs.extensions.contains(file.extension) ||
                        FileCategories.Audio.extensions.contains(file.extension)
                )

                if (isMedia) {
                    AsyncImage(
                        model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                            .data(File(file.absolutePath))
                            .size(256)
                            .build(),
                        contentDescription = stringResource(R.string.desc_thumbnail),
                        modifier = Modifier
                            .size(40.dp)
                            .clip(MaterialTheme.shapes.extraLarge),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = if (file.isDirectory) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                        contentDescription = if (file.isDirectory) "Folder" else "File",
                        tint = if (file.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(40.dp)
                    )
                }
            },
            headlineContent = { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(formattedDate)
                    if (!file.isDirectory) {
                        Text(formatFileSize(file.size))
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}
