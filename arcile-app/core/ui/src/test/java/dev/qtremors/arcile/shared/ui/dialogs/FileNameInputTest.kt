package dev.qtremors.arcile.core.ui.dialogs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileNameInputTest {

    @Test
    fun `validation trims valid file names`() {
        val result = validateFileName("  report.txt  ")

        assertTrue(result.isValid)
        assertEquals("report.txt", result.sanitizedName)
    }

    @Test
    fun `validation rejects invalid characters and parent paths`() {
        assertEquals(FileNameValidationError.InvalidCharacters, validateFileName("bad/name").error)
        assertEquals(FileNameValidationError.ParentPath, validateFileName("../secret").error)
    }

    @Test
    fun `validation detects duplicates case insensitively`() {
        val result = validateFileName("report.pdf", existingNames = setOf("Report.pdf"))

        assertFalse(result.isValid)
        assertEquals(FileNameValidationError.Duplicate, result.error)
    }

    @Test
    fun `validation ignores current name when renaming`() {
        val result = validateFileName(
            value = "Report.pdf",
            existingNames = setOf("Report.pdf"),
            ignoredName = "report.pdf"
        )

        assertTrue(result.isValid)
    }
}
