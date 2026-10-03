package dev.qtremors.arcile.core.ui.texteditor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextEditorPersistenceTest {
    @Test
    fun `short writes exceptions and failed publication preserve original and complete draft`() {
        val root = java.nio.file.Files.createTempDirectory("text-recovery").toFile()
        try {
            val original = java.io.File(root, "document.txt").apply { writeText("original") }
            val draft = java.io.File(root, "document.draft")
            val edited = "complete edited document"
            writeTextDraft(draft, "original", edited)
            val attempts = listOf(
                persistAtomicText(original, edited, writeStaging = { file, bytes -> file.writeBytes(bytes.take(3).toByteArray()) }),
                persistAtomicText(original, edited, writeStaging = { file, _ -> file.writeText("partial"); throw java.io.IOException("disk full") }),
                persistAtomicText(original, edited, publish = { _, _ -> throw java.io.IOException("interrupted") })
            )
            attempts.forEach { assertTrue(it.isFailure) }
            assertEquals("original", original.readText())
            assertEquals(edited, readTextDraft(draft)?.text)
            assertTrue(persistAtomicText(original, edited).isSuccess)
            assertEquals(edited, original.readText())
        } finally { root.deleteRecursively() }
    }

    @Test
    fun `changed source retains a recoverable mismatching draft`() {
        val root = java.nio.file.Files.createTempDirectory("text-draft").toFile()
        try {
            val file = java.io.File(root, "draft")
            writeTextDraft(file, "original", "complete edits")
            val draft = requireNotNull(readTextDraft(file))
            assertTrue(!draft.matches("truncated"))
            assertEquals("complete edits", draft.text)
            assertEquals(draft, readTextDraft(file))
        } finally { root.deleteRecursively() }
    }

    @Test
    fun `a changed original during staging is not overwritten`() {
        val root = java.nio.file.Files.createTempDirectory("text-concurrent").toFile()
        try {
            val file = java.io.File(root, "text").apply { writeText("old") }
            val result = persistAtomicText(file, "new", writeStaging = { staged, bytes ->
                staged.writeBytes(bytes)
                file.writeText("external edit")
            })
            assertTrue(result.isFailure)
            assertEquals("external edit", file.readText())
        } finally { root.deleteRecursively() }
    }
    @Test
    fun `verified persistence writes the requested snapshot`() {
        var persisted = "old"

        val result = persistVerifiedText(
            content = "saved snapshot",
            write = { persisted = it },
            read = { persisted }
        )

        assertTrue(result.isSuccess)
        assertEquals("saved snapshot", persisted)
    }

    @Test
    fun `verified persistence rejects providers that retain stale content`() {
        val result = persistVerifiedText(
            content = "new",
            write = { },
            read = { "old" }
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun `word count handles blank and repeated whitespace`() {
        assertEquals(0, "  \n ".wordCount())
        assertEquals(3, "one  two\nthree".wordCount())
    }
}
