package dev.qtremors.arcile.feature.audio

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal class AudioWaveform(
    val peaks: FloatArray,
    val durationMs: Long
)

internal class AudioWaveformExtractor(
    private val bucketCount: Int = 16_384
) {
    suspend fun extract(path: String, durationMs: Long): AudioWaveform {
        val peaks = FloatArray(bucketCount)
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        try {
            extractor.setDataSource(path)
            val track = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: return AudioWaveform(peaks, durationMs)
            extractor.selectTrack(track)
            val inputFormat = extractor.getTrackFormat(track)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME)
                ?: return AudioWaveform(peaks, durationMs)
            val durationUs = max(
                1L,
                inputFormat.getLongOrDefault(MediaFormat.KEY_DURATION, durationMs * 1_000L)
            )
            decoder = MediaCodec.createDecoderByType(mime).also {
                it.configure(inputFormat, null, null, 0)
                it.start()
            }

            val bufferInfo = MediaCodec.BufferInfo()
            var inputEnded = false
            var outputEnded = false
            var outputFormat = inputFormat
            while (!outputEnded) {
                currentCoroutineContext().ensureActive()
                if (!inputEnded) {
                    val inputIndex = decoder.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val input = decoder.getInputBuffer(inputIndex)
                        val sampleSize = input?.let { extractor.readSampleData(it, 0) } ?: -1
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputEnded = true
                        } else {
                            decoder.queueInputBuffer(
                                inputIndex,
                                0,
                                sampleSize,
                                extractor.sampleTime.coerceAtLeast(0),
                                0
                            )
                            extractor.advance()
                        }
                    }
                }

                when (val outputIndex = decoder.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> outputFormat = decoder.outputFormat
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    else -> if (outputIndex >= 0) {
                        decoder.getOutputBuffer(outputIndex)?.let { buffer ->
                            collectPeaks(
                                buffer = buffer,
                                info = bufferInfo,
                                format = outputFormat,
                                durationUs = durationUs,
                                peaks = peaks
                            )
                        }
                        decoder.releaseOutputBuffer(outputIndex, false)
                        outputEnded = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    }
                }
            }
        } finally {
            runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            extractor.release()
        }
        normalize(peaks)
        return AudioWaveform(peaks, durationMs)
    }

    private fun collectPeaks(
        buffer: ByteBuffer,
        info: MediaCodec.BufferInfo,
        format: MediaFormat,
        durationUs: Long,
        peaks: FloatArray
    ) {
        val channels = format.getIntegerOrDefault(MediaFormat.KEY_CHANNEL_COUNT, 1).coerceAtLeast(1)
        val sampleRate = format.getIntegerOrDefault(MediaFormat.KEY_SAMPLE_RATE, 44_100)
            .coerceAtLeast(1)
        val encoding = format.getIntegerOrDefault(
            MediaFormat.KEY_PCM_ENCODING,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bytesPerSample = when (encoding) {
            AudioFormat.ENCODING_PCM_8BIT -> 1
            AudioFormat.ENCODING_PCM_FLOAT, AudioFormat.ENCODING_PCM_32BIT -> 4
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
            else -> 2
        }
        val frameSize = bytesPerSample * channels
        if (frameSize <= 0 || info.size < frameSize) return

        val pcm = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN).apply {
            position(info.offset)
            limit(info.offset + info.size)
        }
        val frameCount = info.size / frameSize
        repeat(frameCount) { frame ->
            var amplitude = 0f
            repeat(channels) {
                amplitude = max(amplitude, readAmplitude(pcm, encoding))
            }
            val timeUs = info.presentationTimeUs + frame * 1_000_000L / sampleRate
            val bucket = ((timeUs.coerceIn(0, durationUs - 1) * peaks.size) / durationUs)
                .toInt()
                .coerceIn(peaks.indices)
            peaks[bucket] = max(peaks[bucket], amplitude)
        }
    }

    private fun readAmplitude(buffer: ByteBuffer, encoding: Int): Float = when (encoding) {
        AudioFormat.ENCODING_PCM_8BIT -> abs((buffer.get().toInt() and 0xFF) - 128) / 128f
        AudioFormat.ENCODING_PCM_FLOAT -> abs(buffer.float).coerceIn(0f, 1f)
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> {
            val value = (buffer.get().toInt() and 0xFF) or
                ((buffer.get().toInt() and 0xFF) shl 8) or
                (buffer.get().toInt() shl 16)
            abs(value / 8_388_608f).coerceIn(0f, 1f)
        }
        AudioFormat.ENCODING_PCM_32BIT -> abs(buffer.int / 2_147_483_648f).coerceIn(0f, 1f)
        else -> abs(buffer.short / 32_768f).coerceIn(0f, 1f)
    }

    private fun normalize(peaks: FloatArray) {
        val maximum = peaks.maxOrNull()?.takeIf { it > 0f } ?: return
        peaks.indices.forEach { index ->
            if (peaks[index] > 0f) {
                peaks[index] = (peaks[index] / maximum).coerceIn(MIN_VISIBLE_PEAK, 1f)
            }
        }
    }

    private fun MediaFormat.getIntegerOrDefault(key: String, fallback: Int): Int =
        if (containsKey(key)) getInteger(key) else fallback

    private fun MediaFormat.getLongOrDefault(key: String, fallback: Long): Long =
        if (containsKey(key)) getLong(key) else fallback

    private companion object {
        const val DEQUEUE_TIMEOUT_US = 10_000L
        const val MIN_VISIBLE_PEAK = 0.025f
    }
}
