package dev.qtremors.arcile.feature.audio

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.presentation.formatFileSize
import dev.qtremors.arcile.core.ui.ArcileDropdownMenu
import dev.qtremors.arcile.core.ui.ArcileDropdownMenuItem
import dev.qtremors.arcile.core.ui.category.CategoryFolderGridItem
import dev.qtremors.arcile.core.ui.category.CategoryItemInfo

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AudioFolderListItem(
    folder: AudioFolder,
    zoom: Float,
    isSelected: Boolean,
    showDetails: Boolean,
    canPaste: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPaste: () -> Unit,
    onTogglePin: () -> Unit,
    onChooseCover: () -> Unit,
    onResetCover: () -> Unit,
    onRenamePlaylist: () -> Unit = {},
    onDeletePlaylist: () -> Unit = {},
    onEditPlaylist: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showMenu by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(
                if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                } else {
                    androidx.compose.ui.graphics.Color.Transparent
                }
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        folder.coverTrack?.let { AudioArtwork(it, Modifier.size((48f * zoom).dp)) }
            ?: Icon(Icons.Default.Image, contentDescription = null,
                modifier = Modifier.size((48f * zoom).dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                folder.displayTitle(stringResource(R.string.audio_favorites)),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                androidx.compose.ui.res.pluralStringResource(R.plurals.audio_track_count, folder.tracks.size, folder.tracks.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (showDetails) {
                Text(
                    "${folder.subtitle.orEmpty()} • ${formatFileSize(androidx.compose.ui.platform.LocalContext.current, folder.totalSize)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (canPaste && !isSelected) {
            IconButton(onClick = onPaste) {
                Icon(
                    Icons.Default.ContentPaste,
                    contentDescription = stringResource(R.string.audio_paste_here),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        } else if (isSelected) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(22.dp)
            )
        }
        if (folder.isDirectory && !isSelected) {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.audio_folder_options)
                    )
                }
                AudioFolderOptionsMenu(
                    folder = folder,
                    expanded = showMenu,
                    onDismiss = { showMenu = false },
                    onTogglePin = onTogglePin,
                    onChooseCover = onChooseCover,
                    onResetCover = onResetCover
                )
            }
        }
        if (folder.kind == AudioFolderKind.Playlist && !isSelected) {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.audio_playlist_options))
                }
                AudioPlaylistOptionsMenu(
                    expanded = showMenu,
                    onDismiss = { showMenu = false },
                    onEdit = onEditPlaylist,
                    onRename = onRenamePlaylist,
                    onDelete = onDeletePlaylist
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AudioFolderGridItem(
    folder: AudioFolder,
    isSelected: Boolean,
    canPaste: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPaste: () -> Unit,
    onTogglePin: () -> Unit,
    onChooseCover: () -> Unit,
    onResetCover: () -> Unit,
    onRenamePlaylist: () -> Unit = {},
    onDeletePlaylist: () -> Unit = {},
    onEditPlaylist: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showPlaylistMenu by rememberSaveable { mutableStateOf(false) }
    Box(modifier = modifier) {
        CategoryFolderGridItem(
            info = CategoryItemInfo(
                title = folder.displayTitle(stringResource(R.string.audio_favorites)),
                detailLines = listOf(
                    androidx.compose.ui.res.pluralStringResource(R.plurals.audio_track_count, folder.tracks.size, folder.tracks.size),
                    formatFileSize(androidx.compose.ui.platform.LocalContext.current, folder.totalSize)
                )
            ),
            onClick = onClick,
            onLongClick = onLongClick
        ) {
            folder.coverTrack?.let { cover ->
                AudioArtwork(cover, Modifier.fillMaxSize(), shape = RectangleShape)
            } ?: Icon(Icons.Default.Image, contentDescription = null,
                modifier = Modifier.size(48.dp))
        }
        if (canPaste && !isSelected) {
            Surface(
                onClick = onPaste,
                shape = androidx.compose.foundation.shape.CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .padding(8.dp)
                    .size(40.dp)
                    .align(Alignment.TopEnd)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.ContentPaste,
                        contentDescription = stringResource(R.string.audio_paste_here)
                    )
                }
            }
        } else if (isSelected) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(8.dp)
                    .size(24.dp)
                    .align(Alignment.TopEnd)
            )
        }
        if (folder.kind == AudioFolderKind.Playlist && !isSelected) {
            Box(modifier = Modifier.align(Alignment.TopEnd)) {
                IconButton(onClick = { showPlaylistMenu = true }) {
                    Icon(Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.audio_playlist_options))
                }
                AudioPlaylistOptionsMenu(
                    expanded = showPlaylistMenu,
                    onDismiss = { showPlaylistMenu = false },
                    onEdit = onEditPlaylist,
                    onRename = onRenamePlaylist,
                    onDelete = onDeletePlaylist
                )
            }
        }
    }
}

@Composable
private fun AudioPlaylistOptionsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    ArcileDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        items = listOf(
            { ArcileDropdownMenuItem(text = stringResource(R.string.audio_edit_playlist_songs),
                onClick = { onDismiss(); onEdit() }) },
            { ArcileDropdownMenuItem(text = stringResource(R.string.audio_rename_playlist),
                onClick = { onDismiss(); onRename() }) },
            { ArcileDropdownMenuItem(text = stringResource(R.string.audio_delete_playlist),
                onClick = { onDismiss(); onDelete() }) }
        )
    )
}

@Composable
private fun AudioFolderOptionsMenu(
    folder: AudioFolder,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onTogglePin: () -> Unit,
    onChooseCover: () -> Unit,
    onResetCover: () -> Unit
) {
    ArcileDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        items = buildList {
            add {
                ArcileDropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                if (folder.isPinned) {
                                    R.string.audio_unpin_folder
                                } else {
                                    R.string.audio_pin_folder
                                }
                            )
                        )
                    },
                    leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null) },
                    onClick = {
                        onDismiss()
                        onTogglePin()
                    }
                )
            }
            add {
                ArcileDropdownMenuItem(
                    text = { Text(stringResource(R.string.audio_choose_folder_cover)) },
                    leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                    onClick = {
                        onDismiss()
                        onChooseCover()
                    }
                )
            }
            if (folder.customCoverPath != null) {
                add {
                    ArcileDropdownMenuItem(
                        text = { Text(stringResource(R.string.audio_reset_folder_cover)) },
                        leadingIcon = { Icon(Icons.Default.Restore, contentDescription = null) },
                        onClick = {
                            onDismiss()
                            onResetCover()
                        }
                    )
                }
            }
        }
    )
}

internal fun AudioFolder.displayTitle(favoritesTitle: String): String =
    if (kind == AudioFolderKind.Favorites) favoritesTitle else title
