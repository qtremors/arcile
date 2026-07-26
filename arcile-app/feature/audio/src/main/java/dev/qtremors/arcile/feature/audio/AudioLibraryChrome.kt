package dev.qtremors.arcile.feature.audio

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.presentation.formatFileSize
import dev.qtremors.arcile.core.storage.domain.AudioTrack
import dev.qtremors.arcile.core.ui.ArcileDropdownMenuItem
import dev.qtremors.arcile.core.ui.SplitButtonGroup
import dev.qtremors.arcile.core.ui.ToolbarAction
import dev.qtremors.arcile.core.ui.keyboardInputField
import dev.qtremors.arcile.core.ui.rememberArcileHaptics
import dev.qtremors.arcile.core.ui.theme.bounceClickable

@Composable
internal fun AudioLibraryFloatingTopBar(
    state: AudioLibraryState,
    currentTab: AudioLibraryTab,
    showSearchBar: Boolean,
    onSearchClick: () -> Unit,
    onCloseSearch: () -> Unit,
    onQueryChange: (String) -> Unit,
    onViewSort: () -> Unit,
    onDefaultTabChange: (AudioLibraryTab) -> Unit,
    onSelectAll: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberArcileHaptics()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    var showOverflow by rememberSaveable { mutableStateOf(false) }

    if (showSearchBar) {
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.85f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 4.dp,
            shadowElevation = 4.dp,
            modifier = modifier
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .height(48.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val closeSearch = {
                    haptics.selectionChanged()
                    onCloseSearch()
                }
                IconButton(
                    onClick = closeSearch,
                    modifier = Modifier.clip(CircleShape).bounceClickable(onClick = closeSearch)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(dev.qtremors.arcile.core.ui.R.string.back)
                    )
                }
                BasicTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                        .focusRequester(focusRequester)
                        .keyboardInputField(),
                    singleLine = true,
                    decorationBox = { inner ->
                        if (state.query.isEmpty()) {
                            Text(
                                stringResource(R.string.audio_search),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                        inner()
                    }
                )
                if (state.query.isNotEmpty()) {
                    val clearQuery = {
                        haptics.selectionChanged()
                        onQueryChange("")
                    }
                    IconButton(
                        onClick = clearQuery,
                        modifier = Modifier.clip(CircleShape).bounceClickable(onClick = clearQuery)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(
                                dev.qtremors.arcile.core.ui.R.string.action_clear
                            )
                        )
                    }
                }
            }
        }
        return
    }

    Box(
        modifier = modifier
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(56.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.85f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 4.dp,
            shadowElevation = 4.dp,
            modifier = Modifier
                .size(48.dp)
                .align(Alignment.CenterStart)
                .bounceClickable {
                    haptics.selectionChanged()
                    onNavigateBack()
                }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(dev.qtremors.arcile.core.ui.R.string.back)
                )
            }
        }
        Box(
            modifier = Modifier
                .height(48.dp)
                .align(Alignment.CenterEnd)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SplitButtonGroup(
                    actions = listOf(
                        ToolbarAction(
                            icon = Icons.Default.Search,
                            contentDescription = stringResource(
                                dev.qtremors.arcile.core.ui.R.string.action_search
                            ),
                            onClick = {
                                haptics.selectionChanged()
                                onSearchClick()
                            }
                        ),
                        ToolbarAction(
                            icon = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = stringResource(
                                dev.qtremors.arcile.core.ui.R.string.action_sort
                            ),
                            onClick = {
                                haptics.selectionChanged()
                                onViewSort()
                            }
                        )
                    ),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.85f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    height = 48.dp,
                    minWidth = 48.dp,
                    iconSize = 24.dp
                )
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.85f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .size(48.dp)
                        .bounceClickable {
                            haptics.toggleMenu()
                            showOverflow = true
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(
                                dev.qtremors.arcile.core.ui.R.string.action_more_options
                            )
                        )
                    }
                }
            }
            val overflowItems = buildList<@Composable () -> Unit> {
                AudioLibraryTab.entries.forEach { tab ->
                    add {
                        ArcileDropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(
                                        if (tab == AudioLibraryTab.AUDIO) {
                                            R.string.audio_open_to_audio
                                        } else {
                                            R.string.audio_open_to_folders
                                        }
                                    )
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    if (tab == AudioLibraryTab.AUDIO) {
                                        Icons.Default.MusicNote
                                    } else {
                                        Icons.Default.Folder
                                    },
                                    contentDescription = null
                                )
                            },
                            trailingIcon = if (state.defaultTab == tab) {
                                { Icon(Icons.Default.CheckCircle, contentDescription = null) }
                            } else {
                                null
                            },
                            onClick = {
                                onDefaultTabChange(tab)
                                showOverflow = false
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
                val hasItems = if (currentTab == AudioLibraryTab.AUDIO) {
                    state.visibleTracks.isNotEmpty()
                } else {
                    state.folders.isNotEmpty()
                }
                if (hasItems) {
                    add {
                        ArcileDropdownMenuItem(
                            text = {
                                Text(stringResource(dev.qtremors.arcile.core.ui.R.string.select_all))
                            },
                            leadingIcon = {
                                Icon(Icons.Default.SelectAll, contentDescription = null)
                            },
                            onClick = {
                                onSelectAll()
                                showOverflow = false
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
            dev.qtremors.arcile.core.ui.ArcileDropdownMenu(
                expanded = showOverflow,
                onDismissRequest = { showOverflow = false },
                items = overflowItems
            )
        }
    }
}

@Composable
internal fun AudioSelectionTopBar(
    selectedCount: Int,
    selectedSize: Long,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberArcileHaptics()
    Box(
        modifier = modifier
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(56.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
            modifier = Modifier
                .height(56.dp)
                .align(Alignment.CenterStart)
        ) {
            Row(
                modifier = Modifier.padding(start = 4.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    haptics.selectionChanged()
                    onClearSelection()
                }, modifier = Modifier.clip(CircleShape).bounceClickable {
                    haptics.selectionChanged()
                    onClearSelection()
                }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(dev.qtremors.arcile.core.ui.R.string.back)
                    )
                }
                Column {
                    Text(
                        stringResource(R.string.audio_selected_count, selectedCount),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        formatFileSize(selectedSize),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
        }
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
            modifier = Modifier
                .height(56.dp)
                .align(Alignment.CenterEnd)
        ) {
            Row(modifier = Modifier.padding(horizontal = 4.dp)) {
                IconButton(onClick = {
                    haptics.selectionChanged()
                    onSelectAll()
                }, modifier = Modifier.clip(CircleShape).bounceClickable {
                    haptics.selectionChanged()
                    onSelectAll()
                }) {
                    Icon(
                        Icons.Default.GridView,
                        contentDescription = stringResource(
                            dev.qtremors.arcile.core.ui.R.string.select_all
                        )
                    )
                }
                IconButton(onClick = {
                    haptics.selectionChanged()
                    onInvertSelection()
                }, modifier = Modifier.clip(CircleShape).bounceClickable {
                    haptics.selectionChanged()
                    onInvertSelection()
                }) {
                    Icon(
                        Icons.Default.SelectAll,
                        contentDescription = stringResource(
                            dev.qtremors.arcile.core.ui.R.string.invert_selection
                        )
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun AudioLibraryBottomBar(
    state: AudioLibraryState,
    currentTab: AudioLibraryTab,
    currentTrack: AudioTrack?,
    selectedTracks: List<AudioTrack>,
    playback: AudioPlaybackState,
    playerExpanded: Boolean,
    sharedTransitionScope: SharedTransitionScope,
    isChromeVisible: Boolean,
    onSelectTab: (AudioLibraryTab) -> Unit,
    onExpandPlayer: () -> Unit,
    onTogglePlayback: () -> Unit,
    onNext: () -> Unit,
    onPlaySelected: () -> Unit,
    onCopySelected: () -> Unit,
    onCutSelected: () -> Unit,
    onRenameSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onShareSelected: () -> Unit,
    onOpenProperties: () -> Unit,
    onCreateZip: () -> Unit,
    onOpenWith: () -> Unit,
    onShowContainingFolder: () -> Unit,
    onPaste: () -> Unit,
    onCancelClipboard: () -> Unit,
    onShowClipboardContents: () -> Unit,
    onClearActiveFileOperation: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSelectionMode = selectedTracks.isNotEmpty()
    val offset by animateDpAsState(
        targetValue = if (
            isChromeVisible ||
            isSelectionMode ||
            state.clipboardState != null ||
            state.activeFileOperation != null
        ) 0.dp else 160.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "audioBottomOffset"
    )
    val alpha by animateFloatAsState(
        targetValue = if (
            isChromeVisible ||
            isSelectionMode ||
            state.clipboardState != null ||
            state.activeFileOperation != null
        ) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "audioBottomAlpha"
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .graphicsLayer {
                translationY = offset.toPx()
                this.alpha = alpha
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 600.dp)
        ) {
            AnimatedVisibility(
                visible = currentTrack != null && !playerExpanded,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                currentTrack?.let {
                    Column {
                        AudioMiniPlayer(
                            track = it,
                            playback = playback,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = this@AnimatedVisibility,
                            onExpand = onExpandPlayer,
                            onTogglePlayback = onTogglePlayback,
                            onNext = onNext
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
            if (isSelectionMode) {
                AudioSelectionActionsBar(
                      canUseSingleTrackActions = selectedTracks.size == 1,
                      onPlay = onPlaySelected,
                      onCopy = onCopySelected,
                      onCut = onCutSelected,
                      onRename = onRenameSelected,
                      onDelete = onDeleteSelected,
                      onShare = onShareSelected,
                    onProperties = onOpenProperties,
                    onCreateZip = onCreateZip,
                    onOpenWith = onOpenWith,
                    onShowFolder = onShowContainingFolder
                )
              } else if (state.clipboardState != null || state.activeFileOperation != null) {
                  AudioClipboardToolbar(
                      state = state,
                      canPaste = state.folderFilter != null,
                      onPaste = onPaste,
                      onCancel = onCancelClipboard,
                      onShowContents = onShowClipboardContents,
                      onClearCompleted = onClearActiveFileOperation
                  )
              } else {
                Row(
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.85f),
                            CircleShape
                        )
                        .padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AudioTabItem(
                        selected = currentTab == AudioLibraryTab.AUDIO,
                        label = stringResource(R.string.audio_tracks),
                        icon = Icons.Default.MusicNote,
                        onClick = { onSelectTab(AudioLibraryTab.AUDIO) }
                    )
                    AudioTabItem(
                        selected = currentTab == AudioLibraryTab.FOLDERS,
                        label = stringResource(R.string.audio_folders),
                        icon = Icons.Default.Folder,
                        onClick = { onSelectTab(AudioLibraryTab.FOLDERS) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AudioTabItem(
    selected: Boolean,
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    val background by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        label = "audioTabBackground"
    )
    val content by animateColorAsState(
        if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "audioTabContent"
    )
    val horizontalPadding by animateDpAsState(
        if (selected) 16.dp else 12.dp,
        label = "audioTabPadding"
    )
    Surface(
        shape = CircleShape,
        color = background,
        contentColor = content,
        modifier = Modifier
            .height(44.dp)
            .clip(CircleShape)
            .bounceClickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = horizontalPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            AnimatedVisibility(
                visible = selected,
                enter = expandHorizontally() + fadeIn(),
                exit = shrinkHorizontally() + fadeOut()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
