package dev.qtremors.arcile.feature.apk

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.qtremors.arcile.core.presentation.formatFileSize
import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.core.storage.domain.FileListingPreferences
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.FileViewMode
import dev.qtremors.arcile.core.storage.domain.SearchFilters
import dev.qtremors.arcile.core.storage.domain.CategoryGrouping
import dev.qtremors.arcile.core.ui.category.CategoryFolderSummary
import dev.qtremors.arcile.core.ui.category.CategoryFolderGridItem
import dev.qtremors.arcile.core.ui.category.CategoryLibraryLabels
import dev.qtremors.arcile.core.ui.category.FileCategoryLibrary
import dev.qtremors.arcile.core.ui.category.CategoryLibraryFileActionCallbacks
import dev.qtremors.arcile.core.ui.category.CategoryGridItem
import dev.qtremors.arcile.core.ui.category.CategoryItemInfo
import dev.qtremors.arcile.core.ui.category.CategoryListItem
import dev.qtremors.arcile.core.ui.ArcileFeedbackEvent
import dev.qtremors.arcile.core.ui.ArcileFeedbackSeverity
import dev.qtremors.arcile.core.ui.rememberArcileHaptics
import dev.qtremors.arcile.core.storage.domain.CategoryLibraryPage
import dev.qtremors.arcile.core.ui.image.ApkPresentationMetadata
import dev.qtremors.arcile.core.ui.image.ApkPresentationMetadataReader
import dev.qtremors.arcile.core.ui.image.ThumbnailKey
import java.io.File

@Composable
internal fun ApkLibraryScreen(
    state: ApkLibraryState,
    onNavigateBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearchFiltersChange: (SearchFilters) -> Unit,
    onTabChange: (CategoryLibraryPage) -> Unit,
    onPresentationChange: (CategoryLibraryPage, FileListingPreferences) -> Unit,
    onDefaultPageChange: (CategoryLibraryPage) -> Unit,
    onGroupingChange: (CategoryGrouping) -> Unit,
    onShowFileDetailsChange: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onClearFolderFilter: () -> Unit,
    onToggleSelection: (String) -> Unit,
    onSelectPaths: (Collection<String>) -> Unit,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onInstall: (FileModel) -> Unit,
    onOpenFolder: (CategoryFolderSummary) -> Unit,
    onShareSelection: () -> Unit,
    onOpenSelectionWith: () -> Unit,
    onClearError: () -> Unit,
    onFeedback: (ArcileFeedbackEvent) -> Unit,
    fileActions: CategoryLibraryFileActionCallbacks
) {
    val haptics = rememberArcileHaptics()
    LaunchedEffect(state.error) {
        state.error?.let { error ->
            haptics.error()
            onFeedback(
                ArcileFeedbackEvent(UiText.Dynamic(error), ArcileFeedbackSeverity.Error)
            )
            onClearError()
        }
    }
    val resources = LocalContext.current.resources
    val labels = CategoryLibraryLabels(
        searchPlaceholder = stringResource(R.string.apk_search),
        filesTab = stringResource(R.string.apk_all),
        foldersTab = stringResource(R.string.apk_folders),
        filesIcon = Icons.Outlined.Android,
        emptyFilesTitle = stringResource(R.string.apk_empty),
        emptyFilesDescription = stringResource(R.string.apk_empty_description),
        emptyFoldersTitle = stringResource(R.string.apk_folders_empty),
        emptyFoldersDescription = stringResource(R.string.apk_folders_empty_description),
        viewSortFilesTitle = stringResource(R.string.apk_view_sort),
        viewSortFoldersTitle = stringResource(R.string.apk_view_sort_folders),
        selectedCount = { count -> resources.getString(R.string.apk_selected, count) }
    )
    FileCategoryLibrary(
        files = state.files,
        folders = state.folders,
        selectedPaths = state.selectedPaths,
        query = state.query,
        searchFilters = state.searchFilters,
        tab = state.tab,
        itemPresentation = state.presentation,
        folderPresentation = state.folderPresentation,
        defaultPage = state.defaultPage,
        grouping = state.grouping,
        showFileDetails = state.showFileDetails,
        scrollbarEnabled = state.scrollbarEnabled,
        isLoading = state.isLoading,
        folderFilterLabel = state.folderFilter?.label,
        folderFilterPath = state.folderFilter?.path,
        labels = labels,
        onNavigateBack = onNavigateBack,
        onQueryChange = onQueryChange,
        onSearchFiltersChange = onSearchFiltersChange,
        onTabChange = onTabChange,
        onPresentationChange = onPresentationChange,
        onDefaultPageChange = onDefaultPageChange,
        onGroupingChange = onGroupingChange,
        onShowFileDetailsChange = onShowFileDetailsChange,
        onRefresh = onRefresh,
        onClearFolderFilter = onClearFolderFilter,
        onToggleSelection = onToggleSelection,
        onSelectPaths = onSelectPaths,
        onClearSelection = onClearSelection,
        onSelectAll = onSelectAll,
        onInvertSelection = onInvertSelection,
        onShareSelection = onShareSelection,
        onOpenSelectionWith = onOpenSelectionWith,
        fileActions = fileActions,
        onOpenFile = onInstall,
        onOpenFolder = onOpenFolder,
        fileItem = { file, selected, selectionMode, onClick, onLongClick, modifier ->
            ApkItem(
                file = file,
                selected = selected,
                selectionMode = selectionMode,
                grid = state.presentation.viewMode == FileViewMode.GRID,
                zoom = state.presentation.listZoom,
                showThumbnail = state.presentation.showThumbnails,
                showInfo = state.showFileDetails,
                onClick = onClick,
                onLongClick = onLongClick,
                modifier = modifier
            )
        },
        folderItem = { folder, onClick, modifier ->
            ApkFolderItem(
                folder = folder,
                showThumbnail = state.folderPresentation.showThumbnails,
                onClick = onClick,
                modifier = modifier
            )
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ApkItem(
    file: FileModel,
    selected: Boolean,
    selectionMode: Boolean,
    grid: Boolean,
    zoom: Float,
    showThumbnail: Boolean,
    showInfo: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val metadata by rememberApkMetadata(file)
    val info = CategoryItemInfo(
        title = file.name,
        detailLines = listOf(
            metadata?.label ?: packageKind(file),
            metadata?.let { "${it.versionName} • ${it.packageName}" }
                ?: file.extension.uppercase(),
            formatFileSize(file.size)
        )
    )
    if (grid) {
        CategoryGridItem(
            info = info,
            selected = selected,
            showInfo = showInfo,
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier,
            previewBackground = false
        ) {
            ApkPreview(file, showThumbnail, Modifier.fillMaxSize())
        }
    } else {
        CategoryListItem(
            info = info,
            selected = selected,
            zoom = zoom,
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier
        ) {
            ApkPreview(file, showThumbnail, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun ApkPreview(
    file: FileModel,
    showThumbnail: Boolean,
    modifier: Modifier = Modifier
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Icon(
            Icons.Outlined.Android,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(34.dp)
        )
        if (showThumbnail) {
            AsyncImage(
                model = ThumbnailKey.from(file),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun ApkFolderItem(
    folder: CategoryFolderSummary,
    showThumbnail: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    CategoryFolderGridItem(
        info = CategoryItemInfo(
            title = folder.label,
            detailLines = listOf(stringResource(R.string.apk_folder_count, folder.itemCount))
        ),
        onClick = onClick,
        modifier = modifier
    ) {
        folder.preview?.let {
            ApkPreview(it, showThumbnail, Modifier.fillMaxSize())
        } ?: Icon(
            Icons.Default.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp)
        )
    }
}

@Composable
private fun rememberApkMetadata(file: FileModel): androidx.compose.runtime.State<ApkPresentationMetadata?> {
    val context = LocalContext.current
    return produceState<ApkPresentationMetadata?>(
        initialValue = null,
        file.absolutePath,
        file.size,
        file.lastModified
    ) {
        value = ApkPresentationMetadataReader.read(context, File(file.absolutePath))
    }
}

@Composable
private fun packageKind(file: FileModel): String =
    stringResource(
        if (file.extension.equals("apk", ignoreCase = true)) {
            R.string.apk_standalone
        } else {
            R.string.apk_split_bundle
        }
    )

@Composable
private fun SelectionMark(modifier: Modifier = Modifier) {
    Icon(
        Icons.Default.CheckCircle,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = modifier.size(24.dp)
    )
}
