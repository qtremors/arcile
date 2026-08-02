package dev.qtremors.arcile.feature.audio

import dev.qtremors.arcile.core.storage.domain.CategoryLibraryPage
import dev.qtremors.arcile.core.storage.domain.AudioTrack
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.FileListingPreferences
import dev.qtremors.arcile.core.storage.domain.FileSortOption
import dev.qtremors.arcile.core.storage.domain.FileViewMode
import dev.qtremors.arcile.core.storage.domain.CategoryGrouping
import dev.qtremors.arcile.core.storage.domain.SearchFilters
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioLibraryPresentationTest {

    @Test
    fun `library groups tracks into folders`() {
        val tracks = listOf(
            track("/Music/One/first.mp3", "First", "Artist", "Album"),
            track("/Music/One/second.mp3", "Second", "Artist", "Album"),
            track("/Music/Two/third.mp3", "Third", "Other", "Other album")
        )

        val state = buildAudioLibraryState(AudioLibraryState(), tracks)

        assertEquals(listOf("One", "Two"), state.folders.map(AudioFolder::title))
        assertEquals(2, state.folders.first().tracks.size)
    }

    @Test
    fun `search matches track artist album and folder metadata`() {
        val tracks = listOf(
            track("/Music/Scores/first.mp3", "Opening", "Composer", "Film"),
            track("/Podcasts/Tech/second.mp3", "Episode", "Host", "Weekly")
        )

        val byArtist = buildAudioLibraryState(
            AudioLibraryState(query = "composer"),
            tracks
        )
        val byFolder = buildAudioLibraryState(
            AudioLibraryState(query = "podcasts"),
            tracks
        )

        assertEquals(listOf("Opening"), byArtist.visibleTracks.map { it.displayTitle })
        assertEquals(listOf("Episode"), byFolder.visibleTracks.map { it.displayTitle })
        assertEquals(listOf("Tech"), byFolder.folders.map(AudioFolder::title))
    }

    @Test
    fun `folder filter only includes tracks from its exact path`() {
        val tracks = listOf(
            track("/Music/folder/one.mp3", "One", "folder", "Album"),
            track("/Music/Elsewhere/two.mp3", "Two", "Artist", "folder")
        )
        val initial = buildAudioLibraryState(AudioLibraryState(), tracks)

        val folder = initial.folders.first { it.title == "folder" }

        assertEquals(
            listOf("One"),
            buildAudioLibraryState(initial.copy(folderFilter = folder)).visibleTracks.map { it.displayTitle }
        )
    }

    @Test
    fun `audio defaults to list view with newest files first`() {
        val tracks = listOf(
            track("/Music/old.mp3", "Old", "Artist", "Album", modified = 10L),
            track("/Music/new.mp3", "New", "Artist", "Album", modified = 30L),
            track("/Music/middle.mp3", "Middle", "Artist", "Album", modified = 20L)
        )

        val state = buildAudioLibraryState(AudioLibraryState(), tracks)

        assertEquals(FileViewMode.LIST, state.audioPresentation.viewMode)
        assertEquals(
            listOf("New", "Middle", "Old"),
            state.visibleTracks.map { it.displayTitle }
        )
    }

    @Test
    fun `folder size sort uses the combined size of its audio`() {
        val tracks = listOf(
            track("/Music/Small/one.mp3", "One", "Artist", "Album", size = 5L),
            track("/Music/Large/two.mp3", "Two", "Artist", "Album", size = 20L),
            track("/Music/Large/three.mp3", "Three", "Artist", "Album", size = 30L)
        )
        val state = buildAudioLibraryState(
            AudioLibraryState(
                folderPresentation = FileListingPreferences(
                    sortOption = FileSortOption.SIZE_LARGEST
                )
            ),
            tracks
        )

        assertEquals(listOf("Large", "Small"), state.folders.map { it.title })
    }

    @Test
    fun `folder cover always uses the latest modified artwork`() {
        val state = buildAudioLibraryState(
            AudioLibraryState(),
            listOf(
                track("/Music/Folder/alphabetical.mp3", "A", "Artist", "Album", modified = 10L),
                track("/Music/Folder/latest.mp3", "Z", "Artist", "Album", modified = 30L),
                track("/Music/Folder/middle.mp3", "M", "Artist", "Album", modified = 20L)
            )
        )

        assertEquals("latest.mp3", state.folders.single().coverTrack.file.name)
    }

    @Test
    fun `custom folder cover overrides the automatic latest track`() {
        val state = buildAudioLibraryState(
            AudioLibraryState(
                folderCoverPaths = mapOf(
                    "/Music/Folder" to "/Music/Folder/selected.mp3"
                )
            ),
            listOf(
                track("/Music/Folder/selected.mp3", "Selected", "Artist", "Album", modified = 10L),
                track("/Music/Folder/latest.mp3", "Latest", "Artist", "Album", modified = 30L)
            )
        )

        assertEquals("selected.mp3", state.folders.single().coverTrack.file.name)
    }

    @Test
    fun `pinned audio folders stay ahead of the selected sort order`() {
        val state = buildAudioLibraryState(
            AudioLibraryState(
                pinnedFolderPaths = setOf("/Music/Zed"),
                folderPresentation = FileListingPreferences(
                    sortOption = FileSortOption.NAME_ASC
                )
            ),
            listOf(
                track("/Music/Alpha/one.mp3", "One", "Artist", "Album"),
                track("/Music/Zed/two.mp3", "Two", "Artist", "Album")
            )
        )

        assertEquals(listOf("Zed", "Alpha"), state.folders.map(AudioFolder::title))
        assertEquals(true, state.folders.first().isPinned)
    }

    @Test
    fun `favorites folder contains only favorited audio and filters its track page`() {
        val tracks = listOf(
            track("/Music/One/favorite.mp3", "Favorite", "Artist", "Album"),
            track("/Music/Two/other.mp3", "Other", "Artist", "Album")
        )
        val state = buildAudioLibraryState(
            AudioLibraryState(favoritePaths = setOf("/Music/One/favorite.mp3")),
            tracks
        )
        val favorites = state.folders.first()
        val filtered = buildAudioLibraryState(state.copy(folderFilter = favorites), tracks)

        assertEquals(true, favorites.isFavorites)
        assertEquals(listOf("Favorite"), favorites.tracks.map { it.displayTitle })
        assertEquals(listOf("Favorite"), filtered.visibleTracks.map { it.displayTitle })
    }

    @Test
    fun `folder tab selection scope includes every track in visible folders`() {
        val presented = buildAudioLibraryState(
            AudioLibraryState(query = "Scores", tab = CategoryLibraryPage.FOLDERS),
            listOf(
                track("/Music/Scores/one.mp3", "One", "Artist", "Album"),
                track("/Music/Scores/two.mp3", "Two", "Artist", "Album"),
                track("/Music/Other/three.mp3", "Three", "Artist", "Album")
            )
        )

        assertEquals(
            setOf("/Music/Scores/one.mp3", "/Music/Scores/two.mp3"),
            presented.visibleSelectionPaths().toSet()
        )
    }

    @Test
    fun `opening a folder atomically presents only that folders tracks`() {
        val presented = buildAudioLibraryState(
            AudioLibraryState(tab = CategoryLibraryPage.FOLDERS),
            listOf(
                track("/Music/Scores/one.mp3", "One", "Artist", "Album"),
                track("/Music/Scores/two.mp3", "Two", "Artist", "Album"),
                track("/Music/Other/three.mp3", "Three", "Artist", "Album")
            )
        )
        val scoresFolder = presented.folders.first { it.title == "Scores" }
        val folderContents = presented
            .copy(folderFilter = scoresFolder)
            .withPresentedVisibleTracks()

        assertEquals(CategoryLibraryPage.FOLDERS, folderContents.tab)
        assertEquals(
            listOf("One", "Two"),
            folderContents.visibleTracks.map { it.displayTitle }
        )
        assertEquals(
            setOf("/Music/Scores/one.mp3", "/Music/Scores/two.mp3"),
            folderContents.visibleSelectionPaths().toSet()
        )
    }

    @Test
    fun `scrollbar index mapping accounts for grouped section headers`() {
        val tracks = listOf(
            track("/Music/new.mp3", "New", "Artist", "Album", modified = 2_000_000_000L),
            track("/Music/old.mp3", "Old", "Artist", "Album", modified = 1_000_000_000L)
        )
        val groups = groupAudioTracks(tracks, CategoryGrouping.DAY)

        assertEquals("New", audioTrackForLazyIndex(0, tracks, CategoryGrouping.DAY, groups)?.displayTitle)
        assertEquals("New", audioTrackForLazyIndex(1, tracks, CategoryGrouping.DAY, groups)?.displayTitle)
        assertEquals("Old", audioTrackForLazyIndex(2, tracks, CategoryGrouping.DAY, groups)?.displayTitle)
        assertEquals("Old", audioTrackForLazyIndex(3, tracks, CategoryGrouping.DAY, groups)?.displayTitle)
    }

    @Test
    fun `duration formatting supports short and long tracks`() {
        assertEquals("0:00", formatAudioDuration(0L))
        assertEquals("3:05", formatAudioDuration(185_000L))
        assertEquals("1:02:03", formatAudioDuration(3_723_000L))
    }

    @Test
    fun `structured search filters apply to audio and folder results`() {
        val state = buildAudioLibraryState(
            AudioLibraryState(searchFilters = SearchFilters(minSize = 10L)),
            listOf(
                track("/Music/Large/large.mp3", "Large", "Artist", "Album", size = 20L),
                track("/Music/Small/small.mp3", "Small", "Artist", "Album", size = 5L)
            )
        )

        assertEquals(listOf("Large"), state.visibleTracks.map { it.displayTitle })
        assertEquals(listOf("Large"), state.folders.map(AudioFolder::title))
    }

    @Test
    fun `playback progress handles missing duration and clamps stale positions`() {
        assertEquals(0f, audioProgressFraction(5_000f, 0L), 0f)
        assertEquals(0.5f, audioProgressFraction(5_000f, 10_000L), 0f)
        assertEquals(0f, audioProgressFraction(-500f, 10_000L), 0f)
        assertEquals(1f, audioProgressFraction(12_000f, 10_000L), 0f)
    }

    private fun track(
        path: String,
        title: String,
        artist: String,
        album: String,
        modified: Long = 1L,
        size: Long = 1L
    ) = AudioTrack(
        file = FileModel(
            name = path.substringAfterLast('/'),
            absolutePath = path,
            size = size,
            lastModified = modified,
            extension = "mp3",
            mimeType = "audio/mpeg"
        ),
        title = title,
        artist = artist,
        album = album,
        durationMs = 10_000L
    )
}
