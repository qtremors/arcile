package dev.qtremors.arcile.core.storage.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ArchiveExtractionNamesTest {
    @Test fun `Browser and vault extraction strip the complete archive suffix consistently`() {
        for (name in listOf("Report.zip", "Report.ZIP", "Report.7z", "Report.tar.gz", "Report.TAR.GZ", "Report.tgz",
            "Report.tar.bz2", "Report.tar.xz")) {
            assertEquals("Report", archiveExtractionFolderName(name))
        }
        assertEquals("Archive", archiveExtractionFolderName(".zip"))
        assertEquals("Archive", archiveExtractionFolderName(""))
    }
}
