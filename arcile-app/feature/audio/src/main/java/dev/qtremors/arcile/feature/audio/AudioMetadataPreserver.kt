package dev.qtremors.arcile.feature.audio

import java.io.File
import java.security.MessageDigest
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.TagOptionSingleton

internal data class MetadataCopyResult(
    val copied: Boolean,
    val warning: String? = null
)

internal object AudioMetadataPreserver {
    fun copy(sourcePath: String, outputPath: String): MetadataCopyResult = runCatching {
        TagOptionSingleton.getInstance().setAndroid(true)
        val sourceAudio = AudioFileIO.read(File(sourcePath))
        val sourceTag = sourceAudio.tag
            ?: return MetadataCopyResult(copied = true)
        if (sourceTag.isEmpty && sourceTag.artworkList.isEmpty()) {
            return MetadataCopyResult(copied = true)
        }

        val sourceSignature = sourceTag.signature()
        val outputAudio = AudioFileIO.read(File(outputPath))
        outputAudio.tag = sourceTag
        outputAudio.commit()
        val writtenTag = AudioFileIO.read(File(outputPath)).tag
        if (writtenTag != null && writtenTag.signature() == sourceSignature) {
            MetadataCopyResult(copied = true)
        } else {
            MetadataCopyResult(
                copied = false,
                warning = "The audio was saved, but its metadata could not be fully verified."
            )
        }
    }.getOrElse {
        MetadataCopyResult(
            copied = false,
            warning = "The audio was saved, but this format did not allow all metadata to be copied."
        )
    }

    private fun Tag.signature(): List<String> = buildList {
        val fields = fields
        while (fields.hasNext()) {
            val field = fields.next()
            val digest = runCatching { field.rawContent.sha256() }
                .getOrElse { field.toString().encodeToByteArray().sha256() }
            add("${field.id}:$digest")
        }
        artworkList.forEach { artwork ->
            add(
                "art:${artwork.pictureType}:${artwork.mimeType}:${artwork.description}:" +
                    artwork.binaryData.sha256()
            )
        }
    }.sorted()

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
}
