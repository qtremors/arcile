package dev.qtremors.arcile.feature.audio

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.muxer.AacMuxer
import androidx.media3.muxer.Muxer
import androidx.media3.muxer.MuxerException
import androidx.media3.muxer.OggMuxer
import androidx.media3.muxer.SeekableMuxerOutput
import androidx.media3.muxer.WavMuxer
import androidx.media3.muxer.WebmMuxer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.InAppMp4Muxer
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.google.common.collect.ImmutableList
import java.io.File
import java.io.FileOutputStream

@OptIn(UnstableApi::class)
internal class AudioEditExporter(
    private val context: Context
) {
    private var transformer: Transformer? = null

    fun start(
        plan: AudioEditPlan,
        onCompleted: (ExportResult) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        check(transformer == null) { "An audio export is already running." }
        val output = File(plan.outputPath)
        output.parentFile?.mkdirs()

        val items = plan.segments.map { segment ->
            val mediaItem = MediaItem.Builder()
                .setUri(Uri.fromFile(File(segment.path)))
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(segment.startMs)
                        .setEndPositionMs(segment.endMs)
                        .build()
                )
                .build()
            EditedMediaItem.Builder(mediaItem)
                .setRemoveVideo(true)
                .build()
        }
        fun startAttempt(transmux: Boolean) {
            val composition = Composition.Builder(
                listOf(EditedMediaItemSequence.withAudioFrom(items))
            ).setTransmuxAudio(transmux).build()
            val activeTransformer = Transformer.Builder(context)
                .setMuxerFactory(AudioEditMuxerFactory(plan.container))
                .addListener(
                    object : Transformer.Listener {
                        override fun onCompleted(
                            composition: Composition,
                            exportResult: ExportResult
                        ) {
                            transformer = null
                            onCompleted(exportResult)
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException
                        ) {
                            transformer = null
                            output.delete()
                            if (transmux) startAttempt(transmux = false)
                            else onError(exportException)
                        }
                    }
                )
                .build()
            transformer = activeTransformer
            runCatching { activeTransformer.start(composition, plan.outputPath) }
                .onFailure { error ->
                    transformer = null
                    output.delete()
                    if (transmux) startAttempt(transmux = false)
                    else onError(error)
                }
        }
        startAttempt(transmux = true)
    }

    fun progressPercent(): Int? {
        val active = transformer ?: return null
        val holder = ProgressHolder()
        return if (active.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
            holder.progress
        } else {
            null
        }
    }

    fun cancel() {
        transformer?.cancel()
        transformer = null
    }
}

@OptIn(UnstableApi::class)
private class AudioEditMuxerFactory(
    private val container: AudioEditContainer
) : Muxer.Factory {
    private val mp4Factory = InAppMp4Muxer.Factory()

    override fun create(path: String): Muxer = try {
        when (container) {
            AudioEditContainer.MP4 -> mp4Factory.create(path)
            AudioEditContainer.AAC -> AacMuxer(FileOutputStream(path))
            AudioEditContainer.WAV -> WavMuxer(SeekableMuxerOutput.of(path))
            AudioEditContainer.OGG -> OggMuxer.Builder(FileOutputStream(path).channel).build()
            AudioEditContainer.WEBM -> WebmMuxer.Builder(SeekableMuxerOutput.of(path)).build()
        }
    } catch (error: Exception) {
        throw MuxerException("Could not create the audio output file.", error)
    }

    override fun getSupportedSampleMimeTypes(trackType: Int): ImmutableList<String> {
        if (trackType != C.TRACK_TYPE_AUDIO) return ImmutableList.of()
        return when (container) {
            AudioEditContainer.MP4 -> mp4Factory.getSupportedSampleMimeTypes(trackType)
            AudioEditContainer.AAC -> ImmutableList.of(MimeTypes.AUDIO_AAC)
            AudioEditContainer.WAV -> ImmutableList.of(MimeTypes.AUDIO_RAW)
            AudioEditContainer.OGG -> ImmutableList.of(MimeTypes.AUDIO_OPUS)
            AudioEditContainer.WEBM -> ImmutableList.of(
                MimeTypes.AUDIO_OPUS,
                MimeTypes.AUDIO_VORBIS
            )
        }
    }

    override fun supportsWritingNegativeTimestampsInEditList(): Boolean =
        container == AudioEditContainer.MP4 &&
            mp4Factory.supportsWritingNegativeTimestampsInEditList()
}
