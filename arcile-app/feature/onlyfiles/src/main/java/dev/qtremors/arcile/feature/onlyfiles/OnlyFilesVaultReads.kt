package dev.qtremors.arcile.feature.onlyfiles

import android.media.MediaDataSource
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.ui.metadata.readVideoVisualMetadata
import dev.qtremors.arcile.core.vault.domain.VaultFailure
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import dev.qtremors.arcile.core.vault.domain.VaultNodeRef
import dev.qtremors.arcile.core.vault.domain.VaultSeekableReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Blocking vault reads used by viewers. Composables call these; they never do the IO themselves. */

internal const val MAX_VAULT_EDITOR_BYTES = 4 * 1024 * 1024

internal suspend fun readVaultText(
    ref: VaultNodeRef,
    openReader: (VaultNodeRef) -> Result<VaultSeekableReader>
): String = withContext(Dispatchers.IO) {
    openReader(ref).getOrThrow().use { reader ->
        if (reader.sizeBytes !in 0..MAX_VAULT_EDITOR_BYTES.toLong()) {
            throw VaultFailure.FileTooLarge(reader.sizeBytes, MAX_VAULT_EDITOR_BYTES.toLong())
        }
        val bytes = ByteArray(reader.sizeBytes.toInt())
        try {
            var offset = 0
            while (offset < bytes.size) {
                ensureActive()
                val read = reader.readAt(offset.toLong(), bytes, offset, bytes.size - offset)
                if (read <= 0) throw VaultFailure.SourceChanged()
                offset += read
            }
            bytes.decodeToString(throwOnInvalidSequence = true).removePrefix("﻿")
        } finally {
            bytes.fill(0)
        }
    }
}

internal suspend fun readVaultVideoMetadata(
    node: VaultNodeMetadata,
    file: FileModel,
    openReader: (VaultNodeRef) -> Result<VaultSeekableReader>
) = withContext(Dispatchers.IO) {
    val reader = openReader(node.ref).getOrNull() ?: return@withContext null
    reader.use {
        val source = object : MediaDataSource() {
            override fun getSize(): Long = reader.sizeBytes
            override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int =
                reader.readAt(position, buffer, offset, size)
            override fun close() = Unit
        }
        readVideoVisualMetadata(file.reference, reader.sizeBytes, file.mimeType) { it.setDataSource(source) }
    }
}
