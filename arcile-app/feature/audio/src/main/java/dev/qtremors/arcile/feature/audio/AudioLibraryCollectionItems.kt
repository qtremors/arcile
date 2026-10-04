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
import androidx.compose.material.icons.automirrored.filled.QueueMusic
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.ArcileDropdownMenu
import dev.qtremors.arcile.core.ui.ArcileDropdownMenuItem

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AudioCollectionListItem(
    collection: AudioCollection,
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
        Box(
            modifier = Modifier.size((58f * zoom).coerceIn(48f, 76f).dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            collection.coverTrack?.let {
                AudioArtwork(it, Modifier.fillMaxSize(), shape = MaterialTheme.shapes.medium)
            } ?: Icon(if (collection.kind == AudioCollectionType.Playlist) Icons.AutoMirrored.Filled.QueueMusic
                else Icons.Default.Image, contentDescription = null)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                collection.displayTitle(stringResource(R.string.audio_favorites)),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                collection.subtitle?.takeUnless { collection.isDirectory }.orEmpty().ifBlank {
                    androidx.compose.ui.res.pluralStringResource(
                        R.plurals.audio_song_count, collection.tracks.size, collection.tracks.size
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if ((showDetails || collection.kind == AudioCollectionType.Album) &&
                collection.subtitle != null && !collection.isDirectory) {
                Text(
                    androidx.compose.ui.res.pluralStringResource(
                        R.plurals.audio_song_count, collection.tracks.size, collection.tracks.size
                    ),
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
        if (collection.isDirectory && !isSelected) {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.audio_folder_options)
                    )
                }
                AudioFolderOptionsMenu(
                    collection = collection,
                    expanded = showMenu,
                    onDismiss = { showMenu = false },
                    onTogglePin = onTogglePin,
                    onChooseCover = onChooseCover,
                    onResetCover = onResetCover
                )
            }
        }
        if (collection.kind == AudioCollectionType.Playlist && !isSelected) {
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
internal fun AudioCollectionGridItem(
    collection: AudioCollection,
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
    var showFolderMenu by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = modifier.clip(MaterialTheme.shapes.large)
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerLow)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            collection.coverTrack?.let { cover ->
                AudioArtwork(cover, Modifier.fillMaxSize(), shape = MaterialTheme.shapes.medium)
            } ?: Icon(if (collection.kind == AudioCollectionType.Playlist) Icons.AutoMirrored.Filled.QueueMusic
                else Icons.Default.Image, contentDescription = null, modifier = Modifier.size(48.dp))
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
        if (collection.kind == AudioCollectionType.Playlist && !isSelected) {
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
        if (collection.isDirectory && !isSelected && !canPaste) {
            Box(modifier = Modifier.align(Alignment.TopEnd)) {
                IconButton(onClick = { showFolderMenu = true }) {
                    Icon(Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.audio_folder_options))
                }
                AudioFolderOptionsMenu(
                    collection = collection,
                    expanded = showFolderMenu,
                    onDismiss = { showFolderMenu = false },
                    onTogglePin = onTogglePin,
                    onChooseCover = onChooseCover,
                    onResetCover = onResetCover
                )
            }
        }
        }
        Text(
            collection.displayTitle(stringResource(R.string.audio_favorites)),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp)
        )
        Text(
            collection.subtitle?.takeUnless { collection.isDirectory }.orEmpty().ifBlank {
                androidx.compose.ui.res.pluralStringResource(
                    R.plurals.audio_song_count, collection.tracks.size, collection.tracks.size
                )
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp)
        )
        if (collection.kind == AudioCollectionType.Album && !collection.subtitle.isNullOrBlank()) {
            Text(androidx.compose.ui.res.pluralStringResource(
                R.plurals.audio_song_count, collection.tracks.size, collection.tracks.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp))
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
    collection: AudioCollection,
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
                                if (collection.isPinned) {
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
            if (collection.customCoverPath != null) {
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

internal fun AudioCollection.displayTitle(favoritesTitle: String): String =
    if (kind == AudioCollectionType.Favorites) favoritesTitle else title
