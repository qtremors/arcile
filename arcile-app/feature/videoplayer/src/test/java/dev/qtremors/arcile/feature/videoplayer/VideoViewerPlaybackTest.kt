package dev.qtremors.arcile.feature.videoplayer

import androidx.media3.common.MediaItem
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.ui.video.VideoPlaybackItem
import dev.qtremors.arcile.core.ui.video.VideoPlaybackSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VideoViewerPlaybackTest {
    @Test
    fun `thumbnail scrolling matches image viewer animation rules`() {
        assertEquals(
            ViewerThumbnailScrollAction.Jump,
            viewerThumbnailScrollAction(previousPage = null, currentPage = 400)
        )
        assertEquals(
            ViewerThumbnailScrollAction.Animate,
            viewerThumbnailScrollAction(previousPage = 20, currentPage = 21)
        )
        assertEquals(
            ViewerThumbnailScrollAction.Jump,
            viewerThumbnailScrollAction(previousPage = 10, currentPage = 200)
        )
        assertEquals(
            ViewerThumbnailScrollAction.None,
            viewerThumbnailScrollAction(previousPage = 20, currentPage = 20)
        )
        assertEquals(
            ViewerThumbnailScrollAction.None,
            viewerThumbnailScrollAction(previousPage = 20, currentPage = -1)
        )
    }

    @Test
    fun `viewer session cache stays bounded and preserves replacements`() {
        val cache = linkedMapOf<Int, Long>()
        repeat(3) { cache.putBounded(it, it.toLong(), maxEntries = 3) }

        cache.putBounded(1, 10L, maxEntries = 3)
        cache.putBounded(3, 3L, maxEntries = 3)

        assertEquals(mapOf(1 to 10L, 2 to 2L, 3 to 3L), cache)
    }

    @Test
    fun `loaded strip thumbnails survive reuse while the cache stays bounded`() {
        val cache = VideoStripLoadedValueCache<String>(maxEntries = 2)
        cache.put("first", "first-painter")
        cache.put("second", "second-painter")

        assertEquals("first-painter", cache["first"])
        cache.put("third", "third-painter")

        assertNull(cache["first"])
        assertEquals("second-painter", cache["second"])
        assertEquals("third-painter", cache["third"])
    }

    @Test
    fun `reordered gallery files retain their matching session media item`() {
        val first = videoFile("/movies/first.mp4")
        val second = videoFile("/movies/second.mp4")
        val firstItem = playbackItem(first)
        val secondItem = playbackItem(second)
        val session = VideoPlaybackSession(
            items = listOf(firstItem, secondItem),
            files = listOf(first, second)
        )

        assertEquals(secondItem, videoPlaybackItemFor(second, fallbackIndex = 0, session))
        assertEquals(firstItem, videoPlaybackItemFor(first, fallbackIndex = 1, session))
    }

    @Test
    fun `discovered sibling never reuses an unrelated session item by page index`() {
        val launchedFile = videoFile("/movies/launched.mp4")
        val sibling = videoFile("/movies/sibling.mp4")
        val launchedItem = playbackItem(launchedFile)
        val session = VideoPlaybackSession(items = listOf(launchedItem))

        val siblingItem = videoPlaybackItemFor(sibling, fallbackIndex = 0, session)

        assertNotEquals(launchedItem.mediaItem, siblingItem.mediaItem)
        assertEquals("sibling.mp4", siblingItem.title)
    }

    @Test
    fun `large pager context can lazily resolve videos outside the eager item queue`() {
        val launchedFile = videoFile("/movies/launched.mp4")
        val sibling = videoFile("/movies/sibling.mp4")
        val launchedItem = playbackItem(launchedFile)
        val session = VideoPlaybackSession(
            items = listOf(launchedItem),
            files = listOf(launchedFile, sibling)
        )
        val resolver = VideoPlaybackItemResolver(session)

        assertEquals(launchedItem, resolver.resolve(launchedFile, fallbackIndex = 0))
        assertEquals("sibling.mp4", resolver.resolve(sibling, fallbackIndex = 1).title)
    }

    @Test
    fun `gallery content uri keeps selected video at its original thumbnail position`() {
        val first = videoFile("/movies/first.mp4")
        val selected = videoFile("/movies/second.mp4")
        val third = videoFile("/movies/third.mp4")
        val selectedItem = VideoPlaybackItem(
            mediaItem = MediaItem.Builder()
                .setUri("content://media/external/video/media/42")
                .setMediaId(selected.reference)
                .build(),
            title = selected.name
        )
        val session = VideoPlaybackSession(
            items = listOf(selectedItem),
            files = listOf(first, selected, third)
        )

        val initialPath = videoPlaybackInitialPath(session)
        val initializedFiles = (session.files.orEmpty() + fileModelFromPath(initialPath))
            .distinctBy(FileModel::reference)

        assertEquals(selected.reference, initialPath)
        assertEquals(listOf(first, selected, third), initializedFiles)
        assertEquals(1, initializedFiles.indexOfFirst { it.reference == initialPath })
        assertEquals(selectedItem, videoPlaybackItemFor(selected, fallbackIndex = 1, session))
    }

    @Test
    fun `path matching does not confuse suffix-related video locations`() {
        val nested = videoFile("/archive/movies/clip.mp4")
        val requested = videoFile("/movies/clip.mp4")
        val nestedItem = playbackItem(nested)
        val session = VideoPlaybackSession(items = listOf(nestedItem))

        val requestedItem = videoPlaybackItemFor(requested, fallbackIndex = 0, session)

        assertNotEquals(nestedItem.mediaItem, requestedItem.mediaItem)
        assertEquals("clip.mp4", requestedItem.title)
    }

    @Test
    fun `vault launch uses opaque context path rather than provider uri path`() {
        val file = videoFile("opaque-node-id")
        val item = VideoPlaybackItem(
            mediaItem = MediaItem.Builder()
                .setUri("onlyfiles://playback/provider-node-id")
                .build(),
            title = "private.mp4"
        )
        val session = VideoPlaybackSession(items = listOf(item), files = listOf(file))

        assertEquals("opaque-node-id", videoPlaybackInitialPath(session))
    }

    @Test
    fun `content playback reference keeps its complete uri`() {
        val item = VideoPlaybackItem(
            mediaItem = MediaItem.Builder()
                .setUri("content://media/external/video/media/42")
                .build(),
            title = "clip.mp4"
        )

        assertEquals(
            "content://media/external/video/media/42",
            videoPlaybackReference(item)
        )
    }

    @Test
    fun `media reload is keyed by path rather than mutable pager index`() {
        assertFalse(videoPlaybackNeedsMediaSwitch("/movies/one.mp4", "/movies/one.mp4"))
        assertTrue(videoPlaybackNeedsMediaSwitch("/movies/one.mp4", "/movies/two.mp4"))
        assertTrue(videoPlaybackNeedsMediaSwitch(null, "/movies/one.mp4"))
    }

    @Test
    fun `seek bar remains stable while the next video duration is loading`() {
        assertEquals(VideoSeekState(progress = 0f, canSeek = false), videoSeekState(0L, 0L))
        assertEquals(VideoSeekState(progress = 0.5f, canSeek = true), videoSeekState(500L, 1_000L))
    }

    @Test
    fun `resize mode cycles through fit zoom and fill`() {
        assertEquals(1, nextVideoResizeModeIndex(0))
        assertEquals(2, nextVideoResizeModeIndex(1))
        assertEquals(0, nextVideoResizeModeIndex(2))
    }

    @Test
    fun `video timer switches between total and remaining time`() {
        assertEquals("2:00", videoPlaybackEndTimeText(30_000L, 120_000L, showRemaining = false))
        assertEquals("−1:30", videoPlaybackEndTimeText(30_000L, 120_000L, showRemaining = true))
        assertEquals("−0:00", videoPlaybackEndTimeText(130_000L, 120_000L, showRemaining = true))
    }

    @Test
    fun `android system edges cannot start vertical video gestures`() {
        fun allowed(x: Float, y: Float) = videoVerticalGestureAllowed(
            startX = x,
            startY = y,
            viewportWidth = 1_000f,
            viewportHeight = 1_000f,
            leftGestureInset = 32f,
            rightGestureInset = 32f,
            topGestureInset = 80f,
            bottomGestureInset = 80f
        )

        assertTrue(allowed(100f, 500f))
        assertTrue(allowed(900f, 500f))
        assertFalse(allowed(31f, 500f))
        assertFalse(allowed(968f, 500f))
        assertFalse(allowed(500f, 79f))
        assertFalse(allowed(500f, 920f))
    }

    @Test
    fun `video zoom clamps scale and pan to the visible viewport`() {
        assertEquals(
            VideoZoomTransform(scale = 2f, offsetX = 250f, offsetY = -250f),
            videoZoomTransformAfterGesture(
                scale = 1f,
                offsetX = 0f,
                offsetY = 0f,
                zoomChange = 2f,
                panX = 900f,
                panY = -600f,
                viewportWidth = 500f,
                viewportHeight = 500f
            )
        )
        assertEquals(
            VideoZoomTransform(scale = 1f, offsetX = 0f, offsetY = 0f),
            videoZoomTransformAfterGesture(
                scale = 2f,
                offsetX = 100f,
                offsetY = 100f,
                zoomChange = 0.1f,
                panX = 0f,
                panY = 0f,
                viewportWidth = 500f,
                viewportHeight = 500f
            )
        )
    }

    @Test
    fun `progress scrubbing continuously maps forward and reverse positions`() {
        assertEquals(8_000L, videoScrubTargetPosition(.8f, 10_000L))
        assertEquals(2_000L, videoScrubTargetPosition(.2f, 10_000L))
        assertEquals(0L, videoScrubTargetPosition(-1f, 10_000L))
        assertEquals(10_000L, videoScrubTargetPosition(2f, 10_000L))
    }

    @Test
    fun `fast pointer jumps map directly to absolute scrub progress`() {
        assertEquals(0.08f, videoScrubProgressForPointer(8f, 100f))
        assertEquals(0.92f, videoScrubProgressForPointer(92f, 100f))
        assertEquals(0.12f, videoScrubProgressForPointer(12f, 100f))
        assertEquals(0f, videoScrubProgressForPointer(-20f, 100f))
        assertEquals(1f, videoScrubProgressForPointer(120f, 100f))
        assertEquals(0f, videoScrubProgressForPointer(50f, 0f))
    }

    @Test
    fun `scrubbing pauses active playback and resumes only when it started active`() {
        val playingStart = videoScrubPlaybackTransition(
            wasScrubbing = false,
            resumeWhenFinished = false,
            scrubbing = true,
            isPlaying = true
        )
        assertTrue(playingStart.pausePlayback)
        assertTrue(playingStart.resumeWhenFinished)

        val playingEnd = videoScrubPlaybackTransition(
            wasScrubbing = playingStart.isScrubbing,
            resumeWhenFinished = playingStart.resumeWhenFinished,
            scrubbing = false,
            isPlaying = false
        )
        assertTrue(playingEnd.resumePlayback)

        val pausedStart = videoScrubPlaybackTransition(
            wasScrubbing = false,
            resumeWhenFinished = false,
            scrubbing = true,
            isPlaying = false
        )
        assertFalse(pausedStart.pausePlayback)
        assertFalse(pausedStart.resumeWhenFinished)
        assertFalse(
            videoScrubPlaybackTransition(
                wasScrubbing = pausedStart.isScrubbing,
                resumeWhenFinished = pausedStart.resumeWhenFinished,
                scrubbing = false,
                isPlaying = false
            ).resumePlayback
        )
    }

    @Test
    fun `next video keeps its thumbnail before delayed loading feedback`() {
        assertFalse(
            shouldShowVideoLoadingIndicator(
                isBuffering = true,
                showPlaceholder = true,
                placeholderDelayElapsed = false
            )
        )
        assertFalse(
            videoPlayerSurfaceCanAttach(
                isPageFocused = true,
                loadedPath = "/movies/old.mp4",
                pagePath = "/movies/next.mp4"
            )
        )
        assertTrue(
            videoPlayerSurfaceCanAttach(
                isPageFocused = true,
                loadedPath = "/movies/next.mp4",
                pagePath = "/movies/next.mp4"
            )
        )
        assertNull(
            videoRenderedPathForFirstFrame(
                currentMediaId = "/movies/next.mp4",
                loadedPath = "/movies/next.mp4",
                transitionedPath = null
            )
        )
        assertEquals(
            "/movies/next.mp4",
            videoRenderedPathForFirstFrame(
                currentMediaId = "/movies/next.mp4",
                loadedPath = "/movies/next.mp4",
                transitionedPath = "/movies/next.mp4"
            )
        )
        assertTrue(
            shouldShowVideoLoadingIndicator(
                isBuffering = true,
                showPlaceholder = true,
                placeholderDelayElapsed = true
            )
        )
        assertFalse(
            shouldShowVideoLoadingIndicator(
                isBuffering = false,
                showPlaceholder = false,
                placeholderDelayElapsed = true
            )
        )
    }

    @Test
    fun `ordinary videos remain active in background while protected playback closes`() {
        assertTrue(videoBackgroundPlaybackAllowed(securityScopeId = null))
        assertFalse(videoBackgroundPlaybackAllowed(securityScopeId = "vault:one"))
        assertTrue(shouldKeepVideoPlayerActive(true, lifecycleStarted = false))
        assertFalse(shouldKeepVideoPlayerActive(false, lifecycleStarted = false))
        assertTrue(shouldKeepVideoPlayerActive(false, lifecycleStarted = true))
    }

    @Test
    fun `initialized viewer never resurrects its deleted launch file`() {
        val remaining = videoFile("/movies/remaining.mp4")

        val context = videoViewerFileContextAfterInitialization(
            initialPath = "/movies/deleted.mp4",
            displayedFiles = listOf(remaining),
            allFiles = listOf(remaining)
        )

        assertEquals(listOf(remaining), context.files)
        assertEquals(0, context.initialPage)
    }

    @Test
    fun `initialized viewer remains empty after its final file is deleted`() {
        val context = videoViewerFileContextAfterInitialization(
            initialPath = "/movies/deleted.mp4",
            displayedFiles = emptyList(),
            allFiles = emptyList()
        )

        assertTrue(context.files.isEmpty())
        assertEquals(0, context.initialPage)
    }

    @Test
    fun `video player surface splits into left center and right gesture zones`() {
        val width = 900f
        assertEquals(GestureZone.LEFT, videoPlayerGestureZone(100f, width))
        assertEquals(GestureZone.LEFT, videoPlayerGestureZone(299f, width))
        assertEquals(GestureZone.CENTER, videoPlayerGestureZone(300f, width))
        assertEquals(GestureZone.CENTER, videoPlayerGestureZone(450f, width))
        assertEquals(GestureZone.CENTER, videoPlayerGestureZone(599f, width))
        assertEquals(GestureZone.RIGHT, videoPlayerGestureZone(600f, width))
        assertEquals(GestureZone.RIGHT, videoPlayerGestureZone(850f, width))
    }

    private fun videoFile(path: String) = FileModel(
        name = path.substringAfterLast('/'),
        reference = path,
        size = 1L,
        lastModified = 1L,
        isDirectory = false,
        extension = "mp4",
        mimeType = "video/mp4"
    )

    private fun playbackItem(file: FileModel) = VideoPlaybackItem(
        mediaItem = MediaItem.Builder()
            .setUri("file://${file.reference}")
            .setMimeType(file.mimeType)
            .build(),
        title = file.name
    )
}
