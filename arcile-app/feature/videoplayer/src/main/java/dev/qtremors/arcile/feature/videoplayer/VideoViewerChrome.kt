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
import androidx.compose.runtime.mutableFloatStateOf
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

internal data class VideoViewerChromeActions(
    val onPageSelected: suspend (Int) -> Unit,
    val onToggleFavorite: (String) -> Unit,
    val onDelete: (String) -> Unit,
    val onToggleSelection: (String) -> Unit,
    val onShowMetadata: (String) -> Unit,
    val onOpenWith: (FileModel) -> Unit,
    val onShare: (FileModel) -> Unit,
    val onPlayPauseToggle: () -> Unit,
    val onSeek: (Long) -> Unit,
    val onResizeModeToggle: () -> Unit
)

// ──────────────────────────────────────────────────────────────
// Top Chrome – matches ImageViewerTopChrome layout exactly:
// floating rounded pill with statusBarsPadding
// ──────────────────────────────────────────────────────────────
@Composable
internal fun VideoViewerTopChrome(
    visible: Boolean,
    currentFile: FileModel?,
    positionText: String,
    dateText: String,
    resolutionText: String,
    sizeText: String,
    marqueeEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)),
        exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow)),
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            if (currentFile != null) {
                Column(
                    horizontalAlignment = Alignment.Start,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 4.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    val titleText = currentFile.name
                    Text(
                        text = if (titleText.isBlank()) positionText else "$positionText \u2022 $titleText",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = if (marqueeEnabled) TextOverflow.Clip else TextOverflow.Ellipsis,
                        modifier = if (marqueeEnabled) Modifier.basicMarquee() else Modifier
                    )
                    if (dateText.isNotEmpty() || resolutionText.isNotEmpty() || sizeText.isNotEmpty()) {
                        Text(
                            text = listOf(resolutionText, sizeText, dateText)
                                .filter { it.isNotBlank() }
                                .joinToString(" \u2022 "),
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = if (marqueeEnabled) TextOverflow.Clip else TextOverflow.Ellipsis,
                            modifier = if (marqueeEnabled) Modifier.basicMarquee() else Modifier
                        )
                    }
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────
// Bottom Chrome – matches ImageViewerBottomChrome layout:
// thumbnail carousel + seek bar + SplitButtonGroup + overflow
// ──────────────────────────────────────────────────────────────
@Composable
internal fun VideoViewerBottomChrome(
    visible: Boolean,
    files: List<FileModel>,
    currentPage: Int,
    currentFile: FileModel?,
    favoriteFiles: Set<String>,
    selectedFiles: Set<String>,
    selectionModeEnabled: Boolean,
    readOnly: Boolean,
    isPlaying: Boolean,
    playbackPosition: Long,
    playbackDuration: Long,
    canOpenWith: Boolean,
    canShare: Boolean,
    resizeModeIndex: Int,
    actions: VideoViewerChromeActions,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val haptics = rememberArcileHaptics()

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)),
        exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // 1. Thumbnail carousel – identical to image viewer
            val lazyListState = rememberLazyListState()
            var previousThumbnailPage by remember(files) { mutableStateOf<Int?>(null) }
            LaunchedEffect(currentPage) {
                if (files.isNotEmpty() && currentPage in files.indices) {
                    when (viewerThumbnailScrollAction(previousThumbnailPage, currentPage)) {
                        ViewerThumbnailScrollAction.Jump -> lazyListState.scrollToItem(currentPage)
                        ViewerThumbnailScrollAction.Animate -> lazyListState.animateScrollToItem(currentPage)
                        ViewerThumbnailScrollAction.None -> Unit
                    }
                    previousThumbnailPage = currentPage
                }
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(vertical = 8.dp)
            ) {
                val thumbnailWidth = 28.dp
                val thumbnailSidePadding = ((maxWidth - thumbnailWidth) / 2).coerceAtLeast(16.dp)
                LazyRow(
                    state = lazyListState,
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                    contentPadding = PaddingValues(horizontal = thumbnailSidePadding)
                ) {
                    itemsIndexed(
                        items = files,
                        key = { _, file -> file.absolutePath }
                    ) { index, file ->
                        val isSelected = currentPage == index
                        val animElevation by animateDpAsState(
                            targetValue = if (isSelected) 6.dp else 0.dp,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "thumbnailElevation"
                        )
                        val animScale by animateFloatAsState(
                            targetValue = if (isSelected) 1f else 0.82f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            ),
                            label = "thumbnailScale"
                        )

                        Box(
                            modifier = Modifier
                                .width(36.dp)
                                .height(54.dp)
                                .zIndex(if (isSelected) 1f else 0f)
                                .graphicsLayer {
                                    scaleX = animScale
                                    scaleY = animScale
                                }
                                .shadow(elevation = animElevation, shape = RoundedCornerShape(4.dp))
                                .clip(RoundedCornerShape(4.dp))
                                .border(
                                    width = if (isSelected) 2.dp else 0.dp,
                                    color = if (isSelected) Color.White else Color.Transparent,
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .semantics {
                                    contentDescription = file.name
                                    selected = isSelected
                                }
                                .bounceClickable {
                                    coroutineScope.launch { actions.onPageSelected(index) }
                                }
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(file.absolutePath)
                                    .crossfade(false)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }

            // 2. Playback seek bar (video-specific addition)
            if (currentFile != null) {
                var isSeeking by remember(currentFile.absolutePath) { mutableStateOf(false) }
                var sliderValue by remember(currentFile.absolutePath) { mutableFloatStateOf(0f) }
                val seekState = videoSeekState(playbackPosition, playbackDuration)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val currentText = formatPlaybackTime(if (isSeeking) (sliderValue * playbackDuration).toLong() else playbackPosition)
                    val totalText = formatPlaybackTime(playbackDuration)

                    Text(
                        text = currentText,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f)
                    )

                    Slider(
                        value = if (isSeeking) sliderValue else seekState.progress,
                        enabled = seekState.canSeek,
                        onValueChange = {
                            isSeeking = true
                            sliderValue = it
                        },
                        onValueChangeFinished = {
                            isSeeking = false
                            actions.onSeek((sliderValue * playbackDuration).toLong())
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.24f),
                            disabledThumbColor = Color.White,
                            disabledActiveTrackColor = Color.White,
                            disabledInactiveTrackColor = Color.White.copy(alpha = 0.24f)
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    Text(
                        text = totalText,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }

            // 3. Action buttons row – matches image viewer layout:
            //    SplitButtonGroup (left) + overflow CircleShape button (right)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isFavorite = currentFile != null && currentFile.absolutePath in favoriteFiles
                val favoriteDescription = stringResource(R.string.action_favorite)
                val deleteDescription = stringResource(R.string.action_delete_selected)
                val isSelected = currentFile != null && currentFile.absolutePath in selectedFiles
                val selectionDescription = stringResource(
                    if (isSelected) R.string.deselect_video else R.string.select_video
                )
                val deleteTint = MaterialTheme.colorScheme.error
                val playDescription = stringResource(R.string.video_player_play)
                val pauseDescription = stringResource(R.string.video_player_pause)
                val toolbarActions = remember(
                    currentFile,
                    isFavorite,
                    favoriteDescription,
                    deleteDescription,
                    isSelected,
                    selectionDescription,
                    selectionModeEnabled,
                    readOnly,
                    deleteTint,
                    isPlaying,
                    playDescription,
                    pauseDescription,
                    actions
                ) {
                    buildList {
                        if (selectionModeEnabled) {
                            add(
                                ToolbarAction(
                                    icon = if (isSelected) {
                                        Icons.Default.CheckCircle
                                    } else {
                                        Icons.Default.RadioButtonUnchecked
                                    },
                                    contentDescription = selectionDescription,
                                    tint = Color.White,
                                    onClick = {
                                        currentFile?.let {
                                            haptics.selectionChanged()
                                            actions.onToggleSelection(it.absolutePath)
                                        }
                                    }
                                )
                            )
                        }
                        // Play/Pause action in the split button group
                        add(
                            ToolbarAction(
                                icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) pauseDescription else playDescription,
                                tint = Color.White,
                                onClick = {
                                    haptics.selectionChanged()
                                    actions.onPlayPauseToggle()
                                }
                            )
                        )
                        if (!readOnly) {
                            add(
                                ToolbarAction(
                                    icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = favoriteDescription,
                                    tint = if (isFavorite) Color.Red else Color.White,
                                    onClick = {
                                        currentFile?.let {
                                            haptics.selectionChanged()
                                            actions.onToggleFavorite(it.absolutePath)
                                        }
                                    }
                                )
                            )
                        }
                        if (!readOnly) {
                            add(
                                ToolbarAction(
                                    icon = Icons.Default.Delete,
                                    contentDescription = deleteDescription,
                                    tint = deleteTint,
                                    onClick = {
                                        currentFile?.let {
                                            haptics.selectionStart()
                                            actions.onDelete(it.absolutePath)
                                        }
                                    }
                                )
                            )
                        }
                    }
                }

                SplitButtonGroup(
                    actions = toolbarActions,
                    containerColor = Color.Black.copy(alpha = 0.5f),
                    contentColor = Color.White,
                    height = 56.dp,
                    minWidth = if (selectionModeEnabled) 52.dp else 64.dp,
                    iconSize = 28.dp
                )

                VideoViewerOverflowMenu(
                    currentFile = currentFile,
                    readOnly = readOnly,
                    canOpenWith = canOpenWith,
                    canShare = canShare,
                    resizeModeIndex = resizeModeIndex,
                    actions = actions
                )
            }
        }
    }
}
