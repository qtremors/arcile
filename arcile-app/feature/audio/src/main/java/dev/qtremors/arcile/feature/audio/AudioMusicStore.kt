package dev.qtremors.arcile.feature.audio

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.qtremors.arcile.core.storage.domain.FileListingPreferences
import dev.qtremors.arcile.core.storage.domain.FileSortOption
import dev.qtremors.arcile.core.storage.domain.FileViewMode
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal data class AudioPlaylist(
    val id: String,
    val name: String,
    val trackPaths: List<String>,
    val updatedAt: Long
)

/** Small user-managed state. Audio files continue to come directly from MediaStore. */
@Singleton
internal class AudioMusicStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val preferences = context.getSharedPreferences("audio_music", Context.MODE_PRIVATE)
    private val lock = Any()
    private val mutablePlaylists = MutableStateFlow(readPlaylists())
    val playlists: StateFlow<List<AudioPlaylist>> = mutablePlaylists
    private val mutableMediaChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val mediaChanges = mutableMediaChanges.asSharedFlow()

    fun notifyMediaChanged() { mutableMediaChanges.tryEmit(Unit) }

    fun defaultSection(fallback: AudioCollectionKind): AudioCollectionKind =
        preferences.getString(DEFAULT_SECTION_KEY, null)?.let { saved ->
            AudioCollectionKind.entries.firstOrNull { it.name == saved }
        } ?: fallback

    suspend fun saveDefaultSection(section: AudioCollectionKind) = withContext(Dispatchers.IO) {
        if (!preferences.edit().putString(DEFAULT_SECTION_KEY, section.name).commit()) {
            throw IllegalStateException("Could not save opening page")
        }
    }

    suspend fun createPlaylist(name: String, paths: List<String> = emptyList()): AudioPlaylist =
        withContext(Dispatchers.IO) {
        synchronized(lock) {
            val title = name.trim().takeIf(String::isNotEmpty)
                ?: throw IllegalArgumentException("Enter a playlist name")
            val playlist = AudioPlaylist(UUID.randomUUID().toString(), title,
                paths.filter(String::isNotBlank).distinct(), now())
            writePlaylists(mutablePlaylists.value + playlist)
            playlist
        }
    }

    suspend fun renamePlaylist(id: String, name: String) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val title = name.trim().takeIf(String::isNotEmpty)
                ?: throw IllegalArgumentException("Enter a playlist name")
            updatePlaylist(id) { it.copy(name = title, updatedAt = now()) }
        }
    }

    suspend fun deletePlaylist(id: String) = withContext(Dispatchers.IO) {
        synchronized(lock) { writePlaylists(mutablePlaylists.value.filterNot { it.id == id }) }
    }

    suspend fun setPlaylistTracks(id: String, paths: List<String>) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            updatePlaylist(id) {
                it.copy(trackPaths = paths.filter(String::isNotBlank).distinct(), updatedAt = now())
            }
        }
    }

    suspend fun appendPlaylistTracks(id: String, paths: List<String>) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            updatePlaylist(id) {
                it.copy(trackPaths = (it.trackPaths + paths).filter(String::isNotBlank).distinct(),
                    updatedAt = now())
            }
        }
    }

    suspend fun replaceTrackPath(oldPath: String, newPath: String) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val updated = mutablePlaylists.value.map { playlist ->
                if (oldPath !in playlist.trackPaths) playlist else playlist.copy(
                    trackPaths = playlist.trackPaths.map { if (it == oldPath) newPath else it }
                        .distinct(),
                    updatedAt = now()
                )
            }
            if (updated != mutablePlaylists.value) writePlaylists(updated)
        }
    }

    fun presentation(section: AudioCollectionKind): FileListingPreferences {
        val key = "page_${section.name.lowercase()}"
        val fallback = FileListingPreferences(
            sortOption = FileSortOption.NAME_ASC,
            viewMode = FileViewMode.GRID,
            gridMinCellSize = 160f,
            showThumbnails = true
        )
        return runCatching {
            val raw = preferences.getString(key, null) ?: return fallback
            val json = JSONObject(raw)
            fallback.copy(
                sortOption = FileSortOption.valueOf(json.getString("sort")),
                viewMode = FileViewMode.valueOf(json.getString("view")),
                listZoom = json.optDouble("listZoom", fallback.listZoom.toDouble()).toFloat(),
                gridMinCellSize = json.optDouble("size", 160.0).toFloat()
            ).normalized()
        }.getOrDefault(fallback)
    }

    suspend fun savePresentation(section: AudioCollectionKind, value: FileListingPreferences) =
        withContext(Dispatchers.IO) {
            val normalized = value.normalized()
            val json = JSONObject()
                .put("sort", normalized.sortOption.name)
                .put("view", normalized.viewMode.name)
                .put("listZoom", normalized.listZoom.toDouble())
                .put("size", normalized.gridMinCellSize.toDouble())
            if (!preferences.edit().putString("page_${section.name.lowercase()}", json.toString())
                    .commit()) throw IllegalStateException("Could not save page view")
        }

    private fun updatePlaylist(id: String, transform: (AudioPlaylist) -> AudioPlaylist) {
        val old = mutablePlaylists.value
        if (old.none { it.id == id }) throw IllegalArgumentException("Playlist was removed")
        writePlaylists(old.map { if (it.id == id) transform(it) else it })
    }

    private fun writePlaylists(value: List<AudioPlaylist>) {
        val json = JSONArray()
        value.forEach { item ->
            json.put(JSONObject()
                .put("id", item.id)
                .put("name", item.name)
                .put("updatedAt", item.updatedAt)
                .put("paths", JSONArray(item.trackPaths)))
        }
        if (!preferences.edit().putString(PLAYLIST_KEY, json.toString()).commit()) {
            throw IllegalStateException("Could not save playlists")
        }
        mutablePlaylists.value = value
    }

    private fun readPlaylists(): List<AudioPlaylist> = runCatching {
        val array = JSONArray(preferences.getString(PLAYLIST_KEY, "[]"))
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val paths = item.optJSONArray("paths") ?: JSONArray()
                add(AudioPlaylist(
                    id = item.getString("id"),
                    name = item.getString("name"),
                    trackPaths = buildList {
                        for (pathIndex in 0 until paths.length()) {
                            paths.optString(pathIndex).takeIf(String::isNotBlank)?.let(::add)
                        }
                    }.distinct(),
                    updatedAt = item.optLong("updatedAt")
                ))
            }
        }
    }.getOrDefault(emptyList())

    private fun now(): Long = System.currentTimeMillis()

    private companion object {
        const val PLAYLIST_KEY = "playlists_v1"
        const val DEFAULT_SECTION_KEY = "default_section_v1"
    }
}
