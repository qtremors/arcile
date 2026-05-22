package dev.qtremors.arcile.presentation.ui.components.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import dev.qtremors.arcile.R
import dev.qtremors.arcile.domain.FileCategories
import dev.qtremors.arcile.domain.FileModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentFilesCarousel(
    files: List<FileModel>,
    onOpenFile: (String) -> Unit,
    onNavigateToPath: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val primaryWidth = configuration.screenWidthDp.dp / 2
    val itemHeight = primaryWidth * 1.25f

    val state = rememberCarouselState { files.size }
    HorizontalMultiBrowseCarousel(
        state = state,
        preferredItemWidth = primaryWidth,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        itemSpacing = 8.dp
    ) { index ->
        val file = files[index]
        RecentFileCarouselItem(
            file = file,
            onClick = { onOpenFile(file.absolutePath) },
            onNavigateToPath = onNavigateToPath,
            itemHeight = itemHeight,
            modifier = Modifier.maskClip(RoundedCornerShape(24.dp))
        )
    }
}

@Composable
fun RecentFileCarouselItem(
    file: FileModel,
    onClick: () -> Unit,
    onNavigateToPath: (String) -> Unit,
    itemHeight: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    val usesFullBleedThumbnail = !file.isDirectory && (
        FileCategories.Images.extensions.contains(file.extension) ||
            FileCategories.Videos.extensions.contains(file.extension)
        )
    val previewAccent = previewAccentFor(file)
    val previewIcon = dev.qtremors.arcile.presentation.ui.components.getFileIconVector(file)

    Card(
        modifier = modifier
            .height(itemHeight)
            .clickable { onClick() },
        shape = RoundedCornerShape(0.dp), // Let maskClip handle the shape
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (usesFullBleedThumbnail) {
                SubcomposeAsyncImage(
                    model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                        .data(File(file.absolutePath))
                        .crossfade(true)
                        .crossfade(300)
                        .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                        .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                        .build(),
                    contentDescription = stringResource(R.string.desc_thumbnail),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    loading = {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = dev.qtremors.arcile.presentation.ui.components.getFileIconVector(file),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    },
                    error = {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = dev.qtremors.arcile.presentation.ui.components.getFileIconVector(file),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    previewAccent.copy(alpha = 0.28f),
                                    MaterialTheme.colorScheme.surfaceContainerHighest,
                                    MaterialTheme.colorScheme.surfaceContainer
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = previewAccent.copy(alpha = 0.16f),
                        modifier = Modifier.size(104.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = previewIcon,
                                contentDescription = null,
                                tint = previewAccent,
                                modifier = Modifier.size(56.dp)
                            )
                        }
                    }
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 92.dp)
                    ) {
                        Text(
                            text = fileTypeLabel(file),
                            style = MaterialTheme.typography.labelSmall,
                            color = previewAccent,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            ) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                val parentFolderName = File(file.absolutePath).parentFile?.name ?: stringResource(R.string.unknown_folder)
                Text(
                    text = parentFolderName,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            var showMenu by remember { mutableStateOf(false) }

            FileTypeBadge(
                icon = previewIcon,
                tint = if (usesFullBleedThumbnail) Color.White else previewAccent,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            )
            
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Surface(
                    onClick = { showMenu = true },
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.4f),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.action_more_options),
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    shape = MaterialTheme.shapes.medium,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.open)) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.open_containing_folder)) },
                        leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            File(file.absolutePath).parentFile?.absolutePath?.let { onNavigateToPath(it) }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FileTypeBadge(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.4f),
        modifier = modifier.size(32.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun previewAccentFor(file: FileModel): Color {
    val scheme = MaterialTheme.colorScheme
    val ext = file.extension.lowercase()
    return when {
        file.isDirectory -> scheme.primary
        FileCategories.APKs.extensions.contains(ext) -> scheme.tertiary
        FileCategories.Archives.extensions.contains(ext) -> scheme.secondary
        FileCategories.Audio.extensions.contains(ext) -> scheme.primary
        FileCategories.Documents.extensions.contains(ext) -> scheme.error
        else -> scheme.onSurfaceVariant
    }
}

@Composable
private fun fileTypeLabel(file: FileModel): String {
    if (file.isDirectory) return stringResource(R.string.file_type_folder)
    return file.extension
        .takeIf { it.isNotBlank() }
        ?.uppercase()
        ?: stringResource(R.string.file_type_generic)
}
