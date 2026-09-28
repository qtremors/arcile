package dev.qtremors.arcile.feature.audio

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** Live frequency bands from the PCM being sent to the audio output. */
@androidx.annotation.OptIn(UnstableApi::class)
internal object AudioPlaybackSpectrum : TeeAudioProcessor.AudioBufferSink {
    private const val WINDOW_SIZE = 1024
    private val bandEdgesHz = intArrayOf(40, 110, 220, 420, 800, 1_500, 2_800, 5_000, 8_000, 16_000)
    private val window = DoubleArray(WINDOW_SIZE) { index ->
        0.5 - 0.5 * cos(2.0 * PI * index / (WINDOW_SIZE - 1))
    }
    private val samples = DoubleArray(WINDOW_SIZE)
    private val real = DoubleArray(WINDOW_SIZE)
    private val imaginary = DoubleArray(WINDOW_SIZE)
    private val smoothed = FloatArray(bandEdgesHz.size - 1)
    private val silentLevels = List(bandEdgesHz.size - 1) { 0f }
    private val _levels = MutableStateFlow(silentLevels)
    val levels = _levels.asStateFlow()

    private var sampleRateHz = 0
    private var channelCount = 0
    private var encoding = C.ENCODING_INVALID
    private var sampleIndex = 0

    @Volatile
    private var isPlaying = false

    @Volatile
    private var spectrumVisible = false

    @Synchronized
    fun setSpectrumVisible(visible: Boolean) {
        if (spectrumVisible == visible) return
        spectrumVisible = visible
        clearSpectrum()
    }

    fun setPlaying(playing: Boolean) {
        isPlaying = playing
        if (!playing) clearSpectrum()
    }

    @Synchronized
    fun clear() {
        clearSpectrum()
    }

    @Synchronized
    private fun clearSpectrum() {
        sampleIndex = 0
        smoothed.fill(0f)
        _levels.value = silentLevels
    }

    @Synchronized
    override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
        this.sampleRateHz = sampleRateHz
        this.channelCount = channelCount
        this.encoding = encoding
        clear()
    }

    @Synchronized
    override fun handleBuffer(buffer: ByteBuffer) {
        if (!isPlaying || !spectrumVisible || sampleRateHz <= 0 || channelCount <= 0) return
        val bytesPerSample = when (encoding) {
            C.ENCODING_PCM_16BIT -> 2
            C.ENCODING_PCM_FLOAT -> 4
            else -> return
        }
        val input = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        val frameBytes = bytesPerSample * channelCount
        while (input.remaining() >= frameBytes && isPlaying && spectrumVisible) {
            var mono = 0.0
            repeat(channelCount) {
                mono += when (encoding) {
                    C.ENCODING_PCM_16BIT -> input.getShort() / 32768.0
                    else -> input.getFloat().toDouble()
                }
            }
            val sample = (mono / channelCount).coerceIn(-1.0, 1.0)
            samples[sampleIndex++] = sample
            if (sampleIndex == WINDOW_SIZE) {
                sampleIndex = 0
                analyzeWindow()
            }
        }
    }

    private fun analyzeWindow() {
        for (index in 0 until WINDOW_SIZE) {
            real[index] = samples[index] * window[index]
            imaginary[index] = 0.0
        }
        fft()

        val levels = FloatArray(smoothed.size)
        for (band in levels.indices) {
            val firstBin = (bandEdgesHz[band] * WINDOW_SIZE / sampleRateHz)
                .coerceIn(1, WINDOW_SIZE / 2 - 1)
            val lastBin = ceil(bandEdgesHz[band + 1] * WINDOW_SIZE.toDouble() / sampleRateHz)
                .toInt().coerceIn(firstBin, WINDOW_SIZE / 2 - 1)
            var magnitude = 0.0
            for (bin in firstBin..lastBin) {
                magnitude = max(magnitude, sqrt(real[bin] * real[bin] + imaginary[bin] * imaginary[bin]))
            }
            val target = ((magnitude * 4.0 / WINDOW_SIZE - 0.015) * 3.0).coerceIn(0.0, 1.0).toFloat()
            val response = if (target > smoothed[band]) 0.82f else 0.38f
            smoothed[band] += (target - smoothed[band]) * response
            levels[band] = smoothed[band]
        }
        _levels.value = levels.toList()
    }

    private fun fft() {
        var reversed = 0
        for (index in 1 until WINDOW_SIZE) {
            var bit = WINDOW_SIZE shr 1
            while (reversed and bit != 0) {
                reversed = reversed xor bit
                bit = bit shr 1
            }
            reversed = reversed xor bit
            if (index < reversed) {
                val realValue = real[index]
                real[index] = real[reversed]
                real[reversed] = realValue
                val imaginaryValue = imaginary[index]
                imaginary[index] = imaginary[reversed]
                imaginary[reversed] = imaginaryValue
            }
        }

        var length = 2
        while (length <= WINDOW_SIZE) {
            val angle = -2.0 * PI / length
            val rotationReal = cos(angle)
            val rotationImaginary = sin(angle)
            for (start in 0 until WINDOW_SIZE step length) {
                var phaseReal = 1.0
                var phaseImaginary = 0.0
                for (offset in 0 until length / 2) {
                    val lower = start + offset
                    val upper = lower + length / 2
                    val upperReal = real[upper] * phaseReal - imaginary[upper] * phaseImaginary
                    val upperImaginary = real[upper] * phaseImaginary + imaginary[upper] * phaseReal
                    real[upper] = real[lower] - upperReal
                    imaginary[upper] = imaginary[lower] - upperImaginary
                    real[lower] += upperReal
                    imaginary[lower] += upperImaginary
                    val nextReal = phaseReal * rotationReal - phaseImaginary * rotationImaginary
                    phaseImaginary = phaseReal * rotationImaginary + phaseImaginary * rotationReal
                    phaseReal = nextReal
                }
            }
            length = length shl 1
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
internal class AudioVisualizerRenderersFactory(context: Context) : DefaultRenderersFactory(context) {
    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioOutputPlaybackParams: Boolean
    ): AudioSink = DefaultAudioSink.Builder(context)
        .setAudioProcessors(arrayOf(TeeAudioProcessor(AudioPlaybackSpectrum)))
        .setEnableFloatOutput(false)
        .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
        .build()
}
