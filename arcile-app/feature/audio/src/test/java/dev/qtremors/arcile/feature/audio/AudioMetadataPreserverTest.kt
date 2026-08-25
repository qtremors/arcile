package dev.qtremors.arcile.feature.audio

import java.io.File
import java.io.FileOutputStream
import java.util.Base64
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.AndroidArtwork
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AudioMetadataPreserverTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun copiesReadableTagsWithoutChangingTheAudioPayload() {
        val source = temporaryFolder.newFile("source.wav")
        val output = temporaryFolder.newFile("output.wav")
        writeSilentWav(source)
        writeSilentWav(output)

        AudioFileIO.read(source).apply {
            tagOrCreateAndSetDefault.apply {
                setField(FieldKey.TITLE, "Precision test")
                setField(FieldKey.ARTIST, "Arcile")
                setField(
                    AndroidArtwork().apply {
                        binaryData = TEST_ARTWORK
                        mimeType = "image/png"
                        description = "Cover"
                        pictureType = 3
                        width = 1
                        height = 1
                    }
                )
            }
            commit()
        }

        val result = AudioMetadataPreserver.copy(source.absolutePath, output.absolutePath)

        assertTrue(result.warning.orEmpty(), result.copied)
        val outputTag = AudioFileIO.read(output).tag
        assertEquals("Precision test", outputTag.getFirst(FieldKey.TITLE))
        assertEquals("Arcile", outputTag.getFirst(FieldKey.ARTIST))
        assertArrayEquals(TEST_ARTWORK, outputTag.firstArtwork.binaryData)
    }

    private fun writeSilentWav(file: File) {
        val sampleRate = 8_000
        val sampleCount = 800
        val dataSize = sampleCount * 2
        FileOutputStream(file).use { output ->
            output.write("RIFF".encodeToByteArray())
            output.writeLittleEndianInt(36 + dataSize)
            output.write("WAVEfmt ".encodeToByteArray())
            output.writeLittleEndianInt(16)
            output.writeLittleEndianShort(1)
            output.writeLittleEndianShort(1)
            output.writeLittleEndianInt(sampleRate)
            output.writeLittleEndianInt(sampleRate * 2)
            output.writeLittleEndianShort(2)
            output.writeLittleEndianShort(16)
            output.write("data".encodeToByteArray())
            output.writeLittleEndianInt(dataSize)
            output.write(ByteArray(dataSize))
        }
    }

    private fun FileOutputStream.writeLittleEndianInt(value: Int) {
        write(value and 0xFF)
        write(value ushr 8 and 0xFF)
        write(value ushr 16 and 0xFF)
        write(value ushr 24 and 0xFF)
    }

    private fun FileOutputStream.writeLittleEndianShort(value: Int) {
        write(value and 0xFF)
        write(value ushr 8 and 0xFF)
    }

    private companion object {
        val TEST_ARTWORK: ByteArray = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUB" +
                "AScY42YAAAAASUVORK5CYII="
        )
    }
}
