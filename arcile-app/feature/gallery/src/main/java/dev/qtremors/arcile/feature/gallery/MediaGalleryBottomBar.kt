package dev.qtremors.arcile.feature.gallery

import dev.qtremors.arcile.core.storage.domain.CategoryLibraryPage
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.ArcileDropdownMenuItem
import dev.qtremors.arcile.core.ui.SplitButtonGroup
import dev.qtremors.arcile.core.ui.ToolbarAction
import dev.qtremors.arcile.core.ui.category.CategoryNavigationBar
import dev.qtremors.arcile.core.ui.category.CategoryBottomChrome
import dev.qtremors.arcile.core.ui.category.CategoryTabSpec
import dev.qtremors.arcile.core.ui.theme.menuGroupFirst
import dev.qtremors.arcile.core.ui.theme.menuGroupLast
import dev.qtremors.arcile.core.ui.theme.menuGroupMiddle
import dev.qtremors.arcile.core.ui.theme.menuGroupSingle

@Composable
internal fun BoxScope.MediaGalleryBottomBar(
    state: MediaGalleryState,
    currentTab: CategoryLibraryPage,
    isTopBarVisible: Boolean,
    isBackPredicting: Boolean,
    backProgress: Float,
    selectionActions: GallerySelectionActions,
    deleteActions: GalleryDeleteActions,
    clipboardActions: GalleryClipboardActions,
    fileActions: GalleryFileActions,
    onSelectItems: () -> Unit,
    onSelectFolders: () -> Unit,
    onShowRenameDialog: () -> Unit,
    onShowClipboardContents: () -> Unit
) {
    val isSelectionMode = state.selectedFiles.isNotEmpty()
    CategoryBottomChrome(
        visible = isTopBarVisible,
        selectionMode = isSelectionMode,
        selectionBackProgress = if (isBackPredicting) backProgress else 0f,
        normalContent = {
                GalleryNavigationOrClipboardBar(
                    state = state,
                    currentTab = currentTab,
                    clipboardActions = clipboardActions,
                    onSelectItems = onSelectItems,
                    onSelectFolders = onSelectFolders,
                    onShowClipboardContents = onShowClipboardContents
                )
        },
        selectionContent = {
                GallerySelectionActionsBar(
                    state = state,
                    selectionActions = selectionActions,
                    deleteActions = deleteActions,
                    clipboardActions = clipboardActions,
                    fileActions = fileActions,
                    onShowRenameDialog = onShowRenameDialog
                )
        },
        modifier = Modifier.align(Alignment.BottomCenter)
    )
}
@Composable
private fun GalleryNavigationOrClipboardBar(
    state: MediaGalleryState,
    currentTab: CategoryLibraryPage,
    clipboardActions: GalleryClipboardActions,
    onSelectItems: () -> Unit,
    onSelectFolders: () -> Unit,
    onShowClipboardContents: () -> Unit
) {
    val folderPastePath = state.selectedFolderPath?.takeIf(::isPasteDestinationFolderPath)
    if (state.clipboardState != null || state.activeFileOperation != null) {
        GalleryClipboardOperationToolbar(
            state = state,
            pasteDestinationPath = folderPastePath.takeIf { currentTab == CategoryLibraryPage.FOLDERS },
            onPasteToFolder = clipboardActions.pasteToFolder,
            onCancelClipboard = clipboardActions.cancel,
            onShowClipboardContents = onShowClipboardContents
        )
        return
    }

    CategoryNavigationBar(
        tabs = listOf(
            CategoryTabSpec(
                selected = currentTab == CategoryLibraryPage.ITEMS,
                label = stringResource(
                    if (state.isVideoGallery) {
                        R.string.video_gallery_tab_videos
                    } else {
                        R.string.image_gallery_tab_photos
                    }
                ),
                icon = if (state.isVideoGallery) {
                    Icons.Default.VideoLibrary
                } else {
                    Icons.Default.Image
                },
                onClick = onSelectItems
            ),
            CategoryTabSpec(
                selected = currentTab == CategoryLibraryPage.FOLDERS,
                label = stringResource(R.string.image_gallery_tab_folders),
                icon = Icons.Default.Folder,
                onClick = onSelectFolders
            )
        )
    )
}

@Composable
private fun GallerySelectionActionsBar(
    state: MediaGalleryState,
    selectionActions: GallerySelectionActions,
    deleteActions: GalleryDeleteActions,
    clipboardActions: GalleryClipboardActions,
    fileActions: GalleryFileActions,
    onShowRenameDialog: () -> Unit
) {
    val errorColor = MaterialTheme.colorScheme.error
    val copyDescription = stringResource(R.string.action_copy)
    val cutDescription = stringResource(R.string.action_cut)
    val deleteDescription = stringResource(R.string.action_delete_selected)
    val renameDescription = stringResource(R.string.action_rename)
    val mainActions = remember(
        state.selectedFiles,
        clipboardActions,
        deleteActions,
        onShowRenameDialog,
        errorColor,
        copyDescription,
        cutDescription,
        deleteDescription,
        renameDescription
    ) {
        buildList {
            add(
                ToolbarAction(
                    icon = Icons.Default.ContentCopy,
                    contentDescription = copyDescription,
                    onClick = clipboardActions.copySelected
                )
            )
            add(
                ToolbarAction(
                    icon = Icons.Default.ContentCut,
                    contentDescription = cutDescription,
                    onClick = clipboardActions.cutSelected
                )
            )
            add(
                ToolbarAction(
                    icon = Icons.Default.Delete,
                    contentDescription = deleteDescription,
                    tint = errorColor,
                    onClick = deleteActions.request
                )
            )
            add(
                ToolbarAction(
                    icon = Icons.Default.Edit,
                    contentDescription = renameDescription,
                    onClick = onShowRenameDialog
                )
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SplitButtonGroup(
                actions = mainActions,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                height = 48.dp,
                minWidth = 48.dp,
                iconSize = 24.dp
            )
            GallerySelectionMoreMenu(
                state = state,
                selectionActions = selectionActions,
                fileActions = fileActions
            )
        }
    }
}

@Composable
private fun GallerySelectionMoreMenu(
    state: MediaGalleryState,
    selectionActions: GallerySelectionActions,
    fileActions: GalleryFileActions
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        Surface(
            onClick = { expanded = true },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shadowElevation = 4.dp,
            tonalElevation = 4.dp,
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.action_more_options),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        val menuActions = buildList<@Composable () -> Unit> {
            if (state.selectedFiles.size == 1) {
                add {
                    ArcileDropdownMenuItem(
                        text = stringResource(R.string.viewer_open_with),
                        leadingIcon = {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                        },
                        onClick = {
                            expanded = false
                            selectionActions.openWith()
                        }
                    )
                }
            }
            add {
                ArcileDropdownMenuItem(
                    text = stringResource(R.string.archive_create_menu_action),
                    leadingIcon = { Icon(Icons.Default.FolderZip, contentDescription = null) },
                    onClick = {
                        expanded = false
                        fileActions.createZipFromSelection()
                    }
                )
            }
            if (
                state.selectedFiles.size == 1 &&
                state.selectedFolderPath != null &&
                state.selectedFolderPath != "__favorites__"
            ) {
                add {
                    ArcileDropdownMenuItem(
                        text = stringResource(R.string.image_gallery_set_as_cover),
                        leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                        onClick = {
                            expanded = false
                            fileActions.setFolderCover(
                                state.selectedFolderPath,
                                state.selectedFiles.first()
                            )
                            selectionActions.clear()
                        }
                    )
                }
            }
            add {
                ArcileDropdownMenuItem(
                    text = stringResource(R.string.share),
                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                    onClick = {
                        expanded = false
                        selectionActions.share()
                    }
                )
            }
            add {
                ArcileDropdownMenuItem(
                    text = stringResource(R.string.properties_title),
                    leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                    onClick = {
                        expanded = false
                        selectionActions.openProperties()
                    }
                )
            }
        }
        dev.qtremors.arcile.core.ui.ArcileDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            items = menuActions
        )
    }
}
