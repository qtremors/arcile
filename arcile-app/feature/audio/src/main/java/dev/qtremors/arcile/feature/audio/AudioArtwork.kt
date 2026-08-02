package dev.qtremors.arcile.feature.audio

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.qtremors.arcile.core.storage.domain.AudioTrack
import dev.qtremors.arcile.core.ui.image.ThumbnailKey

@Composable
internal fun AudioArtwork(
    track: AudioTrack,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large
) {
    var retainedArtwork by remember { mutableStateOf<Painter?>(null) }
    Surface(
        modifier = modifier.clip(shape),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = shape
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            retainedArtwork?.let { artwork ->
                Image(
                    painter = artwork,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            AsyncImage(
                model = ThumbnailKey.from(track.file),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onSuccess = { retainedArtwork = it.painter },
                onError = { retainedArtwork = null },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
