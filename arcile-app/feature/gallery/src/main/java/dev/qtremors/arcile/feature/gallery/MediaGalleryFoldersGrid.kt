@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package dev.qtremors.arcile.feature.gallery

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.FileSortOption
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.image.ThumbnailKey
import dev.qtremors.arcile.core.ui.image.ThumbnailPolicy
import dev.qtremors.arcile.core.ui.ArcileDropdownMenuItem
import dev.qtremors.arcile.core.ui.ArcilePullRefreshIndicator
import dev.qtremors.arcile.core.ui.ArcileSectionHeader
import dev.qtremors.arcile.core.ui.theme.ExpressiveShapes

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MediaGalleryFoldersGrid(
    state: MediaGalleryState,
    gridMinCellSize: Float,
    onFoldersGridCellSizeChange: (Float) -> Unit,
    onFoldersGridCellSizeFinalized: (Float) -> Unit,
    contentPadding: PaddingValues,
    onSelectFolder: (String?) -> Unit,
    onRefresh: () -> Unit,
    gridState: LazyGridState,
    modifier: Modifier = Modifier,
    onPasteToFolder: (String) -> Unit = {},
    onTogglePinnedFolder: (String) -> Unit = {}
) {
    val pullRefreshState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
    val thumbnailPolicy = remember { ThumbnailPolicy() }
    val sortedFolders = remember(state.folders, state.folderPresentation.sortOption) {
        when (state.folderPresentation.sortOption) {
            FileSortOption.NAME_ASC -> state.folders.sortedBy { it.label.lowercase() }
            FileSortOption.NAME_DESC -> state.folders.sortedByDescending { it.label.lowercase() }
            FileSortOption.SIZE_LARGEST -> state.folders.sortedByDescending { it.count }
            FileSortOption.SIZE_SMALLEST -> state.folders.sortedBy { it.count }
            FileSortOption.DATE_NEWEST -> state.folders.sortedByDescending { it.lastModified }
            FileSortOption.DATE_OLDEST -> state.folders.sortedBy { it.lastModified }
            FileSortOption.FILE_COUNT_HIGHEST -> state.folders.sortedByDescending { it.count }
            FileSortOption.FILE_COUNT_LOWEST -> state.folders.sortedBy { it.count }
        }
    }

    val favoritesLabel = stringResource(R.string.image_gallery_favorites_folder)
    val foldersList = remember(sortedFolders, state.files, state.favoriteFiles, favoritesLabel) {
        buildVisibleFolderTiles(
            sortedFolders = sortedFolders,
            files = state.files,
            favoriteFiles = state.favoriteFiles,
            favoritesLabel = favoritesLabel
        )
    }
    val coverLookup = remember(state.files, state.favoriteFiles, state.folderCovers) {
        buildFolderCoverLookup(
            files = state.files,
            favoriteFiles = state.favoriteFiles,
            folderCovers = state.folderCovers
        )
    }

    val favoritesFolder = remember(foldersList) { foldersList.firstOrNull { it.path == FAVORITES_FOLDER_PATH } }
    val regularFolders = remember(foldersList) { foldersList.filter { it.path != FAVORITES_FOLDER_PATH } }
    val pinnedFoldersList = remember(regularFolders, state.pinnedFolders) {
        regularFolders.filter { it.path in state.pinnedFolders }
    }
    val otherFolders = remember(regularFolders, state.pinnedFolders) {
        regularFolders.filter { it.path !in state.pinnedFolders }
    }
    val groupsList = emptyList<MediaGalleryFolder>() // Placeholder for future custom album groups

    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        state = pullRefreshState,
        modifier = modifier.fillMaxSize(),
        indicator = {
            ArcilePullRefreshIndicator(
                isRefreshing = state.isRefreshing,
                state = pullRefreshState
            )
        }
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = gridMinCellSize.dp),
            state = gridState,
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .pinchToResize(
                    currentCellSize = gridMinCellSize,
                    onSizeChanged = onFoldersGridCellSizeChange,
                    onSizeFinalized = onFoldersGridCellSizeFinalized
                )
                .padding(horizontal = 16.dp)
        ) {
            // Section 1: Favourites
            if (favoritesFolder != null) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "section_favorites_header") {
                    ArcileSectionHeader(
                        text = stringResource(R.string.image_gallery_section_favourites),
                        modifier = Modifier.padding(start = 0.dp, top = 8.dp)
                    )
                }
                item(key = FAVORITES_FOLDER_PATH) {
                    FolderGridItem(
                        folder = favoritesFolder,
                        coverFile = coverLookup[FAVORITES_FOLDER_PATH],
                        canPasteToFolder = false,
                        isPinned = false,
                        onSelectFolder = onSelectFolder,
                        onPasteToFolder = onPasteToFolder,
                        onTogglePinnedFolder = onTogglePinnedFolder,
                        thumbnailPolicy = thumbnailPolicy
                    )
                }
            }

            // Section 2: Pinned Albums
            if (pinnedFoldersList.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "section_pinned_header") {
                    ArcileSectionHeader(
                        text = stringResource(R.string.image_gallery_section_pinned),
                        modifier = Modifier.padding(start = 0.dp, top = 8.dp)
                    )
                }
                items(pinnedFoldersList, key = { "pinned_${it.path ?: it.label}" }) { folder ->
                    val coverFile = folder.path?.let(coverLookup::get)
                    val canPasteToFolder = state.clipboardState != null && isPasteDestinationFolderPath(folder.path)
                    FolderGridItem(
                        folder = folder,
                        coverFile = coverFile,
                        canPasteToFolder = canPasteToFolder,
                        isPinned = true,
                        onSelectFolder = onSelectFolder,
                        onPasteToFolder = onPasteToFolder,
                        onTogglePinnedFolder = onTogglePinnedFolder,
                        thumbnailPolicy = thumbnailPolicy
                    )
                }
            }

            // Section 3: Groups (Custom Album Groups)
            if (groupsList.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "section_groups_header") {
                    ArcileSectionHeader(
                        text = stringResource(R.string.image_gallery_section_groups),
                        modifier = Modifier.padding(start = 0.dp, top = 8.dp)
                    )
                }
                items(groupsList, key = { "group_${it.path ?: it.label}" }) { folder ->
                    val coverFile = folder.path?.let(coverLookup::get)
                    FolderGridItem(
                        folder = folder,
                        coverFile = coverFile,
                        canPasteToFolder = false,
                        isPinned = false,
                        onSelectFolder = onSelectFolder,
                        onPasteToFolder = onPasteToFolder,
                        onTogglePinnedFolder = onTogglePinnedFolder,
                        thumbnailPolicy = thumbnailPolicy
                    )
                }
            }

            // Section 4: Albums (Folders)
            if (otherFolders.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "section_albums_header") {
                    ArcileSectionHeader(
                        text = stringResource(
                            R.string.image_gallery_section_folders
                        ),
                        modifier = Modifier.padding(start = 0.dp, top = 8.dp)
                    )
                }
                items(otherFolders, key = { it.path ?: it.label }) { folder ->
                    val coverFile = folder.path?.let(coverLookup::get)
                    val canPasteToFolder = state.clipboardState != null && isPasteDestinationFolderPath(folder.path)
                    FolderGridItem(
                        folder = folder,
                        coverFile = coverFile,
                        canPasteToFolder = canPasteToFolder,
                        isPinned = false,
                        onSelectFolder = onSelectFolder,
                        onPasteToFolder = onPasteToFolder,
                        onTogglePinnedFolder = onTogglePinnedFolder,
                        thumbnailPolicy = thumbnailPolicy
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderGridItem(
    folder: MediaGalleryFolder,
    coverFile: FileModel?,
    canPasteToFolder: Boolean,
    isPinned: Boolean,
    onSelectFolder: (String?) -> Unit,
    onPasteToFolder: (String) -> Unit,
    onTogglePinnedFolder: (String) -> Unit,
    thumbnailPolicy: ThumbnailPolicy,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    val folderShape = ExpressiveShapes.large

    Box(modifier = modifier) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            shape = folderShape,
            modifier = Modifier
                .fillMaxWidth()
                .clip(folderShape)
                .combinedClickable(
                    onClick = { onSelectFolder(folder.path) },
                    onLongClick = {
                        if (folder.path != FAVORITES_FOLDER_PATH) {
                            showMenu = true
                        }
                    }
                )
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (coverFile != null) {
                        GalleryThumbnail(
                            file = coverFile,
                            thumbnailKey = ThumbnailKey.from(coverFile),
                            thumbnailPolicy = thumbnailPolicy,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }

                    if (folder.path == FAVORITES_FOLDER_PATH) {
                        Box(
                            modifier = Modifier
                                .padding(8.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                .size(32.dp)
                                .align(Alignment.TopStart),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                tint = Color.Red,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else if (isPinned) {
                        Box(
                            modifier = Modifier
                                .padding(8.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                .size(32.dp)
                                .align(Alignment.TopStart),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PushPin,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    if (canPasteToFolder) {
                        Surface(
                            onClick = { folder.path?.let(onPasteToFolder) },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            shadowElevation = 4.dp,
                            tonalElevation = 4.dp,
                            modifier = Modifier
                                .padding(8.dp)
                                .size(40.dp)
                                .align(Alignment.TopEnd)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = stringResource(R.string.action_paste_here),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Text(
                        text = folder.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = androidx.compose.ui.res.pluralStringResource(R.plurals.image_gallery_folder_count, folder.count, folder.count),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (showMenu && folder.path != null) {
            dev.qtremors.arcile.core.ui.ArcileDropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                items = listOf {
                    ArcileDropdownMenuItem(
                        text = {
                            Text(
                                text = if (isPinned) {
                                    stringResource(R.string.image_gallery_action_unpin_folder)
                                } else {
                                    stringResource(R.string.image_gallery_action_pin_folder)
                                }
                            )
                        },
                        onClick = {
                            onTogglePinnedFolder(folder.path)
                            showMenu = false
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.PushPin,
                                contentDescription = null
                            )
                        }
                    )
                }
            )
        }
    }
}
