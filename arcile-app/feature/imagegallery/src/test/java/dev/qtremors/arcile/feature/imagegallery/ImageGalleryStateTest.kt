package dev.qtremors.arcile.feature.imagegallery

import android.net.Uri
import dev.qtremors.arcile.core.storage.domain.BrowserPresentationPreferences
import dev.qtremors.arcile.core.storage.domain.BrowserViewMode
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ImageGalleryStateTest {
    @Test
    fun `default state is ready for image gallery first load`() {
        val state = ImageGalleryState(volumeId = "primary")

        assertEquals("primary", state.volumeId)
        assertEquals(BrowserPresentationPreferences.DEFAULT_CATEGORY_SORT_OPTION, state.presentation.sortOption)
        assertEquals(BrowserViewMode.GRID, state.presentation.viewMode)
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
            albumPresentation = BrowserPresentationPreferences(gridMinCellSize = 180f),
            albumAspectRatio = true
        )
        assertEquals(ImageGalleryGrouping.DAY, state.imageGalleryGrouping)
        assertEquals(180f, state.albumPresentation.gridMinCellSize)
        assertTrue(state.albumAspectRatio)
    }

    @Test
    fun `state clipboard syncing works`() {
        val dummyFiles = listOf(
            FileModel("file1", "path1", size = 100, lastModified = 0, isDirectory = false)
        )
        val clipboard = ClipboardState(ClipboardOperation.COPY, dummyFiles)
        val state = ImageGalleryState(clipboardState = clipboard)
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
}
