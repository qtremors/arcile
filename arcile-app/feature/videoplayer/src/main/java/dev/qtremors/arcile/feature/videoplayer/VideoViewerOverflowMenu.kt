package dev.qtremors.arcile.feature.videoplayer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.ArcileDropdownMenuItem
import dev.qtremors.arcile.core.ui.SplitButtonGroup
import dev.qtremors.arcile.core.ui.ToolbarAction
import dev.qtremors.arcile.core.ui.rememberArcileHaptics
import dev.qtremors.arcile.core.ui.theme.bounceClickable
import dev.qtremors.arcile.core.ui.theme.menuGroupFirst
import dev.qtremors.arcile.core.ui.theme.menuGroupLast
import dev.qtremors.arcile.core.ui.theme.menuGroupMiddle
import dev.qtremors.arcile.core.ui.theme.menuGroupSingle
import kotlinx.coroutines.launch
import java.util.Locale

// ──────────────────────────────────────────────────────────────
// Overflow Menu – matches ImageViewerOverflowMenu exactly:
// CircleShape 56dp button + shaped dropdown items
// ──────────────────────────────────────────────────────────────
@Composable
internal fun VideoViewerOverflowMenu(
    currentFile: FileModel?,
    readOnly: Boolean,
    canOpenWith: Boolean,
    canShare: Boolean,
    resizeModeIndex: Int,
    actions: VideoViewerChromeActions
) {
    var expanded by remember { mutableStateOf(false) }
    val hasActions = !readOnly || canOpenWith || canShare

    if (!hasActions) return

    Box {
        Surface(
            onClick = { expanded = true },
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.5f),
            modifier = Modifier.size(56.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.action_more_options),
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.width(200.dp)
        ) {
            val menuActions = buildList<@Composable () -> Unit> {
                add {
                    ViewerOverflowMenuItem(
                        text = stringResource(
                            when (resizeModeIndex) {
                                0 -> R.string.video_player_resize_fit
                                1 -> R.string.video_player_resize_zoom
                                else -> R.string.video_player_resize_fill
                            }
                        ),
                        icon = { Icon(Icons.Default.AspectRatio, contentDescription = null) },
                        onClick = {
                            expanded = false
                            actions.onResizeModeToggle()
                        }
                    )
                }
                if (!readOnly) add {
                    ViewerOverflowMenuItem(
                        text = stringResource(R.string.action_info),
                        icon = { Icon(Icons.Default.Info, contentDescription = null) },
                        onClick = {
                            expanded = false
                            currentFile?.let { actions.onShowMetadata(it.absolutePath) }
                        }
                    )
                }
                if (canOpenWith) add {
                    ViewerOverflowMenuItem(
                        text = stringResource(R.string.image_gallery_open_with),
                        icon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
                        onClick = {
                            expanded = false
                            currentFile?.let(actions.onOpenWith)
                        }
                    )
                }
                if (canShare) add {
                    ViewerOverflowMenuItem(
                        text = stringResource(R.string.share),
                        icon = { Icon(Icons.Default.Share, contentDescription = null) },
                        onClick = {
                            expanded = false
                            currentFile?.let(actions.onShare)
                        }
                    )
                }
            }

            menuActions.forEachIndexed { index, action ->
                val shape = when {
                    menuActions.size == 1 -> MaterialTheme.shapes.menuGroupSingle
                    index == 0 -> MaterialTheme.shapes.menuGroupFirst
                    index == menuActions.lastIndex -> MaterialTheme.shapes.menuGroupLast
                    else -> MaterialTheme.shapes.menuGroupMiddle
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                ) {
                    action()
                }
            }
        }
    }
}

@Composable
private fun ViewerOverflowMenuItem(
    text: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    ArcileDropdownMenuItem(
        text = {
            Text(
                text = text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingIcon = icon,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    )
}

// ──────────────────────────────────────────────────────────────
// Thumbnail scroll action – matches image viewer logic
// ──────────────────────────────────────────────────────────────
internal enum class ViewerThumbnailScrollAction { Jump, Animate, None }

internal fun viewerThumbnailScrollAction(previousPage: Int?, currentPage: Int): ViewerThumbnailScrollAction {
    if (previousPage == null) return ViewerThumbnailScrollAction.Jump
    if (previousPage == currentPage) return ViewerThumbnailScrollAction.None
    return ViewerThumbnailScrollAction.Animate
}

internal fun formatPlaybackTime(timeMs: Long): String {
    val totalSeconds = (timeMs / 1000).coerceAtLeast(0L)
    val seconds = totalSeconds % 60
    val totalMinutes = totalSeconds / 60
    val minutes = totalMinutes % 60
    val hours = totalMinutes / 60
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}
