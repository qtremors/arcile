package dev.qtremors.arcile.core.ui.archive

import dev.qtremors.arcile.core.storage.domain.ArchiveCompressionLevel
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import org.junit.Assert.*
import org.junit.Test

class ArchiveCreationOptionsTest {
    @Test fun `changing from stored ZIP to compressed TAR never advertises no compression`() {
        for (format in listOf(ArchiveFormat.TAR_BZIP2, ArchiveFormat.TBZ2, ArchiveFormat.TAR_XZ, ArchiveFormat.TXZ)) {
            assertFalse(ArchiveCompressionLevel.STORE in archiveCompressionLevels(format))
            assertEquals(ArchiveCompressionLevel.DEFAULT, resolveArchiveCompressionLevel(format, ArchiveCompressionLevel.STORE))
        }
    }

    @Test fun `plain TAR always confirms no compression regardless of a previous format selection`() {
        for (level in ArchiveCompressionLevel.entries) {
            assertEquals(ArchiveCompressionLevel.STORE, resolveArchiveCompressionLevel(ArchiveFormat.TAR, level))
        }
        assertEquals(listOf(ArchiveCompressionLevel.STORE), archiveCompressionLevels(ArchiveFormat.TAR))
    }

    @Test fun `ZIP 7z and GZIP wrapped TAR keep their supported levels`() {
        for (format in listOf(ArchiveFormat.ZIP, ArchiveFormat.SEVEN_Z, ArchiveFormat.TAR_GZIP, ArchiveFormat.TGZ)) {
            for (level in ArchiveCompressionLevel.entries) assertEquals(level, resolveArchiveCompressionLevel(format, level))
        }
    }
}
