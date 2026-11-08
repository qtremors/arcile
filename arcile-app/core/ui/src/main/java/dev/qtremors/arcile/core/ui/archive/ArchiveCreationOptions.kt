package dev.qtremors.arcile.core.ui.archive

import dev.qtremors.arcile.core.storage.domain.ArchiveCompressionLevel
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat

internal fun archiveCompressionLevels(format: ArchiveFormat): List<ArchiveCompressionLevel> = when (format) {
    ArchiveFormat.TAR -> listOf(ArchiveCompressionLevel.STORE)
    ArchiveFormat.TAR_BZIP2, ArchiveFormat.TBZ2, ArchiveFormat.TAR_XZ, ArchiveFormat.TXZ ->
        ArchiveCompressionLevel.entries.filterNot { it == ArchiveCompressionLevel.STORE }
    else -> ArchiveCompressionLevel.entries
}

internal fun resolveArchiveCompressionLevel(format: ArchiveFormat, requested: ArchiveCompressionLevel): ArchiveCompressionLevel {
    val available = archiveCompressionLevels(format)
    return requested.takeIf { it in available } ?: ArchiveCompressionLevel.DEFAULT.takeIf { it in available } ?: available.first()
}
