package dev.qtremors.arcile.feature.imagegallery

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.shared.ui.dialogs.DeleteConfirmationDialog
import dev.qtremors.arcile.shared.ui.rememberArcileHaptics
import kotlinx.coroutines.launch
import kotlin.math.abs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)

fun viewerFilesForInitialPath(
    initialPath: String,
    displayedFiles: List<FileModel>,
    allFiles: List<FileModel>
): List<FileModel> {
    return viewerFileContextForInitialPath(initialPath, displayedFiles, allFiles).files
}

data class ViewerFileContext(
    val files: List<FileModel>,
    val initialPage: Int
)

enum class ViewerThumbnailScrollAction {
    None,
    Jump,
    Animate
}

internal const val VIEWER_MIN_STABLE_SCALE = 0.000001f
internal const val VIEWER_MAX_STABLE_SCALE = 1_000_000f

internal fun viewerReleaseScale(scale: Float): Float =
    scale.coerceIn(VIEWER_MIN_STABLE_SCALE, VIEWER_MAX_STABLE_SCALE)

internal fun viewerRenderScale(scale: Float, dragFraction: Float): Float =
    (scale * (1f - dragFraction * 0.15f)).coerceIn(VIEWER_MIN_STABLE_SCALE, VIEWER_MAX_STABLE_SCALE)

internal fun viewerPanLimit(scale: Float, viewportSize: Int): Float =
    ((scale - 1f).coerceAtLeast(0f) * viewportSize / 2f)

internal fun viewerFittedContentSize(
    viewportWidth: Float,
    viewportHeight: Float,
    imageWidth: Float,
    imageHeight: Float,
    rotationDegrees: Float
): Size {
    if (
        viewportWidth <= 0f ||
        viewportHeight <= 0f ||
        imageWidth <= 0f ||
        imageHeight <= 0f
    ) {
        return Size(viewportWidth.coerceAtLeast(0f), viewportHeight.coerceAtLeast(0f))
    }
    val quarterTurns = ((rotationDegrees / 90f).toInt() % 4 + 4) % 4
    val rotatedWidth = if (quarterTurns % 2 == 0) imageWidth else imageHeight
    val rotatedHeight = if (quarterTurns % 2 == 0) imageHeight else imageWidth
    val fitScale = minOf(viewportWidth / rotatedWidth, viewportHeight / rotatedHeight)
    return Size(rotatedWidth * fitScale, rotatedHeight * fitScale)
}

internal fun viewerPanLimit(
    scale: Float,
    contentSize: Float,
    viewportSize: Float
): Float = ((contentSize * scale - viewportSize) / 2f).coerceAtLeast(0f)

internal fun viewerOffsetForScale(
    currentOffset: Offset,
    oldScale: Float,
    newScale: Float,
    centroid: Offset,
    viewportCenter: Offset,
    pan: Offset = Offset.Zero
): Offset {
    if (oldScale <= 0f || !oldScale.isFinite() || !newScale.isFinite()) return currentOffset + pan
    val ratio = newScale / oldScale
    val focalFromCenter = centroid - viewportCenter
    return currentOffset + (focalFromCenter - currentOffset) * (1f - ratio) + pan
}

fun viewerFileContextForInitialPath(
    initialPath: String,
    displayedFiles: List<FileModel>,
    allFiles: List<FileModel>
): ViewerFileContext {
    val displayedIndex = displayedFiles.indexOfFirst { it.absolutePath == initialPath }
    if (displayedIndex >= 0) {
        return ViewerFileContext(displayedFiles, displayedIndex)
    }

    val allIndex = allFiles.indexOfFirst { it.absolutePath == initialPath }
    if (allIndex >= 0) {
        return ViewerFileContext(allFiles, allIndex)
    }

    return ViewerFileContext(listOf(fileModelFromPath(initialPath)), 0)
}

internal fun viewerInitialPageForSession(
    initialPath: String,
    viewerSessionInitialPath: String?,
    viewerCurrentPath: String?,
    viewerContext: ViewerFileContext
): Int {
    val restoredPath = viewerCurrentPath.takeIf { viewerSessionInitialPath == initialPath }
    return restoredPath
        ?.let { path -> viewerContext.files.indexOfFirst { it.absolutePath == path } }
        ?.takeIf { it >= 0 }
        ?: viewerContext.initialPage
}

internal fun viewerThumbnailScrollAction(
    previousIndex: Int?,
    targetIndex: Int,
    maxAnimatedDistance: Int = 12
): ViewerThumbnailScrollAction {
    if (targetIndex < 0) return ViewerThumbnailScrollAction.None
    val previous = previousIndex ?: return ViewerThumbnailScrollAction.Jump
    if (previous == targetIndex) return ViewerThumbnailScrollAction.None
    return if (kotlin.math.abs(targetIndex - previous) <= maxAnimatedDistance) {
        ViewerThumbnailScrollAction.Animate
    } else {
        ViewerThumbnailScrollAction.Jump
    }
}

fun fileModelFromPath(path: String): FileModel {
    val file = java.io.File(path)
    return FileModel(
        name = file.name.ifBlank { path.substringAfterLast('/').ifBlank { path } },
        absolutePath = path,
        size = file.takeIf { it.exists() }?.length() ?: 0L,
        lastModified = file.takeIf { it.exists() }?.lastModified() ?: 0L,
        isDirectory = false,
        extension = path.substringAfterLast('/', path).substringAfterLast('.', "").lowercase(),
        isHidden = file.name.startsWith("."),
        mimeType = android.webkit.MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(path.substringAfterLast('.', "").lowercase())
    )
}

fun FileModel.openableReference(): String =
    nodeRef.contentUri?.takeIf { it.isNotBlank() } ?: absolutePath

internal fun viewerPositionLabel(currentPage: Int, total: Int): String {
    if (total <= 0) return "0/0"
    return "${currentPage.coerceIn(0, total - 1) + 1}/$total"
}

internal fun formatViewerDateTime(
    timestampMillis: Long,
    locale: Locale = Locale.getDefault(),
    timeZone: TimeZone = TimeZone.getDefault()
): String? {
    if (timestampMillis <= 0L) return null
    return SimpleDateFormat("MMM d, yyyy • h:mm a", locale)
        .apply { this.timeZone = timeZone }
        .format(Date(timestampMillis))
}

internal fun formatResolution(width: Int, height: Int): String? {
    if (width <= 0 || height <= 0) return null
    return "$width x $height"
}
