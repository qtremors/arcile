package dev.qtremors.arcile.feature.audio

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dev.qtremors.arcile.core.storage.domain.AudioTrack
import dev.qtremors.arcile.core.ui.ArcileFeedbackEvent
import dev.qtremors.arcile.navigation.AppRoutes
import kotlinx.coroutines.launch

fun NavGraphBuilder.registerAudioLibraryRoute(
    enterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
    exitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition,
    popEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
    popExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition,
    onNavigateBack: () -> Unit,
    onShareSelected: suspend (List<AudioTrack>) -> Boolean,
    onOpenWith: (AudioTrack) -> Unit,
    onShowContainingFolder: (AudioTrack) -> Unit,
    onFeedback: (ArcileFeedbackEvent) -> Unit = {}
) {
    composable<AppRoutes.AudioLibrary>(
        enterTransition = enterTransition,
        exitTransition = exitTransition,
        popEnterTransition = popEnterTransition,
        popExitTransition = popExitTransition
    ) {
        val viewModel = hiltViewModel<AudioLibraryViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()
        val playback by viewModel.playback.state.collectAsStateWithLifecycle()
        val coroutineScope = rememberCoroutineScope()
        AudioLibraryScreen(
            state = state,
            playback = playback,
            onNavigateBack = onNavigateBack,
            onRefresh = { viewModel.load(refresh = true) },
            onQueryChange = viewModel::updateQuery,
            onSelectTab = viewModel::selectTab,
            onSelectFolder = viewModel::selectFolder,
            onClearFolderFilter = viewModel::clearFolderFilter,
            onPresentationChange = viewModel::updatePresentation,
            onGroupingChange = viewModel::updateGrouping,
            onShowFileDetailsChange = viewModel::updateShowFileDetails,
            onDefaultTabChange = viewModel::updateDefaultTab,
            onToggleSelection = viewModel::toggleSelection,
            onSelectPaths = viewModel::selectPaths,
            onTogglePaths = viewModel::togglePaths,
            onSelectAll = viewModel::selectAllVisible,
            onInvertSelection = viewModel::invertSelection,
            onClearSelection = viewModel::clearSelection,
            onCopySelection = viewModel::copySelection,
            onCutSelection = viewModel::cutSelection,
            onRenameSelection = viewModel::renameSelected,
            onDeleteSelection = viewModel::requestDeleteSelected,
            onConfirmDelete = viewModel::confirmDeleteSelected,
            onDismissDelete = viewModel::dismissDeleteConfirmation,
            onTogglePermanentDelete = viewModel::togglePermanentDelete,
            onToggleShred = viewModel::toggleShred,
            onOpenProperties = viewModel::openPropertiesForSelection,
            onDismissProperties = viewModel::dismissProperties,
            onCreateZip = viewModel::createZipFromSelection,
            onPaste = viewModel::pasteToCurrentFolder,
            onPasteToFolder = viewModel::pasteToFolder,
            onCancelClipboard = viewModel::cancelClipboard,
            onRemoveFromClipboard = viewModel::removeFromClipboard,
            onResolvePasteConflicts = viewModel::resolvePasteConflicts,
            onDismissPasteConflictDialog = viewModel::dismissPasteConflictDialog,
            onClearActiveFileOperation = viewModel::clearActiveFileOperation,
            onPlay = viewModel::play,
            onPlaySelection = viewModel::playSelection,
            onTogglePlayback = viewModel.playback::togglePlayback,
            onPrevious = viewModel.playback::seekToPrevious,
            onNext = viewModel.playback::seekToNext,
            onQueueTrack = viewModel.playback::seekToQueueIndex,
            onToggleRepeat = viewModel.playback::toggleRepeatMode,
            onToggleShuffle = viewModel.playback::toggleShuffle,
            onSeek = viewModel.playback::seekTo,
            onExpandPlayer = viewModel::expandPlayer,
            onCollapsePlayer = viewModel::collapsePlayer,
            onShareSelected = { tracks, onShared ->
                coroutineScope.launch {
                    if (onShareSelected(tracks)) onShared()
                }
            },
            onOpenWith = onOpenWith,
            onShowContainingFolder = onShowContainingFolder,
            onClearError = {
                viewModel.clearError()
                viewModel.playback.clearError()
            },
            onFeedback = onFeedback
        )
    }
}
