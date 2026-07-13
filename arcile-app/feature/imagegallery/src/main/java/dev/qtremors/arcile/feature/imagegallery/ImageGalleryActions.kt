package dev.qtremors.arcile.feature.imagegallery

import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.FileListingPreferences
import dev.qtremors.arcile.core.storage.domain.ImageGalleryDefaultTab
import dev.qtremors.arcile.core.storage.domain.ImageGalleryGrouping
import dev.qtremors.arcile.core.ui.ArcileFeedbackEvent

internal data class GalleryNavigationActions(
    val navigateBack: () -> Unit,
    val openFile: (String, List<FileModel>, Set<String>) -> Unit
)

internal data class GallerySelectionActions(
    val toggle: (String) -> Unit,
    val clear: () -> Unit,
    val selectAll: () -> Unit,
    val invert: () -> Unit,
    val selectMultiple: (List<String>) -> Unit,
    val share: () -> Unit,
    val openProperties: () -> Unit,
    val dismissProperties: () -> Unit
)

internal data class GalleryDeleteActions(
    val request: () -> Unit,
    val confirm: () -> Unit,
    val togglePermanent: () -> Unit,
    val toggleShred: () -> Unit,
    val dismiss: () -> Unit
)

internal data class GalleryContentActions(
    val refresh: () -> Unit,
    val searchQueryChange: (String) -> Unit,
    val clearSearch: () -> Unit,
    val selectAlbum: (String?) -> Unit,
    val clearError: () -> Unit,
    val feedback: (ArcileFeedbackEvent) -> Unit
)

internal data class GalleryPresentationActions(
    val photosChange: (FileListingPreferences) -> Unit,
    val albumsChange: (FileListingPreferences) -> Unit,
    val showFileDetailsChange: (Boolean) -> Unit,
    val aspectRatioChange: (Boolean) -> Unit,
    val sectionedChange: (Boolean) -> Unit,
    val groupingChange: (ImageGalleryGrouping) -> Unit,
    val defaultTabChange: (ImageGalleryDefaultTab) -> Unit,
    val togglePinnedAlbum: (String) -> Unit
)

internal data class GalleryClipboardActions(
    val copySelected: () -> Unit,
    val cutSelected: () -> Unit,
    val pasteToAlbum: (String) -> Unit,
    val cancel: () -> Unit,
    val remove: (String) -> Unit,
    val clearActiveOperation: () -> Unit,
    val resolveConflicts: (Map<String, ConflictResolution>) -> Unit,
    val dismissConflictDialog: () -> Unit
)

internal data class GalleryFileActions(
    val rename: (String, String) -> Unit,
    val createZipFromSelection: () -> Unit,
    val setAlbumCover: (String, String) -> Unit
)
