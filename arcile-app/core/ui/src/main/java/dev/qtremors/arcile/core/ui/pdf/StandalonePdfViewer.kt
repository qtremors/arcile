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
import kotlin.math.sqrt
data class PdfPageSize(
    val width: Int,
    val height: Int
) {
    val aspectRatio: Float
        get() = width.toFloat() / height.coerceAtLeast(1).toFloat()
}
internal data class PdfTextMatch(
    val pageIndex: Int,
    val bounds: List<RectF>
)

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

        val bitmap = androidx.core.graphics.createBitmap(width, height).apply { eraseColor(AndroidColor.WHITE) }
        renderer.openPage(pageIndex).use { page ->
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        }
        bitmapCache.put(cacheKey, bitmap)
        bitmap
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    internal fun searchText(query: String): List<PdfTextMatch> = synchronized(renderLock) {
        check(!closed) { "PDF document is closed" }
        require(query.isNotBlank())
        pageSizes.indices.flatMap { pageIndex ->
            renderer.openPage(pageIndex).use { page ->
                page.searchText(query).map { match ->
                    PdfTextMatch(pageIndex, match.bounds.map(::RectF))
                }
            }
        }
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
            val uri = runCatching { reference.toUri() }.getOrNull()
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
    val resources = LocalResources.current
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val horizontalScrollState = rememberScrollState()
    val marqueeEnabled = LocalMarqueeFilenames.current
    var uiVisible by remember { mutableStateOf(true) }
    var infoVisible by remember { mutableStateOf(false) }
    var searchVisible by rememberSaveable(reference) { mutableStateOf(false) }
    var searchQuery by rememberSaveable(reference) { mutableStateOf("") }
    var searchMatches by remember(reference) { mutableStateOf(emptyList<PdfTextMatch>()) }
    var activeSearchIndex by remember(reference) { mutableIntStateOf(0) }
    var searchStatus by remember(reference) { mutableStateOf<PdfSearchStatus?>(null) }
    var keepScreenOn by rememberSaveable(reference) { mutableStateOf(false) }
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
    DisposableEffect(view, keepScreenOn) {
        view.keepScreenOn = keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    LaunchedEffect(searchVisible, searchQuery, document) {
        if (!searchVisible || searchQuery.isBlank() || document == null) {
            searchMatches = emptyList()
            activeSearchIndex = 0
            searchStatus = null
            return@LaunchedEffect
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            searchMatches = emptyList()
            searchStatus = PdfSearchStatus.Unsupported
            return@LaunchedEffect
        }
        delay(PDF_SEARCH_DEBOUNCE_MILLIS)
        searchStatus = PdfSearchStatus.Searching
        val result = withContext(Dispatchers.IO) {
            runCatching { document.searchText(searchQuery.trim()) }
        }
        result.fold(
            onSuccess = { matches ->
                searchMatches = matches
                activeSearchIndex = 0
                searchStatus = if (matches.isEmpty()) PdfSearchStatus.NoResults else null
                matches.firstOrNull()?.let { listState.animateScrollToItem(it.pageIndex) }
            },
            onFailure = {
                searchMatches = emptyList()
                searchStatus = PdfSearchStatus.Failed
            }
        )
    }

    BackHandler(enabled = infoVisible || searchVisible) {
        if (searchVisible) {
            searchVisible = false
            searchQuery = ""
        } else {
            infoVisible = false
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color(0xFF202124)
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val baseWidthPx = with(density) { maxWidth.roundToPx() }
            val pageWidth = maxWidth * zoom
            val pageCount = document?.pageCount ?: 0
            val currentPage by remember(listState, pageCount) {
                androidx.compose.runtime.derivedStateOf {
                    listState.firstVisibleItemIndex.takeIf { pageCount > 0 }?.coerceAtMost(pageCount - 1) ?: 0
                }
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
                            .pointerInput(currentLoadState.document) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    do {
                                        val event = awaitPointerEvent()
                                        if (event.changes.count { it.pressed } >= 2) {
                                            val zoomChange = event.calculateZoom()
                                            if (zoomChange.isFinite() && zoomChange > 0f) {
                                                zoom = (zoom * zoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM)
                                            }
                                            event.changes.forEach { change ->
                                                if (change.positionChanged()) change.consume()
                                            }
                                        }
                                    } while (event.changes.any { it.pressed })
                                }
                            }
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
                                    matches = searchMatches.filter { it.pageIndex == pageIndex },
                                    activeMatch = searchMatches.getOrNull(activeSearchIndex)
                                        ?.takeIf { it.pageIndex == pageIndex },
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
                visible = uiVisible && !infoVisible && !searchVisible,
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
                    onNavigateBack = onNavigateBack,
                    onSearch = { searchVisible = true }
                )
            }

            AnimatedVisibility(
                visible = searchVisible && !infoVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            ) {
                val statusText = when (searchStatus) {
                    PdfSearchStatus.Searching -> stringResource(R.string.pdf_searching)
                    PdfSearchStatus.NoResults -> stringResource(R.string.pdf_search_no_results)
                    PdfSearchStatus.Failed -> stringResource(R.string.pdf_search_failed)
                    PdfSearchStatus.Unsupported -> stringResource(R.string.pdf_search_requires_android_15)
                    null -> null
                }
                PdfSearchChrome(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    resultPosition = activeSearchIndex + 1,
                    resultCount = searchMatches.size,
                    statusText = statusText,
                    onPrevious = {
                        if (searchMatches.isNotEmpty()) {
                            activeSearchIndex = (activeSearchIndex - 1).mod(searchMatches.size)
                            coroutineScope.launch {
                                listState.animateScrollToItem(searchMatches[activeSearchIndex].pageIndex)
                            }
                        }
                    },
                    onNext = {
                        if (searchMatches.isNotEmpty()) {
                            activeSearchIndex = (activeSearchIndex + 1).mod(searchMatches.size)
                            coroutineScope.launch {
                                listState.animateScrollToItem(searchMatches[activeSearchIndex].pageIndex)
                            }
                        }
                    },
                    onClose = {
                        searchVisible = false
                        searchQuery = ""
                    }
                )
            }

            AnimatedVisibility(
                visible = uiVisible && document != null && !infoVisible && !searchVisible,
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
                        onOpenWith = onOpenWith,
                        keepScreenOn = keepScreenOn,
                        onKeepScreenOnChange = { keepScreenOn = it },
                        onPrint = {
                            printPdf(context, reference, title).onFailure {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(resources.getString(R.string.pdf_print_failed))
                                }
                            }
                        }
                    )
                }
            }

            ArcileSnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 112.dp)
            )
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
