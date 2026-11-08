package dev.qtremors.arcile.core.ui.audio

data class AudioPlaybackState(
    val isConnected: Boolean = false,
    val currentMediaId: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
    val queueMediaIds: List<String> = emptyList(),
    val queueEntries: List<AudioQueueEntry> = emptyList(),
    val currentMediaIndex: Int = 0,
    val shuffleMediaIds: List<String> = emptyList(),
    val repeatMode: AudioRepeatMode = AudioRepeatMode.OFF,
    val shuffleEnabled: Boolean = false,
    val playbackSpeed: Float = 1f,
    val playbackPitch: Float = 1f,
    val sleepTimerEndElapsedMs: Long? = null,
    val error: Boolean = false
)

data class AudioQueueEntry(
    val id: String,
    val uri: String,
    val title: String?,
    val artist: String?,
    val album: String?
)

enum class AudioRepeatMode {
    OFF,
    ALL,
    ONE
}
