package dev.qtremors.arcile.feature.audio

import java.io.File

internal enum class AudioEditMode {
    EXTRACT,
    REMOVE,
    COMBINE
}

internal enum class AudioEditContainer(val extension: String) {
    MP4("m4a"),
    AAC("aac"),
    WAV("wav"),
    OGG("opus"),
    WEBM("webm");

    companion object {
        fun fromPath(path: String): AudioEditContainer? = when (
            path.substringAfterLast('.', "").lowercase()
        ) {
            "m4a", "mp4", "3gp", "3gpp" -> MP4
            "aac" -> AAC
            "wav", "wave" -> WAV
            "ogg", "oga", "opus" -> OGG
            "webm", "weba" -> WEBM
            else -> null
        }
    }
}

internal data class AudioEditSource(
    val path: String,
    val durationMs: Long
)

internal data class AudioEditSegment(
    val path: String,
    val startMs: Long,
    val endMs: Long
)

internal data class AudioEditRange(
    val startMs: Long,
    val endMs: Long
)

internal data class AudioEditPlan(
    val mode: AudioEditMode,
    val container: AudioEditContainer,
    val segments: List<AudioEditSegment>,
    val outputPath: String
)

internal sealed interface AudioEditPlanResult {
    data class Ready(val plan: AudioEditPlan) : AudioEditPlanResult
    data class UnsupportedFormat(val extension: String) : AudioEditPlanResult
    data object MixedFormats : AudioEditPlanResult
    data object InvalidSelection : AudioEditPlanResult
}

internal object AudioEditPlanner {
    fun create(
        sources: List<AudioEditSource>,
        mode: AudioEditMode,
        selectionStartMs: Long = 0,
        selectionEndMs: Long = 0,
        selectionRanges: List<AudioEditRange> = emptyList(),
        outputDirectoryPath: String? = null,
        outputPath: String? = null
    ): AudioEditPlanResult {
        if (sources.isEmpty() || sources.any { it.path.isBlank() || it.durationMs <= 0 }) {
            return AudioEditPlanResult.InvalidSelection
        }
        if (mode != AudioEditMode.COMBINE && sources.size != 1) {
            return AudioEditPlanResult.InvalidSelection
        }
        if (mode == AudioEditMode.COMBINE && sources.size < 2) {
            return AudioEditPlanResult.InvalidSelection
        }

        val containers = sources.map { source ->
            AudioEditContainer.fromPath(source.path)
                ?: return AudioEditPlanResult.UnsupportedFormat(
                    source.path.substringAfterLast('.', "").lowercase()
                )
        }
        val container = containers.first()
        if (containers.any { it != container }) return AudioEditPlanResult.MixedFormats

        val ranges = if (mode == AudioEditMode.COMBINE) {
            emptyList()
        } else {
            normalizeRanges(
                ranges = selectionRanges.ifEmpty {
                    listOf(AudioEditRange(selectionStartMs, selectionEndMs))
                },
                durationMs = sources.single().durationMs
            ) ?: return AudioEditPlanResult.InvalidSelection
        }

        val segments = when (mode) {
            AudioEditMode.EXTRACT -> {
                val source = sources.single()
                ranges.map { range ->
                    AudioEditSegment(source.path, range.startMs, range.endMs)
                }
            }
            AudioEditMode.REMOVE -> {
                val source = sources.single()
                buildList {
                    var cursorMs = 0L
                    ranges.forEach { range ->
                        if (range.startMs > cursorMs) {
                            add(AudioEditSegment(source.path, cursorMs, range.startMs))
                        }
                        cursorMs = range.endMs
                    }
                    if (cursorMs < source.durationMs) {
                        add(AudioEditSegment(source.path, cursorMs, source.durationMs))
                    }
                }.takeIf(List<AudioEditSegment>::isNotEmpty)
                    ?: return AudioEditPlanResult.InvalidSelection
            }
            AudioEditMode.COMBINE -> sources.map {
                AudioEditSegment(it.path, 0, it.durationMs)
            }
        }

        return AudioEditPlanResult.Ready(
            AudioEditPlan(
                mode = mode,
                container = container,
                segments = segments,
                outputPath = outputPath ?: nextAvailableOutputPath(
                    sources = sources,
                    mode = mode,
                    container = container,
                    outputDirectoryPath = outputDirectoryPath
                )
            )
        )
    }

    private fun normalizeRanges(
        ranges: List<AudioEditRange>,
        durationMs: Long
    ): List<AudioEditRange>? {
        if (
            ranges.isEmpty() ||
            ranges.any { it.startMs < 0 || it.endMs > durationMs || it.endMs <= it.startMs }
        ) {
            return null
        }
        return ranges.sortedBy(AudioEditRange::startMs).fold(mutableListOf()) { merged, range ->
            val previous = merged.lastOrNull()
            if (previous != null && range.startMs <= previous.endMs) {
                merged[merged.lastIndex] = previous.copy(endMs = maxOf(previous.endMs, range.endMs))
            } else {
                merged += range
            }
            merged
        }
    }

    private fun nextAvailableOutputPath(
        sources: List<AudioEditSource>,
        mode: AudioEditMode,
        container: AudioEditContainer,
        outputDirectoryPath: String?
    ): String {
        val first = File(sources.first().path)
        val parent = outputDirectoryPath?.let(::File) ?: first.parentFile ?: File(".")
        val base = when (mode) {
            AudioEditMode.EXTRACT -> "${first.nameWithoutExtension}_extract"
            AudioEditMode.REMOVE -> "${first.nameWithoutExtension}_cut"
            AudioEditMode.COMBINE -> "${first.nameWithoutExtension}_combined"
        }
        var candidate = File(parent, "$base.${container.extension}")
        var suffix = 2
        while (candidate.exists()) {
            candidate = File(parent, "${base}_$suffix.${container.extension}")
            suffix += 1
        }
        return candidate.absolutePath
    }
}
