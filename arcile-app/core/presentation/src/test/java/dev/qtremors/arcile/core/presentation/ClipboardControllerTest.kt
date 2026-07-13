package dev.qtremors.arcile.core.presentation

import dev.qtremors.arcile.core.storage.domain.ClipboardOperation
import dev.qtremors.arcile.core.storage.domain.ClipboardRepository
import dev.qtremors.arcile.core.storage.domain.ClipboardState
import dev.qtremors.arcile.core.storage.domain.FileModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ClipboardControllerTest {
    private lateinit var repository: ClipboardRepository
    private lateinit var clipboard: MutableStateFlow<ClipboardState?>
    private lateinit var controller: ClipboardController

    @Before
    fun setup() {
        clipboard = MutableStateFlow(null)
        repository = mockk(relaxed = true)
        every { repository.clipboardState } returns clipboard
        every { repository.setClipboardState(any()) } answers {
            clipboard.value = firstArg()
        }
        every { repository.clearClipboardState() } answers {
            clipboard.value = null
        }
        controller = ClipboardController(repository)
    }

    @Test
    fun `store rejects empty input and preserves existing clipboard`() {
        val existing = ClipboardState(ClipboardOperation.COPY, listOf(file("old")))
        clipboard.value = existing

        assertFalse(controller.store(ClipboardOperation.CUT, emptyList()))

        assertEquals(existing, clipboard.value)
        verify(exactly = 0) { repository.setClipboardState(any()) }
    }

    @Test
    fun `store writes requested operation and files`() {
        val files = listOf(file("one"), file("two"))

        assertTrue(controller.store(ClipboardOperation.CUT, files))

        assertEquals(ClipboardOperation.CUT, clipboard.value?.operation)
        assertEquals(files, clipboard.value?.files)
    }

    @Test
    fun `remove updates remaining files and clears final item`() {
        clipboard.value = ClipboardState(ClipboardOperation.COPY, listOf(file("one"), file("two")))

        controller.remove("/one")
        assertEquals(listOf("/two"), clipboard.value?.files?.map(FileModel::absolutePath))

        controller.remove("/two")
        assertNull(clipboard.value)
    }

    private fun file(name: String) = FileModel(
        name = name,
        absolutePath = "/$name",
        size = 0,
        lastModified = 0,
        isDirectory = false,
        extension = ""
    )
}
