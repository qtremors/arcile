package dev.qtremors.arcile.feature.audio

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dev.qtremors.arcile.core.storage.domain.AudioTrack
import dev.qtremors.arcile.navigation.AppRoutes

fun NavGraphBuilder.registerAudioLibraryRoute(
    enterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
    exitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition,
    popEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
    popExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition,
    onNavigateBack: () -> Unit,
    onShareSelected: (List<AudioTrack>) -> Unit,
    onOpenWith: (AudioTrack) -> Unit,
    onShowContainingFolder: (AudioTrack) -> Unit
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
            onSelectAll = viewModel::selectAllVisible,
            onInvertSelection = viewModel::invertSelection,
            onClearSelection = viewModel::clearSelection,
            onCopySelection = viewModel::copySelection,
            onCutSelection = viewModel::cutSelection,
            onRenameSelection = viewModel::renameSelected,
            onPaste = viewModel::pasteToCurrentFolder,
            onCancelClipboard = viewModel::cancelClipboard,
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
            onShareSelected = onShareSelected,
            onOpenWith = onOpenWith,
            onShowContainingFolder = onShowContainingFolder,
            onClearError = {
                viewModel.clearError()
                viewModel.playback.clearError()
            }
        )
    }
}
