package dev.qtremors.arcile.buildlogic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProductionSemanticsStringsTest {
    @Test
    fun `conditional descriptions and action labels are checked`() {
        val source = """
            Modifier.semantics(mergeDescendants = true) {
                val type = if (folder) "Folder" else "File"
                contentDescription = buildString { append("Modified "); append(date) }
                onClick(label = if (selected) "Toggle selection" else "Open folder") { true }
                customActions = listOf(CustomAccessibilityAction(
                    label = "Select item",
                    action = { true }
                ))
            }
        """.trimIndent()
        assertEquals(setOf(1, 2, 3, 5), hardcodedSemanticsLines(source))
    }

    @Test
    fun `resources interpolated values comments and animation labels are allowed`() {
        val source = """
            animateFloatAsState(value, label = "Row scale")
            // semantics { contentDescription = "Comment" }
            val sample = "semantics { contentDescription = unrelated }"
            Modifier.semantics {
                contentDescription = "${'$'}{file.name}, ${'$'}{formattedDate}"
                /* contentDescription = "Comment" */
                onClick(label = if (folder) folderLabel else fileLabel) { true }
                customActions = listOf(CustomAccessibilityAction(
                    label = context.getString(R.string.select_item),
                    action = { true }
                ))
            }
        """.trimIndent()
        assertTrue(hardcodedSemanticsLines(source).isEmpty())
    }
}
