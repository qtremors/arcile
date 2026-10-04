package dev.qtremors.arcile.feature.audio

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.qtremors.arcile.core.storage.domain.AudioLibraryPreferencesStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Entity(tableName = "audio_tracks")
internal data class AudioTrackRecord(
    @PrimaryKey val path: String,
    val favorite: Boolean = false,
    val playCount: Int = 0,
    val lastPlayedAt: Long = 0L
)

@Entity(tableName = "audio_play_history")
internal data class AudioPlayRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val path: String,
    val playedAt: Long
)

@Entity(tableName = "audio_metadata")
internal data class AudioDatabaseMetadata(
    @PrimaryKey val key: String,
    val value: String
)

@Dao
internal interface AudioListeningDao {
    @Query("SELECT path FROM audio_tracks WHERE favorite = 1")
    fun observeFavoritePaths(): Flow<List<String>>

    @Query("SELECT * FROM audio_tracks")
    fun observeTrackRecords(): Flow<List<AudioTrackRecord>>

    @Query("SELECT path FROM audio_play_history ORDER BY playedAt DESC, id DESC LIMIT 100")
    fun observeHistoryPaths(): Flow<List<String>>

    @Query("SELECT * FROM audio_tracks WHERE path = :path LIMIT 1")
    suspend fun track(path: String): AudioTrackRecord?

    @Query("SELECT * FROM audio_tracks WHERE path = :path LIMIT 1")
    fun observeTrack(path: String): Flow<AudioTrackRecord?>

    @Query("SELECT value FROM audio_metadata WHERE `key` = :key LIMIT 1")
    suspend fun metadata(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putTrack(record: AudioTrackRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putMetadata(value: AudioDatabaseMetadata)

    @Insert
    suspend fun putPlay(record: AudioPlayRecord)

    @Query("DELETE FROM audio_tracks WHERE path = :path")
    suspend fun deleteTrack(path: String)

    @Query("UPDATE audio_play_history SET path = :newPath WHERE path = :oldPath")
    suspend fun moveHistory(oldPath: String, newPath: String)

    @Query("DELETE FROM audio_play_history WHERE id NOT IN (SELECT id FROM audio_play_history ORDER BY playedAt DESC, id DESC LIMIT 500)")
    suspend fun trimHistory()

    @Query("DELETE FROM audio_play_history")
    suspend fun clearHistory()

    @Query("UPDATE audio_tracks SET playCount = 0, lastPlayedAt = 0")
    suspend fun clearPlayCounts()
}

@Database(
    entities = [AudioTrackRecord::class, AudioPlayRecord::class, AudioDatabaseMetadata::class],
    version = 1,
    exportSchema = false
)
internal abstract class AudioListeningDatabase : RoomDatabase() {
    abstract fun listeningDao(): AudioListeningDao
}

@Module
@InstallIn(SingletonComponent::class)
internal object AudioListeningModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AudioListeningDatabase =
        Room.databaseBuilder(context, AudioListeningDatabase::class.java, "arcile_audio.db")
            .build()
}

/** Durable, Audio-owned listening state. Arcile's file cache does not store these records. */
@Singleton
internal class AudioListeningStore @Inject constructor(
    private val legacyPreferences: AudioLibraryPreferencesStore,
    private val database: AudioListeningDatabase
) {
    private val dao = database.listeningDao()
    private val migrationMutex = Mutex()
    private var migrated = false

    val favoritePaths: Flow<Set<String>> = flow {
        importLegacyFavorites()
        emitAll(dao.observeFavoritePaths().map { it.toSet() })
    }
    val trackRecords: Flow<List<AudioTrackRecord>> = dao.observeTrackRecords()
    val historyPaths: Flow<List<String>> = dao.observeHistoryPaths()

    fun trackRecord(path: String): Flow<AudioTrackRecord?> = dao.observeTrack(path)

    suspend fun setFavorite(path: String, favorite: Boolean) {
        if (path.isBlank()) return
        importLegacyFavorites()
        database.withTransaction {
            val old = dao.track(path) ?: AudioTrackRecord(path)
            dao.putTrack(old.copy(favorite = favorite))
        }
    }

    suspend fun setFavorites(paths: Collection<String>, favorite: Boolean) {
        importLegacyFavorites()
        database.withTransaction {
            paths.filter(String::isNotBlank).distinct().forEach { path ->
                val old = dao.track(path) ?: AudioTrackRecord(path)
                dao.putTrack(old.copy(favorite = favorite))
            }
        }
    }

    suspend fun recordQualifiedPlay(path: String, playedAt: Long = System.currentTimeMillis()) {
        if (path.isBlank()) return
        database.withTransaction {
            val old = dao.track(path) ?: AudioTrackRecord(path)
            dao.putTrack(old.copy(
                playCount = if (old.playCount == Int.MAX_VALUE) Int.MAX_VALUE
                    else old.playCount + 1,
                lastPlayedAt = playedAt
            ))
            dao.putPlay(AudioPlayRecord(path = path, playedAt = playedAt))
            dao.trimHistory()
        }
    }

    suspend fun replacePath(oldPath: String, newPath: String) {
        if (oldPath == newPath || oldPath.isBlank() || newPath.isBlank()) return
        database.withTransaction {
            val old = dao.track(oldPath)
            val destination = dao.track(newPath)
            if (old != null) {
                dao.putTrack(AudioTrackRecord(
                    path = newPath,
                    favorite = old.favorite || destination?.favorite == true,
                    playCount = (old.playCount.toLong() + (destination?.playCount ?: 0))
                        .coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    lastPlayedAt = maxOf(old.lastPlayedAt, destination?.lastPlayedAt ?: 0L)
                ))
            }
            dao.moveHistory(oldPath, newPath)
            dao.deleteTrack(oldPath)
        }
    }

    suspend fun clearHistory() = database.withTransaction {
        dao.clearHistory()
        dao.clearPlayCounts()
    }

    private suspend fun importLegacyFavorites() {
        if (migrated) return
        migrationMutex.withLock {
            if (migrated) return@withLock
            if (dao.metadata("legacy_favorites_imported") != "true") {
                val favorites = legacyPreferences.audioLibraryPreferencesFlow.first().favoriteFiles
                database.withTransaction {
                    favorites.filter(String::isNotBlank).forEach { path ->
                        val old = dao.track(path) ?: AudioTrackRecord(path)
                        dao.putTrack(old.copy(favorite = true))
                    }
                    dao.putMetadata(AudioDatabaseMetadata("legacy_favorites_imported", "true"))
                }
            }
            legacyPreferences.clearMigratedFavorites()
            migrated = true
        }
    }
}
