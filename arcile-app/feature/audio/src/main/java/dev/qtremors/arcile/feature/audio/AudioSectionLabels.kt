package dev.qtremors.arcile.feature.audio

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

@Composable
internal fun AudioCollectionKind.displayName(): String = stringResource(
    when (this) {
        AudioCollectionKind.SONGS -> R.string.audio_tracks
        AudioCollectionKind.FOLDERS -> R.string.audio_folders
        AudioCollectionKind.ALBUMS -> R.string.audio_albums
        AudioCollectionKind.ARTISTS -> R.string.audio_artists
        AudioCollectionKind.GENRES -> R.string.audio_genres
        AudioCollectionKind.PLAYLISTS -> R.string.audio_playlists
    }
)
