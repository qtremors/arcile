@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package dev.qtremors.arcile.feature.imagegallery

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.DropdownMenuItem
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
import dev.qtremors.arcile.image.ThumbnailKey
import dev.qtremors.arcile.image.ThumbnailPolicy
import dev.qtremors.arcile.shared.ui.ArcilePullRefreshIndicator
import dev.qtremors.arcile.shared.ui.ArcileSectionHeader

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ImageGalleryAlbumsGrid(
    state: ImageGalleryState,
    gridMinCellSize: Float,
    onAlbumsGridCellSizeChange: (Float) -> Unit,
    onAlbumsGridCellSizeFinalized: (Float) -> Unit,
    contentPadding: PaddingValues,
    onSelectAlbum: (String?) -> Unit,
    onRefresh: () -> Unit,
    gridState: LazyGridState,
    onPasteToAlbum: (String) -> Unit = {},
    onTogglePinnedAlbum: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val pullRefreshState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
    val thumbnailPolicy = remember { ThumbnailPolicy() }
    val sortedAlbums = remember(state.albums, state.albumPresentation.sortOption) {
        when (state.albumPresentation.sortOption) {
            FileSortOption.NAME_ASC -> state.albums.sortedBy { it.label.lowercase() }
            FileSortOption.NAME_DESC -> state.albums.sortedByDescending { it.label.lowercase() }
            FileSortOption.SIZE_LARGEST -> state.albums.sortedByDescending { it.count }
            FileSortOption.SIZE_SMALLEST -> state.albums.sortedBy { it.count }
            FileSortOption.DATE_NEWEST -> state.albums.sortedByDescending { it.lastModified }
            FileSortOption.DATE_OLDEST -> state.albums.sortedBy { it.lastModified }
        }
    }

    val favoritesLabel = stringResource(R.string.image_gallery_favorites_folder)
    val albumsList = remember(sortedAlbums, state.files, state.favoriteFiles, favoritesLabel) {
        buildVisibleAlbumTiles(
            sortedAlbums = sortedAlbums,
            files = state.files,
            favoriteFiles = state.favoriteFiles,
            favoritesLabel = favoritesLabel
        )
    }
    val coverLookup = remember(state.files, state.favoriteFiles, state.albumCovers) {
        buildAlbumCoverLookup(
            files = state.files,
            favoriteFiles = state.favoriteFiles,
            albumCovers = state.albumCovers
        )
    }

    val favoritesAlbum = remember(albumsList) { albumsList.firstOrNull { it.path == FAVORITES_ALBUM_PATH } }
    val regularAlbums = remember(albumsList) { albumsList.filter { it.path != FAVORITES_ALBUM_PATH } }
    val pinnedAlbumsList = remember(regularAlbums, state.pinnedAlbums) {
        regularAlbums.filter { it.path in state.pinnedAlbums }
    }
    val otherAlbums = remember(regularAlbums, state.pinnedAlbums) {
        regularAlbums.filter { it.path !in state.pinnedAlbums }
    }
    val groupsList = emptyList<ImageGalleryAlbum>() // Placeholder for future custom album groups

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
                    onSizeChanged = onAlbumsGridCellSizeChange,
                    onSizeFinalized = onAlbumsGridCellSizeFinalized
                )
                .padding(horizontal = 16.dp)
        ) {
            // Section 1: Favourites
            if (favoritesAlbum != null) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "section_favorites_header") {
                    ArcileSectionHeader(
                        text = stringResource(R.string.image_gallery_section_favourites),
                        modifier = Modifier.padding(start = 0.dp, top = 8.dp)
                    )
                }
                item(key = FAVORITES_ALBUM_PATH) {
                    AlbumGridItem(
                        album = favoritesAlbum,
                        coverFile = coverLookup[FAVORITES_ALBUM_PATH],
                        canPasteToAlbum = false,
                        isPinned = false,
                        onSelectAlbum = onSelectAlbum,
                        onPasteToAlbum = onPasteToAlbum,
                        onTogglePinnedAlbum = onTogglePinnedAlbum,
                        thumbnailPolicy = thumbnailPolicy
                    )
                }
            }

            // Section 2: Pinned Albums
            if (pinnedAlbumsList.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "section_pinned_header") {
                    ArcileSectionHeader(
                        text = stringResource(R.string.image_gallery_section_pinned),
                        modifier = Modifier.padding(start = 0.dp, top = 8.dp)
                    )
                }
                items(pinnedAlbumsList, key = { "pinned_${it.path ?: it.label}" }) { album ->
                    val coverFile = album.path?.let(coverLookup::get)
                    val canPasteToAlbum = state.clipboardState != null && isPasteDestinationAlbumPath(album.path)
                    AlbumGridItem(
                        album = album,
                        coverFile = coverFile,
                        canPasteToAlbum = canPasteToAlbum,
                        isPinned = true,
                        onSelectAlbum = onSelectAlbum,
                        onPasteToAlbum = onPasteToAlbum,
                        onTogglePinnedAlbum = onTogglePinnedAlbum,
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
                items(groupsList, key = { "group_${it.path ?: it.label}" }) { album ->
                    val coverFile = album.path?.let(coverLookup::get)
                    AlbumGridItem(
                        album = album,
                        coverFile = coverFile,
                        canPasteToAlbum = false,
                        isPinned = false,
                        onSelectAlbum = onSelectAlbum,
                        onPasteToAlbum = onPasteToAlbum,
                        onTogglePinnedAlbum = onTogglePinnedAlbum,
                        thumbnailPolicy = thumbnailPolicy
                    )
                }
            }

            // Section 4: Albums (Folders)
            if (otherAlbums.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "section_albums_header") {
                    ArcileSectionHeader(
                        text = stringResource(R.string.image_gallery_section_albums),
                        modifier = Modifier.padding(start = 0.dp, top = 8.dp)
                    )
                }
                items(otherAlbums, key = { it.path ?: it.label }) { album ->
                    val coverFile = album.path?.let(coverLookup::get)
                    val canPasteToAlbum = state.clipboardState != null && isPasteDestinationAlbumPath(album.path)
                    AlbumGridItem(
                        album = album,
                        coverFile = coverFile,
                        canPasteToAlbum = canPasteToAlbum,
                        isPinned = false,
                        onSelectAlbum = onSelectAlbum,
                        onPasteToAlbum = onPasteToAlbum,
                        onTogglePinnedAlbum = onTogglePinnedAlbum,
                        thumbnailPolicy = thumbnailPolicy
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumGridItem(
    album: ImageGalleryAlbum,
    coverFile: FileModel?,
    canPasteToAlbum: Boolean,
    isPinned: Boolean,
    onSelectAlbum: (String?) -> Unit,
    onPasteToAlbum: (String) -> Unit,
    onTogglePinnedAlbum: (String) -> Unit,
    thumbnailPolicy: ThumbnailPolicy,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { onSelectAlbum(album.path) },
                    onLongClick = {
                        if (album.path != FAVORITES_ALBUM_PATH) {
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

                    if (album.path == FAVORITES_ALBUM_PATH) {
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

                    if (canPasteToAlbum) {
                        Surface(
                            onClick = { album.path?.let(onPasteToAlbum) },
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
                        text = album.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.image_gallery_album_count, album.count),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (showMenu && album.path != null) {
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = if (isPinned) {
                                stringResource(R.string.image_gallery_action_unpin_album)
                            } else {
                                stringResource(R.string.image_gallery_action_pin_album)
                            }
                        )
                    },
                    onClick = {
                        onTogglePinnedAlbum(album.path)
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
        }
    }
}
