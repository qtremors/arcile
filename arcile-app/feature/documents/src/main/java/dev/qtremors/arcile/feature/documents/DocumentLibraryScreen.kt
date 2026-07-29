package dev.qtremors.arcile.feature.documents

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import dev.qtremors.arcile.core.ui.getFileIconVector
import dev.qtremors.arcile.core.ui.image.ThumbnailKey
import dev.qtremors.arcile.core.ui.rememberDateTimeFormatter

@Composable
internal fun DocumentLibraryScreen(
    state: DocumentLibraryState,
    onNavigateBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearchFiltersChange: (SearchFilters) -> Unit,
    onTabChange: (CategoryLibraryPage) -> Unit,
    onPresentationChange: (
        CategoryLibraryPage,
        dev.qtremors.arcile.core.storage.domain.FileListingPreferences
    ) -> Unit,
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
    onOpenFile: (FileModel) -> Unit,
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
        searchPlaceholder = stringResource(R.string.documents_search),
        filesTab = stringResource(R.string.documents_all),
        foldersTab = stringResource(R.string.documents_folders),
        filesIcon = Icons.Outlined.Description,
        emptyFilesTitle = stringResource(R.string.documents_empty),
        emptyFilesDescription = stringResource(R.string.documents_empty_description),
        emptyFoldersTitle = stringResource(R.string.documents_folders_empty),
        emptyFoldersDescription = stringResource(R.string.documents_folders_empty_description),
        viewSortFilesTitle = stringResource(R.string.documents_view_sort),
        viewSortFoldersTitle = stringResource(R.string.documents_view_sort_folders),
        selectedCount = { count -> resources.getString(R.string.documents_selected, count) }
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
        onOpenFile = onOpenFile,
        onOpenFolder = onOpenFolder,
        fileItem = { file, selected, selectionMode, onClick, onLongClick, modifier ->
            DocumentItem(
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
            DocumentFolderItem(
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
private fun DocumentItem(
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
    val formatter = rememberDateTimeFormatter()
    val info = CategoryItemInfo(
        title = file.name,
        detailLines = listOf(
            file.extension.uppercase().ifBlank { "FILE" },
            "${formatFileSize(file.size)} • ${formatter.format(file.lastModified)}"
        )
    )
    if (grid) {
        CategoryGridItem(
            info = info,
            selected = selected,
            showInfo = showInfo,
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier
        ) {
            DocumentPreview(file, showThumbnail, Modifier.fillMaxSize())
            DocumentTypeBadge(file.extension, Modifier.align(Alignment.BottomStart).padding(8.dp))
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
            DocumentPreview(file, showThumbnail, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun DocumentPreview(
    file: FileModel,
    showThumbnail: Boolean,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Icon(
            imageVector = getFileIconVector(file),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(38.dp)
        )
        if (showThumbnail && file.extension.equals("pdf", ignoreCase = true)) {
            AsyncImage(
                model = ThumbnailKey.from(file),
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun DocumentTypeBadge(extension: String, modifier: Modifier = Modifier) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = modifier
    ) {
        Text(
            extension.uppercase().ifBlank { "FILE" },
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun DocumentFolderItem(
    folder: CategoryFolderSummary,
    showThumbnail: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val preview = folder.preview
    CategoryFolderGridItem(
        info = CategoryItemInfo(
            title = folder.label,
            detailLines = listOf(stringResource(R.string.documents_folder_count, folder.itemCount))
        ),
        onClick = onClick,
        modifier = modifier
    ) {
        if (preview != null) {
            DocumentPreview(
                file = preview,
                showThumbnail = showThumbnail,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                Icons.Default.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}

@Composable
private fun SelectionMark(modifier: Modifier = Modifier) {
    Icon(
        Icons.Default.CheckCircle,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = modifier.size(24.dp)
    )
}
