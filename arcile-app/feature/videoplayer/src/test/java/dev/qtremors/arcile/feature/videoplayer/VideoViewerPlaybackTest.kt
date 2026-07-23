package dev.qtremors.arcile.feature.videoplayer

import androidx.media3.common.MediaItem
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.ui.video.VideoPlaybackItem
import dev.qtremors.arcile.core.ui.video.VideoPlaybackSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
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

    private fun videoFile(path: String) = FileModel(
        name = path.substringAfterLast('/'),
        absolutePath = path,
        size = 1L,
        lastModified = 1L,
        isDirectory = false,
        extension = "mp4",
        mimeType = "video/mp4"
    )

    private fun playbackItem(file: FileModel) = VideoPlaybackItem(
        mediaItem = MediaItem.Builder()
            .setUri("file://${file.absolutePath}")
            .setMimeType(file.mimeType)
            .build(),
        title = file.name
    )
}
