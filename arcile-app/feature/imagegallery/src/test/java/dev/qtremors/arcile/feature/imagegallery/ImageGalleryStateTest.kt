package dev.qtremors.arcile.feature.imagegallery

import android.net.Uri
import androidx.compose.ui.geometry.Offset
import dev.qtremors.arcile.core.storage.domain.FileListingPreferences
import dev.qtremors.arcile.core.storage.domain.FileViewMode
import dev.qtremors.arcile.core.storage.domain.ImageGalleryDefaultTab
import dev.qtremors.arcile.core.storage.domain.ImageGalleryGrouping
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.ClipboardState
import dev.qtremors.arcile.core.storage.domain.ClipboardOperation
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import kotlinx.collections.immutable.persistentMapOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.Locale
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ImageGalleryStateTest {
    @Test
    fun `default state is ready for image gallery first load`() {
        val state = ImageGalleryState(volumeId = "primary")

        assertEquals("primary", state.volumeId)
        assertEquals(FileListingPreferences.DEFAULT_CATEGORY_SORT_OPTION, state.presentation.sortOption)
        assertEquals(FileViewMode.GRID, state.presentation.viewMode)
        assertTrue(state.isLoading)
        assertTrue(state.files.isEmpty())
        assertTrue(state.selectedFiles.isEmpty())
    }

    @Test
    fun `state aspect ratios and grouping preferences are correct`() {
        val state = ImageGalleryState(
            isAspectRatio = true,
            isSectioned = true,
            aspectRatios = persistentMapOf("path/to/img" to 1.5f)
        )
        assertTrue(state.isAspectRatio)
        assertTrue(state.isSectioned)
        assertEquals(1.5f, state.aspectRatios["path/to/img"])
    }

    @Test
    fun `dynamic image gallery grouping options are stored correctly in state`() {
        val state = ImageGalleryState(
            imageGalleryGrouping = ImageGalleryGrouping.DAY,
            albumPresentation = FileListingPreferences(gridMinCellSize = 180f),
            imageGalleryDefaultTab = ImageGalleryDefaultTab.ALBUMS,
            preferencesLoaded = true
        )
        assertEquals(ImageGalleryGrouping.DAY, state.imageGalleryGrouping)
        assertEquals(180f, state.albumPresentation.gridMinCellSize)
        assertEquals(ImageGalleryDefaultTab.ALBUMS, state.imageGalleryDefaultTab)
        assertTrue(state.preferencesLoaded)
    }

    @Test
    fun `state clipboard syncing works`() {
        val dummyFiles = listOf(
            FileModel("file1", "path1", size = 100, lastModified = 0, isDirectory = false)
        )
        val clipboard = ClipboardState(ClipboardOperation.COPY, dummyFiles)
        val state = ImageGalleryState(
            fileActions = ImageGalleryFileActionState(clipboardState = clipboard)
        )
        assertEquals(clipboard, state.clipboardState)
    }

    @Test
    fun `time section helper categorizes dates correctly`() {
        val now = System.currentTimeMillis()
        val oneDay = 24 * 60 * 60 * 1000L
        val oneWeek = 7 * oneDay
        val oneMonth = 30 * oneDay

        assertEquals(TimeSection.TODAY, getTimeSection(now - 1000L, now))
        assertEquals(TimeSection.WEEK, getTimeSection(now - 2 * oneDay, now))
        assertEquals(TimeSection.MONTH, getTimeSection(now - 10 * oneDay, now))
        assertEquals(TimeSection.OLDER, getTimeSection(now - 45 * oneDay, now))
    }

    @Test
    fun `invert selection logic accurately swaps selected and unselected paths`() {
        val displayed = listOf(
            FileModel("file1", "path1", size = 100, lastModified = 0, isDirectory = false),
            FileModel("file2", "path2", size = 100, lastModified = 0, isDirectory = false),
            FileModel("file3", "path3", size = 100, lastModified = 0, isDirectory = false)
        )
        val selected = setOf("path1")
        
        val allPaths = displayed.map(FileModel::absolutePath).toSet()
        val inverted = allPaths - selected
        
        assertEquals(2, inverted.size)
        assertTrue(inverted.contains("path2"))
        assertTrue(inverted.contains("path3"))
        assertTrue(!inverted.contains("path1"))
    }

    @Test
    fun `favorites and album covers properties are populated correctly in state`() {
        val state = ImageGalleryState(
            favoriteFiles = kotlinx.collections.immutable.persistentSetOf("fav1", "fav2"),
            albumCovers = kotlinx.collections.immutable.persistentMapOf("album1" to "cover1")
        )
        assertEquals(2, state.favoriteFiles.size)
        assertTrue(state.favoriteFiles.contains("fav1"))
        assertEquals("cover1", state.albumCovers["album1"])
    }

    @Test
    fun `favorites album is omitted when persisted favorites are not in current files`() {
        val albums = listOf(ImageGalleryAlbum("album", "Album", 1, 100L))
        val files = listOf(FileModel("one.jpg", "album/one.jpg", size = 100, lastModified = 100, isDirectory = false))

        val tiles = buildVisibleAlbumTiles(
            sortedAlbums = albums,
            files = files,
            favoriteFiles = setOf("missing.jpg"),
            favoritesLabel = "Favorites"
        )

        assertEquals(albums, tiles)
    }

    @Test
    fun `favorites album count uses only current files`() {
        val currentFavorite = FileModel("one.jpg", "album/one.jpg", size = 100, lastModified = 100, isDirectory = false)

        val tiles = buildVisibleAlbumTiles(
            sortedAlbums = emptyList(),
            files = listOf(currentFavorite),
            favoriteFiles = setOf(currentFavorite.absolutePath, "missing.jpg"),
            favoritesLabel = "Favorites"
        )

        assertEquals(1, tiles.size)
        assertEquals(FAVORITES_ALBUM_PATH, tiles.first().path)
        assertEquals(1, tiles.first().count)
    }

    @Test
    fun `favorites album cover resolves to latest current favorite`() {
        val older = FileModel("one.jpg", "album/one.jpg", size = 100, lastModified = 100, isDirectory = false)
        val latest = FileModel("two.jpg", "album/two.jpg", size = 200, lastModified = 200, isDirectory = false)

        val cover = resolveAlbumCoverFile(
            albumPath = FAVORITES_ALBUM_PATH,
            files = listOf(latest, older),
            favoriteFiles = linkedSetOf(older.absolutePath, "missing.jpg", latest.absolutePath),
            albumCovers = emptyMap()
        )

        assertEquals(latest, cover)
    }

    @Test
    fun `custom album cover resolves to persisted cover when present`() {
        val fallback = FileModel("one.jpg", "album/one.jpg", size = 100, lastModified = 100, isDirectory = false)
        val custom = FileModel("two.jpg", "album/two.jpg", size = 200, lastModified = 200, isDirectory = false)

        val cover = resolveAlbumCoverFile(
            albumPath = "album",
            files = listOf(fallback, custom),
            favoriteFiles = emptySet(),
            albumCovers = mapOf("album" to custom.absolutePath)
        )

        assertEquals(custom, cover)
    }

    @Test
    fun `album cover lookup precomputes custom fallback and favorites covers`() {
        val fallback = FileModel("one.jpg", "album/one.jpg", size = 100, lastModified = 100, isDirectory = false)
        val custom = FileModel("two.jpg", "album/two.jpg", size = 200, lastModified = 200, isDirectory = false)
        val favorite = FileModel("fav.jpg", "favorites/fav.jpg", size = 300, lastModified = 300, isDirectory = false)

        val lookup = buildAlbumCoverLookup(
            files = listOf(fallback, custom, favorite),
            favoriteFiles = linkedSetOf(fallback.absolutePath, favorite.absolutePath),
            albumCovers = mapOf("album" to custom.absolutePath)
        )

        assertEquals(custom, lookup["album"])
        assertEquals(favorite, lookup[FAVORITES_ALBUM_PATH])
    }

    @Test
    fun `only real albums are valid paste destinations`() {
        assertTrue(isPasteDestinationAlbumPath("/storage/emulated/0/Pictures/Camera"))
        assertTrue(!isPasteDestinationAlbumPath(null))
        assertTrue(!isPasteDestinationAlbumPath(""))
        assertTrue(!isPasteDestinationAlbumPath(FAVORITES_ALBUM_PATH))
    }

    @Test
    fun `custom album cover falls back to album image when persisted cover is missing`() {
        val fallback = FileModel("one.jpg", "album/one.jpg", size = 100, lastModified = 100, isDirectory = false)

        val cover = resolveAlbumCoverFile(
            albumPath = "album",
            files = listOf(fallback),
            favoriteFiles = emptySet(),
            albumCovers = mapOf("album" to "album/missing.jpg")
        )

        assertEquals(fallback, cover)
    }

    @Test
    fun `album cover is null when no current file belongs to album`() {
        val cover = resolveAlbumCoverFile(
            albumPath = "album",
            files = emptyList(),
            favoriteFiles = emptySet(),
            albumCovers = mapOf("album" to "album/missing.jpg")
        )

        assertNull(cover)
    }

    @Test
    fun `album sorting by last modified date order is correct`() {
        val albums = listOf(
            ImageGalleryAlbum("path1", "Album A", 10, 1000L),
            ImageGalleryAlbum("path2", "Album B", 5, 3000L),
            ImageGalleryAlbum("path3", "Album C", 20, 2000L)
        )

        val sortedNewest = albums.sortedByDescending { it.lastModified }
        val sortedOldest = albums.sortedBy { it.lastModified }

        assertEquals("path2", sortedNewest[0].path)
        assertEquals("path3", sortedNewest[1].path)
        assertEquals("path1", sortedNewest[2].path)

        assertEquals("path1", sortedOldest[0].path)
        assertEquals("path3", sortedOldest[1].path)
        assertEquals("path2", sortedOldest[2].path)
    }

    @Test
    fun `exif metadata data class stores all attributes correctly`() {
        val metadata = GalleryFileMetadata(
            path = "path1",
            size = 1024L,
            mimeType = "image/jpeg",
            width = 1920,
            height = 1080,
            megapixel = 2.07,
            cameraMaker = "Google",
            cameraModel = "Pixel 6",
            lensModel = "Pixel 6 Lens",
            iso = 100,
            exposureTime = "1/100 s",
            fNumber = 1.8,
            focalLength = 6.8,
            whiteBalance = "Auto",
            flash = "Did not fire",
            dateTaken = "2026:06:11 12:00:00",
            latitude = 37.7749,
            longitude = -122.4194,
            altitude = 15.0
        )
        assertEquals("Google", metadata.cameraMaker)
        assertEquals("Pixel 6", metadata.cameraModel)
        assertEquals(100, metadata.iso)
        assertEquals(37.7749, metadata.latitude ?: 0.0, 0.0001)
    }

    @Test
    fun `png image request data prefers content uri`() {
        val file = FileModel(
            name = "photo.png",
            absolutePath = "/storage/emulated/0/Pictures/photo.png",
            extension = "png",
            mimeType = "image/png",
            nodeRef = StorageNodeRef.mediaStore(
                id = 42L,
                volumeName = "external",
                contentUri = "content://media/external/images/media/42",
                displayPath = "/storage/emulated/0/Pictures/photo.png"
            )
        )

        assertEquals(Uri.parse("content://media/external/images/media/42"), imageRequestDataFor(file))
        assertEquals(Uri.parse("content://media/external/images/media/42"), galleryThumbnailRequestDataFor(file))
    }

    @Test
    fun `image request data falls back to file path without content uri`() {
        val file = FileModel(
            name = "photo.png",
            absolutePath = "/storage/emulated/0/Pictures/photo.png",
            extension = "png",
            mimeType = "image/png"
        )

        assertEquals(File("/storage/emulated/0/Pictures/photo.png"), imageRequestDataFor(file))
    }

    @Test
    fun `archive thumbnail data takes precedence over image content uri`() {
        val file = FileModel(
            name = "photo.png",
            absolutePath = "/storage/emulated/0/archive.zip!/photo.png",
            extension = "png",
            mimeType = "image/png",
            nodeRef = StorageNodeRef.mediaStore(
                id = 42L,
                volumeName = "external",
                contentUri = "content://media/external/images/media/42",
                displayPath = "/storage/emulated/0/archive.zip!/photo.png"
            )
        )
        val archiveData = Any()

        assertEquals(archiveData, galleryThumbnailRequestDataFor(file, archiveData))
    }

    @Test
    fun `viewer context preserves displayed order and initial clicked index`() {
        val first = FileModel("one.jpg", "/photos/one.jpg", size = 100, lastModified = 100, isDirectory = false)
        val second = FileModel("two.jpg", "/photos/two.jpg", size = 100, lastModified = 200, isDirectory = false)
        val third = FileModel("three.jpg", "/photos/three.jpg", size = 100, lastModified = 300, isDirectory = false)

        val context = viewerFileContextForInitialPath(
            initialPath = second.absolutePath,
            displayedFiles = listOf(first, second, third),
            allFiles = listOf(third, second, first)
        )

        assertEquals(listOf(first, second, third), context.files)
        assertEquals(1, context.initialPage)
    }

    @Test
    fun `viewer context uses loaded gallery when opened outside displayed filter`() {
        val hiddenByFilter = FileModel("hidden.jpg", "/photos/hidden.jpg", size = 100, lastModified = 100, isDirectory = false)
        val visible = FileModel("visible.jpg", "/photos/visible.jpg", size = 100, lastModified = 200, isDirectory = false)

        val context = viewerFileContextForInitialPath(
            initialPath = hiddenByFilter.absolutePath,
            displayedFiles = listOf(visible),
            allFiles = listOf(hiddenByFilter, visible)
        )

        assertEquals(listOf(hiddenByFilter, visible), context.files)
        assertEquals(0, context.initialPage)
    }

    @Test
    fun `viewer initial page restores only within same viewer session`() {
        val first = FileModel("one.jpg", "/photos/one.jpg", size = 100, lastModified = 100, isDirectory = false)
        val second = FileModel("two.jpg", "/photos/two.jpg", size = 100, lastModified = 200, isDirectory = false)
        val third = FileModel("three.jpg", "/photos/three.jpg", size = 100, lastModified = 300, isDirectory = false)
        val context = ViewerFileContext(files = listOf(first, second, third), initialPage = 2)

        assertEquals(
            1,
            viewerInitialPageForSession(
                initialPath = third.absolutePath,
                viewerSessionInitialPath = third.absolutePath,
                viewerCurrentPath = second.absolutePath,
                viewerContext = context
            )
        )
        assertEquals(
            2,
            viewerInitialPageForSession(
                initialPath = third.absolutePath,
                viewerSessionInitialPath = first.absolutePath,
                viewerCurrentPath = second.absolutePath,
                viewerContext = context
            )
        )
    }

    @Test
    fun `gallery lazy index for viewer return includes grouped section headers`() {
        val first = FileModel("one.jpg", "/photos/one.jpg", size = 100, lastModified = 100, isDirectory = false)
        val second = FileModel("two.jpg", "/photos/two.jpg", size = 100, lastModified = 200, isDirectory = false)
        val third = FileModel("three.jpg", "/photos/three.jpg", size = 100, lastModified = 300, isDirectory = false)
        val grouped = linkedMapOf(
            GroupKey("First", 1L) to listOf(first, second),
            GroupKey("Second", 2L) to listOf(third)
        )

        assertEquals(
            2,
            galleryLazyIndexForPath(
                path = second.absolutePath,
                displayedFiles = listOf(first, second, third),
                imageGalleryGrouping = ImageGalleryGrouping.MONTH,
                groupedFiles = grouped
            )
        )
        assertEquals(
            4,
            galleryLazyIndexForPath(
                path = third.absolutePath,
                displayedFiles = listOf(first, second, third),
                imageGalleryGrouping = ImageGalleryGrouping.MONTH,
                groupedFiles = grouped
            )
        )
    }

    @Test
    fun `viewer position label is based on current page and context size`() {
        assertEquals("3/100", viewerPositionLabel(currentPage = 2, total = 100))
        assertEquals("1/1", viewerPositionLabel(currentPage = 0, total = 1))
        assertEquals("0/0", viewerPositionLabel(currentPage = 0, total = 0))
    }

    @Test
    fun `viewer thumbnail strip jumps for first and far positioning`() {
        assertEquals(
            ViewerThumbnailScrollAction.Jump,
            viewerThumbnailScrollAction(previousIndex = null, targetIndex = 400)
        )
        assertEquals(
            ViewerThumbnailScrollAction.Jump,
            viewerThumbnailScrollAction(previousIndex = 10, targetIndex = 200)
        )
    }

    @Test
    fun `viewer thumbnail strip animates only nearby positioning`() {
        assertEquals(
            ViewerThumbnailScrollAction.Animate,
            viewerThumbnailScrollAction(previousIndex = 20, targetIndex = 24)
        )
        assertEquals(
            ViewerThumbnailScrollAction.None,
            viewerThumbnailScrollAction(previousIndex = 20, targetIndex = 20)
        )
        assertEquals(
            ViewerThumbnailScrollAction.None,
            viewerThumbnailScrollAction(previousIndex = 20, targetIndex = -1)
        )
    }

    @Test
    fun `viewer zoom release keeps high zoom instead of old clamp`() {
        assertEquals(0.8f, viewerReleaseScale(0.8f), 0f)
        assertEquals(0.05f, viewerReleaseScale(0.05f), 0f)
        assertEquals(250f, viewerReleaseScale(250f), 0f)
        assertEquals(10_000f, viewerRenderScale(10_000f, dragFraction = 0f), 0f)
    }

    @Test
    fun `viewer zoom out is not clamped back to fit`() {
        assertEquals(0.25f, viewerReleaseScale(0.25f), 0f)
        assertEquals(0.25f, viewerRenderScale(0.25f, dragFraction = 0f), 0f)
        assertEquals(0f, viewerPanLimit(0.25f, viewportSize = 1080), 0f)
        assertEquals(540f, viewerPanLimit(2f, viewportSize = 1080), 0f)
    }

    @Test
    fun `viewer zoom preserves the touched focal point`() {
        val next = viewerOffsetForScale(
            currentOffset = Offset.Zero,
            oldScale = 1f,
            newScale = 2f,
            centroid = Offset(250f, 400f),
            viewportCenter = Offset(500f, 500f)
        )

        assertEquals(250f, next.x, 0.001f)
        assertEquals(100f, next.y, 0.001f)
    }

    @Test
    fun `viewer pan limits respect fitted letterboxed image bounds`() {
        val fitted = viewerFittedContentSize(
            viewportWidth = 1080f,
            viewportHeight = 1920f,
            imageWidth = 4000f,
            imageHeight = 3000f,
            rotationDegrees = 0f
        )

        assertEquals(1080f, fitted.width, 0.001f)
        assertEquals(810f, fitted.height, 0.001f)
        assertEquals(540f, viewerPanLimit(2f, fitted.width, 1080f), 0.001f)
        assertEquals(0f, viewerPanLimit(2f, fitted.height, 1920f), 0.001f)
    }

    @Test
    fun `viewer fitted bounds swap dimensions after quarter turn`() {
        val fitted = viewerFittedContentSize(
            viewportWidth = 1080f,
            viewportHeight = 1920f,
            imageWidth = 4000f,
            imageHeight = 3000f,
            rotationDegrees = 90f
        )

        assertEquals(1080f, fitted.width, 0.001f)
        assertEquals(1440f, fitted.height, 0.001f)
    }

    @Test
    fun `viewer date time uses compact display format`() {
        val timestamp = 1_781_086_440_000L

        assertEquals(
            "Jun 10, 2026 • 10:14 AM",
            formatViewerDateTime(timestamp, Locale.US, TimeZone.getTimeZone("UTC"))
        )
    }

    @Test
    fun `metadata detail rows include core file information when available`() {
        val file = FileModel(
            name = "photo.jpg",
            absolutePath = "/storage/emulated/0/DCIM/photo.jpg",
            size = 30_360L,
            lastModified = 1_781_086_440_000L,
            extension = "jpg",
            mimeType = "image/jpeg",
            nodeRef = StorageNodeRef.mediaStore(
                id = 42L,
                volumeName = "external",
                contentUri = "content://media/external/images/media/42",
                displayPath = "/storage/emulated/0/DCIM/photo.jpg"
            )
        )
        val metadata = GalleryFileMetadata(
            path = file.absolutePath,
            size = file.size,
            mimeType = file.mimeType,
            width = 1053,
            height = 317,
            megapixel = 0.33,
            cameraMaker = null,
            cameraModel = null,
            lensModel = null,
            iso = null,
            exposureTime = null,
            fNumber = null,
            focalLength = null,
            whiteBalance = null,
            flash = null,
            dateTaken = "2026:06:10 10:14:00",
            latitude = null,
            longitude = null,
            altitude = null
        )

        val rows = buildMetadataDetailRows(
            file = file,
            metadata = metadata,
            labels = testMetadataLabels,
            dateText = formatViewerDateTime(file.lastModified, Locale.US, TimeZone.getTimeZone("UTC"))
        )

        assertEquals("photo.jpg", rows.valueFor("Title"))
        assertEquals("Jun 10, 2026 • 10:14 AM", rows.valueFor("Date"))
        assertEquals("2026:06:10 10:14:00", rows.valueFor("Date taken"))
        assertEquals("1053 x 317", rows.valueFor("Resolution"))
        assertEquals("29.65 KB", rows.valueFor("Size"))
        assertEquals("content://media/external/images/media/42", rows.valueFor("URI"))
        assertEquals("/storage/emulated/0/DCIM/photo.jpg", rows.valueFor("Path"))
        assertEquals("image/jpeg", rows.valueFor("MIME type"))
        assertEquals("JPG", rows.valueFor("Extension"))
    }

    @Test
    fun `metadata detail rows omit unavailable uri and resolution`() {
        val file = FileModel(
            name = "photo",
            absolutePath = "/storage/emulated/0/DCIM/photo",
            size = 0L,
            lastModified = 0L,
            isDirectory = false
        )

        val rows = buildMetadataDetailRows(
            file = file,
            metadata = null,
            labels = testMetadataLabels
        )

        assertNull(rows.valueFor("Date"))
        assertNull(rows.valueFor("Resolution"))
        assertNull(rows.valueFor("URI"))
        assertEquals("0 B", rows.valueFor("Size"))
        assertEquals("/storage/emulated/0/DCIM/photo", rows.valueFor("Path"))
    }

    @Test
    fun `removing gallery paths updates displayed files favorites and album counts`() {
        val kept = FileModel("kept.jpg", "/photos/album/kept.jpg", size = 100, lastModified = 100, isDirectory = false)
        val deleted = FileModel("deleted.jpg", "/photos/album/deleted.jpg", size = 100, lastModified = 200, isDirectory = false)
        val other = FileModel("other.jpg", "/photos/other/other.jpg", size = 100, lastModified = 300, isDirectory = false)
        val state = ImageGalleryState(
            files = kotlinx.collections.immutable.persistentListOf(kept, deleted, other),
            displayedFiles = kotlinx.collections.immutable.persistentListOf(deleted, kept, other),
            albums = kotlinx.collections.immutable.persistentListOf(
                ImageGalleryAlbum("/photos/album", "album", 2, 200),
                ImageGalleryAlbum("/photos/other", "other", 1, 300)
            ),
            selectedAlbumPath = "/photos/album",
            favoriteFiles = kotlinx.collections.immutable.persistentSetOf(deleted.absolutePath, kept.absolutePath),
            albumCovers = kotlinx.collections.immutable.persistentMapOf("/photos/album" to deleted.absolutePath)
        )

        val next = state.withoutGalleryPaths(listOf(deleted.absolutePath))

        assertEquals(listOf(kept.absolutePath, other.absolutePath), next.files.map { it.absolutePath })
        assertEquals(listOf(kept.absolutePath), next.displayedFiles.map { it.absolutePath })
        assertEquals(setOf(kept.absolutePath), next.favoriteFiles)
        assertTrue(next.albumCovers.isEmpty())
        assertEquals(1, next.albums.first { it.path == "/photos/album" }.count)
    }

    private fun List<MetadataDetailRow>.valueFor(label: String): String? =
        firstOrNull { it.label == label }?.value

    private val testMetadataLabels = MetadataDetailLabels(
        title = "Title",
        date = "Date",
        dateTaken = "Date taken",
        resolution = "Resolution",
        size = "Size",
        uri = "URI",
        path = "Path",
        mimeType = "MIME type",
        extension = "Extension"
    )
}
