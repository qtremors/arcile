package dev.qtremors.arcile.core.ui.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.presentation.formatFileSize
import dev.qtremors.arcile.core.ui.ArcileDropdownMenu
import dev.qtremors.arcile.core.ui.ArcileDropdownMenuItem
import dev.qtremors.arcile.core.ui.ArcileSnackbarHost
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.SplitButtonGroup
import dev.qtremors.arcile.core.ui.ToolbarAction
import dev.qtremors.arcile.core.ui.rememberArcileHaptics
import dev.qtremors.arcile.core.ui.theme.LocalMarqueeFilenames
import dev.qtremors.arcile.core.ui.theme.bounceClickable
import java.io.Closeable
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import dev.qtremors.arcile.core.ui.viewer.ViewerFileAction
import kotlin.math.sqrt

@Composable
internal fun PdfTopChrome(
    title: String,
    page: Int,
    pageCount: Int,
    marqueeEnabled: Boolean,
    onNavigateBack: () -> Unit,
    onSearch: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButtonSurface(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            description = stringResource(R.string.back),
            onClick = onNavigateBack
        )
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Black.copy(alpha = 0.62f))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = if (marqueeEnabled) TextOverflow.Clip else TextOverflow.Ellipsis,
                modifier = if (marqueeEnabled) Modifier.basicMarquee() else Modifier
            )
            if (pageCount > 0) {
                Text(
                    text = stringResource(R.string.pdf_page_position, page + 1, pageCount),
                    color = Color.White.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        IconButtonSurface(
            icon = Icons.Default.Search,
            description = stringResource(R.string.pdf_search),
            onClick = onSearch
        )
    }
}

@Composable
internal fun PdfBottomChrome(
    page: Int,
    pageCount: Int,
    zoom: Float,
    onZoomOut: () -> Unit,
    onZoomIn: () -> Unit,
    onFit: () -> Unit,
    onPageChange: (Int) -> Unit,
    onInfo: () -> Unit,
    onShare: () -> Unit,
    onOpenWith: () -> Unit,
    keepScreenOn: Boolean,
    onKeepScreenOnChange: (Boolean) -> Unit,
    onPrint: () -> Unit,
    viewerActions: Set<ViewerFileAction> = emptySet(),
    onViewerAction: (ViewerFileAction) -> Unit = {}
) {
    var sliderPage by remember(page) { mutableFloatStateOf(page.toFloat()) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.62f))
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (pageCount > 1) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Slider(
                    value = sliderPage,
                    onValueChange = { sliderPage = it },
                    onValueChangeFinished = { onPageChange(sliderPage.roundToInt()) },
                    valueRange = 0f..(pageCount - 1).toFloat(),
                    steps = (pageCount - 2).coerceIn(0, 100),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${page + 1}/$pageCount",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            SplitButtonGroup(
                actions = listOf(
                    ToolbarAction(
                        icon = Icons.Default.ZoomOut,
                        contentDescription = stringResource(R.string.pdf_zoom_out),
                        tint = if (zoom > MIN_ZOOM) {
                            Color.White
                        } else {
                            Color.White.copy(alpha = 0.35f)
                        },
                        onClick = {
                            if (zoom > MIN_ZOOM) onZoomOut()
                        }
                    ),
                    ToolbarAction(
                        icon = Icons.Default.FitScreen,
                        contentDescription = stringResource(R.string.pdf_fit_page),
                        tint = Color.White,
                        onClick = onFit
                    ),
                    ToolbarAction(
                        icon = Icons.Default.ZoomIn,
                        contentDescription = stringResource(R.string.pdf_zoom_in),
                        tint = if (zoom < MAX_ZOOM) {
                            Color.White
                        } else {
                            Color.White.copy(alpha = 0.35f)
                        },
                        onClick = {
                            if (zoom < MAX_ZOOM) onZoomIn()
                        }
                    )
                ),
                containerColor = Color.Black.copy(alpha = 0.5f),
                contentColor = Color.White,
                height = 48.dp,
                minWidth = 48.dp,
                iconSize = 24.dp
            )
            Text(
                text = "${(zoom * 100).roundToInt()}%",
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = 12.dp)
            )
            Spacer(Modifier.weight(1f))
            PdfOverflowMenu(
                onInfo = onInfo,
                onShare = onShare,
                onOpenWith = onOpenWith,
                keepScreenOn = keepScreenOn,
                onKeepScreenOnChange = onKeepScreenOnChange,
                onPrint = onPrint,
                viewerActions = viewerActions,
                onViewerAction = onViewerAction
            )
        }
    }
}

@Composable
private fun PdfOverflowMenu(
    onInfo: () -> Unit,
    onShare: () -> Unit,
    onOpenWith: () -> Unit,
    keepScreenOn: Boolean,
    onKeepScreenOnChange: (Boolean) -> Unit,
    onPrint: () -> Unit,
    viewerActions: Set<ViewerFileAction>,
    onViewerAction: (ViewerFileAction) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val haptics = rememberArcileHaptics()
    Box {
        Surface(
            onClick = {
                haptics.toggleMenu()
                expanded = true
            },
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.5f),
            modifier = Modifier
                .size(48.dp)
                .bounceClickable {
                    haptics.toggleMenu()
                    expanded = true
                }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.action_more_options),
                    tint = Color.White
                )
            }
        }
        ArcileDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            items = buildList {
                add(
                {
                    ArcileDropdownMenuItem(
                        text = stringResource(R.string.pdf_keep_screen_on),
                        leadingIcon = {
                            Icon(Icons.Default.ScreenLockPortrait, contentDescription = null)
                        },
                        isSelected = keepScreenOn,
                        onClick = {
                            expanded = false
                            onKeepScreenOnChange(!keepScreenOn)
                        }
                    )
                })
                add(
                {
                    ArcileDropdownMenuItem(
                        text = stringResource(R.string.pdf_print),
                        leadingIcon = { Icon(Icons.Default.Print, contentDescription = null) },
                        onClick = {
                            expanded = false
                            onPrint()
                        }
                    )
                })
                add(
                {
                    ArcileDropdownMenuItem(
                        text = stringResource(R.string.action_info),
                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                        onClick = {
                            expanded = false
                            onInfo()
                        }
                    )
                })
                add(
                {
                    ArcileDropdownMenuItem(
                        text = stringResource(R.string.image_gallery_open_with),
                        leadingIcon = {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                        },
                        onClick = {
                            expanded = false
                            onOpenWith()
                        }
                    )
                })
                add(
                {
                    ArcileDropdownMenuItem(
                        text = stringResource(R.string.share),
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                        onClick = {
                            expanded = false
                            onShare()
                        }
                    )
                })
                fun addViewerAction(action: ViewerFileAction, label: Int, icon: androidx.compose.ui.graphics.vector.ImageVector) {
                    if (action !in viewerActions) return
                    add {
                        ArcileDropdownMenuItem(
                            text = stringResource(label),
                            leadingIcon = { Icon(icon, contentDescription = null) },
                            onClick = {
                                expanded = false
                                onViewerAction(action)
                            }
                        )
                    }
                }
                addViewerAction(ViewerFileAction.Rename, R.string.action_rename, Icons.Default.DriveFileRenameOutline)
                addViewerAction(ViewerFileAction.Copy, R.string.action_copy, Icons.Default.ContentCopy)
                addViewerAction(ViewerFileAction.Cut, R.string.action_cut, Icons.Default.ContentCut)
                addViewerAction(ViewerFileAction.CreateArchive, R.string.action_create_archive, Icons.Default.FolderZip)
                addViewerAction(ViewerFileAction.Delete, R.string.action_delete, Icons.Default.Delete)
            }
        )
    }
}

@Composable
internal fun PdfInfoSheet(
    title: String,
    reference: String,
    sizeBytes: Long,
    pageCount: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.pdf_details),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        PdfInfoRow(stringResource(R.string.pdf_name), title)
        PdfInfoRow(stringResource(R.string.pdf_pages), pageCount.toString())
        if (sizeBytes > 0L) {
            PdfInfoRow(stringResource(R.string.pdf_size), formatFileSize(androidx.compose.ui.platform.LocalContext.current, sizeBytes))
        }
        PdfInfoRow(
            stringResource(
                if (reference.startsWith("content://")) {
                    R.string.image_gallery_metadata_label_uri
                } else {
                    R.string.image_gallery_metadata_label_path
                }
            ),
            reference
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun PdfInfoRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
internal fun PdfLoadFailure(
    message: String?,
    onOpenWith: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(24.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black.copy(alpha = 0.68f))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.pdf_cannot_open),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        if (!message.isNullOrBlank()) {
            Text(
                text = message,
                color = Color.White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.bodySmall
            )
        }
        Button(onClick = onOpenWith) {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.image_gallery_open_with))
        }
    }
}

@Composable
private fun IconButtonSurface(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.62f),
        modifier = Modifier
            .size(48.dp)
            .bounceClickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, tint = Color.White)
        }
    }
}

internal const val MIN_ZOOM = 1f
internal const val MAX_ZOOM = 3f
internal const val ZOOM_STEP = 0.25f
internal const val PDF_SEARCH_DEBOUNCE_MILLIS = 250L

internal enum class PdfSearchStatus {
    Searching,
    NoResults,
    Unsupported,
    Failed
}
