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
    fun `library groups tracks into collections`() {
        val tracks = listOf(
            track("/Music/One/first.mp3", "First", "Artist", "Album"),
            track("/Music/One/second.mp3", "Second", "Artist", "Album"),
            track("/Music/Two/third.mp3", "Third", "Other", "Other album")
        )

        val state = buildAudioLibraryState(
            AudioLibraryState(collectionKind = AudioCollectionKind.FOLDERS), tracks)

        assertEquals(listOf("One", "Two"), state.collections.map(AudioCollection::title))
        assertEquals(2, state.collections.first().tracks.size)
    }

    @Test
    fun `search matches track artist album and folder metadata`() {
        val tracks = listOf(
            track("/Music/Scores/first.mp3", "Opening", "Composer", "Film"),
            track("/Podcasts/Tech/second.mp3", "Episode", "Host", "Weekly")
        )

        val byArtist = buildAudioLibraryState(
            AudioLibraryState(collectionKind = AudioCollectionKind.FOLDERS, query = "composer"),
            tracks
        )
        val byFolder = buildAudioLibraryState(
            AudioLibraryState(collectionKind = AudioCollectionKind.FOLDERS, query = "podcasts"),
            tracks
        )

        assertEquals(listOf("Opening"), byArtist.visibleTracks.map { it.displayTitle })
        assertEquals(listOf("Episode"), byFolder.visibleTracks.map { it.displayTitle })
        assertEquals(listOf("Tech"), byFolder.collections.map(AudioCollection::title))
    }

    @Test
    fun `folder filter only includes tracks from its exact path`() {
        val tracks = listOf(
            track("/Music/folder/one.mp3", "One", "folder", "Album"),
            track("/Music/Elsewhere/two.mp3", "Two", "Artist", "folder")
        )
        val initial = buildAudioLibraryState(
            AudioLibraryState(collectionKind = AudioCollectionKind.FOLDERS), tracks)

        val folder = initial.collections.first { it.title == "folder" }

        assertEquals(
            listOf("One"),
            buildAudioLibraryState(initial.copy(collectionFilter = folder)).visibleTracks.map { it.displayTitle }
        )
    }

    @Test
    fun `audio defaults to an alphabetical song list`() {
        val tracks = listOf(
            track("/Music/old.mp3", "Old", "Artist", "Album", modified = 10L),
            track("/Music/new.mp3", "New", "Artist", "Album", modified = 30L),
            track("/Music/middle.mp3", "Middle", "Artist", "Album", modified = 20L)
        )

        val state = buildAudioLibraryState(AudioLibraryState(), tracks)

        assertEquals(FileViewMode.LIST, state.audioPresentation.viewMode)
        assertEquals(
            listOf("Middle", "New", "Old"),
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
                collectionKind = AudioCollectionKind.FOLDERS,
                collectionPresentation = FileListingPreferences(
                    sortOption = FileSortOption.SIZE_LARGEST
                )
            ),
            tracks
        )

        assertEquals(listOf("Large", "Small"), state.collections.map { it.title })
    }

    @Test
    fun `folder cover always uses the latest modified artwork`() {
        val state = buildAudioLibraryState(
            AudioLibraryState(collectionKind = AudioCollectionKind.FOLDERS),
            listOf(
                track("/Music/Folder/alphabetical.mp3", "A", "Artist", "Album", modified = 10L),
                track("/Music/Folder/latest.mp3", "Z", "Artist", "Album", modified = 30L),
                track("/Music/Folder/middle.mp3", "M", "Artist", "Album", modified = 20L)
            )
        )

        assertEquals("latest.mp3", state.collections.single().coverTrack?.file?.name)
    }

    @Test
    fun `custom folder cover overrides the automatic latest track`() {
        val state = buildAudioLibraryState(
            AudioLibraryState(
                collectionKind = AudioCollectionKind.FOLDERS,
                folderCoverPaths = mapOf(
                    "/Music/Folder" to "/Music/Folder/selected.mp3"
                )
            ),
            listOf(
                track("/Music/Folder/selected.mp3", "Selected", "Artist", "Album", modified = 10L),
                track("/Music/Folder/latest.mp3", "Latest", "Artist", "Album", modified = 30L)
            )
        )

        assertEquals("selected.mp3", state.collections.single().coverTrack?.file?.name)
    }

    @Test
    fun `pinned audio collections stay ahead of the selected sort order`() {
        val state = buildAudioLibraryState(
            AudioLibraryState(
                collectionKind = AudioCollectionKind.FOLDERS,
                pinnedFolderPaths = setOf("/Music/Zed"),
                collectionPresentation = FileListingPreferences(
                    sortOption = FileSortOption.NAME_ASC
                )
            ),
            listOf(
                track("/Music/Alpha/one.mp3", "One", "Artist", "Album"),
                track("/Music/Zed/two.mp3", "Two", "Artist", "Album")
            )
        )

        assertEquals(listOf("Zed", "Alpha"), state.collections.map(AudioCollection::title))
        assertEquals(true, state.collections.first().isPinned)
    }

    @Test
    fun `favorites folder contains only favorited audio and filters its track page`() {
        val tracks = listOf(
            track("/Music/One/favorite.mp3", "Favorite", "Artist", "Album"),
            track("/Music/Two/other.mp3", "Other", "Artist", "Album")
        )
        val state = buildAudioLibraryState(
            AudioLibraryState(collectionKind = AudioCollectionKind.FOLDERS,
                favoritePaths = setOf("/Music/One/favorite.mp3")),
            tracks
        )
        val favorites = state.collections.first()
        val filtered = buildAudioLibraryState(state.copy(collectionFilter = favorites), tracks)

        assertEquals(true, favorites.isFavorites)
        assertEquals(AudioCollectionType.Favorites, favorites.kind)
        assertEquals("", favorites.title)
        assertEquals("Favoris", favorites.displayTitle("Favoris"))
        assertEquals(listOf("Favorite"), favorites.tracks.map { it.displayTitle })
        assertEquals(listOf("Favorite"), filtered.visibleTracks.map { it.displayTitle })
    }

    @Test
    fun `favorites search follows localized aliases after a locale change`() {
        val tracks = listOf(
            track("/Music/One/song.mp3", "Song", "Artist", "Album")
        )
        val english = buildAudioLibraryState(
            AudioLibraryState(
                collectionKind = AudioCollectionKind.FOLDERS,
                query = "favorites",
                favoritePaths = setOf("/Music/One/song.mp3"),
                favoriteSearchAliases = setOf("Favorites")
            ),
            tracks
        )
        val french = buildAudioLibraryState(
            english.copy(
                query = "favoris",
                favoriteSearchAliases = setOf("Favoris")
            ),
            tracks
        )

        assertEquals(AudioCollectionType.Favorites, english.collections.single().kind)
        assertEquals(AudioCollectionType.Favorites, french.collections.single().kind)
    }

    @Test
    fun `folder tab selection scope includes every track in visible collections`() {
        val presented = buildAudioLibraryState(
            AudioLibraryState(collectionKind = AudioCollectionKind.FOLDERS,
                query = "Scores", tab = CategoryLibraryPage.FOLDERS),
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
    fun `opening a folder atomically presents only that collections tracks`() {
        val presented = buildAudioLibraryState(
            AudioLibraryState(collectionKind = AudioCollectionKind.FOLDERS,
                tab = CategoryLibraryPage.FOLDERS),
            listOf(
                track("/Music/Scores/one.mp3", "One", "Artist", "Album"),
                track("/Music/Scores/two.mp3", "Two", "Artist", "Album"),
                track("/Music/Other/three.mp3", "Three", "Artist", "Album")
            )
        )
        val scoresFolder = presented.collections.first { it.title == "Scores" }
        val folderContents = presented
            .copy(collectionFilter = scoresFolder)
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
            AudioLibraryState(collectionKind = AudioCollectionKind.FOLDERS,
                searchFilters = SearchFilters(minSize = 10L)),
            listOf(
                track("/Music/Large/large.mp3", "Large", "Artist", "Album", size = 20L),
                track("/Music/Small/small.mp3", "Small", "Artist", "Album", size = 5L)
            )
        )

        assertEquals(listOf("Large"), state.visibleTracks.map { it.displayTitle })
        assertEquals(listOf("Large"), state.collections.map(AudioCollection::title))
    }

    @Test
    fun `playback progress handles missing duration and clamps stale positions`() {
        assertEquals(0f, audioProgressFraction(5_000f, 0L), 0f)
        assertEquals(0.5f, audioProgressFraction(5_000f, 10_000L), 0f)
        assertEquals(0f, audioProgressFraction(-500f, 10_000L), 0f)
        assertEquals(1f, audioProgressFraction(12_000f, 10_000L), 0f)
    }

    @Test
    fun `albums are grouped by album artist and ordered by release year`() {
        val tracks = listOf(
            track("/Music/a.mp3", "A", "Singer", "Older", year = 2001),
            track("/Music/b.mp3", "B", "Singer", "Newer", year = 2024),
            track("/Music/c.mp3", "C", "Guest", "Newer", year = 2024,
                albumArtist = "Singer")
        )
        val state = buildAudioLibraryState(AudioLibraryState(
            collectionKind = AudioCollectionKind.ALBUMS,
            sectionPresentations = mapOf(AudioCollectionKind.ALBUMS to FileListingPreferences(
                sortOption = FileSortOption.DATE_NEWEST
            ))
        ), tracks)

        assertEquals(listOf("Newer", "Older"), state.collections.map(AudioCollection::title))
        assertEquals(2, state.collections.first().tracks.size)
    }

    @Test
    fun `album details follow disc and track numbers`() {
        val tracks = listOf(
            track("/Music/three.mp3", "Three", "Artist", "Record")
                .copy(discNumber = 2, trackNumber = 1),
            track("/Music/two.mp3", "Two", "Artist", "Record")
                .copy(discNumber = 1, trackNumber = 2),
            track("/Music/one.mp3", "One", "Artist", "Record")
                .copy(discNumber = 1, trackNumber = 1)
        )
        val state = buildAudioLibraryState(
            AudioLibraryState(collectionKind = AudioCollectionKind.ALBUMS), tracks)
        val details = buildAudioLibraryState(state.copy(collectionFilter = state.collections.single()))

        assertEquals(listOf("One", "Two", "Three"),
            details.visibleTracks.map(AudioTrack::displayTitle))
    }

    @Test
    fun `artists and genres use separate song count sorting`() {
        val tracks = listOf(
            track("/Music/a.mp3", "A", "One", "Album", genre = "Rock"),
            track("/Music/b.mp3", "B", "One", "Album", genre = "Jazz"),
            track("/Music/c.mp3", "C", "Two", "Album", genre = "Rock")
        )
        val settings = FileListingPreferences(sortOption = FileSortOption.FILE_COUNT_HIGHEST)
        val artists = buildAudioLibraryState(AudioLibraryState(
            collectionKind = AudioCollectionKind.ARTISTS,
            sectionPresentations = mapOf(AudioCollectionKind.ARTISTS to settings)
        ), tracks)
        val genres = buildAudioLibraryState(AudioLibraryState(
            collectionKind = AudioCollectionKind.GENRES,
            sectionPresentations = mapOf(AudioCollectionKind.GENRES to settings)
        ), tracks)

        assertEquals("One", artists.collections.first().title)
        assertEquals("Rock", genres.collections.first().title)
    }

    @Test
    fun `playlist keeps user order and stays visible when empty`() {
        val tracks = listOf(
            track("/Music/a.mp3", "A", "Artist", "Album"),
            track("/Music/b.mp3", "B", "Artist", "Album")
        )
        val playlist = AudioPlaylist("p1", "Mix",
            listOf("/Music/b.mp3", "/missing.mp3", "/Music/a.mp3"), 1L)
        val state = buildAudioLibraryState(AudioLibraryState(
            collectionKind = AudioCollectionKind.PLAYLISTS,
            playlists = listOf(playlist, AudioPlaylist("p2", "Empty", emptyList(), 2L))
        ), tracks)
        val mix = state.collections.first { it.key == "p1" }
        val contents = buildAudioLibraryState(state.copy(collectionFilter = mix), tracks)

        assertEquals(listOf("B", "A"), contents.visibleTracks.map(AudioTrack::displayTitle))
        assertEquals(2, state.collections.size)
        assertEquals(null, state.collections.first { it.key == "p2" }.coverTrack)
    }

    @Test
    fun `song shortcuts use Audio favorites and listening records`() {
        val tracks = listOf(
            track("/Music/a.mp3", "A", "Artist", "Album"),
            track("/Music/b.mp3", "B", "Artist", "Album"),
            track("/Music/c.mp3", "C", "Artist", "Album")
        )
        val base = AudioLibraryState(
            favoritePaths = setOf("/Music/b.mp3"),
            playCounts = mapOf("/Music/a.mp3" to 3, "/Music/c.mp3" to 8),
            lastPlayedAt = mapOf("/Music/a.mp3" to 20L, "/Music/c.mp3" to 10L)
        )

        assertEquals(listOf("B"), buildAudioLibraryState(
            base.copy(songFilter = AudioSongFilter.FAVORITES), tracks
        ).visibleTracks.map(AudioTrack::displayTitle))
        assertEquals(listOf("A", "C"), buildAudioLibraryState(
            base.copy(songFilter = AudioSongFilter.RECENTLY_PLAYED), tracks
        ).visibleTracks.map(AudioTrack::displayTitle))
        assertEquals(listOf("C", "A"), buildAudioLibraryState(
            base.copy(songFilter = AudioSongFilter.MOST_PLAYED), tracks
        ).visibleTracks.map(AudioTrack::displayTitle))
    }

    private fun track(
        path: String,
        title: String,
        artist: String,
        album: String,
        modified: Long = 1L,
        size: Long = 1L,
        genre: String? = null,
        year: Int? = null,
        albumArtist: String? = null
    ) = AudioTrack(
        file = FileModel(
            name = path.substringAfterLast('/'),
            reference = path,
            size = size,
            lastModified = modified,
            extension = "mp3",
            mimeType = "audio/mpeg"
        ),
        title = title,
        artist = artist,
        album = album,
        durationMs = 10_000L,
        genre = genre,
        year = year,
        albumArtist = albumArtist
    )
}
