package dev.qtremors.arcile.feature.imagegallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.storage.domain.FileListingPreferences
import dev.qtremors.arcile.core.storage.domain.ImageGalleryGrouping
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.rememberArcileHaptics
import dev.qtremors.arcile.core.ui.theme.ExpressiveShapes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GalleryViewOptionsDialog(
    currentTab: GalleryTab,
    isVideoGallery: Boolean,
    photosPresentation: FileListingPreferences,
    albumPresentation: FileListingPreferences,
    isAspectRatio: Boolean,
    grouping: ImageGalleryGrouping,
    showFileDetails: Boolean,
    onPhotosPresentationChange: (FileListingPreferences) -> Unit,
    onAlbumPresentationChange: (FileListingPreferences) -> Unit,
    onPhotosAspectRatioChange: (Boolean) -> Unit,
    onGroupingChange: (ImageGalleryGrouping) -> Unit,
    onShowFileDetailsChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val haptics = rememberArcileHaptics()
    var photos by remember(photosPresentation) {
        mutableStateOf(photosPresentation.normalized())
    }
    var albums by remember(albumPresentation) {
        mutableStateOf(albumPresentation.normalized())
    }
    var aspectRatio by remember(isAspectRatio) { mutableStateOf(isAspectRatio) }
    var draftGrouping by remember(grouping) { mutableStateOf(grouping) }
    var details by remember(showFileDetails) { mutableStateOf(showFileDetails) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    text = if (currentTab == GalleryTab.PHOTOS) {
                        stringResource(
                            if (isVideoGallery) R.string.video_gallery_view_sort_title
                            else R.string.image_gallery_view_sort_title
                        )
                    } else {
                        "View and sort albums"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                if (currentTab == GalleryTab.PHOTOS) {
                    GalleryPhotosViewOptions(
                        preferences = photos,
                        aspectRatio = aspectRatio,
                        grouping = draftGrouping,
                        showDetails = details,
                        isVideoGallery = isVideoGallery,
                        availableWidth = this@BoxWithConstraints.maxWidth,
                        onPreferencesChange = { photos = it },
                        onAspectRatioChange = { aspectRatio = it },
                        onGroupingChange = { draftGrouping = it },
                        onShowDetailsChange = { details = it }
                    )
                } else {
                    GalleryAlbumViewOptions(
                        preferences = albums,
                        availableWidth = this@BoxWithConstraints.maxWidth,
                        onPreferencesChange = { albums = it }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            haptics.selectionChanged()
                            onDismiss()
                        },
                        shape = ExpressiveShapes.medium
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = {
                            haptics.selectionChanged()
                            if (currentTab == GalleryTab.PHOTOS) {
                                onPhotosPresentationChange(photos.normalized())
                                onPhotosAspectRatioChange(aspectRatio)
                                onGroupingChange(draftGrouping)
                                onShowFileDetailsChange(details)
                            } else {
                                onAlbumPresentationChange(albums.normalized())
                            }
                            onDismiss()
                        },
                        shape = ExpressiveShapes.medium
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.apply))
                    }
                }
            }
        }
    }
}
