package dev.qtremors.arcile.feature.documents

import dev.qtremors.arcile.core.presentation.formatFileSize
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DocumentLibraryItemPresentationTest {

    @Test
    fun `grid mode produces single detail line with size and formatted date`() {
        val context = RuntimeEnvironment.getApplication()
        val size = 1048576L
        val lines = documentItemDetailLines(
            context = context,
            extension = "pdf",
            sizeBytes = size,
            lastModified = 1700000000000L,
            isGrid = true,
            dateFormatter = { "Nov 14, 2023" }
        )

        assertEquals(1, lines.size)
        assertEquals("${formatFileSize(context, size)} • Nov 14, 2023", lines[0])
    }

    @Test
    fun `list mode produces two detail lines with document type and size-date`() {
        val context = RuntimeEnvironment.getApplication()
        val size = 2048L
        val lines = documentItemDetailLines(
            context = context,
            extension = "docx",
            sizeBytes = size,
            lastModified = 1700000000000L,
            isGrid = false,
            dateFormatter = { "Nov 14, 2023" }
        )

        assertEquals(2, lines.size)
        assertEquals("DOCX", lines[0])
        assertEquals("${formatFileSize(context, size)} • Nov 14, 2023", lines[1])
    }

    @Test
    fun `list mode falls back to FILE when extension is blank`() {
        val context = RuntimeEnvironment.getApplication()
        val size = 512L
        val lines = documentItemDetailLines(
            context = context,
            extension = "",
            sizeBytes = size,
            lastModified = 1700000000000L,
            isGrid = false,
            dateFormatter = { "Nov 14, 2023" }
        )

        assertEquals(2, lines.size)
        assertEquals("FILE", lines[0])
        assertEquals("${formatFileSize(context, size)} • Nov 14, 2023", lines[1])
    }
}
