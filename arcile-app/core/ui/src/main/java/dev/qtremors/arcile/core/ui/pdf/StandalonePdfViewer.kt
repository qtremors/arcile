package dev.qtremors.arcile.core.ui.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.presentation.formatFileSize
import dev.qtremors.arcile.core.ui.ArcileDropdownMenu
import dev.qtremors.arcile.core.ui.ArcileDropdownMenuItem
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.SplitButtonGroup
import dev.qtremors.arcile.core.ui.ToolbarAction
import dev.qtremors.arcile.core.ui.rememberArcileHaptics
import dev.qtremors.arcile.core.ui.theme.LocalMarqueeFilenames
import dev.qtremors.arcile.core.ui.theme.bounceClickable
import java.io.Closeable
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class PdfPageSize(
    val width: Int,
    val height: Int
) {
    val aspectRatio: Float
        get() = width.toFloat() / height.coerceAtLeast(1).toFloat()
}

/**
 * Thread-safe, bounded PdfRenderer owner shared by the internal and global viewers.
 */
class PdfDocumentHandle private constructor(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    val pageSizes: List<PdfPageSize>
) : Closeable {
    private val renderLock = Any()
    private var closed = false
    private val bitmapCache = object : LruCache<String, Bitmap>(BITMAP_CACHE_KIB) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.byteCount / 1024).coerceAtLeast(1)
    }

    val pageCount: Int
        get() = pageSizes.size

    fun renderPage(pageIndex: Int, requestedWidthPx: Int): Bitmap = synchronized(renderLock) {
        check(!closed) { "PDF document is closed" }
        require(pageIndex in pageSizes.indices) { "Invalid PDF page" }
        val size = pageSizes[pageIndex]
        val requestedWidth = requestedWidthPx.coerceIn(MIN_RENDER_WIDTH_PX, MAX_RENDER_EDGE_PX)
        val requestedHeight = (
            requestedWidth.toFloat() *
                size.height.coerceAtLeast(1).toFloat() /
                size.width.coerceAtLeast(1).toFloat()
            ).roundToInt().coerceAtLeast(1)
        val pixelCount = requestedWidth.toLong() * requestedHeight.toLong()
        val scale = if (pixelCount > MAX_RENDER_PIXELS) {
            sqrt(MAX_RENDER_PIXELS.toDouble() / pixelCount.toDouble()).toFloat()
        } else {
            1f
        }
        val width = (requestedWidth * scale).roundToInt().coerceAtLeast(1)
        val height = (requestedHeight * scale).roundToInt().coerceAtLeast(1)
        val cacheKey = "$pageIndex:$width:$height"
        bitmapCache.get(cacheKey)?.let { return@synchronized it }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            eraseColor(AndroidColor.WHITE)
        }
        renderer.openPage(pageIndex).use { page ->
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        }
        bitmapCache.put(cacheKey, bitmap)
        bitmap
    }

    override fun close() = synchronized(renderLock) {
        if (closed) return@synchronized
        closed = true
        bitmapCache.evictAll()
        renderer.close()
        descriptor.close()
    }

    companion object {
        fun open(context: Context, reference: String): PdfDocumentHandle {
            val descriptor = openDescriptor(context, reference)
                ?: throw IllegalArgumentException("Unable to open PDF")
            try {
                val renderer = PdfRenderer(descriptor)
                try {
                    val sizes = (0 until renderer.pageCount).map { index ->
                        renderer.openPage(index).use { page ->
                            PdfPageSize(page.width, page.height)
                        }
                    }
                    require(sizes.isNotEmpty()) { "PDF contains no pages" }
                    return PdfDocumentHandle(descriptor, renderer, sizes)
                } catch (error: Throwable) {
                    renderer.close()
                    throw error
                }
            } catch (error: Throwable) {
                descriptor.close()
                throw error
            }
        }

        private fun openDescriptor(context: Context, reference: String): ParcelFileDescriptor? {
            val uri = runCatching { Uri.parse(reference) }.getOrNull()
            return if (uri?.scheme == "content") {
                context.contentResolver.openFileDescriptor(uri, "r")
            } else {
                val file = when (uri?.scheme) {
                    "file" -> File(uri.path.orEmpty())
                    else -> File(reference)
                }
                if (!file.isFile) null else {
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                }
            }
        }

        private const val MIN_RENDER_WIDTH_PX = 320
        private const val MAX_RENDER_EDGE_PX = 3_072
        private const val MAX_RENDER_PIXELS = 8_000_000L
        private const val BITMAP_CACHE_KIB = 64 * 1024
    }
}

private sealed interface PdfLoadState {
    data object Loading : PdfLoadState
    data class Ready(val document: PdfDocumentHandle) : PdfLoadState
    data class Failed(val message: String?) : PdfLoadState
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StandalonePdfViewer(
    reference: String,
    title: String,
    sizeBytes: Long,
    onNavigateBack: () -> Unit,
    onShare: () -> Unit,
    onOpenWith: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val horizontalScrollState = rememberScrollState()
    val marqueeEnabled = LocalMarqueeFilenames.current
    var uiVisible by remember { mutableStateOf(true) }
    var infoVisible by remember { mutableStateOf(false) }
    var zoom by remember(reference) { mutableFloatStateOf(1f) }
    var loadState by remember(reference) { mutableStateOf<PdfLoadState>(PdfLoadState.Loading) }

    LaunchedEffect(reference) {
        loadState = PdfLoadState.Loading
        loadState = withContext(Dispatchers.IO) {
            runCatching { PdfDocumentHandle.open(context, reference) }
                .fold(
                    onSuccess = PdfLoadState::Ready,
                    onFailure = { PdfLoadState.Failed(it.localizedMessage) }
                )
        }
    }
    val document = (loadState as? PdfLoadState.Ready)?.document
    DisposableEffect(document) {
        onDispose { document?.close() }
    }

    BackHandler(enabled = infoVisible) {
        infoVisible = false
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color(0xFF202124)
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val baseWidthPx = with(density) { maxWidth.roundToPx() }
            val pageWidth = maxWidth * zoom
            val currentPage = remember(listState.firstVisibleItemIndex, document?.pageCount) {
                if (document == null) 0
                else listState.firstVisibleItemIndex.coerceIn(0, document.pageCount - 1)
            }

            when (val currentLoadState = loadState) {
                PdfLoadState.Loading -> LoadingIndicator(
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
                is PdfLoadState.Failed -> PdfLoadFailure(
                    message = currentLoadState.message,
                    onOpenWith = onOpenWith,
                    modifier = Modifier.align(Alignment.Center)
                )
                is PdfLoadState.Ready -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .horizontalScroll(horizontalScrollState)
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .width(pageWidth)
                                .fillMaxSize(),
                            contentPadding = PaddingValues(
                                top = 88.dp,
                                bottom = 120.dp,
                                start = 12.dp,
                                end = 12.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            items(
                                items = currentLoadState.document.pageSizes.indices.toList(),
                                key = { it }
                            ) { pageIndex ->
                                PdfPage(
                                    document = currentLoadState.document,
                                    pageIndex = pageIndex,
                                    requestedWidthPx = (baseWidthPx * zoom).roundToInt(),
                                    onTap = { uiVisible = !uiVisible },
                                    onDoubleTap = {
                                        zoom = if (zoom > 1f) 1f else 2f
                                    }
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = uiVisible && !infoVisible,
                enter = fadeIn(spring(stiffness = Spring.StiffnessLow)),
                exit = fadeOut(spring(stiffness = Spring.StiffnessLow)),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            ) {
                PdfTopChrome(
                    title = title,
                    page = currentPage,
                    pageCount = document?.pageCount ?: 0,
                    marqueeEnabled = marqueeEnabled,
                    onNavigateBack = onNavigateBack
                )
            }

            AnimatedVisibility(
                visible = uiVisible && document != null && !infoVisible,
                enter = fadeIn(spring(stiffness = Spring.StiffnessLow)),
                exit = fadeOut(spring(stiffness = Spring.StiffnessLow)),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            ) {
                document?.let {
                    PdfBottomChrome(
                        page = currentPage,
                        pageCount = it.pageCount,
                        zoom = zoom,
                        onZoomOut = { zoom = (zoom - ZOOM_STEP).coerceAtLeast(MIN_ZOOM) },
                        onZoomIn = { zoom = (zoom + ZOOM_STEP).coerceAtMost(MAX_ZOOM) },
                        onFit = {
                            zoom = 1f
                            coroutineScope.launch { horizontalScrollState.animateScrollTo(0) }
                        },
                        onPageChange = { target ->
                            coroutineScope.launch {
                                listState.scrollToItem(target.coerceIn(0, it.pageCount - 1))
                            }
                        },
                        onInfo = { infoVisible = true },
                        onShare = onShare,
                        onOpenWith = onOpenWith
                    )
                }
            }
        }
    }

    if (infoVisible) {
        ModalBottomSheet(
            onDismissRequest = { infoVisible = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            PdfInfoSheet(
                title = title,
                reference = reference,
                sizeBytes = sizeBytes,
                pageCount = document?.pageCount ?: 0
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PdfPage(
    document: PdfDocumentHandle,
    pageIndex: Int,
    requestedWidthPx: Int,
    onTap: () -> Unit,
    onDoubleTap: () -> Unit
) {
    val rendered by produceState<Bitmap?>(
        initialValue = null,
        document,
        pageIndex,
        requestedWidthPx
    ) {
        value = withContext(Dispatchers.IO) {
            runCatching { document.renderPage(pageIndex, requestedWidthPx) }.getOrNull()
        }
    }
    val pageSize = document.pageSizes[pageIndex]
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = Color.White,
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(pageSize.aspectRatio)
            .pointerInput(document, pageIndex) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { onDoubleTap() }
                )
            }
    ) {
        Box(contentAlignment = Alignment.Center) {
            val bitmap = rendered
            if (bitmap == null) {
                LoadingIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            } else {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.pdf_page_description, pageIndex + 1),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )
            }
        }
    }
}

@Composable
private fun PdfTopChrome(
    title: String,
    page: Int,
    pageCount: Int,
    marqueeEnabled: Boolean,
    onNavigateBack: () -> Unit
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
    }
}

@Composable
private fun PdfBottomChrome(
    page: Int,
    pageCount: Int,
    zoom: Float,
    onZoomOut: () -> Unit,
    onZoomIn: () -> Unit,
    onFit: () -> Unit,
    onPageChange: (Int) -> Unit,
    onInfo: () -> Unit,
    onShare: () -> Unit,
    onOpenWith: () -> Unit
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
                onOpenWith = onOpenWith
            )
        }
    }
}

@Composable
private fun PdfOverflowMenu(
    onInfo: () -> Unit,
    onShare: () -> Unit,
    onOpenWith: () -> Unit
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
            items = listOf(
                {
                    ArcileDropdownMenuItem(
                        text = stringResource(R.string.action_info),
                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                        onClick = {
                            expanded = false
                            onInfo()
                        }
                    )
                },
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
                },
                {
                    ArcileDropdownMenuItem(
                        text = stringResource(R.string.share),
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                        onClick = {
                            expanded = false
                            onShare()
                        }
                    )
                }
            )
        )
    }
}

@Composable
private fun PdfInfoSheet(
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
            PdfInfoRow(stringResource(R.string.pdf_size), formatFileSize(sizeBytes))
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
private fun PdfLoadFailure(
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

private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 3f
private const val ZOOM_STEP = 0.25f
